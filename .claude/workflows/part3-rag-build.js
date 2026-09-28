export const meta = {
  name: 'part3-rag-build',
  description: 'Implement the part3 RAG module (retrieval over part1/part2 source) per src/main/resources/part3-plan.md',
  phases: [
    { title: 'Explore', detail: 'read part1/part2 source and the plan/skill docs' },
    { title: 'Design', detail: 'decide embeddings-vs-local-fallback and chunking granularity' },
    { title: 'Implement', detail: 'write part3 Java sources, one dependency-ordered agent per group' },
    { title: 'Test', detail: 'compile, write unit tests, run and self-correct up to 3 attempts' },
    { title: 'Verify', detail: 'grounding check plus an adversarial skeptic on the same evidence' },
    { title: 'Report', detail: 'summarize what was built, decisions made, and remaining gaps' },
  ],
}

const ROOT = 'D:\\AgenticAI\\AgenticAI_p01'
const PLAN = `${ROOT}\\src\\main\\resources\\part3-plan.md`
const SKILL = `${ROOT}\\.claude\\skills\\part3-rag\\SKILL.md`

phase('Explore')
const exploration = await agent(
  `Read the plan at ${PLAN} and the skill reference at ${SKILL} in full. Then read every ` +
  `.java file under ${ROOT}\\src\\main\\java\\part1 and ${ROOT}\\src\\main\\java\\part2. ` +
  `Do not modify anything. Return a structured summary of the part1/part2 codebase: for ` +
  `each file, its class name, its public methods (name + one-line purpose), and whether it ` +
  `has javadoc/comment blocks worth keeping attached to a chunk. Also note the exact gateway ` +
  `base URL and auth header names used by part1.RunnerUtil/part2.RunnerUtil, since a later ` +
  `step needs to probe a sibling endpoint on the same host.`,
  {
    label: 'explore:part1+part2',
    schema: {
      type: 'object',
      properties: {
        gatewayBaseUrl: { type: 'string' },
        authHeaderName: { type: 'string' },
        files: {
          type: 'array',
          items: {
            type: 'object',
            properties: {
              path: { type: 'string' },
              className: { type: 'string' },
              hasDocComments: { type: 'boolean' },
              publicMethods: {
                type: 'array',
                items: {
                  type: 'object',
                  properties: { name: { type: 'string' }, purpose: { type: 'string' } },
                  required: ['name', 'purpose'],
                },
              },
            },
            required: ['path', 'className', 'publicMethods'],
          },
        },
      },
      required: ['files'],
    },
  }
)
log(`Explored ${exploration.files.length} part1/part2 source files.`)

phase('Design')
const design = await agent(
  `Given this exploration of the part1/part2 codebase: ${JSON.stringify(exploration)}\n\n` +
  `Follow the "Decide: gateway embeddings vs. local fallback" procedure from the skill at ` +
  `${SKILL} (section 2): probe whether the gateway at ${exploration.gatewayBaseUrl || '(see part1.RunnerUtil.API_URL)'} ` +
  `exposes a sibling embeddings endpoint (e.g. try replacing the messages path with ` +
  `"/v1/embeddings" on the same host, using the same auth header the exploration reported), ` +
  `using curl via Bash with a trivial input string. If the API key/network is not reachable ` +
  `from this sandbox, or the probe clearly 404s/errors as unsupported, decide "local-fallback" ` +
  `and record why. Do not add any new dependency to pom.xml to perform this probe or to build ` +
  `the fallback — use curl for the probe and plain Java for the fallback design. Return your ` +
  `decision and a one-paragraph justification, plus the chunk granularity you recommend ` +
  `(should be "one chunk per class + one chunk per public method over ~40 lines", per the ` +
  `skill, unless you find a concrete reason to deviate — state the reason if you deviate).`,
  {
    label: 'design:embedding-strategy',
    schema: {
      type: 'object',
      properties: {
        embeddingStrategy: { type: 'string', enum: ['gateway', 'local-fallback'] },
        reason: { type: 'string' },
        chunkGranularity: { type: 'string' },
        embeddingsEndpointUrl: { type: 'string' },
      },
      required: ['embeddingStrategy', 'reason', 'chunkGranularity'],
    },
  }
)
log(`Design decision: ${design.embeddingStrategy} — ${design.reason}`)

