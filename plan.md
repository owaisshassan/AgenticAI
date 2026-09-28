# Plan: Unit Test Agent

## Goal
Reads a module, writes unit tests, runs them, and on failure analyzes the log, corrects the test, and reruns — reports remaining failures if correction doesn't resolve them. It never edits source code.

## Scope & Constraints
- **Input**: one existing source module/class (e.g. a file under `src/main/java/...`).
- **Output**: a corresponding JUnit test class under `src/test/java/...`, plus a run log.
- **Hard constraint**: the agent MUST NOT modify any file under `src/main/java` or `pom.xml` production dependencies/build config in a way that changes runtime behavior. It may only:
  - Create/update files under `src/test/java`.
  - Add test-scoped dependencies (e.g. JUnit 5, Mockito) to `pom.xml` if missing, since the project currently has no test framework configured.
- If writing a passing test would require changing source code (e.g. to make something testable), the agent stops and asks for human intervention instead of editing source.
- On a failing test run, the agent may analyze the failure and correct the **test file only**, then rerun — up to **3 correction attempts** per module, regardless of whether the failure looks like a source bug or a bad test assumption. Source code is never touched during correction, even when the failure looks like a genuine source bug — a suspected source bug still only earns a test-side correction attempt (e.g. asserting the actual buggy behavior, or a skip with a documented reason) followed by escalation if that's not appropriate; the agent does not decide to fix source on the human's behalf.
- If all 3 attempts still fail, halt and escalate to a human — do not attempt a 4th time and do not weaken/delete a test just to force a pass.
- One module per run. No batch/bulk mode in v1.

## Steps

1. **Select target module**
   - Human (or caller) specifies the class/file to test, e.g. `src/main/java/part1/RunnerUtil.java`.
   - Confirm the file exists and is a `.java` source file. If not found → failure log, stop.

2. **Read and analyze the module**
   - Read the full source file.
   - Identify: package, public/package-private classes, public methods, constructors, static vs instance members, external dependencies (I/O, network, env vars) that will need test doubles or mocking.
   - Note any code that is inherently hard to unit test (e.g. hits a live network call with no seam for mocking). Record this — it may lead to a failure/intervention case later rather than editing source to add a seam.

3. **Check/prepare test infra (test-only changes)**
   - Check `pom.xml` for JUnit (and Mockito if mocking is needed).
   - If missing, add test-scoped dependencies only (JUnit Jupiter, Mockito if needed) and confirm Surefire is present/default-configured for Maven.
   - This is the only permitted edit outside `src/test/java`.

4. **Write unit tests**
   - Create/overwrite `src/test/java/<package-path>/<ClassName>Test.java`.
   - Cover: normal/happy-path behavior, edge cases (nulls, empty inputs, boundary values), and error conditions the method is documented/observed to throw.
   - Use test doubles/mocks for external dependencies instead of touching source for testability.
   - Never modify the file under test.

5. **Run the tests**
   - Execute `mvn -q -Dtest=<ClassName>Test test` (or `mvn test` for the whole module if scoped test run isn't practical).
   - Capture full stdout/stderr and the Surefire XML/text report under `target/surefire-reports/`.

6. **Evaluate results**
   - Parse Surefire output for pass/fail/error counts.
   - Branch:
     - **All tests pass** → go to Success Logging (step 7).
     - **Any test fails, errors, or the build itself fails to compile** → go to Self-Correction (step 6a) before ever asking a human.

6a. **Self-correction on failure (test-only, max 3 attempts)**
   - Analyze the failure: read the Surefire report/stack trace and the relevant part of the failing test to determine the root cause (e.g. wrong expected value, bad fixture/mock setup, wrong argument passed to the method under test, incorrect assumption about behavior — including cases where the module's actual behavior is simply different from what the test assumed).
   - Write a corrected version of the failing test(s) only. Never edit the source file, even if the analysis suggests the source itself has a bug — a corrected test may assert the source's actual (possibly buggy) behavior, or skip that specific case with a comment stating why, but the agent does not patch source to make behavior match the test.
   - Rerun the tests (repeat step 5).
   - If they now pass → go to Success Logging (step 7), and note in the log that correction attempt(s) were needed.
   - If they still fail and fewer than 3 attempts have been made → repeat step 6a.
   - If 3 attempts have been made and tests still fail → go to Failure Logging (step 8) and request human intervention. Do not attempt a 4th time, and do not delete/weaken the test just to force a pass.

7. **Success logging**
   - Append an entry to `test-agent.log` (or console) with:
     - Timestamp
     - Module tested (file path)
     - Test file created/updated (path)
     - Number of tests written, number passed
     - Short summary of what was covered
     - If self-correction was needed: number of correction attempts, and a one-line reason per attempt (e.g. "attempt 1: fixed wrong expected model name in request fixture")
   - Report completion to the human; no further action needed.

8. **Failure logging**
   - Append an entry to `test-agent.log` with:
     - Timestamp
     - Module tested
     - Test file path
     - Which test(s) failed/errored, with the assertion/exception message and stack trace excerpt, for the final attempt
     - Whether the failure indicates a likely **bug in source** vs a **bad test assumption** (best-effort classification, not a fix)
     - A log of all correction attempts made (up to 3), what was changed in the test each time, and why it still failed
   - **Halt.** Explicitly flag: "Human intervention required" — do not modify source code to make the test pass, and do not modify the test further to hide the failure once 3 attempts are exhausted.
   - Present the failure log to the human and wait for their decision (fix source, fix test, accept as known issue, etc.) before doing anything further with that module.

## Success Log Format (example)
```
[2026-09-19 10:15:03] SUCCESS
Module: src/main/java/part1/RunnerUtil.java
Tests written: src/test/java/part1/RunnerUtilTest.java
Result: 6/6 passed
Correction attempts: 1
  Attempt 1: fixed wrong model name in test fixture (used 'claude-sonnet-5', gateway only allows 'claude-sonnet') — rerun passed.
Coverage notes: constructor validation, null-arg handling, happy path for main utility method
```

## Failure Log Format (example)
```
[2026-09-19 10:22:47] FAILURE — HUMAN INTERVENTION REQUIRED
Module: src/main/java/part2/WeatherConnector.java
Tests written: src/test/java/part2/WeatherConnectorTest.java
Result: 4/5 passed, 1 failed (after 3 correction attempts)
Failed test: shouldParseValidResponse
Reason: NullPointerException at WeatherConnector.java:42 (getTemperature on null response body)
Classification: likely source bug (no null-check on API response)
Correction attempts:
  Attempt 1: adjusted fixture to send a non-null but empty body — still NPE, root cause is elsewhere.
  Attempt 2: asserted expected exception (NullPointerException) instead of a parsed result — rejected on review, masks a real bug rather than testing intended behavior.
  Attempt 3: reverted to original assertion, tried isolating the null field — still fails at line 42.
Action: awaiting human decision — no source code changes made.
```

## Out of Scope (v1)
- Automatically fixing source code to satisfy tests.
- Automatically weakening/deleting tests to force a pass instead of a genuine correction.
- Multi-module/bulk test generation in a single run.
- Mutation testing or coverage-threshold enforcement.
- More than 3 correction attempts per module per run.
