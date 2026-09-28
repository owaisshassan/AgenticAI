package part3;

public class TokenUsage {
    private final int inputTokens;
    private final int outputTokens;
    private final double costUsd;

    public TokenUsage(int inputTokens, int outputTokens) {
        this(inputTokens, outputTokens, 0.0);
    }

    public TokenUsage(int inputTokens, int outputTokens, double costUsd) {
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.costUsd = costUsd;
    }

    /**
     * Computes token usage plus its USD cost from the given model's pricing
     * (part3-plan.md requires cost to be logged alongside token usage).
     */
    public static TokenUsage forModel(String model, int inputTokens, int outputTokens) {
        PricingTable.Pricing pricing = PricingTable.lookup(model);
        double cost = (inputTokens / 1_000_000.0) * pricing.inputPerMillionUsd()
                + (outputTokens / 1_000_000.0) * pricing.outputPerMillionUsd();
        return new TokenUsage(inputTokens, outputTokens, cost);
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public int getTotalTokens() {
        return inputTokens + outputTokens;
    }

    public double getCostUsd() {
        return costUsd;
    }

    @Override
    public String toString() {
        return "input=" + inputTokens + ", output=" + outputTokens + ", total=" + getTotalTokens()
                + ", cost=$" + String.format("%.6f", costUsd);
    }
}
