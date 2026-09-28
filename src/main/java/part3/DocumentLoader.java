package part3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads the part1/part2 .java source files and splits each into chunks: one
 * chunk for the whole class, plus one chunk per public method for any file
 * over ~40 lines (per src/main/resources/part3-plan.md, Slice 1).
 */
public class DocumentLoader {

    private static final int METHOD_CHUNK_LINE_THRESHOLD = 40;

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\bclass\\s+(\\w+)");
    private static final Pattern PUBLIC_METHOD_PATTERN = Pattern.compile(
            "^\\s*public\\s+(?:static\\s+)?(?:final\\s+)?[\\w<>\\[\\],.\\s]+?\\s+(\\w+)\\s*\\([^;{]*\\)\\s*(?:throws\\s+[\\w,\\s.]+)?\\{\\s*$"
    );

    public List<Chunk> loadDefaultKnowledgeBase() throws IOException {
        Path projectRoot = Path.of(System.getProperty("user.dir"));
        List<Path> sourceDirs = List.of(
                projectRoot.resolve("src").resolve("main").resolve("java").resolve("part1"),
                projectRoot.resolve("src").resolve("main").resolve("java").resolve("part2")
        );
        return loadKnowledgeBase(sourceDirs);
    }

    public List<Chunk> loadKnowledgeBase(List<Path> sourceDirs) throws IOException {
        List<Chunk> chunks = new ArrayList<>();
        for (Path dir : sourceDirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.list(dir)) {
                List<Path> javaFiles = files
                        .filter(p -> p.toString().endsWith(".java"))
                        .sorted()
                        .toList();
                for (Path file : javaFiles) {
                    chunks.addAll(chunkFile(file));
                }
            }
        }
        return chunks;
    }

    public List<Chunk> chunkFile(Path file) throws IOException {
        String content = Files.readString(file);
        String[] lines = content.split("\n", -1);

        String packageName = extractPackage(lines);
        String className = extractClassName(lines);
        String qualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

        List<Chunk> chunks = new ArrayList<>();
        chunks.add(new Chunk(qualifiedName, file.toString(), content));

        if (lines.length > METHOD_CHUNK_LINE_THRESHOLD) {
            chunks.addAll(extractPublicMethodChunks(lines, qualifiedName, file.toString()));
        }

        return chunks;
    }

    private String extractPackage(String[] lines) {
        for (String line : lines) {
            Matcher m = PACKAGE_PATTERN.matcher(line);
            if (m.find()) {
                return m.group(1);
            }
        }
        return "";
    }

    private String extractClassName(String[] lines) {
        for (String line : lines) {
            Matcher m = CLASS_PATTERN.matcher(line);
            if (m.find()) {
                return m.group(1);
            }
        }
        return "Unknown";
    }

    private List<Chunk> extractPublicMethodChunks(String[] lines, String qualifiedClassName, String sourceFile) {
        List<Chunk> methodChunks = new ArrayList<>();
        LinkedHashSet<String> usedIds = new LinkedHashSet<>();

        int i = 0;
        while (i < lines.length) {
            Matcher m = PUBLIC_METHOD_PATTERN.matcher(lines[i]);
            if (m.find()) {
                String methodName = m.group(1);

                int leadingCommentStart = findLeadingCommentStart(lines, i);
                int bodyEnd = findMatchingCloseBrace(lines, i);

                if (bodyEnd >= i) {
                    StringBuilder text = new StringBuilder();
                    for (int j = leadingCommentStart; j <= bodyEnd; j++) {
                        text.append(lines[j]).append('\n');
                    }

                    String id = qualifiedClassName + "#" + methodName;
                    while (usedIds.contains(id)) {
                        id = id + "*";
                    }
                    usedIds.add(id);

                    methodChunks.add(new Chunk(id, sourceFile, text.toString()));
                    i = bodyEnd + 1;
                    continue;
                }
            }
            i++;
        }

        return methodChunks;
    }

    private int findLeadingCommentStart(String[] lines, int methodLineIndex) {
        int start = methodLineIndex;
        int cursor = methodLineIndex - 1;
        while (cursor >= 0) {
            String trimmed = lines[cursor].trim();
            if (trimmed.isEmpty()) {
                break;
            }
            if (trimmed.startsWith("*") || trimmed.startsWith("/**") || trimmed.startsWith("/*")
                    || trimmed.startsWith("//") || trimmed.endsWith("*/")) {
                start = cursor;
                cursor--;
                continue;
            }
            break;
        }
        return start;
    }

    private int findMatchingCloseBrace(String[] lines, int methodLineIndex) {
        int depth = 0;
        boolean opened = false;
        for (int i = methodLineIndex; i < lines.length; i++) {
            for (char c : lines[i].toCharArray()) {
                if (c == '{') {
                    depth++;
                    opened = true;
                } else if (c == '}') {
                    depth--;
                }
            }
            if (opened && depth == 0) {
                return i;
            }
        }
        return -1;
    }
}
