package org.aerf.analysis.role.graph;

import org.aerf.analysis.role.GraphRoleRefinementRule;
import org.aerf.analysis.role.RoleInferenceRule;
import org.aerf.model.Edge;
import org.aerf.model.Graph;
import org.aerf.model.NodeId;
import org.aerf.model.NodeRef;
import org.aerf.model.RelationType;
import org.aerf.model.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A small, illustrative catalog of graph-relationship refinement rules.
 *
 * <p>Like {@code org.aerf.analysis.role.seed.DefaultSeedRules}, this is an
 * implementation decision, not part of AERF v0.4: the specification names
 * "Graph" as an evidence class (section 3.3: inbound/outbound
 * relationships, dependency direction, centrality, neighboring roles) but
 * does not define a concrete rule catalog.
 */
public final class DefaultGraphRefinementRules {

    private DefaultGraphRefinementRules() {
    }

    public static List<GraphRoleRefinementRule> illustrativeRules() {
        return List.of(new InheritRoleFromSupertype());
    }

    /**
     * If an unclassified node EXTENDS or IMPLEMENTS a node whose role is
     * already known, it inherits that role: a subclass of a classified
     * Presentation controller, or an implementation of a classified
     * Persistence interface, ordinarily shares its supertype's role.
     *
     * <p>If a node has multiple such supertypes with different known
     * roles, all are returned as candidates and left to
     * {@code RolePrecedence} to resolve, exactly like a seed rule
     * reporting multiple candidates.
     */
    static final class InheritRoleFromSupertype implements GraphRoleRefinementRule {

        @Override
        public String name() {
            return "graph-inherit-role-from-extends-or-implements-target";
        }

        @Override
        public List<RoleInferenceRule.Candidate> refine(NodeId nodeId, Graph graph, Map<NodeId, Role> currentRoles) {
            List<RoleInferenceRule.Candidate> candidates = new ArrayList<>();
            for (Edge edge : graph.edgesFrom(nodeId)) {
                if (edge.relation() != RelationType.EXTENDS && edge.relation() != RelationType.IMPLEMENTS) {
                    continue;
                }
                if (!(edge.target() instanceof NodeRef.Resolved targetRef)) {
                    continue;
                }
                Role targetRole = currentRoles.get(targetRef.id());
                if (targetRole != null && targetRole != Role.UNKNOWN) {
                    candidates.add(new RoleInferenceRule.Candidate(targetRole,
                            "inherits role from " + edge.relation() + " target " + targetRef.id()));
                }
            }
            return candidates;
        }
    }
}
