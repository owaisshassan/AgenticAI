package com.aira.guardian.rules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Read-only inspection of the TARGET project's {@code src/main/java} tree -
 * backs {@code list_existing_classes} and {@code get_class}. Assumes the
 * standard Maven layout (the same assumption {@code scaffold_class} makes
 * when deciding where to write a new file).
 */
public class SourceTree {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");

    public record ExistingClass(String className, String packageName, String relativePath) {
    }

    public Path sourceRoot(Path workspaceRoot) {
        return workspaceRoot.resolve("src").resolve("main").resolve("java");
    }

    /** Every .java file under the source root whose package matches the layer's glob, or empty if the tree doesn't exist yet. */
    public List<ExistingClass> listByLayer(Path workspaceRoot, RuleSet.Layer layer) throws IOException {
        Path root = sourceRoot(workspaceRoot);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<ExistingClass> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String packageName = extractPackage(file);
                String asPath = packageName.replace('.', '/');
                if (RuleSet.globMatches(layer.pkg(), asPath)) {
                    found.add(new ExistingClass(
                            classNameFromFile(file), packageName, root.relativize(file).toString()));
                }
            }
        }
        return found;
    }

    public record Lookup(List<ExistingClass> matches) {
    }

    /**
     * Finds a class by name (and, if given, exact package). Returns every
     * match found - zero, one, or more - so the caller can distinguish
     * "not found" from "ambiguous" without a separate exists() call.
     */
    public Lookup find(Path workspaceRoot, String className, String packageName) throws IOException {
        Path root = sourceRoot(workspaceRoot);
        if (!Files.isDirectory(root)) {
            return new Lookup(List.of());
        }
        List<ExistingClass> matches = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (!classNameFromFile(file).equals(className)) {
                    continue;
                }
                String actualPackage = extractPackage(file);
                if (packageName != null && !packageName.isBlank() && !packageName.equals(actualPackage)) {
                    continue;
                }
                matches.add(new ExistingClass(className, actualPackage, root.relativize(file).toString()));
            }
        }
        return new Lookup(matches);
    }

    public String readSource(Path workspaceRoot, ExistingClass existingClass) throws IOException {
        return Files.readString(sourceRoot(workspaceRoot).resolve(existingClass.relativePath()));
    }

    private String extractPackage(Path javaFile) throws IOException {
        for (String line : Files.readAllLines(javaFile)) {
            Matcher m = PACKAGE_PATTERN.matcher(line);
            if (m.find()) {
                return m.group(1);
            }
        }
        return "";
    }

    private String classNameFromFile(Path javaFile) {
        String fileName = javaFile.getFileName().toString();
        return fileName.substring(0, fileName.length() - ".java".length());
    }
}
