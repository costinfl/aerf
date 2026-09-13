package org.aerf.analysis.metrics.security;

import org.aerf.model.Graph;
import org.aerf.model.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Computes basic security entropy (AERF v0.4 section 4.4) by running a
 * set of {@link SecurityOpportunityRule}s against every node in a graph.
 *
 * <p>This increment scopes rules to a single node in isolation, the same
 * starting point role inference's seed rules used (section 3.2's
 * {@code R^(0)}) — no edge-level or cross-node opportunity detection
 * (which CSRF and mass-assignment concerns would plausibly need, since
 * they are naturally about a form/binding relationship rather than a
 * single artifact) is attempted here. See the increment notes for why.
 */
public final class SecurityEntropyCalculator {

    private final List<SecurityOpportunityRule> rules;

    public SecurityEntropyCalculator(List<SecurityOpportunityRule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    /**
     * Always {@link OptionalDouble#empty()} — security confidence is
     * undefined by construction, not merely unmeasured (OQ-13).
     *
     * <p>The other three dimensions measure over edges, and an edge can
     * genuinely fail to resolve: the extractor saw a reference but could
     * not identify what it points to. That is exactly what confidence
     * (§5.4) reports. This dimension measures over {@code graph.nodes()},
     * and a node present in the graph is present by construction — there
     * is no unresolved-node population to be uncertain about. A
     * resolved/total reading here would be a fixed 1.0, which would
     * assert certainty that was never measured; §5.4 forbids converting
     * incomplete evidence into certainty, and the same rule applies to
     * evidence that was never incomplete in the first place because the
     * question does not arise.
     *
     * <p>Reported as an explicit undefined rather than omitted, so a
     * consumer sees that this dimension has no confidence reading rather
     * than silently finding one key missing.
     */
    public OptionalDouble confidence(Graph graph) {
        Objects.requireNonNull(graph, "graph");
        return OptionalDouble.empty();
    }

    public SecurityEntropyResult compute(Graph graph) {
        Objects.requireNonNull(graph, "graph");

        List<SecurityFinding> opportunities = new ArrayList<>();
        List<SecurityFinding> flagged = new ArrayList<>();

        for (Node node : graph.nodes()) {
            for (SecurityOpportunityRule rule : rules) {
                rule.evaluate(node).ifPresent(finding -> {
                    SecurityFinding resolved = new SecurityFinding(
                            node.id(), rule.name(), finding.concern(), finding.weaknessDetected(), finding.rationale());
                    opportunities.add(resolved);
                    if (finding.weaknessDetected()) {
                        flagged.add(resolved);
                    }
                });
            }
        }

        return new SecurityEntropyResult(opportunities, flagged);
    }
}
