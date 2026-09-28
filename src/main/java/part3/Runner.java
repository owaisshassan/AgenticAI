package part3;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Console RAG agent: retrieves context from the part1/part2 knowledge base
 * for each user question, injects it into the prompt, calls the gateway, and
 * prints the answer. Every turn logs a SUCCESS or FAILURE entry with status
 * and token usage to a dedicated log file under D:\AgenticAI_Git\logs\part3_RAG
 * (see RagLogger).
 */
public class Runner {

    private static final String SYSTEM_PROMPT =
            "You are a helpful assistant that answers questions about the part1 and part2 " +
                    "Java modules in this project, using only the retrieved context provided in each " +
                    "user message. If the context does not contain the answer, say so explicitly " +
                    "instead of guessing.";

    public static void main(String[] args) throws Exception {
        RagLogger.info("Session started.");

        EmbeddingClient embeddingClient = new EmbeddingClient();
        VectorStore vectorStore = new VectorStore();

        List<Chunk> chunks = new DocumentLoader().loadDefaultKnowledgeBase();
        for (Chunk chunk : chunks) {
            chunk.setEmbedding(embeddingClient.embed(chunk.getText()));
        }
        vectorStore.addAll(chunks);
        RagLogger.info("Index built: " + chunks.size() + " chunks indexed, vocabulary size "
                + embeddingClient.vocabularySize() + ".");

        Retriever retriever = new Retriever(embeddingClient, vectorStore);

        System.out.println("part3 RAG agent ready. Indexed " + chunks.size()
                + " chunks from part1/part2. Ask a question, or type 'exit' to quit.");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("You: ");
            String userInput = scanner.hasNextLine() ? scanner.nextLine() : null;

            if (userInput == null || userInput.isBlank()
                    || userInput.equalsIgnoreCase("exit") || userInput.equalsIgnoreCase("quit")) {
                RagLogger.info("Blank input received, ending conversation.");
                System.out.println("Ending conversation.");
                break;
            }

            handleTurn(userInput, retriever);
        }

        RagLogger.info("Session ended.");
        scanner.close();
    }

    private static final String MODEL = "claude-sonnet";

    private static void handleTurn(String userInput, Retriever retriever) {
        TokenUsage usage = new TokenUsage(0, 0, 0.0);
        try {
            List<VectorStore.ScoredChunk> retrieved = retriever.retrieve(userInput);
            String augmentedPrompt = buildAugmentedPrompt(userInput, retrieved);

            List<part1.Message> messages = new ArrayList<>();
            messages.add(new part1.Message("user", augmentedPrompt));

            Agent agent = new Agent(MODEL, 500, SYSTEM_PROMPT, messages);
            String response = RunnerUtil.callGateway(agent);
            usage = RunnerUtil.extractTokenUsage(response, MODEL);
            String answer = RunnerUtil.extractAssistantText(response);

            System.out.println("Agent: " + answer);
            System.out.println("[status=SUCCESS, tokens=" + usage.getTotalTokens()
                    + ", cost=$" + String.format("%.6f", usage.getCostUsd()) + "]");
            RagLogger.logSuccess(userInput, answer, usage);
        } catch (Exception e) {
            System.out.println("Agent: Sorry, something went wrong answering that question.");
            System.out.println("[status=FAILURE, tokens=" + usage.getTotalTokens()
                    + ", cost=$" + String.format("%.6f", usage.getCostUsd()) + "]");
            RagLogger.logFailure(userInput, e, usage);
        }
    }

    private static String buildAugmentedPrompt(String userQuery, List<VectorStore.ScoredChunk> retrieved) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Context (retrieved, most relevant first):\n");

        int rank = 1;
        for (VectorStore.ScoredChunk scored : retrieved) {
            prompt.append("[").append(rank++).append("] ").append(scored.getChunk().getId()).append("\n");
            prompt.append(scored.getChunk().getText()).append("\n\n");
        }

        prompt.append("Question: ").append(userQuery).append("\n\n");
        prompt.append("Answer using only the context above. If the context doesn't contain the answer, say so.");
        return prompt.toString();
    }
}
