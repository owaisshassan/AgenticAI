# Lab5 — Test Enforcer Agent

An agent that enforces test-case writing on `*Service` classes: it finds
services with **no test at all**, and services whose **existing test is
missing coverage for some public method** — generates or extends a test
for either case, runs it for real, scores it against a coverage threshold,
and if the run fails, analyzes the real failure and corrects the test
(bounded retries) before giving up. It never writes anything without
explicit human approval. Built with four mandatory elements wired directly
into its pipeline — **guardrails**, **evaluation**, **trace**, and **human
approval** — not bolted on as an afterthought.

See `RUNBOOK.md` for a concrete, step-by-step walkthrough of one run
against a single new service.

## 1. How to run this agent

### Requirements

- Java 17+, Maven (builds from the repo's root `pom.xml` — Lab5 is not a
  separate module).
- No `.env` or credentials setup needed: the LLM gateway URL/key are the
  same hardcoded constants `part1.RunnerUtil` already uses
  (`API_URL`/`API_KEY`), which `TestGeneratorTool` calls directly.
- The target codebase to enforce tests on is **this repo itself** by
  default: `src/main/java` is scanned for `*Service` classes, and any test
  it writes/extends goes under `src/test/java`.

### Build

From the repo root (`D:\AgenticAI\AgenticAI_p01`):

```bash
mvn compile
```

### Run interactively

```bash
mvn exec:java -Dexec.mainClass=Lab5.TestEnforcerAgent
```

