package com.aira.guardian.rules;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders a {@link ClassSpec} into Java source text. Pure string
 * generation - no filesystem access, so it's directly unit-testable
 * without a workspace fixture. {@code scaffold_class} (the MCP tool) owns
 * writing the result to disk.
 */
public class ClassScaffolder {

    public String render(ClassSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(spec.packageName()).append(";\n\n");

        List<String> imports = importsFor(spec);
        for (String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
        }
        if (!imports.isEmpty()) {
            sb.append('\n');
        }

        for (String annotation : spec.annotations() == null ? List.<String>of() : spec.annotations()) {
            sb.append('@').append(simpleName(annotation)).append('\n');
        }

        sb.append("public class ").append(spec.className()).append(" {\n");
        renderFields(sb, spec);
        renderConstructor(sb, spec);
        sb.append("}\n");
        return sb.toString();
    }

    private void renderFields(StringBuilder sb, ClassSpec spec) {
        for (ClassSpec.Field field : fieldsOf(spec)) {
            sb.append("\n    private final ").append(field.type()).append(' ').append(field.name()).append(";\n");
        }
    }

    private void renderConstructor(StringBuilder sb, ClassSpec spec) {
        List<ClassSpec.Field> fields = fieldsOf(spec);
        sb.append('\n');
        String params = fields.stream()
                .map(f -> f.type() + " " + f.name())
                .collect(Collectors.joining(", "));
        sb.append("    public ").append(spec.className()).append('(').append(params).append(") {\n");
        for (ClassSpec.Field field : fields) {
            sb.append("        this.").append(field.name()).append(" = ").append(field.name()).append(";\n");
        }
        sb.append("    }\n");
    }

    private List<ClassSpec.Field> fieldsOf(ClassSpec spec) {
        return spec.fields() == null ? List.of() : spec.fields();
    }

    private List<String> importsFor(ClassSpec spec) {
        return (spec.annotations() == null ? List.<String>of() : spec.annotations()).stream()
                .filter(a -> a.contains("."))
                .toList();
    }

    private String simpleName(String maybeQualified) {
        int lastDot = maybeQualified.lastIndexOf('.');
        return lastDot < 0 ? maybeQualified : maybeQualified.substring(lastDot + 1);
    }

    /** Relative path (from a src/main/java root) this class's file belongs at. */
    public String relativePath(ClassSpec spec) {
        return spec.packageName().replace('.', '/') + "/" + spec.className() + ".java";
    }
}
