package com.aira.guardian.arch;

import com.aira.guardian.rules.RuleSet;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.library.Architectures;

import java.nio.file.Path;
import java.util.List;

/**
 * Derives an ArchUnit layered-architecture rule from guardian-rules.yaml's
 * {@code layers:} section and checks it against the target project's
 * compiled classes - backs {@code run_architecture_check}.
 *
 * Requires the target project to already be compiled (target/classes) -
 * ArchUnit inspects bytecode, not source. If it isn't compiled yet, the
 * caller gets a clear compiled_classes_missing error rather than a cryptic
 * "no classes found" from ArchUnit itself.
 */
public class ArchRules {

    public record Violation(String description) {
    }

    public record CheckResult(boolean passed, List<Violation> violations) {
    }

    public CheckResult check(Path compiledClassesDir, RuleSet rules) {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPath(compiledClassesDir);

        Architectures.LayeredArchitecture architecture = buildArchitecture(rules);
        EvaluationResult result = architecture.evaluate(classes);

        if (!result.hasViolation()) {
            return new CheckResult(true, List.of());
        }

        List<Violation> violations = result.getFailureReport().getDetails().stream()
                .map(Violation::new)
                .toList();
        return new CheckResult(false, violations);
    }

    private Architectures.LayeredArchitecture buildArchitecture(RuleSet rules) {
        Architectures.LayeredArchitecture architecture = Architectures.layeredArchitecture()
                .consideringAllDependencies();

        for (RuleSet.Layer layer : rules.layers()) {
            architecture = architecture.layer(layer.name()).definedBy(toArchUnitPattern(layer.pkg()));
        }

        for (RuleSet.Layer layer : rules.layers()) {
            String[] allowedAccessors = accessorsOf(rules, layer.name());
            architecture = allowedAccessors.length == 0
                    ? architecture.whereLayer(layer.name()).mayNotBeAccessedByAnyLayer()
                    : architecture.whereLayer(layer.name()).mayOnlyBeAccessedByLayers(allowedAccessors);
        }

        return architecture;
    }

    /**
     * Layers that declare {@code layer.name()} in their own mayCallLayers -
     * i.e. the inverse index needed for ArchUnit's "may only be accessed by"
     * phrasing, since guardian-rules.yaml expresses the relationship from
     * the caller's side ("service mayCallLayers: [repository]") while
     * ArchUnit's fluent API wants it from the callee's side ("repository
     * mayOnlyBeAccessedByLayers(service)").
     */
    private String[] accessorsOf(RuleSet rules, String targetLayerName) {
        return rules.layers().stream()
                .filter(l -> l.mayCallLayers() != null && l.mayCallLayers().contains(targetLayerName))
                .map(RuleSet.Layer::name)
                .toArray(String[]::new);
    }

    /** guardian-rules.yaml globs ("**{@code /}service/**") -> ArchUnit's package-matching syntax ("..service.."). */
    private String toArchUnitPattern(String glob) {
        String normalized = glob.replace('/', '.');
        if (normalized.startsWith("**.")) {
            normalized = ".." + normalized.substring(3);
        } else if (normalized.startsWith("**")) {
            normalized = ".." + normalized.substring(2);
        }
        if (normalized.endsWith(".**")) {
            normalized = normalized.substring(0, normalized.length() - 3) + "..";
        } else if (normalized.endsWith("**")) {
            normalized = normalized.substring(0, normalized.length() - 2) + "..";
        }
        return normalized;
    }
}
