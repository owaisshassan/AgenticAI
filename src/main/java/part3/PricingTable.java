package part3;

import java.util.Map;

/**
 * USD-per-million-token pricing, keyed by the model alias the gateway
 * accepts (part1.RunnerUtil.API_KEY is restricted to the tier aliases
 * "claude-sonnet", "claude-opus", "claude-haiku" - probed 2026-09-19, see
 * src/main/resources/part3-plan.md Changelog). The gateway abstracts away
 * the exact underlying model version behind each alias, so these rates are
 * the best available mapping (current-generation pricing per tier: Sonnet 5
 * $2/$10, Opus 5 $5/$25, Haiku 4.5 $1/$5 per 1M tokens) rather than a price
 * confirmed against the gateway's actual backing model.
 */
public class PricingTable {

    public record Pricing(double inputPerMillionUsd, double outputPerMillionUsd) {
    }

    private static final Map<String, Pricing> PRICING = Map.of(
            "claude-sonnet", new Pricing(2.00, 10.00),
            "claude-opus", new Pricing(5.00, 25.00),
            "claude-haiku", new Pricing(1.00, 5.00)
    );

    public static Pricing lookup(String model) {
        Pricing pricing = PRICING.get(model);
        if (pricing == null) {
            RagLogger.warn("No pricing entry for model alias \"" + model + "\" - cost will be reported as $0. "
                    + "Add an entry to PricingTable if this gateway alias is expected to be billed.");
            return new Pricing(0.0, 0.0);
        }
        return pricing;
    }
}
