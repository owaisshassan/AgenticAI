package part3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Produces embedding vectors for chunks/queries.
 *
 * The gateway used by part1/part2 (RunnerUtil.API_URL) restricts its API key
 * to chat models only (probed 2026-09-19: POST /v1/embeddings -> HTTP 403,
 * "key not allowed to access model... can only access
 * models=['claude-sonnet','claude-opus','claude-haiku']"). No embeddings
 * endpoint is usable with the current key, so this is a local term-frequency
 * vectorizer (skill part3-rag section 2's fallback), not a gateway call.
 *
 * The vocabulary is built incrementally: every new term seen while embedding
 * a chunk grows the vocabulary and the dimension of all future vectors.
 * VectorStore.cosineSimilarity pads shorter/older vectors with zeros, so
 * vectors embedded before a vocabulary grew remain comparable.
 */
public class EmbeddingClient {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[^a-zA-Z0-9]+");

    private final Map<String, Integer> vocabulary = new LinkedHashMap<>();

    public synchronized double[] embed(String text) {
        String[] tokens = tokenize(text);
        for (String token : tokens) {
            vocabulary.computeIfAbsent(token, t -> vocabulary.size());
        }

        double[] vector = new double[vocabulary.size()];
        for (String token : tokens) {
            Integer index = vocabulary.get(token);
            if (index != null) {
                vector[index] += 1.0;
            }
        }
        return vector;
    }

    /**
     * Embeds a query without growing the vocabulary, so an unseen query term
     * simply contributes nothing to the vector instead of silently resizing
     * every previously-stored chunk vector's meaning out from under it.
     */
    public synchronized double[] embedQuery(String text) {
        String[] tokens = tokenize(text);
        double[] vector = new double[vocabulary.size()];
        for (String token : tokens) {
            Integer index = vocabulary.get(token);
            if (index != null) {
                vector[index] += 1.0;
            }
        }
        return vector;
    }

    public synchronized int vocabularySize() {
        return vocabulary.size();
    }

    private String[] tokenize(String text) {
        if (text == null || text.isBlank()) {
            return new String[0];
        }
        return TOKEN_PATTERN.splitAsStream(text.toLowerCase())
                .filter(t -> !t.isBlank())
                .toArray(String[]::new);
    }
}
