package Lab5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurefireReportReaderTest {

    private final SurefireReportReader reader = new SurefireReportReader();

    @Test
    void readsAFailureWithMessageAndType(@TempDir Path dir) throws IOException {
        writeReport(dir, "demo.WidgetServiceTest",
                "  <testcase name=\"t\" classname=\"demo.WidgetServiceTest\">\n"
                        + "    <failure message=\"expected: &lt;2&gt; but was: &lt;1&gt;\" type=\"org.opentest4j.AssertionFailedError\">"
                        + "stack trace here</failure>\n"
                        + "  </testcase>\n");

        List<SurefireReportReader.FailureDetail> failures = reader.readFailures(dir, "demo.WidgetServiceTest");

        assertEquals(1, failures.size());
        assertEquals("t", failures.get(0).testMethod());
        assertEquals("failure", failures.get(0).kind());
        assertEquals("org.opentest4j.AssertionFailedError", failures.get(0).type());
        assertTrue(failures.get(0).message().contains("expected"));
    }

    @Test
    void readsAnErrorSeparatelyFromAFailure(@TempDir Path dir) throws IOException {
        writeReport(dir, "demo.WidgetServiceTest",
                "  <testcase name=\"t\" classname=\"demo.WidgetServiceTest\">\n"
                        + "    <error message=\"null\" type=\"java.lang.NullPointerException\">stack</error>\n"
                        + "  </testcase>\n");

        List<SurefireReportReader.FailureDetail> failures = reader.readFailures(dir, "demo.WidgetServiceTest");

        assertEquals("error", failures.get(0).kind());
        assertEquals("java.lang.NullPointerException", failures.get(0).type());
    }

    @Test
    void ignoresPassingTestcasesWithNoFailureOrErrorElement(@TempDir Path dir) throws IOException {
        writeReport(dir, "demo.WidgetServiceTest",
                "  <testcase name=\"passingOne\" classname=\"demo.WidgetServiceTest\"/>\n"
                        + "  <testcase name=\"failingOne\" classname=\"demo.WidgetServiceTest\">\n"
                        + "    <failure message=\"x\" type=\"y\">z</failure>\n"
                        + "  </testcase>\n");

        List<SurefireReportReader.FailureDetail> failures = reader.readFailures(dir, "demo.WidgetServiceTest");

        assertEquals(1, failures.size());
        assertEquals("failingOne", failures.get(0).testMethod());
    }

    @Test
    void returnsEmptyWhenReportDoesNotExist(@TempDir Path dir) throws IOException {
        assertTrue(reader.readFailures(dir, "demo.NoSuchTest").isEmpty());
    }

    @Test
    void truncatesAnOversizedStackTrace(@TempDir Path dir) throws IOException {
        String hugeStack = "at line ".repeat(500);
        writeReport(dir, "demo.WidgetServiceTest",
                "  <testcase name=\"t\" classname=\"demo.WidgetServiceTest\">\n"
                        + "    <failure message=\"x\" type=\"y\">" + hugeStack + "</failure>\n"
                        + "  </testcase>\n");

        List<SurefireReportReader.FailureDetail> failures = reader.readFailures(dir, "demo.WidgetServiceTest");

        assertTrue(failures.get(0).stackExcerpt().endsWith("...(truncated)"));
    }

    private void writeReport(Path dir, String qualifiedTestClass, String testcasesXml) throws IOException {
        Files.createDirectories(dir);
        String xml = "<?xml version=\"1.0\"?>\n<testsuite>\n" + testcasesXml + "</testsuite>\n";
        Files.writeString(dir.resolve("TEST-" + qualifiedTestClass + ".xml"), xml);
    }
}
