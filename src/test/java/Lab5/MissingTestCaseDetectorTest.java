package Lab5;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissingTestCaseDetectorTest {

    private final MissingTestCaseDetector detector = new MissingTestCaseDetector();

    private static final String SERVICE_SOURCE =
            "package demo;\n\npublic class WidgetService {\n"
                    + "    public int size() { return 1; }\n"
                    + "    public int weight() { return 2; }\n"
                    + "    private int helper() { return 0; }\n"
                    + "}\n";

    @Test
    void findsMethodsNeverCalledByTheExistingTest() {
        String testSource = "public class WidgetServiceTest {\n"
                + "    @Test void t() { assertEquals(1, new WidgetService().size()); }\n}\n";

        List<String> missing = detector.findMissingMethods(SERVICE_SOURCE, "WidgetService", testSource);

        assertEquals(List.of("weight"), missing);
    }

    @Test
    void returnsEmptyWhenEveryPublicMethodIsAlreadyCalled() {
        String testSource = "public class WidgetServiceTest {\n"
                + "    @Test void t() {\n"
                + "        assertEquals(1, new WidgetService().size());\n"
                + "        assertEquals(2, new WidgetService().weight());\n"
                + "    }\n}\n";

        assertTrue(detector.findMissingMethods(SERVICE_SOURCE, "WidgetService", testSource).isEmpty());
    }

    @Test
    void ignoresPrivateMethodsAndTheConstructor() {
        String testSource = "public class WidgetServiceTest {\n"
                + "    @Test void t() { new WidgetService(); }\n}\n";

        List<String> missing = detector.findMissingMethods(SERVICE_SOURCE, "WidgetService", testSource);

        assertEquals(List.of("size", "weight"), missing);
    }
}
