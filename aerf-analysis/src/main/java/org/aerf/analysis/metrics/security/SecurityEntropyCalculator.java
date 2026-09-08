package org.aerf.analysis.metrics.security;

import org.aerf.model.Graph;
import org.aerf.model.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
