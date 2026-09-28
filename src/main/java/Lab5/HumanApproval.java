package Lab5;

import java.io.PrintStream;
import java.util.Scanner;

/**
 * Human approval element (mandatory #4): every write this agent makes
 * (writing the generated test file) requires an explicit human "yes" before
 * it happens - mirrors the reference MCP server's client.py gate ("reads
 * run; anything else needs a human at a terminal") and this repo's own
 * ask/allow split. With no terminal available, this refuses rather than
 * defaulting to approved - the same fail-closed behavior client.py's
 * approved() has when stdin is not a tty.
 */
public class HumanApproval {

    private final Scanner input;
    private final PrintStream output;

    public HumanApproval(Scanner input, PrintStream output) {
        this.input = input;
        this.output = output;
    }

    public record Decision(boolean approved, String reason) {
    }

    public Decision requestApproval(String action, String detail) {
        output.println("APPROVAL REQUIRED: " + action);
        output.println(detail);
        output.print("Proceed? [y/N] ");
        output.flush();

        if (System.console() == null && !input.hasNextLine()) {
            return new Decision(false, "no human at a terminal to ask - refusing rather than assuming approval");
        }

        String answer;
        try {
            answer = input.hasNextLine() ? input.nextLine() : "";
        } catch (Exception e) {
            return new Decision(false, "could not read a response - refusing rather than assuming approval");
        }

        boolean approved = answer.trim().equalsIgnoreCase("y");
        return new Decision(approved, approved ? "approved by human" : "declined by human");
    }
}
