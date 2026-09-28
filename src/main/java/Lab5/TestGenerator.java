package Lab5;

import java.io.IOException;
import java.util.List;

/**
 * Seam between the pipeline and whatever actually produces test source -
 * the real implementation ({@link TestGeneratorTool}) calls the LLM
 * gateway; tests substitute a stub so the guardrail/approval/write/evaluate
 * wiring can be exercised without a live network call or real API spend.
 */
public interface TestGenerator {

    /** A brand-new test class for a service that has none yet. */
    String generate(ServiceTarget target) throws IOException, InterruptedException;

    /**
     * A complete, updated test class for a service that already has a test
     * file, but is missing coverage for the given method names - the
     * returned source must keep every existing test method and add new
     * ones for {@code missingMethods}.
     */
    String generateWithMissingMethods(ServiceTarget target, String existingTestSource, List<String> missingMethods)
            throws IOException, InterruptedException;

    /**
     * A corrected version of a test class that is currently failing.
     * {@code failures} is what actually failed, read from the real test
     * run's own report - never a guess. The returned source must only
     * change the TEST; it must never assume or describe a change to the
     * service under test (this agent has no ability to touch source code
     * regardless of what the correction reasons about).
     */
    String correct(ServiceTarget target, String currentTestSource, List<SurefireReportReader.FailureDetail> failures)
            throws IOException, InterruptedException;
}
