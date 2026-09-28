package Lab5;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only tool: given a service's source and its EXISTING test file's
 * source, finds public methods the test file never exercises. Backs the
 * "enforce test cases for missing test cases as well" requirement - a
 * service can already have a test file and still be under-enforced if that
 * file only covers some of its public methods.
 *
 * Deliberately text-based rather than a full parser: it checks whether the
 * test source contains a call-shaped reference to the method name
 * ("methodName(") anywhere. That is conservative in the tool's favor - a
 * method genuinely exercised only via a helper/indirect call could be
 * mis-flagged as missing - so a false positive here costs an extra
 * (human-approved) test-case addition, never a false "fully covered".
 */
public class MissingTestCaseDetector implements Tool {

    private static final Pattern PUBLIC_METHOD = Pattern.compile(
            "public\\s+(?:static\\s+)?(?:final\\s+)?[\\w<>\\[\\],. ]+?\\s+(\\w+)\\s*\\(");

    @Override
    public String name() {
        return "missing_test_case_detector";
    }

    @Override
    public boolean readOnly() {
        return true;
    }

    /** Public method names declared on the service that the test source never calls. */
    public List<String> findMissingMethods(String serviceSource, String className, String existingTestSource) {
        Set<String> declared = extractPublicMethodNames(serviceSource, className);
        return declared.stream()
                .filter(method -> !existingTestSource.contains(method + "("))
                .toList();
    }

    private Set<String> extractPublicMethodNames(String source, String className) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PUBLIC_METHOD.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!name.equals(className)) {
                names.add(name);
            }
        }
        return names;
    }
}
