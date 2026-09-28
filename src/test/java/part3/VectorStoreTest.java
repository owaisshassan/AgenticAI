package part3;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorStoreTest {

    @Test
    void cosineSimilarity_identicalVectors_returnsOne() {
        double[] a = {1, 2, 3};
        double[] b = {1, 2, 3};
        assertEquals(1.0, VectorStore.cosineSimilarity(a, b), 1e-9);
    }

    @Test
    void cosineSimilarity_orthogonalVectors_returnsZero() {
        double[] a = {1, 0};
        double[] b = {0, 1};
        assertEquals(0.0, VectorStore.cosineSimilarity(a, b), 1e-9);
    }

    @Test
    void cosineSimilarity_oppositeVectors_returnsNegativeOne() {
        double[] a = {1, 0};
        double[] b = {-1, 0};
        assertEquals(-1.0, VectorStore.cosineSimilarity(a, b), 1e-9);
    }

    @Test
    void cosineSimilarity_mismatchedLengths_padsWithZeros() {
        double[] a = {1, 1};
        double[] b = {1, 1, 0, 0};
        assertEquals(1.0, VectorStore.cosineSimilarity(a, b), 1e-9);
    }

    @Test
    void cosineSimilarity_zeroVector_returnsZero() {
        double[] a = {0, 0, 0};
        double[] b = {1, 2, 3};
        assertEquals(0.0, VectorStore.cosineSimilarity(a, b), 1e-9);
    }

    @Test
    void topK_ordersByDescendingScore() {
        VectorStore store = new VectorStore();

        Chunk close = new Chunk("close", "A.java", "close");
        close.setEmbedding(new double[]{1, 0});

        Chunk far = new Chunk("far", "B.java", "far");
        far.setEmbedding(new double[]{0, 1});

        Chunk exact = new Chunk("exact", "C.java", "exact");
        exact.setEmbedding(new double[]{2, 0});

        store.add(close);
        store.add(far);
        store.add(exact);

        double[] query = {1, 0};
        List<VectorStore.ScoredChunk> results = store.topK(query, 3);

        assertEquals(3, results.size());
        assertTrue(results.get(0).getScore() >= results.get(1).getScore());
        assertTrue(results.get(1).getScore() >= results.get(2).getScore());
        assertEquals(0.0, results.get(2).getScore(), 1e-9);
        assertEquals("far", results.get(2).getChunk().getId());
    }

    @Test
    void topK_limitsToRequestedSize() {
        VectorStore store = new VectorStore();
        for (int i = 0; i < 5; i++) {
            Chunk chunk = new Chunk("c" + i, "F.java", "text " + i);
            chunk.setEmbedding(new double[]{i + 1});
            store.add(chunk);
        }

        List<VectorStore.ScoredChunk> results = store.topK(new double[]{1}, 2);
        assertEquals(2, results.size());
    }

    @Test
    void topK_kLargerThanStoreSize_returnsAllChunks() {
        VectorStore store = new VectorStore();
        store.add(withEmbedding("only", new double[]{1}));

        List<VectorStore.ScoredChunk> results = store.topK(new double[]{1}, 5);
        assertEquals(1, results.size());
    }

    private Chunk withEmbedding(String id, double[] embedding) {
        Chunk chunk = new Chunk(id, "F.java", id);
        chunk.setEmbedding(embedding);
        return chunk;
    }
}
