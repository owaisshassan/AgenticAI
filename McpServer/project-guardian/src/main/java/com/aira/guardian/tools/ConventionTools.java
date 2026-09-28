package com.aira.guardian.tools;

import com.aira.guardian.GuardianProperties;
import com.aira.guardian.arch.ArchRules;
import com.aira.guardian.errors.GuardianError;
import com.aira.guardian.proc.WorkspaceGuard;
import com.aira.guardian.rules.ClassScaffolder;
import com.aira.guardian.rules.ClassSpec;
import com.aira.guardian.rules.ClassSpecValidator;
import com.aira.guardian.rules.RuleSet;
import com.aira.guardian.rules.RuleSetLoader;
import com.aira.guardian.rules.SourceTree;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Convention-enforcement tools: get_conventions, list_existing_classes,
 * get_class, validate_class_spec (all read-only), scaffold_class (write),
 * and run_architecture_check (read-only). See server-level design rule
 * "split read from write" (mirrors aira-ops's search_tickets/get_ticket
 * vs. add_ticket_comment split): validate_class_spec never writes anything,
 * even though its sibling scaffold_class does.
 */
@Component
public class ConventionTools {

    private final RuleSetLoader ruleSetLoader;
    private final GuardianProperties properties;
    private final WorkspaceGuard workspaceGuard;
    private final ClassSpecValidator validator = new ClassSpecValidator();
    private final ClassScaffolder scaffolder = new ClassScaffolder();
    private final SourceTree sourceTree = new SourceTree();
    private final ArchRules archRules = new ArchRules();

    public ConventionTools(RuleSetLoader ruleSetLoader, GuardianProperties properties, WorkspaceGuard workspaceGuard) {
        this.ruleSetLoader = ruleSetLoader;
        this.properties = properties;
        this.workspaceGuard = workspaceGuard;
    }

    @McpTool(
            name = "get_conventions",
            description = "Returns the loaded guardian-rules.yaml as structured JSON: layers and their "
                    + "call-direction rules, naming patterns per layer, required annotations per layer, "
                    + "and the test gate configuration. Call this before proposing a new class so you know "
                    + "the exact naming pattern and layering rules to follow.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false)
    )
    public Map<String, Object> getConventions() {
        try {
            RuleSet rules = ruleSetLoader.load(properties.getRulesFile());
            return toJson(rules);
        } catch (IOException e) {
            return error("rules_unreadable",
                    "could not read guardian-rules.yaml: " + e.getMessage(),
                    "check guardian.rules-file points at a readable YAML file");
        }
    }

    @McpTool(
            name = "validate_class_spec",
            description = "Validates a proposed class against this project's layering, naming, and "
                    + "required-annotation rules. Never writes anything - call this before scaffold_class "
                    + "to check the spec will pass. Returns {\"valid\": true} or {\"valid\": false, "
                    + "\"violations\": [{code, message, hint}, ...]} with one violation per rule broken.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false)
    )
    public Map<String, Object> validateClassSpec(
            @McpToolParam(required = true, description = "Class name in PascalCase, e.g. \"InvoiceService\"")
            String className,
            @McpToolParam(required = true, description = "Fully-qualified package, e.g. \"com.acme.billing.service\"")
            String packageName,
            @McpToolParam(required = true, description = "One of the layer names from get_conventions, e.g. \"service\"")
            String layer,
            @McpToolParam(required = false, description = "Fields as {name, type} pairs, e.g. "
                    + "[{\"name\": \"repo\", \"type\": \"InvoiceRepository\"}]. Omit for a class with no fields yet.")
            List<Map<String, String>> fields,
            @McpToolParam(required = false, description = "Fully-qualified annotations the class will carry, "
                    + "e.g. [\"org.springframework.stereotype.Service\"]. Omit if none yet.")
            List<String> annotations
    ) {
        try {
            RuleSet rules = ruleSetLoader.load(properties.getRulesFile());
            ClassSpec spec = toClassSpec(className, packageName, layer, fields, annotations);
            ClassSpecValidator.ValidationResult result = validator.validate(spec, rules);

            if (result.valid()) {
                Map<String, Object> ok = new LinkedHashMap<>();
                ok.put("valid", true);
                return ok;
            }

            Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("valid", false);
            failed.put("violations", result.violations().stream()
                    .map(v -> Map.of("code", v.code(), "message", v.message(), "hint", v.hint()))
                    .toList());
            return failed;
        } catch (IOException e) {
            return error("rules_unreadable",
                    "could not read guardian-rules.yaml: " + e.getMessage(),
                    "check guardian.rules-file points at a readable YAML file");
        }
    }

