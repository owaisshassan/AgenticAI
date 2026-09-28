package com.aira.guardian.rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClassScaffolderTest {

    private final ClassScaffolder scaffolder = new ClassScaffolder();

    @Test
    void rendersPackageDeclarationAndClassName() {
        ClassSpec spec = new ClassSpec("InvoiceService", "com.acme.service", "service", List.of(), List.of());

        String source = scaffolder.render(spec);

        assertThat(source).contains("package com.acme.service;");
        assertThat(source).contains("public class InvoiceService {");
    }

    @Test
    void rendersAnnotationsWithImports() {
        ClassSpec spec = new ClassSpec(
                "InvoiceService", "com.acme.service", "service", List.of(),
                List.of("org.springframework.stereotype.Service"));

        String source = scaffolder.render(spec);

        assertThat(source).contains("import org.springframework.stereotype.Service;");
        assertThat(source).contains("@Service");
    }

    @Test
    void rendersFieldsAndConstructor() {
        ClassSpec spec = new ClassSpec(
                "InvoiceService", "com.acme.service", "service",
                List.of(new ClassSpec.Field("repo", "InvoiceRepository")),
                List.of("org.springframework.stereotype.Service"));

        String source = scaffolder.render(spec);

        assertThat(source).contains("private final InvoiceRepository repo;");
        assertThat(source).contains("public InvoiceService(InvoiceRepository repo) {");
        assertThat(source).contains("this.repo = repo;");
    }

    @Test
    void relativePathMatchesPackageAndClassName() {
        ClassSpec spec = new ClassSpec("InvoiceService", "com.acme.service", "service", List.of(), List.of());

        assertThat(scaffolder.relativePath(spec)).isEqualTo("com/acme/service/InvoiceService.java");
    }

    @Test
    void classWithNoFieldsHasNoArgConstructor() {
        ClassSpec spec = new ClassSpec("Marker", "com.acme.service", "service", List.of(), List.of());

        String source = scaffolder.render(spec);

        assertThat(source).contains("public Marker() {");
    }
}
