package part3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * In-memory store of embedded chunks with cosine-similarity top-k search
 * (src/main/resources/part3-plan.md, Slice 2).
 */
public class VectorStore {

    public static class ScoredChunk {
        private final Chunk chunk;
        private final double score;

        public ScoredChunk(Chunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }

        public Chunk getChunk() {
            return chunk;
        }

        public double getScore() {
            return score;
        }
    }

    private final List<Chunk> chunks = new ArrayList<>();

    public void add(Chunk chunk) {
        chunks.add(chunk);
    }

    public void addAll(List<Chunk> newChunks) {
        chunks.addAll(newChunks);
    }

    public int size() {
        return chunks.size();
    }

    public List<ScoredChunk> topK(double[] queryVector, int k) {
        List<ScoredChunk> scored = new ArrayList<>();
        for (Chunk chunk : chunks) {
            double score = cosineSimilarity(queryVector, chunk.getEmbedding());
            scored.add(new ScoredChunk(chunk, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::getScore).reversed());
        return scored.subList(0, Math.min(k, scored.size()));
    }

    public static double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null) {
            return 0;
        }
        double dot = 0, normA = 0, normB = 0;
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            double x = i < a.length ? a[i] : 0;
            double y = i < b.length ? b[i] : 0;
            dot += x * y;
            normA += x * x;
            normB += y * y;
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
