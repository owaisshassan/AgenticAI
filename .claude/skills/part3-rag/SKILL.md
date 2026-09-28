---
name: part3-rag
description: Procedural reference for implementing a dependency-free RAG layer in Java (chunking, embeddings-or-fallback decision, cosine similarity, prompt injection, grounding checks). Use when implementing or reviewing the part3 module described in src/main/resources/part3-plan.md — i.e. any time a part1/part2-style Java project needs retrieval-augmented generation without adding a vector-database or embedding-framework dependency.
---

# RAG-in-plain-Java (no new dependencies)

This skill packages the procedural knowledge needed to implement `part3-plan.md`
without guessing at each step. It assumes the target stack is plain Java 17 +
`java.net.http.HttpClient` + Jackson (`jackson-databind`) — the same stack part1/part2
already use — and that the only allowed dependency additions are test-scoped ones
(JUnit), matching this project's existing constraint.

## 1. Chunking strategy

Chunk at the granularity of **one top-level class = one chunk**, plus **one chunk per
public method body** for any class over ~40 lines. Keep each class's leading Javadoc/
comment block attached to its class chunk — comments carry intent that raw code
doesn't, and intent is usually what a retrieval query is actually asking about
("what does X do", "why does Y happen").

Do not chunk by fixed character/token windows (e.g. "every 500 chars") — it splits
method signatures from bodies and produces chunks with no coherent meaning, which
tanks retrieval quality far more than it helps with size limits in a codebase this
small.

Each chunk needs: a stable `id` (e.g. `part2.WeatherConnector#getWeather`), the
`sourceFile` path, and the raw `text`. Store these as plain fields on a `Chunk` class —
no builder/generic abstraction needed for ~15 source files.

## 2. Decide: gateway embeddings vs. local fallback

Before writing `EmbeddingClient`, determine whether the gateway already used by
`part1.RunnerUtil`/`part2.RunnerUtil` (`API_URL`, `API_KEY`) exposes an embeddings
endpoint:

1. Check whether the base URL has a sibling `/v1/embeddings` path (common for
   OpenAI-compatible or LiteLLM-style gateways — this project's `API_URL` ends in
   `/v1/messages`, so `/v1/embeddings` on the same host is the first thing to try).
2. Probe it with a minimal request (same auth headers as `callGateway`: `x-litellm-api-key`,
   `Content-Type: application/json`) and a trivial input string. A 2xx with a numeric
   vector back confirms support. A 404/501 or an error about unsupported endpoint means
   fall back.
3. **Do not** add a new dependency (no OpenAI SDK, no LangChain4j) to call this — reuse
   `java.net.http.HttpClient` + Jackson exactly like `RunnerUtil.callGateway` does.

If there is no usable embeddings endpoint, implement a **term-frequency vector**
fallback:
- Lowercase, split on non-alphanumeric characters, drop empty tokens.
- Build a vocabulary incrementally as chunks are indexed (a `Map<String,Integer>` term
  → index).
- Represent each chunk/query as a sparse or dense `double[]` of raw term counts (no
  need for TF-IDF or normalization in v1 — cosine similarity already normalizes for
  vector length, and this corpus is small enough that raw counts separate relevant from
  irrelevant chunks adequately).

Record which path was chosen, and why, in the plan's Changelog — this is exactly the
kind of decision a future reader needs justified, since it's not derivable from the
code alone (it depends on what the gateway supports *today*).

## 3. Cosine similarity (top-k search)

```java
static double cosineSimilarity(double[] a, double[] b) {
    double dot = 0, normA = 0, normB = 0;
    int n = Math.max(a.length, b.length);
    for (int i = 0; i < n; i++) {
        double x = i < a.length ? a[i] : 0;
        double y = i < b.length ? b[i] : 0;
        dot += x * y;
        normA += x * x;
        normB += y * y;
    }
    if (normA == 0 || normB == 0) return 0;
    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
}
```

For top-k: score every stored chunk against the query vector, sort descending by
score, take the first `k` (k=3 is a reasonable default for a corpus this small — large
enough to give the model real context, small enough to keep the prompt short). If two
vectors have different lengths (vocabulary grew after a chunk was embedded), pad the
shorter one with zeros as above rather than throwing — a fixed-size vocabulary snapshot
taken once at index-build time avoids this entirely and is simpler; prefer that if
`DocumentLoader` builds the whole vocabulary before any embedding happens.

## 4. Prompt injection template

Keep retrieved context clearly delimited from the question, and cite sources so the
answer's grounding is checkable in the log:

```
Context (retrieved, most relevant first):
[1] part2.WeatherConnector#getWeather
<chunk text>

[2] part2.Runner#resolveTurn
<chunk text>

Question: <user query>

Answer using only the context above. If the context doesn't contain the answer, say so.
```

That last instruction line matters: without it, the model will happily answer from its
own general knowledge even when retrieval failed, which defeats the purpose of building
retrieval at all and makes grounding failures invisible.

## 5. Observability

Log, per query, in the same style as `part2.FileLogger`:
- The query text.
- Each retrieved chunk's `id` and similarity score (not just the winning one — seeing
  the runner-up scores is what tells you whether retrieval was confident or a coin
  flip).
- The final answer.

This is what step 8 of the plan ("verify end-to-end") and any later debugging depend
on — without it, a wrong answer is unexplainable after the fact.

## 6. Grounding check (how to tell RAG is actually working)

A demo answering correctly is not proof retrieval helped — the base model may already
know the answer. Verify with a question that is **only** answerable from the specific
source (e.g. "What tool name does part2's Runner pass to WeatherConnector?" — an
arbitrary implementation detail, not something inferable from "weather agent" alone),
then confirm:
1. The retrieved chunks (per the log) actually contain that detail.
2. The answer matches the source, not a plausible-sounding guess.

If both hold, retrieval is doing real work. If the answer is right but the retrieved
chunks don't contain the detail, the model guessed — that's a finding to report, not a
success.
