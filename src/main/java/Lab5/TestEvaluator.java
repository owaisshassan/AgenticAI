package Lab5;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Evaluation element (mandatory #2): runs the generated test for real and
 * scores it - never just checks that a *Test.java file exists. Two gates,
 * both must clear: the suite actually passes, and it clears a minimum
 * line-coverage threshold on the target class, read from JaCoCo's own XML
 * report rather than reimplemented here.
 */
public class TestEvaluator {

    public record EvaluationResult(
            boolean testsPassed,
            double coveragePercent,
            double minCoveragePercent,
            boolean coverageMet,
            String rawOutput
    ) {
        public boolean passed() {
            return testsPassed && coverageMet;
        }
    }

    private final ProcessRunner processRunner;
    private final JacocoCoverageReader coverageReader;
    private final double minCoveragePercent;
    private final Duration testTimeout;

    public TestEvaluator(ProcessRunner processRunner, JacocoCoverageReader coverageReader,
                          double minCoveragePercent, Duration testTimeout) {
        this.processRunner = processRunner;
        this.coverageReader = coverageReader;
        this.minCoveragePercent = minCoveragePercent;
        this.testTimeout = testTimeout;
    }

    public EvaluationResult evaluate(Path projectRoot, ServiceTarget target) throws IOException {
        String testClassSimpleName = target.className() + "Test";

        ProcessRunner.Result mvnResult = processRunner.run(
                List.of("mvn", "-q", "-Dtest=" + testClassSimpleName, "test"),
                projectRoot, testTimeout);

        if (!mvnResult.succeeded()) {
            return new EvaluationResult(false, 0.0, minCoveragePercent, false,
                    mvnResult.timedOut()
                            ? "mvn test timed out after " + testTimeout
                            : mvnResult.stdout() + "\n" + mvnResult.stderr());
        }

        Path jacocoXml = projectRoot.resolve("target/site/jacoco/jacoco.xml");
        JacocoCoverageReader.CoverageResult coverage =
                coverageReader.readClassCoverage(jacocoXml, target.qualifiedName());

        boolean coverageMet = coverage.found() && coverage.percent() >= minCoveragePercent;
        return new EvaluationResult(true, coverage.percent(), minCoveragePercent, coverageMet, mvnResult.stdout());
    }
}
