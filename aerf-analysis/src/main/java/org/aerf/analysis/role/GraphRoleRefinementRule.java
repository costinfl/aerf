package org.aerf.analysis.role;

import org.aerf.model.Graph;
import org.aerf.model.NodeId;
import org.aerf.model.Role;

import java.util.List;
import java.util.Map;

/**
 * One rule contributing to the graph-relationship half of AERF v0.4
 * section 3.2's iterative refinement, {@code R^(n+1) = F(R^(n), G)}. This
 * is the "Graph" evidence class from section 3.3 (inbound/outbound
 * relationships, dependency direction, centrality, neighboring roles),
 * as opposed to {@link RoleInferenceRule}, which only ever sees one node
 * in isolation.
 *
 * <p><b>Contract enforced by {@link IterativeRoleInferenceEngine}, not by
 * this interface:</b> a rule is only ever asked to refine a node whose
 * current role is {@link Role#UNKNOWN}, and may only propose a concrete
 * (non-{@code UNKNOWN}) role for it. The engine never asks a rule about
 * an already-resolved node and never lets a rule take a role away. This
 * is what makes the iteration provably terminate in a bounded number of
 * passes without needing a general fixed-point argument: v0.4 states
 * that "inference terminates when a fixed point is reached" but does not
 * establish that one always exists for an arbitrary {@code F}, so this
 * implementation restricts {@code F} to a shape where termination is
 * structurally guaranteed instead of assumed.
 */
public interface GraphRoleRefinementRule {

    String name();

    /**
     * @param nodeId       the node being considered; guaranteed by the
     *                     engine to currently have role {@link Role#UNKNOWN}
     * @param graph        the full graph, for inspecting relationships
     * @param currentRoles a read-only snapshot of {@code R^(n)} — every
     *                     node's role as of the start of the current
     *                     iteration. Must be treated as fixed for the
     *                     duration of this call.
     */
    List<RoleInferenceRule.Candidate> refine(NodeId nodeId, Graph graph, Map<NodeId, Role> currentRoles);
}