    @McpTool(
            name = "list_existing_classes",
            description = "Lists classes under a given layer, so the model can check for naming collisions "
                    + "before proposing a new class. Returns className, packageName, and the relative source "
                    + "file path for each existing class in that layer.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false)
    )
    public Map<String, Object> listExistingClasses(
            @McpToolParam(required = true, description = "One of the layer names from get_conventions, e.g. \"service\"")
            String layer
    ) {
        try {
            RuleSet rules = ruleSetLoader.load(properties.getRulesFile());
            RuleSet.Layer layerDef = rules.layer(layer);
            if (layerDef == null) {
                return error("unknown_layer", "layer \"" + layer + "\" is not defined in guardian-rules.yaml",
                        "call get_conventions to see the defined layers");
            }

            List<SourceTree.ExistingClass> classes = sourceTree.listByLayer(workspaceGuard.root(), layerDef);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("classes", classes.stream()
                    .map(c -> Map.of("className", c.className(), "packageName", c.packageName(),
                            "path", c.relativePath()))
                    .toList());
            return result;
        } catch (IOException e) {
            return error("rules_unreadable", "could not read guardian-rules.yaml: " + e.getMessage(),
                    "check guardian.rules-file points at a readable YAML file");
        }
    }

    @McpTool(
            name = "get_class",
            description = "Returns a single class's current source, if it exists in the target project. "
                    + "Use this before proposing changes to an existing class, or to confirm scaffold_class "
                    + "actually wrote what was asked.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false)
    )
    public Map<String, Object> getClass(
            @McpToolParam(required = true, description = "Class name, e.g. \"InvoiceService\"")
            String className,
            @McpToolParam(required = false, description = "Fully-qualified package to disambiguate if multiple "
                    + "classes share this name. Omit if the name is unique in the project.")
            String packageName
    ) {
        try {
            SourceTree.Lookup lookup = sourceTree.find(workspaceGuard.root(), className, packageName);
            if (lookup.matches().isEmpty()) {
                return error("not_found", "no class named \"" + className + "\" found in the target project",
                        "call list_existing_classes to see what exists, or scaffold_class to create it");
            }
            if (lookup.matches().size() > 1) {
                return error("ambiguous_class_name",
                        "multiple classes named \"" + className + "\" exist: "
                                + lookup.matches().stream().map(SourceTree.ExistingClass::packageName).toList(),
                        "pass packageName to disambiguate which one you mean");
            }

            SourceTree.ExistingClass found = lookup.matches().get(0);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("className", found.className());
            result.put("packageName", found.packageName());
            result.put("path", found.relativePath());
            result.put("source", sourceTree.readSource(workspaceGuard.root(), found));
            return result;
        } catch (IOException e) {
            return error("io_error", "failed reading source: " + e.getMessage(),
                    "confirm the target project's src/main/java tree is readable");
        }
    }

    @McpTool(
            name = "scaffold_class",
            description = "Creates a new class matching this project's conventions. Re-validates the spec "
                    + "internally before writing anything (does not trust a prior validate_class_spec call) - "
                    + "if validation fails, returns the same violations validate_class_spec would and writes "
                    + "nothing. Refuses to overwrite an existing file. On success returns the exact file path "
                    + "created.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = false, destructiveHint = false, idempotentHint = false, openWorldHint = false)
    )
    public Map<String, Object> scaffoldClass(
            @McpToolParam(required = true, description = "Class name in PascalCase, e.g. \"InvoiceService\"")
            String className,
            @McpToolParam(required = true, description = "Fully-qualified package, e.g. \"com.acme.billing.service\"")
            String packageName,
            @McpToolParam(required = true, description = "One of the layer names from get_conventions, e.g. \"service\"")
            String layer,
            @McpToolParam(required = false, description = "Fields as {name, type} pairs, e.g. "
                    + "[{\"name\": \"repo\", \"type\": \"InvoiceRepository\"}]. Omit for a class with no fields yet.")
            List<Map<String, String>> fields,
            @McpToolParam(required = false, description = "Fully-qualified annotations the class will carry, "
                    + "e.g. [\"org.springframework.stereotype.Service\"]. Omit if none yet.")
            List<String> annotations
    ) {
        try {
            RuleSet rules = ruleSetLoader.load(properties.getRulesFile());
            ClassSpec spec = toClassSpec(className, packageName, layer, fields, annotations);

            ClassSpecValidator.ValidationResult validation = validator.validate(spec, rules);
            if (!validation.valid()) {
                Map<String, Object> failed = new LinkedHashMap<>();
                failed.put("valid", false);
                failed.put("violations", validation.violations().stream()
                        .map(v -> Map.of("code", v.code(), "message", v.message(), "hint", v.hint()))
                        .toList());
                failed.put("error", new GuardianError(
                        "spec_invalid",
                        "scaffold_class refused: the spec does not pass validate_class_spec",
                        "fix the violations listed, or call validate_class_spec directly for the same detail"
                ).toEnvelope().get("error"));
                return failed;
            }

            String relativePath = "src/main/java/" + scaffolder.relativePath(spec);
            Path target = workspaceGuard.resolve(relativePath);

            if (Files.exists(target)) {
                return error("already_exists",
                        "a file already exists at " + relativePath,
                        "use get_class to see its current content, or choose a different className/packageName");
            }

            Files.createDirectories(target.getParent());
            Files.writeString(target, scaffolder.render(spec));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("created", relativePath);
            result.put("className", spec.className());
            result.put("packageName", spec.packageName());
            return result;
        } catch (IOException e) {
            return error("io_error", "failed writing the new class: " + e.getMessage(),
                    "confirm the target project's src/main/java tree is writable");
        } catch (IllegalArgumentException e) {
            return error("path_escapes_workspace", e.getMessage(),
                    "packageName must resolve to a path inside the target project's workspace root");
        }
    }

