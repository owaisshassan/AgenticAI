package com.aira.guardian.rules;

import com.aira.guardian.errors.GuardianError;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pure validation logic for {@code validate_class_spec} - checks naming
 * pattern, layer legality, and required annotations against a {@link RuleSet}.
 * Kept free of any MCP/Spring concern so it's directly unit-testable.
 */
public class ClassSpecValidator {

    public record Violation(String code, String message, String hint) {
        public GuardianError toError() {
            return new GuardianError(code, message, hint);
        }
    }

    public record ValidationResult(boolean valid, List<Violation> violations) {
        public static ValidationResult pass() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult fail(List<Violation> violations) {
            return new ValidationResult(false, violations);
        }
    }

    public ValidationResult validate(ClassSpec spec, RuleSet rules) {
        List<Violation> violations = new ArrayList<>();

        checkLayerExists(spec, rules, violations);
        checkNaming(spec, rules, violations);
        checkLayerCallDirection(spec, rules, violations);
        checkRequiredAnnotations(spec, rules, violations);

        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    private void checkLayerExists(ClassSpec spec, RuleSet rules, List<Violation> violations) {
        if (spec.layer() == null || rules.layer(spec.layer()) == null) {
            violations.add(new Violation(
                    "unknown_layer",
                    "layer \"" + spec.layer() + "\" is not defined in guardian-rules.yaml",
                    "use one of the layers returned by get_conventions, e.g. \"service\", \"controller\", \"repository\", \"domain\""));
        }
    }

    private void checkNaming(ClassSpec spec, RuleSet rules, List<Violation> violations) {
        if (spec.layer() == null) {
            return;
        }
        String pattern = rules.naming().get(spec.layer());
        if (pattern == null) {
            return;
        }
        if (!Pattern.matches(pattern, spec.className())) {
            violations.add(new Violation(
                    "naming_violation",
                    "class name \"" + spec.className() + "\" does not match the required pattern for layer \""
                            + spec.layer() + "\": " + pattern,
                    "rename the class to match " + pattern + ", e.g. a service layer class should end in \"Service\""));
        }
    }

    private void checkLayerCallDirection(ClassSpec spec, RuleSet rules, List<Violation> violations) {
        RuleSet.Layer layer = rules.layer(spec.layer());
        if (layer == null || spec.fields() == null) {
            return;
        }
        for (ClassSpec.Field field : spec.fields()) {
            String fieldLayer = inferLayerFromTypeName(field.type(), rules);
            if (fieldLayer == null || fieldLayer.equals(spec.layer())) {
                continue;
            }
            boolean allowed = layer.mayCallLayers() != null && layer.mayCallLayers().contains(fieldLayer);
            if (!allowed) {
                violations.add(new Violation(
                        "illegal_layer_call",
                        "layer \"" + spec.layer() + "\" may not depend on layer \"" + fieldLayer
                                + "\" (field \"" + field.name() + "\" of type " + field.type() + ")",
                        "layer \"" + spec.layer() + "\" may only call: " + layer.mayCallLayers()
                                + " - remove this dependency or move the field's type to an allowed layer"));
            }
        }
    }

    private void checkRequiredAnnotations(ClassSpec spec, RuleSet rules, List<Violation> violations) {
        List<String> required = rules.requiredAnnotations().get(spec.layer());
        if (required == null || required.isEmpty()) {
            return;
        }
        List<String> have = spec.annotations() == null ? List.of() : spec.annotations();
        for (String requiredAnnotation : required) {
            if (!have.contains(requiredAnnotation)) {
                violations.add(new Violation(
                        "missing_required_annotation",
                        "class \"" + spec.className() + "\" in layer \"" + spec.layer()
                                + "\" is missing required annotation " + requiredAnnotation,
                        "add @" + simpleName(requiredAnnotation) + " (import " + requiredAnnotation + ")"));
            }
        }
    }

    /**
     * Best-effort: a field type ending in a layer's naming suffix (e.g.
     * "...Repository") is treated as belonging to that layer, so
     * "service may not call repository" style rules have something to check
     * against field types alone, without needing a full classpath scan.
     */
    private String inferLayerFromTypeName(String typeName, RuleSet rules) {
        if (typeName == null) {
            return null;
        }
        String simple = simpleName(typeName);
        for (var entry : rules.naming().entrySet()) {
            if (Pattern.matches(entry.getValue(), simple)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String simpleName(String maybeQualified) {
        int lastDot = maybeQualified.lastIndexOf('.');
        return lastDot < 0 ? maybeQualified : maybeQualified.substring(lastDot + 1);
    }
}
