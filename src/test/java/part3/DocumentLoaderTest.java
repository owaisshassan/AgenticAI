package part3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentLoaderTest {

    private final DocumentLoader loader = new DocumentLoader();

    @Test
    void chunkFile_smallClassWithoutPublicMethods_producesOnlyClassChunk(@TempDir Path tempDir) throws IOException {
        String source = "package demo;\n\npublic class Small {\n    private int x;\n}\n";
        Path file = tempDir.resolve("Small.java");
        Files.writeString(file, source);

        List<Chunk> chunks = loader.chunkFile(file);

        assertEquals(1, chunks.size());
        assertEquals("demo.Small", chunks.get(0).getId());
        assertTrue(chunks.get(0).getText().contains("private int x"));
    }

    @Test
    void chunkFile_largeClassWithPublicMethods_producesClassChunkPlusMethodChunks(@TempDir Path tempDir) throws IOException {
        StringBuilder source = new StringBuilder();
        source.append("package demo;\n\n");
        source.append("public class Big {\n");
        for (int i = 0; i < 50; i++) {
            source.append("    // padding line ").append(i).append("\n");
        }
        source.append("    /**\n     * Does something useful.\n     */\n");
        source.append("    public void doSomething() {\n");
        source.append("        System.out.println(\"hi\");\n");
        source.append("    }\n");
        source.append("}\n");

        Path file = tempDir.resolve("Big.java");
        Files.writeString(file, source.toString());

        List<Chunk> chunks = loader.chunkFile(file);

        assertEquals(2, chunks.size());
        assertEquals("demo.Big", chunks.get(0).getId());
        assertEquals("demo.Big#doSomething", chunks.get(1).getId());
        assertTrue(chunks.get(1).getText().contains("Does something useful"));
        assertTrue(chunks.get(1).getText().contains("System.out.println"));
    }

    @Test
    void chunkFile_producesStableAndUniqueIds(@TempDir Path tempDir) throws IOException {
        StringBuilder source = new StringBuilder();
        source.append("package demo;\n\npublic class Repeated {\n");
        for (int i = 0; i < 45; i++) {
            source.append("    // padding line ").append(i).append("\n");
        }
        source.append("    public void run() {\n        int a = 1;\n    }\n");
        source.append("}\n");

        Path file = tempDir.resolve("Repeated.java");
        Files.writeString(file, source.toString());

        List<Chunk> firstRun = loader.chunkFile(file);
        List<Chunk> secondRun = loader.chunkFile(file);

        assertEquals(
                firstRun.stream().map(Chunk::getId).toList(),
                secondRun.stream().map(Chunk::getId).toList()
        );

        Set<String> ids = new HashSet<>();
        for (Chunk chunk : firstRun) {
            assertTrue(ids.add(chunk.getId()), "duplicate chunk id: " + chunk.getId());
        }
    }

    @Test
    void loadDefaultKnowledgeBase_indexesPart1AndPart2Sources() throws IOException {
        List<Chunk> chunks = loader.loadDefaultKnowledgeBase();

        assertTrue(chunks.size() >= 13, "expected at least one chunk per part1/part2 file, got " + chunks.size());

        boolean hasPart1 = chunks.stream().anyMatch(c -> c.getSourceFile().contains("part1"));
        boolean hasPart2 = chunks.stream().anyMatch(c -> c.getSourceFile().contains("part2"));
        assertTrue(hasPart1, "expected at least one chunk from part1");
        assertTrue(hasPart2, "expected at least one chunk from part2");

        boolean hasWeatherConnectorMethodChunk = chunks.stream()
                .anyMatch(c -> c.getId().equals("part2.WeatherConnector#getWeather"));
        assertTrue(hasWeatherConnectorMethodChunk,
                "expected a method-level chunk for WeatherConnector#getWeather since the file exceeds the line threshold");
    }
}
