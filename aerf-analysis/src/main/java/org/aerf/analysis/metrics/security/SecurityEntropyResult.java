package org.aerf.analysis.metrics.security;

import java.util.List;
import java.util.OptionalDouble;

/**
 * The result of computing basic security entropy (AERF v0.4 section 4.4)
 * over one graph. {@code opportunities} is every applicable control
 * opportunity found (Appendix B's denominator); {@code flagged} is the
 * subset where a weakness was actually detected (the numerator,
 * "Detected security deviations").
 */
public record SecurityEntropyResult(List<SecurityFinding> opportunities, List<SecurityFinding> flagged) {

    public SecurityEntropyResult {
        opportunities = List.copyOf(opportunities);
        flagged = List.copyOf(flagged);
    }

    /**
     * {@code detected security deviations / applicable control
     * opportunities}, per section 4.4 / Appendix B. Empty when there are
     * no applicable control opportunities at all: undefined, not zero —
     * same reasoning as every other entropy dimension implemented so
     * far. A graph with no evidence about any rendering, binding, or
     * form-handling behavior says nothing about its security posture;
     * it does not mean that posture is good.
     */
    public OptionalDouble value() {
        if (opportunities.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((double) flagged.size() / opportunities.size());
    }
}