    @McpTool(
            name = "run_architecture_check",
            description = "Runs the ArchUnit rules derived from guardian-rules.yaml's layers: section against "
                    + "the target project's COMPILED classes (target/classes must already exist - this reads "
                    + "bytecode, not source). Returns violations in the same {code, message, hint} shape as "
                    + "other tools.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true, destructiveHint = false, idempotentHint = true, openWorldHint = false)
    )
    public Map<String, Object> runArchitectureCheck() {
        try {
            RuleSet rules = ruleSetLoader.load(properties.getRulesFile());
            Path compiledClasses = workspaceGuard.resolve("target/classes");
            if (!Files.isDirectory(compiledClasses)) {
                return error("compiled_classes_missing",
                        "no compiled classes found at target/classes",
                        "run `mvn compile` on the target project first, then retry run_architecture_check");
            }

            ArchRules.CheckResult result = archRules.check(compiledClasses, rules);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("passed", result.passed());
            response.put("violations", result.violations().stream()
                    .map(v -> Map.of(
                            "code", "layer_violation",
                            "message", v.description(),
                            "hint", "this dependency breaks the layer's mayCallLayers rule in guardian-rules.yaml - "
                                    + "remove the dependency or update the ruleset if the rule itself is wrong"))
                    .toList());
            return response;
        } catch (IOException e) {
            return error("rules_unreadable", "could not read guardian-rules.yaml: " + e.getMessage(),
                    "check guardian.rules-file points at a readable YAML file");
        } catch (IllegalArgumentException e) {
            return error("path_escapes_workspace", e.getMessage(), "this should not happen for a fixed relative path");
        }
    }

    private ClassSpec toClassSpec(String className, String packageName, String layer,
                                   List<Map<String, String>> fields, List<String> annotations) {
        List<ClassSpec.Field> fieldList = fields == null ? List.of() : fields.stream()
                .map(f -> new ClassSpec.Field(f.get("name"), f.get("type")))
                .toList();
        return new ClassSpec(className, packageName, layer, fieldList, annotations == null ? List.of() : annotations);
    }

    private Map<String, Object> toJson(RuleSet rules) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("layers", rules.layers().stream()
                .map(l -> Map.of("name", l.name(), "package", l.pkg(), "mayCallLayers", l.mayCallLayers()))
                .toList());
        json.put("naming", rules.naming());
        json.put("requiredAnnotations", rules.requiredAnnotations());
        json.put("testGate", Map.of(
                "requireTestForPackages", rules.testGate().requireTestForPackages(),
                "skipPackages", rules.testGate().skipPackages(),
                "minCoveragePercent", rules.testGate().minCoveragePercent(),
                "minAssertionsPerTestClass", rules.testGate().minAssertionsPerTestClass(),
                "forbidDisabledWithoutReason", rules.testGate().forbidDisabledWithoutReason()));
        return json;
    }

    private Map<String, Object> error(String code, String message, String hint) {
        return new com.aira.guardian.errors.GuardianError(code, message, hint).toEnvelope();
    }
}
