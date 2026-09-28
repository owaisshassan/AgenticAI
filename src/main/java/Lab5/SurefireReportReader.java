package Lab5;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads Surefire's own XML test report ({@code target/surefire-reports/
 * TEST-<QualifiedClass>.xml}) and extracts one {@link FailureDetail} per
 * failing/erroring test method - the "analyze the log file and check the
 * reason for failure" step. Never reimplements Surefire's own
 * pass/fail bookkeeping; just reads what it already wrote.
 */
public class SurefireReportReader {

    public record FailureDetail(
            String testMethod,
            String kind,
            String type,
            String message,
            String stackExcerpt
    ) {
    }

    private static final int MAX_STACK_EXCERPT_CHARS = 800;

    /** Empty list if the report doesn't exist yet (e.g. a timeout killed the run before Surefire could write it). */
    public List<FailureDetail> readFailures(Path reportsDir, String qualifiedTestClassName) throws IOException {
        Path xml = reportsDir.resolve("TEST-" + qualifiedTestClassName + ".xml");
        if (!Files.exists(xml)) {
            return List.of();
        }

        Document doc = parse(xml);
        NodeList testcases = doc.getElementsByTagName("testcase");
        List<FailureDetail> failures = new ArrayList<>();

        for (int i = 0; i < testcases.getLength(); i++) {
            Element testcase = (Element) testcases.item(i);
            String methodName = testcase.getAttribute("name");

            Element problem = firstElement(testcase, "failure");
            String kind = "failure";
            if (problem == null) {
                problem = firstElement(testcase, "error");
                kind = "error";
            }
            if (problem == null) {
                continue;
            }

            failures.add(new FailureDetail(
                    methodName,
                    kind,
                    problem.getAttribute("type"),
                    problem.getAttribute("message"),
                    excerpt(problem.getTextContent())));
        }
        return failures;
    }

    private Element firstElement(Element parent, String tagName) {
        NodeList matches = parent.getElementsByTagName(tagName);
        return matches.getLength() == 0 ? null : (Element) matches.item(0);
    }

    private String excerpt(String stackTrace) {
        if (stackTrace == null) {
            return "";
        }
        String trimmed = stackTrace.strip();
        return trimmed.length() > MAX_STACK_EXCERPT_CHARS
                ? trimmed.substring(0, MAX_STACK_EXCERPT_CHARS) + "...(truncated)"
                : trimmed;
    }

    private Document parse(Path xmlFile) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().parse(xmlFile.toFile());
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("failed to parse Surefire report " + xmlFile, e);
        }
    }
}
