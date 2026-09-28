package Lab5;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanApprovalTest {

    @Test
    void approvesOnExplicitYes() {
        Scanner input = new Scanner("y\n");
        HumanApproval approval = new HumanApproval(input, new PrintStream(new ByteArrayOutputStream()));

        HumanApproval.Decision decision = approval.requestApproval("write test", "detail");

        assertTrue(decision.approved());
    }

    @Test
    void refusesOnExplicitNo() {
        Scanner input = new Scanner("n\n");
        HumanApproval approval = new HumanApproval(input, new PrintStream(new ByteArrayOutputStream()));

        HumanApproval.Decision decision = approval.requestApproval("write test", "detail");

        assertFalse(decision.approved());
    }

    @Test
    void refusesOnAnythingOtherThanY() {
        Scanner input = new Scanner("sure\n");
        HumanApproval approval = new HumanApproval(input, new PrintStream(new ByteArrayOutputStream()));

        HumanApproval.Decision decision = approval.requestApproval("write test", "detail");

        assertFalse(decision.approved());
    }

    @Test
    void failsClosedWhenNoInputIsAvailable() {
        Scanner input = new Scanner("");
        HumanApproval approval = new HumanApproval(input, new PrintStream(new ByteArrayOutputStream()));

        HumanApproval.Decision decision = approval.requestApproval("write test", "detail");

        assertFalse(decision.approved());
        assertTrue(decision.reason().toLowerCase().contains("no human") || decision.reason().toLowerCase().contains("refusing"));
    }
}
