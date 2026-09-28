package part4;
/*
 * Lab 4.1 -- Tool design clinic, measured. (Java port of clinic.py)
 *
 * Runs the same ten goals against the real model with one toolset, executes every
 * tool call for real against a throwaway aira-ops, and scores the result.
 *
 *     java Clinic --tools bad
 *     java Clinic --tools good
 *     java Clinic --tools mine        // your rewrite, wired up in ToolsetRegistry.load("mine")
 *     java Clinic --tools bad good    // side by side
 *
 * Costs roughly $0.05-0.15 per toolset on claude-sonnet. Needs a .env (gateway key).
 * The aira-ops it starts is private to this run: own port, own token, own database.
 *
 * PORTING NOTES
 * -------------
 * The original script leans on two sibling Python modules that were not part of the
 * file that was converted here: `labkit/python/agentic_core.py` (Config, GatewayClient,
 * BudgetGuard, BudgetExceeded) and `toolsets.py` (TOOLSETS, tool JSON schemas, and the
 * per-toolset `runner` that turns a tool_use block into an aira-ops API call).
 *
 * Those two modules define the actual tool schemas and business logic, so they can't be
 * mechanically translated without their source. This port keeps the exact control flow,
 * scoring, and process/HTTP plumbing of clinic.py, and gives you:
 *
 *   - Config          -- reads .env the same way (GATEWAY_API_KEY / MODEL / BUDGET_USD)
 *   - GatewayClient    -- POSTs to the Anthropic-style /v1/messages endpoint
 *   - BudgetGuard / BudgetExceeded -- same cost-tracking behavior
 *   - ToolsetRegistry  -- the place to port toolsets.py's TOOLSETS map into Java; it
 *                         currently throws with a clear message so a missing toolset
 *                         fails loudly instead of silently doing nothing
 *   - Mine             -- the place your own toolset (mine.py's equivalent) goes; see
 *                         the stub at the bottom for the shape it must have
 *
 * This file has no external dependencies -- only the JDK (java.net.http.HttpClient and
 * a small hand-rolled JSON reader/writer, since org.json/Jackson aren't reachable from
 * this environment's package mirrors).
 */

