package com.aira.guardian.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end tests over the REAL MCP protocol, same discipline as the
 * aira-ops reference server's test_mcp_server.py: no mocking of the tool
 * layer, no in-process shortcut - a real subprocess speaking real
 * stdio JSON-RPC, driven by a real client (io.modelcontextprotocol's own
 * McpSyncClient, the Java-side equivalent of aira-ops's hand-rolled
 * client.py). If the wiring between @McpTool methods and the MCP server
 * is ever wrong, this is what notices - unit tests on ConventionTools'
 * collaborators alone would not.
 */
class ProjectGuardianE2ETest {

    private static McpSyncClient client;
    private static Path fixtureWorkspace;

    @BeforeAll
    static void startServer() throws IOException {
        fixtureWorkspace = Files.createTempDirectory("guardian-e2e-");

        Path jar = Path.of("target").toAbsolutePath()
                .resolve("project-guardian-0.1.0-SNAPSHOT.jar");
        if (!Files.exists(jar)) {
            throw new IllegalStateException(
                    "packaged jar not found at " + jar + " - run `mvn package` before this test");
        }

        ServerParameters params = ServerParameters.builder("java")
                .args("-jar", jar.toString())
                .addEnvVar("GUARDIAN_WORKSPACE_ROOT", fixtureWorkspace.toString())
                .addEnvVar("GUARDIAN_LOG_FILE", fixtureWorkspace.resolve("server.log").toString())
                .build();

        StdioClientTransport transport = new StdioClientTransport(params, new JacksonMcpJsonMapper(new ObjectMapper()));
        client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(30))
                .build();
        client.initialize();
    }

    @AfterAll
    static void stopServer() {
        if (client != null) {
            client.closeGracefully();
        }
    }

    @Test
    void handshakeReportsServerName() {
        assertThat(client.getServerInfo().name()).isEqualTo("project-guardian");
    }

    @Test
    void toolsListReturnsEveryToolWithHonestAnnotations() {
        McpSchema.ListToolsResult result = client.listTools();
        List<String> names = result.tools().stream().map(McpSchema.Tool::name).toList();

        assertThat(names).contains(
                "get_conventions", "validate_class_spec", "list_existing_classes",
                "get_class", "scaffold_class", "run_architecture_check");

        for (McpSchema.Tool tool : result.tools()) {
            boolean isWriteTool = tool.name().equals("scaffold_class");
            assertThat(tool.annotations().readOnlyHint())
                    .as(tool.name() + " readOnlyHint must reflect whether it writes")
                    .isEqualTo(!isWriteTool);
        }
    }

    @Test
    void getConventionsReturnsStructuredRuleset() {
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("get_conventions", Map.of()));

        String text = text(result);
        assertThat(text).contains("\"layers\"");
        assertThat(text).contains("\"service\"");
        assertThat(text).contains("minCoveragePercent");
    }

    @Test
    void validateClassSpec_validSpecPasses() {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "validate_class_spec",
                Map.of(
                        "className", "InvoiceService",
                        "packageName", "com.acme.service",
                        "layer", "service",
                        "annotations", List.of("org.springframework.stereotype.Service"))));

        assertThat(text(result)).contains("\"valid\":true");
    }

    @Test
    void validateClassSpec_badNamingCaughtWithNamingViolationCode() {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "validate_class_spec",
                Map.of("className", "invoice_handler", "packageName", "com.acme.service", "layer", "service")));

        assertThat(text(result)).contains("\"naming_violation\"");
    }

    @Test
    void validateClassSpec_wrongLayerCallDirectionCaughtWithIllegalLayerCallCode() {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "validate_class_spec",
                Map.of(
                        "className", "InvoiceRepository",
                        "packageName", "com.acme.repository",
                        "layer", "repository",
                        "fields", List.of(Map.of("name", "svc", "type", "InvoiceService")),
                        "annotations", List.of("org.springframework.data.jpa.repository.JpaRepository"))));

        assertThat(text(result)).contains("\"illegal_layer_call\"");
    }

    @Test
    void validateClassSpec_missingRequiredAnnotationCaughtWithDistinctCode() {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "validate_class_spec",
                Map.of("className", "InvoiceService", "packageName", "com.acme.service", "layer", "service")));

        assertThat(text(result)).contains("\"missing_required_annotation\"");
    }

    @Test
    void validateClassSpec_malformedInputRejectedBeforeAnyFilesystemWork() {
        // No layer at all - schema-shaped rejection, but must still surface as a
        // structured error rather than an unhandled exception reaching the model.
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "validate_class_spec",
                Map.of("className", "Whatever", "packageName", "com.acme.x", "layer", "not_a_real_layer")));

        assertThat(text(result)).contains("\"unknown_layer\"");
    }

    @Test
    void listExistingClassesReturnsEmptyForALayerNothingHasScaffoldedInto() {
        // Uses "repository", not "service" - other tests in this class scaffold into
        // "service" and this class shares one server/workspace for its whole lifecycle
        // (like aira-ops's single subprocess per test class), so method order is not
        // guaranteed. Asserting on an untouched layer keeps this test order-independent.
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "list_existing_classes", Map.of("layer", "repository")));

        assertThat(text(result)).contains("\"classes\":[]");
    }

    @Test
    void getClassReturnsNotFoundForAClassThatDoesNotExist() {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "get_class", Map.of("className", "NoSuchClass")));

        assertThat(text(result)).contains("\"not_found\"");
    }

    @Test
    void scaffoldClass_thenValidateClassSpec_thenGetClass_thenListExistingClasses_reflectTheNewFile() {
        McpSchema.CallToolResult scaffolded = client.callTool(new McpSchema.CallToolRequest(
                "scaffold_class",
                Map.of(
                        "className", "OrderService",
                        "packageName", "com.acme.order.service",
                        "layer", "service",
                        "annotations", List.of("org.springframework.stereotype.Service"))));
        assertThat(text(scaffolded)).contains("\"created\"");
        assertThat(text(scaffolded)).contains("OrderService.java");

        // Re-scaffolding the same spec must refuse rather than overwrite.
        McpSchema.CallToolResult repeated = client.callTool(new McpSchema.CallToolRequest(
                "scaffold_class",
                Map.of(
                        "className", "OrderService",
                        "packageName", "com.acme.order.service",
                        "layer", "service",
                        "annotations", List.of("org.springframework.stereotype.Service"))));
        assertThat(text(repeated)).contains("\"already_exists\"");

        McpSchema.CallToolResult fetched = client.callTool(new McpSchema.CallToolRequest(
                "get_class", Map.of("className", "OrderService", "packageName", "com.acme.order.service")));
        assertThat(text(fetched)).contains("public class OrderService");
        assertThat(text(fetched)).contains("@Service");

        McpSchema.CallToolResult listed = client.callTool(new McpSchema.CallToolRequest(
                "list_existing_classes", Map.of("layer", "service")));
        assertThat(text(listed)).contains("OrderService");
    }

    @Test
    void scaffoldClass_refusesAnInvalidSpecWithoutWritingAnything() {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "scaffold_class",
                Map.of("className", "bad_name", "packageName", "com.acme.service", "layer", "service")));

        assertThat(text(result)).contains("\"valid\":false");
        assertThat(text(result)).contains("\"naming_violation\"");

        McpSchema.CallToolResult lookup = client.callTool(new McpSchema.CallToolRequest(
                "get_class", Map.of("className", "bad_name")));
        assertThat(text(lookup)).contains("\"not_found\"");
    }

    @Test
    void runArchitectureCheck_reportsCompiledClassesMissingWhenTargetHasNotBeenCompiled() {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                "run_architecture_check", Map.of()));

        assertThat(text(result)).contains("\"compiled_classes_missing\"");
    }

    private static String text(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(c -> c instanceof McpSchema.TextContent)
                .map(c -> ((McpSchema.TextContent) c).text())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no text content in tool result: " + result));
    }
}