(or run `Lab5.TestEnforcerAgent`'s `main` method directly from your IDE).

For every `*Service` class it finds — whether it has no test yet, or has
one that's missing coverage — the agent will:

1. Print a preview of the generated/extended/corrected test and ask
   `Proceed? [y/N]` on the console.
2. Type `y` and press Enter to approve the write, or anything else (or just
   Enter) to refuse it.

If a written test then fails its real run, the agent analyzes the failure
itself and asks for approval again before applying a correction — up to 3
attempts per service.

If there is no terminal attached (e.g. run non-interactively with stdin
closed), approval is refused automatically — the agent fails closed rather
than assuming consent.

On completion it prints one outcome line per service found, e.g.:

```
com.acme.billing.InvoiceService -> WRITTEN_AND_PASSED (testsPassed=true, coverage=92.0% (min 80.0%), correctionAttempts=0)
com.acme.billing.RefundService -> MISSING_METHODS_ADDED_AND_PASSED (testsPassed=true, coverage=85.0% (min 80.0%), correctionAttempts=0)
com.acme.billing.LedgerService -> ALREADY_COMPLETE (existing test already covers every public method, correctionAttempts=0)
Trace written to: /path/to/repo/Lab5-trace-1758870000000.ndjson
```

### Run it against a different target project

The constructor takes the project root, source root, and test root
explicitly, so it isn't hardcoded to this repo — point it at any other
Maven-shaped project:

```java
TestEnforcerAgent agent = new TestEnforcerAgent(
        Path.of("/path/to/other-project"),
        Path.of("/path/to/other-project/src/main/java"),
        Path.of("/path/to/other-project/src/test/java"),
        Path.of("/path/to/other-project/trace.ndjson"),
        80.0,                        // minimum line-coverage percent to pass
        Duration.ofSeconds(120),     // hard timeout on the `mvn test` subprocess
        new Scanner(System.in), System.out);

agent.run();
```

### Run the tests (no network, no live approval needed)

```bash
mvn test -Dtest="Lab5/**"
```

(`-Dtest=Lab5.*` looks reasonable but does **not** work with this project's
Surefire version — it reports "No tests matching pattern" even though the
classes exist. Use the path-style glob above, or list the class names
directly.)

51 tests cover every element in isolation (`GuardrailsTest`, `AgentTraceTest`,
`HumanApprovalTest`, `ServiceDiscoveryToolTest`, `MissingTestCaseDetectorTest`,
`SurefireReportReaderTest`, `TestWriterToolTest`, `JacocoCoverageReaderTest`,
`ProcessRunnerTest`) plus 8 full-pipeline integration tests
(`TestEnforcerAgentTest`) that stub the LLM call and the `mvn test`
subprocess so the whole discover → guardrail → generate/extend/correct →
guardrail → approval → write → evaluate flow — including the
missing-method extension and the analyze-and-correct retry loop — can be
verified without spending real API calls or real build time.

### Uploading to AWS Bedrock / S3

`S3ArtifactUploader` (an AWS SDK v2 `S3Client` wrapper) uploads a run's
trace file to S3 — the hand-off point for a Bedrock Agent to pick up later:

```java
S3Client s3 = S3Client.create(); // picks up standard AWS credential chain
S3ArtifactUploader uploader = new S3ArtifactUploader(s3, "my-bucket");
uploader.uploadTrace(agent.trace().traceFile(), "lab5-runs/2026-09-26");
```

`src/main/resources/Lab5/bedrock-agent-manifest.json` is the agent
definition to register in Bedrock — model, instructions, and the same
guardrail/evaluation/self-correction/approval policy described below, in a
shape a Bedrock Agent config expects. This isn't uploaded automatically by
any code here; it's the manifest you hand to Bedrock when creating the
Agent resource.

## 2. How this agent works

### Pipeline

For every service `discover` finds, one of two starting branches, both
converging on the same guardrail → approval → write → evaluate spine, with
a bounded self-correction loop hanging off the end:

```
discover
  no existing test    -> guardrail(input) -> generate              --\
  has existing test     -> detect missing methods                    |
                             none missing  -> ALREADY_COMPLETE, done  |
                             some missing  -> guardrail(input)        |
                                              -> generateWithMissing  |
                                                 Methods            --/
                                                                       v
                        guardrail(output) -> human approval -> write/overwrite -> evaluate
                                                                                      |
                                                            passed <-------- +-- failed
                                                              |                       |
                                                             done      analyze real Surefire report
                                                                             -> correct -> guardrail(output)
                                                                             -> human approval -> overwrite
                                                                             -> evaluate again
                                                                     (repeat up to MAX_CORRECTION_ATTEMPTS=3,
                                                                      then stop and report)
```

Every step — pass or fail — is written to the trace. A rejection at any
guardrail or the approval gate stops that service's pipeline immediately:
generation never happens without passing the input guardrail, nothing is
ever written without passing the output guardrail *and* explicit human
approval, and evaluation only ever runs against a file that was actually
written.

| Step | Class | What it does |
|---|---|---|
| Discover | `ServiceDiscoveryTool.discoverAll` | Walks `src/main/java`, finds every public class named `*Service`, and pairs each with its existing test file if one exists (`ServiceDiscoveryTool.Discovery`). |
| Detect missing methods | `MissingTestCaseDetector` | For a service that already has a test: finds every public method the test source never references. Empty result → `ALREADY_COMPLETE`, no generation, no approval prompt. |
| Guardrail (input) | `Guardrails.checkInput` | Rejects a target that isn't a real `.java` file, has an invalid class name, or contains a path-traversal `..` segment. |
| Generate | `TestGeneratorTool` (implements `TestGenerator`) | `generate` writes a brand-new test; `generateWithMissingMethods` extends an existing test with cases for just the missing methods, keeping the rest unchanged; `correct` fixes a failing test using the real failure detail. All three call the LLM gateway (via `part1.Agent`/`part1.RunnerUtil`) and strip markdown fencing from the reply. |
| Guardrail (output) | `Guardrails.checkOutput` / `checkWriteTarget` | Rejects generated/extended/corrected source with no `@Test` method, no assertion, a dangerous call (`ProcessBuilder`, `System.exit`, file deletion), an oversized body, or a target path outside `src/test/java`/not named `*Test.java`. Runs again before every correction, not just the first write. |
| Human approval | `HumanApproval` | Prints a preview and asks `Proceed? [y/N]` on the console. No terminal or no response → refused, never defaults to approved. Runs again before every correction attempt, not just the first write. |
| Write | `TestWriterTool` | `write` creates a new file and refuses if one already exists; `overwrite` replaces an existing file's content — used only for the missing-method-extension and correction flows, both of which are updating a test this agent already produced (or is about to, with approval), never a service's source. |
| Evaluate | `TestEvaluator` + `JacocoCoverageReader` | Runs `mvn -Dtest=<Class>Test test` for real (bounded by `ProcessRunner`'s timeout), then reads the class's actual line-coverage percentage out of JaCoCo's own `target/site/jacoco/jacoco.xml` — never reimplements coverage math. Passes only if the suite ran green *and* coverage clears the configured threshold (80% by default). |
| Analyze failure | `SurefireReportReader` | On an evaluation failure, reads the REAL Surefire XML report (`target/surefire-reports/TEST-<Class>.xml`) for the exact failing method, exception type, message, and a stack excerpt — never guesses at why it failed. |
| Correct | `TestGeneratorTool.correct` | Sends the service source, the current (failing) test, and the real failure detail to the LLM and asks for a corrected test — constrained to only ever change the test, never to assume the service itself will change, since this agent has no ability to touch source. |

`TestEnforcerAgent.run()` returns one `RunResult` per service — target,
outcome, a detail string, and how many correction attempts it took.
Outcomes: `WRITTEN_AND_PASSED`, `WRITTEN_BUT_EVAL_FAILED`,
`MISSING_METHODS_ADDED_AND_PASSED`, `MISSING_METHODS_ADDED_BUT_EVAL_FAILED`,
`ALREADY_COMPLETE`, `REJECTED_BY_GUARDRAIL`, `REJECTED_BY_HUMAN`,
`ALREADY_EXISTS`, `GENERATION_FAILED`.

### The four mandatory elements

**Guardrails** (`Guardrails.java`) — hard boundaries the agent cannot
reason its way past, checked before generation (is this even a class the
agent may act on?) and after every generation/extension/correction (is the
output itself safe to write and run?), plus a write-scope check that
confines every write to `src/test/java` and to filenames ending in
`Test.java`/`Tests.java`. Same discipline as this repo's `plan.md`
unit-test-agent ("never touch `src/main/java`") and `project-guardian`'s
`WorkspaceGuard`.

**Evaluation** (`TestEvaluator.java`, `JacocoCoverageReader.java`,
`ProcessRunner.java`) — the test is judged by actually running it, not by
static inspection, every time (first write, after extension, and after
every correction). `ProcessRunner` bounds the `mvn test` subprocess with a
hard timeout (killing the *whole* process tree on timeout, not just the
direct child, so a hung build can't hang the agent), and
`JacocoCoverageReader` reads real coverage numbers from JaCoCo's XML report
rather than guessing.

**Trace** (`AgentTrace.java`, `TraceEvent.java`) — every pipeline step,
including `detect_missing_methods`, `analyze_failure`, and `correct`,
appends a structured `TraceEvent` (`timestamp`, `step`, `status`, `detail`)
to an in-memory list and to a newline-delimited JSON file on disk, so a run
— including every correction attempt — can be replayed and audited step by
step after the fact.

**Human approval** (`HumanApproval.java`) — every write this agent makes
(a brand-new test, an extension for missing coverage, or a correction)
requires an explicit `y` from a human at a terminal, asked again each time.
Mirrors the reference MCP server's `client.py` gate: reads run freely, but
a write with no human available to ask is refused, never silently approved.

### Design notes

- **Self-correction is analysis-driven, not a blind retry.** `correct`
  never re-asks the model to "try again" — it hands over the exact
  Surefire failure (method, exception type, message, stack excerpt) so the
  correction is grounded in what actually happened. If no Surefire report
  exists to analyze (e.g. a timeout killed the run before Surefire could
  write one), the agent stops rather than guessing.
- **Corrections are bounded.** `MAX_CORRECTION_ATTEMPTS = 3`, the same
  ceiling this repo's `plan.md` unit-test-agent uses — after that it stops
  and reports the failure with its attempt count rather than looping
  forever or weakening the test to force a pass.
- **A correction can only ever touch the test.** The system prompt for
  `correct` (in `TestGeneratorTool`) explicitly forbids proposing a fix
  that assumes the service's behavior will change, because this agent has
  no mechanism to modify source at all — `Guardrails.checkWriteTarget`
  would refuse that write regardless.
- **Generation is a seam, not a dependency.** `TestEnforcerAgent` depends
  on the `TestGenerator` interface (`generate`/`generateWithMissingMethods`/
  `correct`), not the concrete `TestGeneratorTool` — tests substitute a
  stub so the whole pipeline can be exercised without a live network call.
  Same idea for `TestEvaluator`, which tests subclass to skip the real
  `mvn test` subprocess.
- **Read/write split.** `TestGeneratorTool` only produces text; it never
  touches the filesystem. `TestWriterTool` is the only class that writes a
  test file. This mirrors the reference MCP server's rule that a
  validate-only tool and the write tool it gates are always separate.
- **Fail closed, not open.** Every ambiguous case (no terminal for
  approval, an unreadable rules file, a guardrail violation, no Surefire
  report to analyze) stops the pipeline rather than proceeding on a best
  guess.