phase('Implement')
const coreResult = await agent(
  `Implement the retrieval core of part3 under ${ROOT}\\src\\main\\java\\part3, following ` +
  `${PLAN} and ${SKILL} exactly. Design decisions to use: ${JSON.stringify(design)}. ` +
  `Codebase context: ${JSON.stringify(exploration)}. Create these files: Chunk.java, ` +
  `DocumentLoader.java (loads and chunks the part1+part2 .java files per the chosen ` +
  `granularity), EmbeddingClient.java (implements the chosen strategy — gateway call via ` +
  `java.net.http.HttpClient + Jackson if "gateway", or a term-frequency vectorizer if ` +
  `"local-fallback" — follow skill section 2), VectorStore.java (in-memory store + cosine ` +
  `similarity top-k search per skill section 3), Retriever.java (embeds a query and returns ` +
  `top-k chunks from VectorStore, logging each candidate's id and score per skill section 5). ` +
  `Do NOT modify anything under src\\main\\java\\part1 or src\\main\\java\\part2 — read-only. ` +
  `Match the coding style already used in part1/part2 (plain Jackson, java.net.http.HttpClient, ` +
  `no new runtime dependencies). Report which files you created.`,
  { label: 'implement:retrieval-core' }
)
log('Retrieval core implemented.')

const gatewayResult = await agent(
  `Continuing part3 implementation under ${ROOT}\\src\\main\\java\\part3 (retrieval core ` +
  `already exists — read it first so you match its types/signatures): ${coreResult}. ` +
  `Now create the gateway/request layer, mirroring part2's Agent.java, AgentRequest.java, ` +
  `RunnerUtil.java (and Message.java/ContentBlock.java only if they cannot be reused directly ` +
  `from part2 due to package-private access — check first, and if part2's types are usable ` +
  `as-is, reuse them by importing part2 rather than duplicating). Also create a part3-local ` +
  `FileLogger-style logger if one does not already exist, following part2.FileLogger's pattern, ` +
  `used for retrieval/response logging. Read ${PLAN} and ${SKILL} for the exact conventions ` +
  `and the prompt-injection template (skill section 4). Do not modify part1 or part2.`,
  { label: 'implement:gateway-layer' }
)
log('Gateway/request layer implemented.')

const runnerResult = await agent(
  `Finish part3 by creating ${ROOT}\\src\\main\\java\\part3\\Runner.java: a CLI loop that ` +
  `builds the retrieval index once at startup (via DocumentLoader/EmbeddingClient/VectorStore ` +
  `already implemented — read them first), logs how many chunks were indexed, then for each ` +
  `user question uses Retriever to get top-k chunks, builds the augmented prompt per the ` +
  `skill's template, calls the gateway the same way part2.Runner does, prints the answer, and ` +
  `logs the query/retrieved-chunk-scores/answer. Context so far: ${gatewayResult}. Then run ` +
  `"mvn -q compile" via Bash from ${ROOT} and fix any compile errors in part3 files only ` +
  `(never touch part1/part2). Report the final compile result.`,
  { label: 'implement:runner+compile' }
)
log(`Runner implemented. Compile result: ${runnerResult}`)

