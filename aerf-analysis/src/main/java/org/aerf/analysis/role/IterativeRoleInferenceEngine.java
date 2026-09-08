package org.aerf.analysis.role;

import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.Role;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implements AERF v0.4 section 3.2 in full: seeds every node with
 * {@link SeedRoleInferenceEngine} ({@code R^(0)}), then repeatedly
 * applies a set of {@link GraphRoleRefinementRule}s to still-{@code
 * UNKNOWN} nodes until a pass makes no further change (a fixed point).
 *
 * <p>Termination is bounded structurally rather than assumed: this
 * engine only ever presents a rule with a node whose role is currently
 * {@link Role#UNKNOWN}, and only accepts a concrete role back — never a
 * downgrade back to {@code UNKNOWN} and never a change to an
 * already-resolved node. Since the graph has finitely many nodes and
 * each pass that changes anything resolves at least one previously
 * unknown node, the loop is guaranteed to reach a fixed point within at
 * most {@code |V| + 1} passes. If it somehow does not, that indicates a
 * refinement rule violated this contract (impossible through the typed
 * interface as written, but guarded defensively below), not a
 * legitimate non-convergent case.
 */
public final class IterativeRoleInferenceEngine {

    private final SeedRoleInferenceEngine seedEngine;
    private final List<GraphRoleRefinementRule> refinementRules;

    public IterativeRoleInferenceEngine(SeedRoleInferenceEngine seedEngine, List<GraphRoleRefinementRule> refinementRules) {
        this.seedEngine = Objects.requireNonNull(seedEngine, "seedEngine");
        this.refinementRules = List.copyOf(Objects.requireNonNull(refinementRules, "refinementRules"));
    }

    public IterativeRoleInferenceResult infer(Graph graph) {
        Objects.requireNonNull(graph, "graph");

        Map<NodeId, RoleInferenceResult> seedResults = seedEngine.inferAll(graph);
        Map<NodeId, Role> roles = new LinkedHashMap<>();
        Map<NodeId, List<RoleSignal>> signals = new LinkedHashMap<>();
        for (Map.Entry<NodeId, RoleInferenceResult> entry : seedResults.entrySet()) {
            roles.put(entry.getKey(), entry.getValue().role());
            signals.put(entry.getKey(), new ArrayList<>(entry.getValue().signals()));
        }

        int maxIterations = graph.nodes().size();
        int totalIterations = 0;
        int effectivePasses = 0;
        boolean changed = true;

        while (changed) {
            if (totalIterations > maxIterations) {
                throw new IllegalStateException(
                        "role refinement did not reach a fixed point within " + maxIterations
                                + " passes; a GraphRoleRefinementRule violated the UNKNOWN-only, no-downgrade contract");
            }
            changed = false;
            Map<NodeId, Role> snapshot = Map.copyOf(roles);

            for (Node node : graph.nodes()) {
                NodeId id = node.id();
                if (snapshot.get(id) != Role.UNKNOWN) {
                    continue;
                }

                List<RoleSignal> firing = new ArrayList<>();
                for (GraphRoleRefinementRule rule : refinementRules) {
                    for (RoleInferenceRule.Candidate candidate : rule.refine(id, graph, snapshot)) {
                        firing.add(new RoleSignal(candidate.role(), rule.name(), candidate.rationale()));
                    }
                }
                if (firing.isEmpty()) {
                    continue;
                }
                firing.sort(Comparator.comparing(RoleSignal::ruleName));

                Role resolved = RolePrecedence.winner(firing);
                if (resolved != Role.UNKNOWN) {
                    roles.put(id, resolved);
                    signals.get(id).addAll(firing);
                    changed = true;
                }
            }
            if (changed) {
                effectivePasses++;
            }
            totalIterations++;
        }

        Map<NodeId, RoleInferenceResult> finalResults = new LinkedHashMap<>();
        for (Node node : graph.nodes()) {
            NodeId id = node.id();
            finalResults.put(id, new RoleInferenceResult(id, roles.get(id), List.copyOf(signals.get(id))));
        }
        return new IterativeRoleInferenceResult(finalResults, effectivePasses);
    }
}
