package com.aira.guardian.errors;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every tool failure takes this shape - never a bare string. Mirrors the
 * aira-ops reference server's {@code fail()} helper
 * ({@code error: {code, message, hint}}): a machine-checkable {@code code},
 * a human-readable {@code message}, and an actionable {@code hint} that
 * tells the caller (model or human) what to do next, every time.
 */
public final class GuardianError {

    private final String code;
    private final String message;
    private final String hint;
    private final Map<String, Object> details;

    public GuardianError(String code, String message, String hint) {
        this(code, message, hint, null);
    }

    public GuardianError(String code, String message, String hint, Map<String, Object> details) {
        this.code = code;
        this.message = message;
        this.hint = hint;
        this.details = details;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }

    public String hint() {
        return hint;
    }

    public Map<String, Object> details() {
        return details;
    }

    /** Wraps this error in the {@code {"error": {...}}} envelope tools return. */
    public Map<String, Object> toEnvelope() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("hint", hint);
        if (details != null && !details.isEmpty()) {
            body.put("details", details);
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("error", body);
        return envelope;
    }
}
