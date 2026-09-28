package Lab5;

/**
 * Minimal shape every pipeline tool declares itself against - name plus an
 * honest readOnly() flag, the same discipline the MCP reference server and
 * project-guardian use ("every tool declares readOnlyHint, and it must be
 * true"). This agent orchestrates its tools directly in code rather than
 * exposing them for a model to call, but the split-read-from-write
 * declaration is kept anyway: it's what HumanApproval below decides to gate
 * on, and it keeps this agent's tools honest about their own blast radius.
 */
public interface Tool {
    String name();

    boolean readOnly();
}
