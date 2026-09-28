package Lab5;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Reads line-coverage percentage for one class out of JaCoCo's own XML
 * report - never reimplements coverage math, per this lab's evaluation
 * requirement. Minimal: this lab needs one number (line coverage % for the
 * targeted class), not JaCoCo's full report model.
 */
public class JacocoCoverageReader {

    public record CoverageResult(boolean found, int coveredLines, int missedLines) {
        public double percent() {
            int total = coveredLines + missedLines;
            return total == 0 ? 0.0 : (100.0 * coveredLines) / total;
        }
    }

    public CoverageResult readClassCoverage(Path jacocoXmlReport, String qualifiedClassName) throws IOException {
        if (!Files.exists(jacocoXmlReport)) {
            return new CoverageResult(false, 0, 0);
        }

        Document doc = parse(jacocoXmlReport);
        NodeList classNodes = doc.getElementsByTagName("class");

        for (int i = 0; i < classNodes.getLength(); i++) {
            Element classElement = (Element) classNodes.item(i);
            String name = classElement.getAttribute("name").replace('/', '.');
            if (!name.equals(qualifiedClassName)) {
                continue;
            }
            return lineCounterOf(classElement)
                    .map(c -> new CoverageResult(true, c[0], c[1]))
                    .orElse(new CoverageResult(true, 0, 0));
        }
        return new CoverageResult(false, 0, 0);
    }

    /**
     * Only DIRECT-CHILD {@code <counter>} elements of {@code <class>} count
     * here - {@code getElementsByTagName} recurses into descendants, and a
     * class element also contains per-method {@code <counter>} children
     * (nested under {@code <method>}), which report that one method's line
     * count, not the whole class's. Taking the first match without this
     * filter silently scores a single method's coverage as if it were the
     * class's overall coverage.
     */
    private Optional<int[]> lineCounterOf(Element classElement) {
        NodeList children = classElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE || !"counter".equals(child.getNodeName())) {
                continue;
            }
            Element counter = (Element) child;
            if ("LINE".equals(counter.getAttribute("type"))) {
                int covered = Integer.parseInt(counter.getAttribute("covered"));
                int missed = Integer.parseInt(counter.getAttribute("missed"));
                return Optional.of(new int[]{covered, missed});
            }
        }
        return Optional.empty();
    }

    /**
     * JaCoCo's own XML report declares a DOCTYPE referencing report.dtd, so
     * doctype declarations can't be disallowed outright here (unlike a
     * generic untrusted-XML parse) - instead, external entity/DTD fetching
     * is disabled so a tampered report can't trigger an XXE fetch, while a
     * legitimate JaCoCo report still parses.
     */
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
            throw new IOException("failed to parse JaCoCo report " + xmlFile, e);
        }
    }
}
