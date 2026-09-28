package part4;

/*
 * YOUR toolset for Lab 4.1. Start from the BAD one (copied below) and fix it.
 *
 * Run:   java Clinic --tools bad mine
 *
 * What to change - each is worth measuring on its own:
 *   1. Names that say what the tool does, and one job per tool.
 *   2. Descriptions that say what it returns, when to use it, and when NOT to.
 *   3. Typed, named parameters: enums for closed sets, patterns for ids, examples.
 *   4. Errors the model can act on: pass through code, message and hint.
 *   5. A way to DISCOVER valid values (e.g. list config keys) instead of guessing.
 *
 * Keep run() honest: it must call the real API through `api`, same as the others.
 *
 * PORTING NOTE
 * ------------
 * The Python starter this was converted from does:
 *
 *     from toolsets import GOOD, run_bad
 *     TOOLS = json.loads(json.dumps(BAD))   # TODO: rewrite these
 *     def run(api, name, args):
 *         return run_bad(api, name, args)   # TODO: route your tools to the API
 *
 * Note that it imports GOOD and run_bad but then builds TOOLS from BAD, which was
 * never imported -- as written, that raises a NameError the moment mine.py is
 * loaded. This port keeps the same "start from BAD" shape (BAD is what the comment
 * and the lab instructions actually say to copy), reproduced faithfully rather than
 * silently fixed, so it does the same job GOOD did import: available for you to
 * reach for once you start rewriting tools 3-5 in the checklist above. There is no
 * separate toolsets.py module here -- both BAD/run_bad and GOOD/run_good live in
 * Clinic.Toolsets (see Clinic.java), so this file reaches them as
 * Clinic.Toolsets.BAD / .GOOD / .runBad / .runGood instead of via an import.
 */

import java.util.List;
import java.util.Map;

final class Mine {

    // TODO: rewrite these. Starting point is a deep copy of BAD, the same as the
    // Python `TOOLS = json.loads(json.dumps(BAD))`.
    static final List<Object> TOOLS = deepCopy(Clinic.Toolsets.BAD);

    static Clinic.ToolResult run(Clinic.OpsClient.Api api, String name, Map<String, Object> args) throws Exception {
        return Clinic.Toolsets.runBad(api, name, args); // TODO: route your tools to the API
    }

    /** Packages TOOLS/run as the Toolset ToolsetRegistry.get("mine") hands to clinic. */
    static Clinic.Toolset toolset() {
        return new Clinic.Toolset(TOOLS, Mine::run);
    }

    /** Deep-copies a tool schema list, the same effect as Python's json.loads(json.dumps(...)). */
    @SuppressWarnings("unchecked")
    private static List<Object> deepCopy(List<Object> tools) {
        return (List<Object>) Clinic.Json.parse(Clinic.Json.stringify(tools));
    }
}