# Build prompt: a Java/Spring Boot/Maven MCP server for project governance

Paste everything below to Claude (ideally Claude Code, since it needs to run
`mvn`, write files, and execute tests) as the task spec.

---

## Task

Build an MCP server, in Java, on Spring Boot, built with Maven, called
**`project-guardian`**. It has two responsibilities over one Spring Boot
project (the one it's pointed at, not itself):

1. **Convention enforcement** — validate and scaffold new models/services so
   they match this project's layering, naming, and boilerplate rules.
2. **Test-before-build gating** — refuse to build the jar until every
   changed class that needs a test has one, the suite passes, and coverage
   clears a threshold.

Follow the engineering patterns below throughout — they come from a working
reference server (`aira-ops`, TypeScript) that this Java server should match
in spirit, not in language. Where TypeScript specifics don't translate,
adapt them to idiomatic Spring/Java; don't skip the underlying discipline.

## Non-goals

- No CI/CD integration. This is an interactive MCP server a developer (or
  Claude, in a session) calls locally. It does not wire into Jenkins/GitHub
  Actions/a Maven plugin phase.
- It does not enforce rules on *its own* codebase — it targets a separate
  Spring Boot project passed in as a working directory.
- No auth/multi-tenant story for v1 — assume a single local user over
  stdio, same trust model as `aira-ops`.

## Stack

- Java 21+, Spring Boot (latest stable), Maven.
- **Spring AI's MCP Server Boot Starter**
  (`org.springframework.ai:spring-ai-starter-mcp-server-webmvc`, GA line —
  check the current stable version on
  `https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html`
  before pinning a version; don't hardcode a version from memory).
  This gives stdio and Streamable HTTP transports and lets tools be plain
  `@Tool`-annotated Spring bean methods instead of hand-rolled JSON-RPC.
- JUnit 5 + AssertJ for tests.
- ArchUnit for structural/layering checks.
- JaCoCo for coverage, read via its XML report — don't reimplement
  coverage math.
- JGit (or shelling out to `git`) for changed-file detection.

If Spring AI's current MCP tool-registration API differs from what's
described below (annotation names, config keys), use the real current API
and note the substitution — don't silently guess.

## Design rules carried over from the reference server (apply to every tool)

- **Split read from write.** A tool either reads or changes state, never
  both. A validate-only tool and the write tool it gates are always two
  separate tools (mirrors `search_tickets`/`get_ticket` vs
  `add_ticket_comment`, and the earlier "keep `scaffold_*` separate from
  `validate_*`" rule).
- **Honest annotations.** Every tool declares `readOnlyHint` and, where it
  applies, `destructiveHint` / `idempotentHint`, the way `server.ts` does.
  These are hints, not enforcement — the enforcement is the approval config
  below — but they must be true.
- **Structured errors, not strings.** Every failure is
  `{ "error": { "code", "message", "hint" } }` — an actionable hint every
  time, never a bare `"error"` or `"invalid"`. This is the single biggest
  difference between this server and a BAD-style one.
- **Typed, named, constrained parameters.** Enums for closed sets, regex
  patterns for anything with a fixed shape (class names, package paths),
  `description` on every field including an example.
- **Idempotency on writes.** Any tool that changes files or triggers a
  build mints its own operation id (never accepts one from the model) and
  returns it on ambiguous failure (e.g. a build that times out mid-run) so
  a retry can be tied to the same operation instead of silently repeating
  side effects.
- **Bounded process execution.** Every `ProcessBuilder` call (`mvn test`,
  `mvn package`, `git diff`) has an explicit timeout and captured
  stdout/stderr; a hung process must not hang a tool call.
- **Secrets and scope.** No secrets needed for v1 (it operates on the local
  filesystem/Git repo), but keep the working-directory root configurable
  and refuse to touch paths outside it — same spirit as `aira-ops` never
  leaking its token.
- **Resources + a prompt**, same as `aira-ops`: expose the ruleset as a
  resource, and ship one reusable prompt for the common workflow.

## Ruleset (externalized, not hardcoded)

A `guardian-rules.yaml` the server loads at startup, reloadable without a
restart if easy to add. Shape:

```yaml
layers:
  - name: controller
    package: "**/controller/**"
    mayCallLayers: [service]
  - name: service
    package: "**/service/**"
    mayCallLayers: [repository]
  - name: repository
    package: "**/repository/**"
    mayCallLayers: []
  - name: domain
    package: "**/domain/**"
    mayCallLayers: []

naming:
  service: "^[A-Z][A-Za-z0-9]*Service$"
  repository: "^[A-Z][A-Za-z0-9]*Repository$"
  controller: "^[A-Z][A-Za-z0-9]*Controller$"
  test: "^${className}Tests?$"

requiredAnnotations:
  service: ["org.springframework.stereotype.Service"]
  repository: ["org.springframework.data.jpa.repository.JpaRepository"]
  entity: ["jakarta.persistence.Entity"]

testGate:
  requireTestForPackages: ["**/service/**", "**/controller/**"]
  skipPackages: ["**/dto/**", "**/config/**"]
  minCoveragePercent: 80
  minAssertionsPerTestClass: 1
  forbidDisabledWithoutReason: true
```

## Tool set

### Convention enforcement

| Tool | Read/Write | Purpose |
|---|---|---|
| `get_conventions` | read | Returns the loaded `guardian-rules.yaml` (as structured JSON, not a raw file dump) so the model doesn't have to guess. |
| `list_existing_classes` | read | Lists classes under a given layer/package, so the model checks for collisions before proposing a new one. |
| `get_class` | read | One class's current source, if it exists. |
| `validate_class_spec` | read | Takes a proposed `{ className, packageName, layer, fields[] }`; checks naming pattern, layer legality, required annotations; returns pass/fail with `code/message/hint` per violation. Never writes anything. |
| `scaffold_class` | write, `idempotentHint:false` | Only proceeds if `validate_class_spec` on the same input would pass (re-validate internally, don't trust a prior call). Writes the file(s), returns exactly what was created. Refuses to overwrite an existing file — returns a `already_exists` error with a hint to rename or use a different tool. |
| `run_architecture_check` | read | Runs the ArchUnit rules derived from `layers:` and returns violations in the same error shape. |

### Test-before-build gate

| Tool | Read/Write | Purpose |
|---|---|---|
| `get_changed_classes` | read | `git diff` against a base ref (default: last commit) → list of added/modified `.java` files, annotated with which layer they're in. |
| `check_test_coverage_gate` | read | For each changed class matching `testGate.requireTestForPackages`: does a test class exist (by convention), does it pass, does it meet `minAssertionsPerTestClass`, is coverage ≥ `minCoveragePercent` (from the JaCoCo report). Returns one structured result per class, not just a boolean — this is the tool the model actually reasons from. |
| `scaffold_test_class` | write, `idempotentHint:false` | Generates a JUnit5 (+ Mockito, if the class has collaborators) stub for a class missing a test, matching the naming convention. Refuses to overwrite an existing test file. |
| `run_tests` | write* | Runs `mvn test` for real (not just checks existence) and returns pass/fail + failure details. Marked non-destructive but still a write-class tool since it has side effects (writes reports) and a timeout. |
| `build_jar` | write, `destructiveHint:false`, `idempotentHint:true` | Runs `mvn clean package`, but **first re-runs `check_test_coverage_gate` internally** and refuses with a structured error listing exactly which classes are missing tests / under coverage if the gate isn't met — it does not trust the model to have called the gate tool itself first. On success, returns the jar path. Hard timeout (configurable, default e.g. 180s). |

\* `run_tests` and `build_jar` should still declare `readOnlyHint: false` even
though they don't mutate the model's understanding of "data" the way a
ticket write does — they run arbitrary project code and take real time/CPU,
so they belong in the same approval tier as a write.

## Resources & prompt

- Resource `guardian://rules` — the loaded ruleset, same idea as
  `aira://config`.
- Resource template `guardian://classes/{className}` — a class's current
  source and which layer it resolved to.
- Prompt `add-service-with-tests` — args `{ className, description }` — a
  reusable workflow prompt: check conventions, check for collisions,
  scaffold the class, scaffold its test, run the gate, only then offer to
  build. Mirrors the `triage` prompt in the reference server.

## Approval / gating story (document it, even without a client to enforce it yet)

Write a table like the reference README's, covering:
- Which tools are safe to pre-approve (`get_conventions`, `list_existing_classes`,
  `get_class`, `validate_class_spec`, `get_changed_classes`,
  `check_test_coverage_gate`, `run_architecture_check`) — all read-only.
- Which require a human ask every time (`scaffold_class`, `scaffold_test_class`,
  `run_tests`, `build_jar`).
- A test, analogous to
  `test_every_tool_that_is_not_read_only_is_gated_in_claude_code`, that fails
  if a non-read-only tool is missing from whatever client config
  (`.claude/settings.json` `ask` list) is checked in alongside the server.

## Testing (build this before declaring the server done)

Same spirit as `test_mcp_server.py`: end-to-end tests that start the real
server as a subprocess/in-process context and drive it over the actual MCP
protocol — not just unit tests of internal Java methods.

At minimum:
- Handshake + `tools/list` returns every tool with honest annotations.
- Schema rejects a malformed class name before touching the filesystem.
- `validate_class_spec` catches: wrong layer call direction, bad naming,
  missing required annotation — one test per rule, with the exact `code`
  asserted.
- `scaffold_class` refuses to overwrite; `scaffold_class` then
  `validate_class_spec` on the same spec now reflects the file exists.
- `get_changed_classes` against a fixture repo with a known diff returns
  the right set.
- `check_test_coverage_gate` against a fixture class with (a) no test,
  (b) a trivial test under the assertion minimum, (c) a real test under the
  coverage threshold, (d) a real test that passes the gate — four distinct
  outcomes, four distinct assertions.
- `build_jar` refuses with a structured, itemized error when the gate
  isn't met, and succeeds (produces a real jar) when it is.
- Every process call (`run_tests`, `build_jar`, `git diff`) is proven to
  respect its timeout — spin up a fixture that hangs and assert the tool
  returns a `timeout` error rather than blocking.

## Project layout to produce

```
project-guardian/
├── pom.xml
├── src/main/java/.../ProjectGuardianApplication.java
├── src/main/java/.../tools/ConventionTools.java
├── src/main/java/.../tools/TestGateTools.java
├── src/main/java/.../rules/RuleSet.java          (loads guardian-rules.yaml)
├── src/main/java/.../arch/ArchRules.java         (ArchUnit)
├── src/main/java/.../git/GitDiff.java
├── src/main/java/.../coverage/JacocoReader.java
├── src/main/java/.../proc/BoundedProcess.java    (shared timeout wrapper)
├── src/main/resources/guardian-rules.yaml
├── src/test/java/.../McpServerE2ETest.java
├── .claude/settings.json      (ask/allow rules for the write tools above)
├── .mcp.json                  (registers the server, mirrors the reference's)
└── README.md                  (setup, the tool table, the checkpoint list below)
```

## Checkpoint (mirror the reference README's)

- [ ] `mvn clean package` on `project-guardian` itself succeeds and produces
      a runnable jar/stdio server.
- [ ] Connecting a client lists every tool with correct `readOnlyHint`.
- [ ] `validate_class_spec` catches all three example violations above with
      distinct `code`s.
- [ ] `build_jar` on a fixture project with a missing test is refused with
      an itemized, actionable error — not a generic failure.
- [ ] `build_jar` on the same fixture, after `scaffold_test_class` fills
      the gap and the gate passes, actually produces the jar.
- [ ] Every write tool is listed in `.claude/settings.json`'s `ask`, and the
      gate-coverage test fails if one is missing.
- [ ] `guardian-rules.yaml` can be edited and reflected in `get_conventions`
      without touching Java code.

---

Start by scaffolding the Maven project and `get_conventions` +
`validate_class_spec` end to end (smallest useful slice), get that fully
tested, then layer in `scaffold_class`, then the test-gate tools, then
`build_jar` last, since it depends on everything before it.