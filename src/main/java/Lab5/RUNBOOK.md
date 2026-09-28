# Lab5 — Runbook

Operational steps for running the Test Enforcer Agent by hand. For what the
agent is and how it works internally, see `README.md`. This file is just
"what do I actually type, in what order, for one concrete case."

## Example: a new service was just added with no test

**Scenario**: someone added `src/main/java/com/acme/billing/InvoiceService.java`
and it has no test yet.

### Step 1 — Build

```bash
cd D:\AgenticAI\AgenticAI_p01
mvn compile
```

Confirms the repo (and the new service) compiles before the agent touches
anything.

### Step 2 — Run the agent

```bash
mvn exec:java -Dexec.mainClass=Lab5.TestEnforcerAgent
```

The agent scans `src/main/java`, finds `InvoiceService` has no test, and
starts its pipeline for it: guardrail check → generate a test → guardrail
check on the generated test → ask for your approval.

### Step 3 — Review and approve the write

The console prints something like:

```
APPROVAL REQUIRED: write new test for com.acme.billing.InvoiceService
Would write: D:\AgenticAI\AgenticAI_p01\src\test\java\com\acme\billing\InvoiceServiceTest.java
--- preview (first 500 chars) ---
package com.acme.billing;

import org.junit.jupiter.api.Test;
...
Proceed? [y/N]
```

Read the preview. Type `y` and press Enter to approve, or anything else to
refuse (e.g. if the preview looks wrong — the agent will not write the file
and moves on to the next service).

### Step 4 — Let it evaluate

After you approve, the agent writes the file, then runs the real test
(`mvn -Dtest=InvoiceServiceTest test`) and reads its actual coverage from
JaCoCo. Two outcomes:

- **It passes.** You'll see:
  ```
  com.acme.billing.InvoiceService -> WRITTEN_AND_PASSED (testsPassed=true, coverage=91.0% (min 80.0%), correctionAttempts=0)
  ```
  Done — the test file is now on disk, verified working.

- **It fails.** The agent reads the real Surefire report for why, asks for
  a correction, shows you a new preview, and asks `Proceed? [y/N]` again
  before applying it. This can repeat up to 3 times. If it still fails
  after that, you'll see something like:
  ```
  com.acme.billing.InvoiceService -> WRITTEN_BUT_EVAL_FAILED (testsPassed=false, coverage=0.0% (min 80.0%) after 3 correction attempt(s), correctionAttempts=3)
  ```
  Go to Step 5.

### Step 5 — If it still failed after 3 attempts

1. Open the trace file the run printed at the end
   (`Trace written to: ...ndjson`) and look at the `analyze_failure` and
   `correct` entries for this service — each one shows exactly what
   Surefire reported and what correction was tried.
2. Open the test file it wrote
   (`src/test/java/com/acme/billing/InvoiceServiceTest.java`) and the
   service it's testing, and diagnose by hand from there — the same way
   you'd triage any other failing test.
3. The agent will never touch `InvoiceService.java` itself and will never
   weaken the test to force a pass — a genuine fix here is a human decision.

### Step 6 — Confirm

```bash
mvn test -Dtest=InvoiceServiceTest
```

Re-run it yourself to double check, independent of the agent's own report.

---

That's the whole loop. The same steps apply if the service already had a
test but was missing coverage for a method — the agent just extends the
existing test instead of writing a new one, and everything else (approval
prompt, evaluation, correction-on-failure) works the same way.
