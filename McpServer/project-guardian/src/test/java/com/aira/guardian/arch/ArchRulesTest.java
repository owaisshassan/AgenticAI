package com.aira.guardian.arch;

import com.aira.guardian.rules.RuleSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ArchRulesTest {

    private final ArchRules archRules = new ArchRules();

    private RuleSet rulesWithLayers() {
        return new RuleSet(
                List.of(
                        new RuleSet.Layer("controller", "**/controller/**", List.of("service")),
                        new RuleSet.Layer("service", "**/service/**", List.of("repository")),
                        new RuleSet.Layer("repository", "**/repository/**", List.of())
                ),
                Map.of(), Map.of(),
                new RuleSet.TestGate(List.of(), List.of(), 0, 0, false)
        );
    }

    @Test
    void legalLayeredDependenciesPassTheCheck(@TempDir Path tempDir) throws IOException {
        Path compiled = compileFixture(tempDir, """
                package fixture.controller;
                import fixture.service.WidgetService;
                public class WidgetController {
                    private final WidgetService service;
                    public WidgetController(WidgetService service) { this.service = service; }
                }
                """, """
                package fixture.service;
                import fixture.repository.WidgetRepository;
                public class WidgetService {
                    private final WidgetRepository repo;
                    public WidgetService(WidgetRepository repo) { this.repo = repo; }
                }
                """, """
                package fixture.repository;
                public class WidgetRepository {
                }
                """);

        ArchRules.CheckResult result = archRules.check(compiled, rulesWithLayers());

        assertThat(result.passed()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void reverseLayerCallIsCaughtAsAViolation(@TempDir Path tempDir) throws IOException {
        Path compiled = compileFixture(tempDir, """
                package fixture.repository;
                import fixture.service.WidgetService;
                public class WidgetRepository {
                    private final WidgetService service;
                    public WidgetRepository(WidgetService service) { this.service = service; }
                }
                """, """
                package fixture.service;
                public class WidgetService {
                }
                """);

        ArchRules.CheckResult result = archRules.check(compiled, rulesWithLayers());

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).isNotEmpty();
    }

    /** Compiles the given source bodies into tempDir/classes and returns that directory. */
    private Path compileFixture(Path tempDir, String... sources) throws IOException {
        Path srcDir = tempDir.resolve("src");
        Path outDir = tempDir.resolve("classes");
        Files.createDirectories(outDir);

        List<Path> files = new java.util.ArrayList<>();
        for (String source : sources) {
            String packageLine = source.strip().lines().findFirst().orElse("");
            String pkg = packageLine.replace("package", "").replace(";", "").trim();
            String className = source.lines()
                    .filter(l -> l.contains("class "))
                    .findFirst()
                    .map(l -> l.substring(l.indexOf("class ") + 6).split("[ {]")[0])
                    .orElseThrow();

            Path pkgDir = srcDir.resolve(pkg.replace('.', '/'));
            Files.createDirectories(pkgDir);
            Path file = pkgDir.resolve(className + ".java");
            Files.writeString(file, source);
            files.add(file);
        }

        List<String> args = new java.util.ArrayList<>(List.of("-d", outDir.toString()));
        files.forEach(f -> args.add(f.toString()));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null, args.toArray(String[]::new));
        if (result != 0) {
            throw new IOException("fixture compilation failed");
        }
        return outDir;
    }
}
