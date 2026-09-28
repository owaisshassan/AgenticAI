# Plan: RAG Agent (part3)

## Goal
Build a Retrieval-Augmented Generation (RAG) agent in package `part3`. The knowledge
base for this RAG system is the source code and behavior of the `part1` and `part2`
modules themselves — i.e. `part3` should be able to answer questions about how the
`part1` (basic conversational agent) and `part2` (agent + tool-use) modules work by
retrieving relevant chunks from their source files and injecting that context into the
prompt sent to the LLM gateway.

## Background
- `part1` — minimal request/response wrapper around the Claude-compatible gateway
  (`Agent`, `AgentRequest`, `Message`, `Runner`, `RunnerUtil`). No tools, no logging.
- `part2` — adds tool-use (function calling) via `Tool`, `ContentBlock`,
  `WeatherConnector`, and file-based logging via `FileLogger`. Reuses part1's
  `API_URL`/`API_KEY` constants.
- `part3` — should follow the same incremental style: reuse the existing
  request/response/gateway-call conventions, and add a retrieval layer on top:
  chunk documents -> embed -> store -> retrieve top-k -> inject into prompt -> call
  gateway.

## Scope & Constraints
- **Knowledge base**: the `.java` source files under `src/main/java/part1` and
  `src/main/java/part2` (treat each file as a document describing "how this agent
  framework works").
- **Hard constraint**: `part3` MUST NOT modify any file under `src/main/java/part1` or
  `src/main/java/part2`. It only reads them as data.
- Reuse `Message`, `ContentBlock`, etc. from `part1`/`part2` where access allows it;
  otherwise create `part3`-local equivalents and note the reason in this plan's
  changelog (do not change visibility modifiers in part1/part2 to enable reuse).
- `pom.xml` currently has only `jackson-databind` and `junit-jupiter`. Do not add a
  heavyweight framework (LangChain4j, a vector database, etc.) without asking the
  human first — prefer the lightest implementation that satisfies the goal (e.g. the
  gateway's own `/embeddings` endpoint if available, otherwise a local
  term-frequency/cosine-similarity fallback).
- Retrieval must be observable: log (via a `part3`-local logger following the
  `FileLogger` pattern from part2) which chunks were retrieved for each query and their
  similarity scores.
- One knowledge base (part1 + part2) for v1 — no other document sources.

## Tooling for agents implementing this plan
- **Skill**: `.claude\skills\part3-rag\SKILL.md` — the procedural reference for every
  non-obvious decision in this plan: chunking granularity, the gateway-embeddings-vs.
  -local-fallback decision procedure, the cosine-similarity formula, the prompt-injection
  template, what to log, and how to run the grounding check. Read it before Slice 1 —
  it removes the guesswork from "how exactly" for Slices 1, 2, 4, and 5.
- **Workflow**: `.claude\workflows\part3-rag-build.js` — a deterministic multi-agent
  orchestration that executes this plan slice-by-slice (see below), testing each slice
  before starting the next, and finishing with an adversarial skeptic pass that
  independently re-checks the grounding-check claim rather than trusting the
  implementing agent's own report. Prefer running this workflow over ad hoc manual
  execution when the human has opted into multi-agent orchestration for this task; the
  slices below remain the authoritative process description either way (the workflow
  is one way to execute them, not a replacement for them).

## Why sliced, not one big step list
Each slice below is a **thin vertical slice**: it produces a small set of files that
compile and pass their own tests on their own, independent of slices that haven't
happened yet. This matters because:
- A failure surfaces at the cheapest possible point (right after the 2-3 files that
  caused it), not after all ~9 files have been written and wired together.
- Each slice has its own Definition of Done, so "done with part3" isn't an all-or-
  nothing judgment call — progress is checkpointed and reportable after every slice.
- Slices 1-2 need no network access at all and can be fully verified in a sandboxed
  environment; only Slice 4 onward needs the live gateway. Don't block early slices on
  gateway availability.
- Later slices only ever depend on earlier slices' *finished, tested* output — never on
  an earlier slice's in-progress state. If a slice's tests don't pass, stop and fix that
  slice before starting the next one.

## Implementation Slices

### Slice 1 — Chunking foundation (no network, no gateway)
**Files**: `Chunk.java`, `DocumentLoader.java`
**Depends on**: nothing (only reads part1/part2 source as plain text).
**Steps**:
1. Read this plan and the `part3-rag` skill in full before writing code.
2. Explore `part1` and `part2`, list every `.java` file as a candidate knowledge
   document. Do not modify any of them.
3. Decide and document the chunking strategy (skill section 1: one chunk per
   top-level class plus one chunk per public method over ~40 lines, Javadoc/comments
   kept with the class chunk). Record any deviation and why in the Changelog.
4. Implement `Chunk` (id, sourceFile, text — plus an embedding field left for Slice 2)
   and `DocumentLoader` (loads part1+part2 `.java` files, splits per the chosen
   strategy).
**Definition of Done**:
- `DocumentLoaderTest` (JUnit, under `src/test/java/part3`) passes, covering: correct
  chunk count for a known file, stable/unique chunk ids, class Javadoc retained on the
  class chunk.
- No live network calls anywhere in this slice or its tests.
- `mvn -q -Dtest=DocumentLoaderTest test` is green.

### Slice 2 — Similarity engine (no gateway call required to test)
**Files**: `EmbeddingClient.java`, `VectorStore.java`
**Depends on**: Slice 1's `Chunk` type (done and tested).
**Steps**:
1. Follow the skill's probe procedure (section 2) to check whether the gateway used by
   `part1.RunnerUtil`/`part2.RunnerUtil` exposes a sibling embeddings endpoint (e.g.
   `/v1/embeddings` on the same host, same auth header) — probe via curl/HttpClient, no
   new dependency.
   - If yes: implement `EmbeddingClient` against it.
   - If no: implement the skill's term-frequency/cosine-similarity fallback instead.
   - Record the decision and why in the Changelog now, before moving on — this is the
     single highest-leverage decision in the whole plan and easy to forget once later
     slices are underway.
2. Implement `VectorStore`: in-memory chunk storage + cosine-similarity top-k search
   (skill section 3).
**Definition of Done**:
- `VectorStoreTest` passes using synthetic vectors (no embedding calls needed to test
  the similarity math itself) — known-vector cosine similarity cases, correct top-k
  ordering, correct handling of mismatched vector lengths if applicable.
- If the gateway path was chosen, one integration smoke test (or a manual curl
  transcript recorded in the Changelog) confirms the embeddings endpoint actually
  returns usable vectors — don't assume the probe result stays valid; confirm it.
- `mvn -q -Dtest=VectorStoreTest test` is green.

### Slice 3 — Retrieval
**Files**: `Retriever.java`, part3-local `FileLogger`-style logger
**Depends on**: Slice 1 + Slice 2 (both tested and passing).
**Steps**:
1. Implement the logger, following `part2.FileLogger`'s pattern.
2. Implement `Retriever`: embeds an incoming query (via Slice 2's `EmbeddingClient`)
   and returns top-k chunks from `VectorStore`, logging each candidate chunk's id and
   score (skill section 5) — not just the winner, so retrieval confidence is visible.
3. Build the full index once (all part1+part2 chunks from Slice 1, embedded via Slice
   2) and sanity-check retrieval manually: a query about weather-tool-calling should
   rank `part2.WeatherConnector`/`part2.Runner` chunks highest.
**Definition of Done**:
- The manual sanity check in step 3 above ranks the expected chunk in the top-k for at
  least 2-3 hand-picked queries — record the queries and results in the Changelog.
- Retrieval logging is visible in the log file for each sanity-check query.
- No `Agent`/gateway/LLM call needed yet — this slice is retrieval-only.

### Slice 4 — Generation loop
**Files**: `Agent.java`/`AgentRequest.java` (reuse part2's if package access allows;
otherwise part3-local equivalents), `RunnerUtil.java`, `Runner.java`
**Depends on**: Slice 3 (tested and passing).
**Steps**:
1. Implement the gateway/request layer, mirroring part2's `Agent`/`AgentRequest`/
   `RunnerUtil` (HttpClient + Jackson, same conventions). Reuse part2's `Message`/
   `ContentBlock` directly if accessible; don't duplicate them without a reason.
2. Implement `Runner`: builds the index once at startup (logs chunk count), then per
   user query retrieves top-k chunks (Slice 3), builds the augmented prompt using the
   skill's template (section 4 — including the "answer using only the context above"
   instruction), calls the gateway, prints the answer, logs query + retrieved scores +
   answer.
3. `mvn -q compile` must succeed for all of `part3`.
**Definition of Done**:
- Full compile succeeds.
- At least one live end-to-end run (or, if the sandbox has no network, a clearly
  documented reason why not) producing a real question → retrieved-context → gateway
  answer transcript.

### Slice 5 — Grounding verification
**Depends on**: Slice 4 (compiling and, ideally, exercised live at least once).
**Steps**:
1. Run the skill's grounding-check procedure (section 6): ask a question answerable
   only from an arbitrary part1/part2 implementation detail (e.g. "What tool does
   part2's Runner call when the user asks about weather?"). Confirm from the log that
   the retrieved chunks actually contain that detail and the answer matches it.
2. Get a second, independent pass to re-check step 1's claim against the real source
   (e.g. a skeptic review, as the `part3-rag-build` workflow's Verify phase does) rather
   than trusting the first pass's own report.
**Definition of Done**:
- The grounding check's retrieved-chunks-contain-the-answer claim has been verified
  twice, independently, against the actual part1/part2 source — not just asserted once.
- If the two passes disagree, that disagreement is reported to the human, not silently
  resolved in either direction.

### Slice 6 — Tests, docs, wrap-up
**Depends on**: Slices 1-5 all at Definition-of-Done.
**Steps**:
1. Confirm `src/test/java/part3` has tests for both deterministic pieces (chunking,
   similarity search) — Slices 1 and 2 should already have these; this step just
   confirms nothing was skipped and the full `part3` test suite is green together.
2. If implementing any slice revealed a genuine need to change `part1` or `part2`
   source (e.g. a class needs to be made public for reuse), stop and ask the human
   before making that change — do not modify part1/part2 unilaterally, at any slice.
3. Finalize the **Changelog** section below so it accurately reflects every deviation
   made across all 6 slices (embedding strategy choice, chunking deviations, any
   reused-vs-duplicated types, grounding-check disagreements, etc).
4. Report completion to the human with: number of chunks indexed, which retrieval
   mechanism was used (gateway embeddings vs. local fallback), and a sample Q&A
   transcript demonstrating a retrieval-augmented answer.

## Proposed Components (under `src/main/java/part3`)
- `DocumentLoader` — reads the part1/part2 `.java` files and splits each into chunks. *(Slice 1)*
- `Chunk` — id, source file, text content, embedding vector (once computed). *(Slice 1)*
- `EmbeddingClient` — gets a vector for a chunk or query (gateway embeddings endpoint,
  or local fallback vectorizer if the gateway doesn't support embeddings). *(Slice 2)*
- `VectorStore` — in-memory collection of embedded `Chunk`s with a cosine-similarity
  top-k search. *(Slice 2)*
- `Retriever` — embeds the incoming query and returns the top-k matching chunks from
  the `VectorStore`. *(Slice 3)*
- `Agent` / `AgentRequest` (part3) — same shape as part1/part2, built with a prompt that
  has retrieved context injected. *(Slice 4)*
- `RunnerUtil` (part3) — gateway call helper, following part1/part2's HttpClient +
  Jackson style. *(Slice 4)*
- `Runner` (part3) — CLI loop: build the index once at startup, then for each user
  question retrieve context, augment the prompt, call the gateway, print the answer,
  and log the retrieval + response. *(Slice 4)*

## Out of Scope (v1)
- Persisting the vector index to disk between runs (in-memory only).
- Document sources other than the part1/part2 `.java` files.
- AST-based chunking (class/method-level text splitting is sufficient for v1).
- Automatic re-indexing on source file changes (index is built once per run).

## Changelog
- **2026-09-19 — Embedding strategy: local fallback.** Probed the gateway
  (`part1.RunnerUtil.API_URL`) for a sibling `/v1/embeddings` endpoint per the skill's
  procedure. Response: HTTP 403, `"key not allowed to access model...can only access
  models=['claude-sonnet','claude-opus','claude-haiku']"`. The API key is restricted to
  chat models only, so `EmbeddingClient` implements the term-frequency vectorizer
  fallback (skill section 2), not a gateway call. Documented inline in
  `EmbeddingClient.java`'s class comment.
- **2026-09-19 — Chunking granularity as planned, method-detection is regex-based.**
  Implemented `DocumentLoader` with one chunk per top-level class plus one chunk per
  public method for files over 40 lines, per the skill. Method boundaries are found via
  a public-method-signature regex + brace-depth counting (no AST parser dependency,
  consistent with "AST-based chunking" being explicitly out of scope for v1). Verified
  against the real part1/part2 corpus: 76 chunks indexed across 13 files.
- **2026-09-19 — Query embedding does not grow the vocabulary.** `EmbeddingClient`
  distinguishes `embed()` (used when indexing chunks — grows the vocabulary) from
  `embedQuery()` (used for a user's question — does not grow it), so a query containing
  a term never before seen in the corpus contributes nothing to its vector instead of
  resizing/reinterpreting every previously-stored chunk vector.
- **2026-09-19 — Agent/RunnerUtil reuse part1's request types directly.**
  `part3.Agent` extends `part1.AgentRequest` (no tool-use needed for a Q&A agent, so
  part2's tool-aware types weren't needed); `part3.RunnerUtil` delegates directly to
  `part1.RunnerUtil.callGateway`/`extractAssistantText` and adds only
  `extractTokenUsage`, which part1/part2 didn't need. No part1/part2 source was
  modified.
- **2026-09-19 — Retrieval sanity check (Slice 3).** Manually verified 3 queries against
  the built index: "what tool...weather" ranked `part2.Runner`/`part2.Runner#main`
  highest; "how does the agent log to a file" ranked `part2.FileLogger` highest; "how
  does part1 call the gateway" ranked `part2.RunnerUtil#callGateway` highest. All
  matched expectation.
- **2026-09-19 — Grounding check (Slice 5), single-pass only.** Asked "What tool name
  does part2's Runner pass to WeatherConnector when the user asks about weather?" via
  the live `Runner`. Retrieved chunks included `part2.Runner`/`part2.ContentBlock`-
  related chunks; the live gateway answer correctly identified `get_weather` and cited
  the actual `resolveTurn` code matching on it. This was verified by the implementing
  agent only — the plan's recommended second, independent skeptic pass (e.g. via the
  `part3-rag-build` workflow's Verify phase) was not run in this session. Flagging this
  so a future reader doesn't assume double-verification happened when it didn't.
- **2026-09-19 — Logging.** Added `RagLogger` (part3-local, `FileLogger`-pattern) writing
  to `D:\AgenticAI_Git\logs\part3_RAG\`: `part3-rag.log` (general info/retrieval/session
  events), `part3-rag-success.log` (one entry per successful turn: query, answer, token
  usage), `part3-rag-failure.log` (one entry per failed turn: query, error, token usage).
  Verified both success and failure paths write correctly.
- **2026-09-19 — Cost tracking added alongside token usage.** Added `PricingTable`
  (USD-per-million-token rates keyed by the gateway's model alias — `claude-sonnet`
  $2/$10, `claude-opus` $5/$25, `claude-haiku` $1/$5 per 1M input/output tokens, current
  first-party pricing for that tier) and `TokenUsage.forModel(model, input, output)`,
  which computes `costUsd = (input/1e6)*inputRate + (output/1e6)*outputRate`. Caveat:
  the gateway's key only exposes tier aliases (`claude-sonnet`/`opus`/`haiku`), not a
  versioned model ID, so this maps the alias to current-generation pricing for that tier
  rather than a rate confirmed against the gateway's actual backing model — if the
  gateway operator pins a different snapshot/version at a different rate, this cost is
  an estimate, not an invoice-accurate figure. An unmapped alias logs a warning and
  reports $0 rather than silently guessing a rate. Cost is now surfaced in three places:
  console output after every turn (`[status=..., tokens=..., cost=$...]`), and both the
  `Token usage: ...` line and a dedicated `Cost: $...` line in the success/failure logs.
  Verified live: a real question cost $0.008006 for 2899 total tokens on `claude-sonnet`.