phase('Test')
const testResult = await agent(
  `Write JUnit 5 tests under ${ROOT}\\src\\test\\java\\part3 for the deterministic pieces of ` +
  `part3: chunking in DocumentLoader and similarity search in VectorStore (no live network ` +
  `calls in tests — per ${PLAN} step 9). Read the part3 sources first. Then run ` +
  `"mvn -q -Dtest=DocumentLoaderTest,VectorStoreTest test" via Bash from ${ROOT} (adjust test ` +
  `class names to whatever you actually created). If any test fails, analyze the Surefire ` +
  `report, correct the TEST file only (never part3 source, and never part1/part2), and rerun — ` +
  `up to 3 correction attempts, exactly like the project's existing unit-test-agent convention ` +
  `in plan.md. Report final pass/fail counts and how many correction attempts were needed.`,
  { label: 'test:core-classes' }
)
log(`Tests: ${testResult}`)

phase('Verify')
const groundingCheck = await agent(
  `Perform the grounding check from ${SKILL} section 6 against the part3 module just built ` +
  `under ${ROOT}\\src\\main\\java\\part3. Ask (via a small non-interactive smoke-test entry ` +
  `point you may add temporarily under src\\test\\java\\part3, or by directly exercising ` +
  `DocumentLoader/Retriever in a quick test) a question only answerable from part1/part2 source ` +
  `detail (e.g. the exact tool name part2.Runner passes to WeatherConnector). Report: the ` +
  `retrieved chunk ids/scores, whether the retrieved chunks actually contain the answer detail, ` +
  `and the final answer text. If network to the gateway is unavailable in this sandbox, report ` +
  `the retrieval-only result (top-k chunks and scores) and clearly say the live-LLM leg was not ` +
  `exercised.`,
  {
    label: 'verify:grounding-check',
    schema: {
      type: 'object',
      properties: {
        query: { type: 'string' },
        retrievedChunks: {
          type: 'array',
          items: { type: 'object', properties: { id: { type: 'string' }, score: { type: 'number' } } },
        },
        chunksContainAnswer: { type: 'boolean' },
        answer: { type: 'string' },
        liveGatewayCallMade: { type: 'boolean' },
      },
      required: ['query', 'retrievedChunks', 'chunksContainAnswer'],
    },
  }
)

const skepticVerdict = await agent(
  `You are an adversarial skeptic reviewing this RAG grounding-check result, not the agent ` +
  `who produced it: ${JSON.stringify(groundingCheck)}. Independently open ` +
  `${ROOT}\\src\\main\\java\\part3 and the specific part1/part2 source file the question is ` +
  `about, and verify for yourself whether the claim "chunksContainAnswer" is actually true and ` +
  `whether the reported answer is actually correct per the real source — do not just trust the ` +
  `prior report. Default to refuted=true if you cannot independently confirm both.`,
  {
    label: 'verify:skeptic',
    schema: {
      type: 'object',
      properties: { refuted: { type: 'boolean' }, reason: { type: 'string' } },
      required: ['refuted', 'reason'],
    },
  }
)
log(`Skeptic verdict: ${skepticVerdict.refuted ? 'REFUTED' : 'confirmed'} — ${skepticVerdict.reason}`)

phase('Report')
const report = await agent(
  `Write the final human-facing report for this part3-rag-build run. Inputs: design decision ` +
  `${JSON.stringify(design)}; implementation notes: core=${coreResult}, gateway=${gatewayResult}, ` +
  `runner/compile=${runnerResult}; test result: ${testResult}; grounding check: ` +
  `${JSON.stringify(groundingCheck)}; skeptic verdict: ${JSON.stringify(skepticVerdict)}. ` +
  `Also append a Changelog entry to ${PLAN} (under its "## Changelog" section) summarizing the ` +
  `embedding strategy chosen and why, and any deviation from the plan's proposed components. ` +
  `Then return a concise plain-text report: what was built, the embedding strategy used and why, ` +
  `test pass/fail counts, the grounding check outcome, and the skeptic's verdict. If the skeptic ` +
  `refuted the grounding claim, say so plainly and flag it for human review rather than smoothing ` +
  `it over.`,
  { label: 'report:final' }
)

return { design, testResult, groundingCheck, skepticVerdict, report }
