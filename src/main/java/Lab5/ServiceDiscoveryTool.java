package Lab5;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Read-only tool: finds services under a source root and reports, for
 * each, whether a test file already exists. "Service" here means any
 * public class whose name matches {@code *Service} - narrow on purpose
 * (matches this lab's stated scope: enforce test writing on SERVICES, not
 * every class in the tree).
 */
public class ServiceDiscoveryTool implements Tool {

    private static final Pattern SERVICE_CLASS_NAME = Pattern.compile(".*Service$");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern PUBLIC_CLASS = Pattern.compile("public\\s+class\\s+(\\w+)");

    /** A discovered service paired with its existing test file, if any (null if there is none yet). */
    public record Discovery(ServiceTarget target, Path existingTestFile) {
        public boolean hasExistingTest() {
            return existingTestFile != null;
        }
    }

    @Override
    public String name() {
        return "service_discovery";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    /** Every *Service class under sourceRoot, each paired with its existing test file if one exists. */
    public List<Discovery> discoverAll(Path sourceRoot, Path testRoot) throws IOException {
        List<Discovery> discoveries = new ArrayList<>();
        if (!Files.isDirectory(sourceRoot)) {
            return discoveries;
        }

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String className = classNameFromFile(file);
                if (className == null || !SERVICE_CLASS_NAME.matcher(className).matches()) {
                    continue;
                }
                String packageName = extractPackage(file);
                Path existingTest = existingTestFile(testRoot, packageName, className);
                discoveries.add(new Discovery(new ServiceTarget(file, packageName, className), existingTest));
            }
        }
        return discoveries;
    }

    /** Every *Service class under sourceRoot that has no sibling *ServiceTest(s).java under testRoot. */
    public List<ServiceTarget> findUntested(Path sourceRoot, Path testRoot) throws IOException {
        return discoverAll(sourceRoot, testRoot).stream()
                .filter(d -> !d.hasExistingTest())
                .map(Discovery::target)
                .toList();
    }

    private Path existingTestFile(Path testRoot, String packageName, String className) {
        Path packageDir = testRoot.resolve(packageName.replace('.', '/'));
        Path testFile = packageDir.resolve(className + "Test.java");
        if (Files.exists(testFile)) {
            return testFile;
        }
        Path testsFile = packageDir.resolve(className + "Tests.java");
        return Files.exists(testsFile) ? testsFile : null;
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

    private String classNameFromFile(Path javaFile) throws IOException {
        for (String line : Files.readAllLines(javaFile)) {
            Matcher m = PUBLIC_CLASS.matcher(line);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }
}
