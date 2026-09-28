package part3;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Embeds an incoming query and returns the top-k matching chunks from the
 * VectorStore, logging every candidate's id and score (not just the winner)
 * so retrieval confidence is visible (part3-rag skill, section 5).
 */
public class Retriever {

    private static final int DEFAULT_TOP_K = 3;

    private final EmbeddingClient embeddingClient;
    private final VectorStore vectorStore;

    public Retriever(EmbeddingClient embeddingClient, VectorStore vectorStore) {
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    public List<VectorStore.ScoredChunk> retrieve(String query) {
        return retrieve(query, DEFAULT_TOP_K);
    }

    public List<VectorStore.ScoredChunk> retrieve(String query, int k) {
        double[] queryVector = embeddingClient.embedQuery(query);
        List<VectorStore.ScoredChunk> results = vectorStore.topK(queryVector, k);

        String scoreSummary = results.stream()
                .map(r -> r.getChunk().getId() + "=" + String.format("%.4f", r.getScore()))
                .collect(Collectors.joining(", "));
        RagLogger.info("Retrieval for query \"" + query + "\" -> [" + scoreSummary + "]");

        return results;
    }
}
