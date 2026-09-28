package com.aira.guardian.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SourceTreeTest {

    private final SourceTree sourceTree = new SourceTree();

    @Test
    void listByLayerFindsClassesMatchingTheLayerPackageGlob(@TempDir Path workspace) throws IOException {
        writeClass(workspace, "com.acme.service", "WidgetService");
        writeClass(workspace, "com.acme.repository", "WidgetRepository");

        RuleSet.Layer serviceLayer = new RuleSet.Layer("service", "**/service/**", java.util.List.of());
        var found = sourceTree.listByLayer(workspace, serviceLayer);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).className()).isEqualTo("WidgetService");
        assertThat(found.get(0).packageName()).isEqualTo("com.acme.service");
    }

    @Test
    void listByLayerReturnsEmptyWhenSourceTreeDoesNotExistYet(@TempDir Path workspace) throws IOException {
        RuleSet.Layer serviceLayer = new RuleSet.Layer("service", "**/service/**", java.util.List.of());
        assertThat(sourceTree.listByLayer(workspace, serviceLayer)).isEmpty();
    }

    @Test
    void findLocatesClassByNameAndPackage(@TempDir Path workspace) throws IOException {
        writeClass(workspace, "com.acme.service", "WidgetService");

        var lookup = sourceTree.find(workspace, "WidgetService", "com.acme.service");

        assertThat(lookup.matches()).hasSize(1);
    }

    @Test
    void findReturnsEmptyWhenNoMatch(@TempDir Path workspace) throws IOException {
        var lookup = sourceTree.find(workspace, "DoesNotExist", null);
        assertThat(lookup.matches()).isEmpty();
    }

    @Test
    void findReturnsMultipleMatchesWhenPackageNotGivenAndNameIsAmbiguous(@TempDir Path workspace) throws IOException {
        writeClass(workspace, "com.acme.a", "Widget");
        writeClass(workspace, "com.acme.b", "Widget");

        var lookup = sourceTree.find(workspace, "Widget", null);

        assertThat(lookup.matches()).hasSize(2);
    }

    @Test
    void readSourceReturnsFileContent(@TempDir Path workspace) throws IOException {
        writeClass(workspace, "com.acme.service", "WidgetService");

        var lookup = sourceTree.find(workspace, "WidgetService", "com.acme.service");
        String source = sourceTree.readSource(workspace, lookup.matches().get(0));

        assertThat(source).contains("package com.acme.service;");
        assertThat(source).contains("class WidgetService");
    }

    private void writeClass(Path workspace, String pkg, String className) throws IOException {
        Path dir = workspace.resolve("src/main/java").resolve(pkg.replace('.', '/'));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(className + ".java"),
                "package " + pkg + ";\n\npublic class " + className + " {\n}\n");
    }
}