import java.io.*;
import java.net.*;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class Clinic {

    static final String SYSTEM =
            "You answer questions about AiraMatrix's support and operations system using the tools " +
                    "provided. Be concise: answer in one or two sentences with the specific ids and values. " +
                    "If you cannot find the answer, say so plainly rather than guessing.";
    static final int MAX_STEPS = 6;

    // An answer that says it could not find the answer is never a pass, whatever else
    // it happens to contain. (Found in the first real run: "I wasn't able to find a
    // config key" passed because the pattern "on" matched inside "config".)
    static final Pattern GAVE_UP = Pattern.compile(
            "(wasn.t|was not|not) able to|couldn.t|could not|cannot find|can.t find|unable to",
            Pattern.CASE_INSENSITIVE);

    // ----------------------------------------------------------------------------
    // Minimal JSON support (no external deps available in this environment)
    // ----------------------------------------------------------------------------

    /** A tiny recursive-descent JSON parser producing Map/List/String/Double/Boolean/null. */
    static final class Json {
        static Object parse(String s) {
            int[] i = {0};
            skipWs(s, i);
            Object v = parseValue(s, i);
            skipWs(s, i);
            return v;
        }

        private static void skipWs(String s, int[] i) {
            while (i[0] < s.length() && Character.isWhitespace(s.charAt(i[0]))) i[0]++;
        }

        private static Object parseValue(String s, int[] i) {
            skipWs(s, i);
            char c = s.charAt(i[0]);
            if (c == '{') return parseObject(s, i);
            if (c == '[') return parseArray(s, i);
            if (c == '"') return parseString(s, i);
            if (c == 't') { i[0] += 4; return Boolean.TRUE; }
            if (c == 'f') { i[0] += 5; return Boolean.FALSE; }
            if (c == 'n') { i[0] += 4; return null; }
            return parseNumber(s, i);
        }

        private static Map<String, Object> parseObject(String s, int[] i) {
            Map<String, Object> m = new LinkedHashMap<>();
            i[0]++; // {
            skipWs(s, i);
            if (s.charAt(i[0]) == '}') { i[0]++; return m; }
            while (true) {
                skipWs(s, i);
                String key = parseString(s, i);
                skipWs(s, i);
                i[0]++; // :
                Object val = parseValue(s, i);
                m.put(key, val);
                skipWs(s, i);
                char c = s.charAt(i[0]++);
                if (c == '}') break;
                // else ',' -> continue
            }
            return m;
        }

        private static List<Object> parseArray(String s, int[] i) {
            List<Object> l = new ArrayList<>();
            i[0]++; // [
            skipWs(s, i);
            if (s.charAt(i[0]) == ']') { i[0]++; return l; }
            while (true) {
                Object val = parseValue(s, i);
                l.add(val);
                skipWs(s, i);
                char c = s.charAt(i[0]++);
                if (c == ']') break;
            }
            return l;
        }

        private static String parseString(String s, int[] i) {
            StringBuilder sb = new StringBuilder();
            i[0]++; // opening quote
            while (true) {
                char c = s.charAt(i[0]++);
                if (c == '"') break;
                if (c == '\\') {
                    char e = s.charAt(i[0]++);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'u':
                            String hex = s.substring(i[0], i[0] + 4);
                            i[0] += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private static Object parseNumber(String s, int[] i) {
            int start = i[0];
            while (i[0] < s.length() && "-+.eE0123456789".indexOf(s.charAt(i[0])) >= 0) i[0]++;
            String num = s.substring(start, i[0]);
            if (num.contains(".") || num.contains("e") || num.contains("E")) return Double.parseDouble(num);
            try { return Long.parseLong(num); } catch (NumberFormatException e) { return Double.parseDouble(num); }
        }

        static String stringify(Object o) {
            StringBuilder sb = new StringBuilder();
            write(o, sb);
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        private static void write(Object o, StringBuilder sb) {
            if (o == null) { sb.append("null"); return; }
            if (o instanceof String s) { writeString(s, sb); return; }
            if (o instanceof Boolean b) { sb.append(b.toString()); return; }
            if (o instanceof Number n) { sb.append(n.toString()); return; }
            if (o instanceof Map<?, ?> m) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    writeString(String.valueOf(e.getKey()), sb);
                    sb.append(':');
                    write(e.getValue(), sb);
                }
                sb.append('}');
                return;
            }
            if (o instanceof List<?> l) {
                sb.append('[');
                boolean first = true;
                for (Object v : l) {
                    if (!first) sb.append(',');
                    first = false;
                    write(v, sb);
                }
                sb.append(']');
                return;
            }
            writeString(o.toString(), sb);
        }

        private static void writeString(String s, StringBuilder sb) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                }
            }
            sb.append('"');
        }
    }

    // ----------------------------------------------------------------------------
    // Config -- reads .env the way agentic_core.Config did
    // ----------------------------------------------------------------------------

    static final class Config {
        final String apiKey;
        final String model;
        final double budgetUsd;
        final String gatewayUrl;

        Config() {
            Map<String, String> env = readDotEnv(Paths.get(".env"));
            this.apiKey = env.getOrDefault("GATEWAY_API_KEY", System.getenv("sk-d855BPwAniE_SuymHAny3g"));
            this.model = env.getOrDefault("MODEL", env.getOrDefault("model", "claude-sonnet"));
            this.gatewayUrl = env.getOrDefault("GATEWAY_URL", "https://13-205-72-246.nip.io/v1/messages");
            String b = env.getOrDefault("BUDGET_USD", "0.50");
            double parsed;
            try { parsed = Double.parseDouble(b); } catch (Exception e) { parsed = 0.50; }
            this.budgetUsd = parsed;
        }

        private static Map<String, String> readDotEnv(Path path) {
            Map<String, String> m = new HashMap<>();
            if (!Files.exists(path)) return m;
            try {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#") || !t.contains("=")) continue;
                    int idx = t.indexOf('=');
                    String k = t.substring(0, idx).trim();
                    String v = t.substring(idx + 1).trim();
                    if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    m.put(k, v);
                }
            } catch (IOException ignored) {}
            return m;
        }
    }

    // ----------------------------------------------------------------------------
    // BudgetGuard / BudgetExceeded
    // ----------------------------------------------------------------------------

    static final class BudgetExceeded extends Exception {
        BudgetExceeded(String msg) { super(msg); }
    }

    static final class BudgetGuard {
        final double maxUsd;
        final String model;
        double spent = 0.0;
        // Rough per-token pricing placeholder; port the real table from agentic_core if it differs.
        static final double INPUT_PER_MTOK = 3.0;
        static final double OUTPUT_PER_MTOK = 15.0;

        BudgetGuard(double maxUsd, String model) {
            this.maxUsd = maxUsd;
            this.model = model;
        }

        void check() throws BudgetExceeded {
            if (spent >= maxUsd) {
                throw new BudgetExceeded(String.format("spent $%.4f, budget $%.4f exhausted", spent, maxUsd));
            }
        }

        @SuppressWarnings("unchecked")
        void record(Map<String, Object> usage) {
            if (usage == null) return;
            double inTok = numOf(usage.get("input_tokens"));
            double outTok = numOf(usage.get("output_tokens"));
            spent += (inTok / 1_000_000.0) * INPUT_PER_MTOK + (outTok / 1_000_000.0) * OUTPUT_PER_MTOK;
        }

        private static double numOf(Object o) {
            if (o instanceof Number n) return n.doubleValue();
            return 0.0;
        }
    }

    // ----------------------------------------------------------------------------
    // GatewayClient -- POSTs to the /v1/messages-shaped gateway endpoint
    // ----------------------------------------------------------------------------

    static final class GatewayClient {
        final Config cfg;
        final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

        GatewayClient(Config cfg) { this.cfg = cfg; }

        /**
         * Mirrors GatewayClient.messages(messages, tools=..., system=..., max_tokens=...)
         * Returns the parsed response body as a Map (content, usage, stop_reason, ...).
         */
        @SuppressWarnings("unchecked")
        Map<String, Object> messages(List<Object> messages, List<Object> tools, String system, int maxTokens) throws IOException, InterruptedException {
            Map<String, Object> body = new LinkedHashMap<>();
            System.out.println("system : "+system);
            body.put("model", cfg.model);
            body.put("max_tokens", maxTokens);
            body.put("content", system);
            body.put("messages", messages);
            if (tools != null && !tools.isEmpty()) body.put("tools", tools);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.gatewayUrl))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + (cfg.apiKey == null ? "" : cfg.apiKey))
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            Object parsed = Json.parse(resp.body());
            System.out.println("resp.body() :"+resp.body());
            if (!(parsed instanceof Map)) {
                throw new IOException("gateway returned non-object body: " + resp.body());
            }
            return (Map<String, Object>) parsed;
        }
    }

    // ----------------------------------------------------------------------------
    // Toolsets -- port toolsets.py's TOOLSETS map here
    // ----------------------------------------------------------------------------

    /** A single tool call's result: raw text content plus whether it was an error. */
    record ToolResult(String content, boolean isError) {}

    interface ToolRunner {
        /** Mirrors toolsets.py's runner(ops.api, name, input) -> (content: str, is_error: bool). */
        ToolResult run(OpsClient.Api api, String toolName, Map<String, Object> input) throws Exception;
    }

    record Toolset(List<Object> tools, ToolRunner runner) {
        List<String> names() {
            List<String> out = new ArrayList<>();
            for (Object t : tools) {
                if (t instanceof Map<?, ?> m && m.get("name") != null) out.add(String.valueOf(m.get("name")));
            }
            return out;
        }
    }

    static final class ToolsetRegistry {
        static Toolset get(String name) {
            switch (name) {
                case "bad":
                    return new Toolset(Toolsets.BAD, Toolsets::runBad);
                case "good":
                    return new Toolset(Toolsets.GOOD, Toolsets::runGood);
                case "mine":
                    return Mine.toolset();
                default:
                    throw new IllegalArgumentException("unknown toolset: " + name);
            }
        }
    }

    /**
     * Two toolsets over the SAME aira-ops API. Only the interface differs.
     *
     * BAD is what gets written in a hurry: three tools, vague names, one-letter
     * parameters, a tool that does two jobs, errors swallowed to the word "error".
     * GOOD is the same capability designed for a model to use.
     *
     * (Java port of toolsets.py.)
     */
    static final class Toolsets {

        // ----------------------------------------------------------------- BAD
        static final List<Object> BAD = List.of(
                mapOf(
                        "name", "tickets",
                        "description", "Ticket stuff.",
                        "input_schema", mapOf(
                                "type", "object",
                                "properties", mapOf("q", mapOf("type", "string")),
                                "required", List.of("q"))),
                mapOf(
                        "name", "acct",
                        "description", "Get account.",
                        "input_schema", mapOf(
                                "type", "object",
                                "properties", mapOf("a", mapOf("type", "string")),
                                "required", List.of("a"))),
                mapOf(
                        "name", "cfg",
                        "description", "Config.",
                        "input_schema", mapOf(
                                "type", "object",
                                "properties", mapOf(
                                        "k", mapOf("type", "string"),
                                        "v", mapOf()), // no constraints, same as Python's {}
                                "required", List.of("k")))
        );

        /** What a hurried implementation does: guess intent, swallow detail. */
        static ToolResult runBad(OpsClient.Api api, String name, Map<String, Object> args) throws Exception {
            switch (name) {
                case "tickets": {
                    String q = String.valueOf(args.getOrDefault("q", ""));
                    OpsClient.ApiResult r;
                    if (q.matches("T-\\d{4}")) {
                        r = api.call("GET", "/tickets/" + q);
                    } else {
                        r = api.call("GET", "/tickets?q=" + urlEncode(q));
                    }
                    return r.status() == 200 ? new ToolResult(Json.stringify(r.body()), false) : new ToolResult("error", true);
                }
                case "acct": {
                    OpsClient.ApiResult r = api.call("GET", "/accounts/" + String.valueOf(args.getOrDefault("a", "")));
                    return r.status() == 200 ? new ToolResult(Json.stringify(r.body()), false) : new ToolResult("error", true);
                }
                case "cfg": {
                    OpsClient.ApiResult r = api.call("GET", "/config/" + String.valueOf(args.getOrDefault("k", "")));
                    return r.status() == 200 ? new ToolResult(Json.stringify(r.body()), false) : new ToolResult("error", true);
                }
                default:
                    return new ToolResult("error", true);
            }
        }

        // ---------------------------------------------------------------- GOOD
        static final List<Object> GOOD = List.of(
                mapOf(
                        "name", "search_tickets",
                        "description",
                        "Find support tickets by status, priority, account or words in the title/body. " +
                                "Returns a short list (id, title, status, priority, account_id, assignee). " +
                                "Use this whenever you do not already have a ticket id; use get_ticket for the " +
                                "full body and comments.",
                        "input_schema", mapOf(
                                "type", "object",
                                "properties", mapOf(
                                        "status", mapOf("type", "string", "enum", List.of("open", "in_progress", "resolved", "closed")),
                                        "priority", mapOf("type", "string", "enum", List.of("P1", "P2", "P3", "P4"),
                                                "description", "P1 is most urgent"),
                                        "account_id", mapOf("type", "string", "pattern", "^ACC-\\d{4}$",
                                                "description", "e.g. ACC-1001"),
                                        "query", mapOf("type", "string", "maxLength", 500,
                                                "description", "Words to match in title or body, e.g. 'DICOM'")),
                                "additionalProperties", false)),
                mapOf(
                        "name", "get_ticket",
                        "description", "Full ticket by id: body, status, assignee and every comment (resolutions are in comments).",
                        "input_schema", mapOf(
                                "type", "object",
                                "properties", mapOf(
                                        "id", mapOf("type", "string", "pattern", "^T-\\d{4}$",
                                                "description", "Ticket id, e.g. T-1001")),
                                "required", List.of("id"))),
                mapOf(
                        "name", "lookup_account",
                        "description",
                        "Customer account by id: name, tier, region, contracted SLA in minutes, and the " +
                                "number of open tickets. Ids look like ACC-1001; this does not search by name.",
                        "input_schema", mapOf(
                                "type", "object",
                                "properties", mapOf(
                                        "id", mapOf("type", "string", "pattern", "^ACC-\\d{4}$",
                                                "description", "e.g. ACC-1001")),
                                "required", List.of("id"))),
                mapOf(
                        "name", "get_config",
                        "description",
                        "Read platform configuration. Pass a key (e.g. 'ingest.rush_slide_limit') for one " +
                                "value, or no key to list every key with its value and description.",
                        "input_schema", mapOf(
                                "type", "object",
                                "properties", mapOf(
                                        "key", mapOf("type", "string", "description", "Dotted key, e.g. alerts.ingest_latency_minutes"))))
        );

        /** Same API. Errors pass through with their code, message and hint. */
        static ToolResult runGood(OpsClient.Api api, String name, Map<String, Object> args) throws Exception {
            OpsClient.ApiResult r;
            switch (name) {
                case "search_tickets": {
                    LinkedHashMap<String, Object> p = new LinkedHashMap<>();
                    putIfPresent(p, "status", args.get("status"));
                    putIfPresent(p, "priority", args.get("priority"));
                    putIfPresent(p, "account_id", args.get("account_id"));
                    putIfPresent(p, "q", args.get("query"));
                    String qs = p.entrySet().stream()
                            .map(e -> e.getKey() + "=" + urlEncode(String.valueOf(e.getValue())))
                            .collect(Collectors.joining("&"));
                    r = api.call("GET", "/tickets?" + qs);
                    break;
                }
                case "get_ticket":
                    r = api.call("GET", "/tickets/" + String.valueOf(args.getOrDefault("id", "")));
                    break;
                case "lookup_account":
                    r = api.call("GET", "/accounts/" + String.valueOf(args.getOrDefault("id", "")));
                    break;
                case "get_config": {
                    Object k = args.get("key");
                    r = api.call("GET", k != null ? "/config/" + k : "/config");
                    break;
                }
                default: {
                    String available = GOOD.stream()
                            .map(t -> String.valueOf(((Map<?, ?>) t).get("name")))
                            .collect(Collectors.joining(", "));
                    Map<String, Object> err = mapOf("error", mapOf(
                            "code", "unknown_tool", "message", name, "hint", "Available: " + available));
                    return new ToolResult(Json.stringify(err), true);
                }
            }
            return new ToolResult(Json.stringify(r.body()), r.status() >= 400);
        }

        private static void putIfPresent(Map<String, Object> m, String key, Object value) {
            if (value != null && !String.valueOf(value).isEmpty()) m.put(key, value);
        }

        private static String urlEncode(String s) {
            return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
        }
    }

    // Mine.toolset() -- your rewrite, the Java equivalent of mine.py -- now lives in
    // its own file, Mine.java, in this same (default) package. See that file.

    // ----------------------------------------------------------------------------
    // ThrowawayOps -- a private aira-ops for this run
    // ----------------------------------------------------------------------------

    static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    static final class OpsClient {
        final int port;
        final String token;
        final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        OpsClient(int port, String token) { this.port = port; this.token = token; }

        /** Bound "api" function, matching ops.api(method, path) -> (status, parsed_json). */
        interface Api {
            ApiResult call(String method, String path) throws IOException, InterruptedException;
        }

        record ApiResult(int status, Object body) {}

        ApiResult api(String method, String path) throws IOException, InterruptedException {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .header("X-Actor", "clinic")
                    .method(method, HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String bodyStr = resp.body();
            Object parsed;
            try {
                parsed = (bodyStr == null || bodyStr.isBlank()) ? new LinkedHashMap<>() : Json.parse(bodyStr);
            } catch (Exception e) {
                parsed = new LinkedHashMap<>();
            }
            return new ApiResult(resp.statusCode(), parsed);
        }
    }

    static final class ThrowawayOps implements AutoCloseable {
        Path tmpDir;
        int port;
        String token;
        Process process;
        OpsClient client;

        static ThrowawayOps start(Path repoRoot) throws IOException {
            ThrowawayOps o = new ThrowawayOps();
            o.tmpDir = Files.createTempDirectory("aira-ops-");
            o.port = freePort();
            o.token = randomHex(32);

            Path opsScript = repoRoot.resolve("day3-integration-security/aira-ops/aira_ops.py");
            ProcessBuilder pb = new ProcessBuilder(
                    "python3", opsScript.toString(),
                    "--port", String.valueOf(o.port),
                    "--db", o.tmpDir.resolve("c.sqlite").toString(),
                    "--quiet");
            pb.environment().put("AIRA_OPS_TOKEN", o.token);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            o.process = pb.start();

            o.client = new OpsClient(o.port, o.token);
            HttpClient probe = HttpClient.newHttpClient();
            for (int i = 0; i < 50; i++) {
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + o.port + "/health"))
                            .timeout(Duration.ofMillis(200))
                            .GET().build();
                    probe.send(req, HttpResponse.BodyHandlers.discarding());
                    break;
                } catch (Exception e) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                } catch (Throwable t) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                }
            }
            return o;
        }

        private static String randomHex(int nBytes) {
            byte[] b = new byte[nBytes];
            new SecureRandomHolder().random.nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        }

        private static final class SecureRandomHolder {
            final java.security.SecureRandom random = new java.security.SecureRandom();
        }

        @Override
        public void close() {
            if (process != null) {
                process.destroy();
                try { process.waitFor(); } catch (InterruptedException ignored) {}
            }
            try {
                if (tmpDir != null) {
                    Files.walk(tmpDir)
                            .sorted(Comparator.reverseOrder())
                            .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                }
            } catch (IOException ignored) {}
        }
    }

    // ----------------------------------------------------------------------------
    // correct() -- pass/fail scoring, same rule as the Python version
    // ----------------------------------------------------------------------------

    static boolean correct(String answer, List<String> expect) {
        if (GAVE_UP.matcher(answer).find()) return false;
        for (String e : expect) {
            if (!Pattern.compile(e, Pattern.CASE_INSENSITIVE).matcher(answer).find()) return false;
        }
        return true;
    }

    // ----------------------------------------------------------------------------
    // runGoal() -- the tool-use loop
    // ----------------------------------------------------------------------------

    record GoalRun(String answer, int calls, int errors, int steps) {}

    @SuppressWarnings("unchecked")
    static GoalRun runGoal(GatewayClient client, BudgetGuard budget, ThrowawayOps ops,
                           Toolset toolset, String goal) throws Exception {
        List<Object> messages = new ArrayList<>();
        messages.add(mapOf("role", "user", "content", goal));
        int calls = 0, errors = 0;

        for (int step = 1; step <= MAX_STEPS; step++) {
            budget.check();
            Map<String, Object> r = client.messages(messages, toolset.tools(), SYSTEM, 800);
            budget.record((Map<String, Object>) r.get("usage"));
            System.out.println("sohb1 : "+SYSTEM);
            List<Object> content = (List<Object>) r.get("content");
            System.out.println("sohb1 : "+content);
            messages.add(mapOf("role", "assistant", "content", content));

            if (!"tool_use".equals(r.get("stop_reason"))) {
                StringBuilder text = new StringBuilder();
                for (Object bo : content) {
                    Map<String, Object> b = (Map<String, Object>) bo;
                    if ("text".equals(b.get("type"))) {
                        if (text.length() > 0) text.append(' ');
                        text.append(String.valueOf(b.getOrDefault("text", "")));
                    }
                }
                return new GoalRun(text.toString().trim(), calls, errors, step);
            }

            List<Object> results = new ArrayList<>();
            System.out.println("sohb : "+content.size());
            for (Object bo : content) {
                Map<String, Object> b = (Map<String, Object>) bo;
                if (!"tool_use".equals(b.get("type"))) continue;
                calls++;
                String out;
                boolean isErr;
                try {
                    Map<String, Object> input = b.get("input") instanceof Map ? (Map<String, Object>) b.get("input") : new LinkedHashMap<>();
                    ToolResult tr = toolset.runner().run(ops.client::api, String.valueOf(b.get("name")), input);
                    out = tr.content();
                    isErr = tr.isError();
                } catch (Exception e) { // a tool failure is data for the model, never a crashed run
                    out = "tool raised " + e.getClass().getSimpleName() + ": " + e.getMessage();
                    isErr = true;
                }
                if (isErr) errors++;
                results.add(mapOf(
                        "type", "tool_result",
                        "tool_use_id", b.get("id"),
                        "content", out.length() > 6000 ? out.substring(0, 6000) : out,
                        "is_error", isErr));
            }
            messages.add(mapOf("role", "user", "content", results));
        }
        return new GoalRun("(step limit reached)", calls, errors, MAX_STEPS);
    }

    static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }

    // ----------------------------------------------------------------------------
    // main()
    // ----------------------------------------------------------------------------

    record Goal(String id, String goal, List<String> expect) {}

    @SuppressWarnings("unchecked")
    static List<Goal> loadGoals(Path path) throws IOException {
        String text = Files.readString(path, StandardCharsets.UTF_8);
        Object parsed = Json.parse(text);
        List<Goal> goals = new ArrayList<>();
        for (Object o : (List<Object>) parsed) {
            Map<String, Object> m = (Map<String, Object>) o;
            List<String> expect = new ArrayList<>();
            for (Object e : (List<Object>) m.get("expect")) expect.add(String.valueOf(e));
            goals.add(new Goal(String.valueOf(m.get("id")), String.valueOf(m.get("goal")), expect));
        }
        return goals;
    }

    record Summary(int correct, int goals, double avgCalls, int errors, double cost) {}

    public static void main(String[] args) throws Exception {
        List<String> toolNames = new ArrayList<>();
        Path goalsPath = Paths.get("D:\\AgenticAI\\AgenticAI_p01\\src\\main\\java\\part4\\goals.json");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--tools")) {
                i++;
                while (i < args.length && !args[i].startsWith("--")) {
                    String t = args[i];
                    if (!t.equals("bad") && !t.equals("good") && !t.equals("mine")) {
                        System.err.println("invalid --tools choice: " + t + " (choose from bad, good, mine)");
                        System.exit(2);
                    }
                    toolNames.add(t);
                    i++;
                }
                i--;
            } else if (args[i].equals("--goals")) {
                goalsPath = Paths.get(args[++i]);
            }
        }
        if (toolNames.isEmpty()) toolNames.add("bad");

        Path here = Paths.get("").toAbsolutePath();
        Path repo = here.getParent() != null && here.getParent().getParent() != null
                ? here.getParent().getParent() : here; // best-effort mirror of HERE.parents[1]

        Config cfg = new Config();
        GatewayClient client = new GatewayClient(cfg);
        List<Goal> goals = loadGoals(goalsPath);

        Map<String, Summary> board = new LinkedHashMap<>();
        Path outDir = Paths.get("results");
        Files.createDirectories(outDir);

        try (ThrowawayOps ops = ThrowawayOps.start(repo)) {
            for (String name : toolNames) {
                Toolset toolset = ToolsetRegistry.get(name);
                BudgetGuard budget = new BudgetGuard(Math.max(cfg.budgetUsd, 0.50), cfg.model);
                List<Map<String, Object>> rows = new ArrayList<>();

                System.out.println("\n=== toolset: " + name + "  (" + String.join(", ", toolset.names()) + ")");
                for (Goal g : goals) {
                    GoalRun run;
                    try {
                        run = runGoal(client, budget, ops, toolset, g.goal());
                    } catch (BudgetExceeded e) {
                        System.out.println("  budget: " + e.getMessage());
                        break;
                    }
                    boolean ok = correct(run.answer(), g.expect());
                    Map<String, Object> row = mapOf(
                            "id", g.id(), "ok", ok, "calls", run.calls(), "errors", run.errors(),
                            "steps", run.steps(), "answer", run.answer());
                    rows.add(row);
                    String preview = run.answer().length() > 90 ? run.answer().substring(0, 90) : run.answer();
                    System.out.printf("  %s  #%-2s calls=%d errors=%d  %s%n",
                            ok ? "PASS" : "FAIL", g.id(), run.calls(), run.errors(), "'" + preview + "'");
                }

                int n = Math.max(rows.size(), 1);
                int correctCount = (int) rows.stream().filter(r -> (Boolean) r.get("ok")).count();
                double avgCalls = rows.stream().mapToInt(r -> (Integer) r.get("calls")).sum() / (double) n;
                int errorSum = rows.stream().mapToInt(r -> (Integer) r.get("errors")).sum();
                Summary summary = new Summary(correctCount, rows.size(), Math.round(avgCalls * 10) / 10.0, errorSum, Math.round(budget.spent * 10000) / 10000.0);
                board.put(name, summary);

                Map<String, Object> fileOut = mapOf(
                        "summary", mapOf("correct", summary.correct(), "goals", summary.goals(),
                                "calls", summary.avgCalls(), "errors", summary.errors(), "cost", summary.cost()),
                        "rows", rows);
                Files.writeString(outDir.resolve(name + ".json"), Json.stringify(fileOut), StandardCharsets.UTF_8);
            }
        }

        System.out.println("\n  toolset   correct   avg calls/goal   tool errors   cost");
        for (Map.Entry<String, Summary> e : board.entrySet()) {
            Summary b = e.getValue();
            System.out.printf("  %-8s  %2d/%-5d   %6.1f           %5d       $%.3f%n",
                    e.getKey(), b.correct(), b.goals(), b.avgCalls(), b.errors(), b.cost());
        }
        System.out.println("\n  model: " + cfg.model + "  ·  every tool call executed against a private aira-ops  ·  results/ has the detail");
    }
}