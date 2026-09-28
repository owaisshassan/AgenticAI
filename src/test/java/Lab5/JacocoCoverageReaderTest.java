package Lab5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JacocoCoverageReaderTest {

    private final JacocoCoverageReader reader = new JacocoCoverageReader();

    private static final String SAMPLE_REPORT = """
            <?xml version="1.0" encoding="UTF-8" standalone="no"?>
            <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">
            <report name="test">
              <package name="com/acme">
                <class name="com/acme/FooService">
                  <method name="doThing">
                    <counter type="LINE" missed="0" covered="4"/>
                  </method>
                  <counter type="LINE" missed="1" covered="9"/>
                </class>
              </package>
            </report>
            """;

    @Test
    void readsCoveragePercentForAKnownClass(@TempDir Path dir) throws IOException {
        Path xml = writeReport(dir);

        JacocoCoverageReader.CoverageResult result = reader.readClassCoverage(xml, "com.acme.FooService");

        assertTrue(result.found());
        assertEquals(9, result.coveredLines());
        assertEquals(1, result.missedLines());
        assertEquals(90.0, result.percent(), 0.01);
    }

    @Test
    void returnsNotFoundForAnUnknownClass(@TempDir Path dir) throws IOException {
        Path xml = writeReport(dir);

        JacocoCoverageReader.CoverageResult result = reader.readClassCoverage(xml, "com.acme.BarService");

        assertFalse(result.found());
    }

    @Test
    void returnsNotFoundWhenReportFileDoesNotExist(@TempDir Path dir) throws IOException {
        Path missing = dir.resolve("does-not-exist.xml");

        JacocoCoverageReader.CoverageResult result = reader.readClassCoverage(missing, "com.acme.FooService");

        assertFalse(result.found());
    }

    private Path writeReport(Path dir) throws IOException {
        Path xml = dir.resolve("jacoco.xml");
        Files.writeString(xml, SAMPLE_REPORT);
        return xml;
    }
}
