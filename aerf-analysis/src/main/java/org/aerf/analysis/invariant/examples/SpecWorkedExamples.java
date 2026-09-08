package org.aerf.analysis.invariant.examples;

import org.aerf.analysis.invariant.Invariant;
import org.aerf.analysis.invariant.Scope;
import org.aerf.model.RelationType;
import org.aerf.model.Role;

import static org.aerf.analysis.invariant.InvariantDsl.always;
import static org.aerf.analysis.invariant.InvariantDsl.and;
import static org.aerf.analysis.invariant.InvariantDsl.edgeRelation;
import static org.aerf.analysis.invariant.InvariantDsl.eq;
import static org.aerf.analysis.invariant.InvariantDsl.in;
import static org.aerf.analysis.invariant.InvariantDsl.lt;
import static org.aerf.analysis.invariant.InvariantDsl.metric;
import static org.aerf.analysis.invariant.InvariantDsl.not;
import static org.aerf.analysis.invariant.InvariantDsl.sourceRole;
import static org.aerf.analysis.invariant.InvariantDsl.targetRole;
import static org.aerf.analysis.invariant.InvariantDsl.value;

/**
 * The two worked invariant examples from AERF v0.4, sections 6.3 and
 * 6.4, transcribed directly into the {@link Invariant} model — not
 * illustrative inventions the way the seed/security rule catalogs are,
 * but the specification's own examples made executable.
 */
public final class SpecWorkedExamples {

    private SpecWorkedExamples() {
    }

    /**
     * Section 6.3:
     * <pre>
     * invariant no_presentation_to_persistence {
     *   scope: edge
     *   when: edge.label in [CALL, DEPENDS]
     *   assert: not (source.role == Presentation and target.role == Persistence)
     *   severity: critical
     * }
     * </pre>
     */
    public static Invariant noPresentationToPersistence() {
        return new Invariant(
                "no_presentation_to_persistence",
                Scope.EDGE,
                in(edgeRelation(), value(RelationType.CALL), value(RelationType.DEPENDS)),
                not(and(eq(sourceRole(), value(Role.PRESENTATION)), eq(targetRole(), value(Role.PERSISTENCE)))),
                "critical");
    }

    /**
     * Section 6.4:
     * <pre>
     * invariant entropy_budget {
     *   scope: graph
     *   when: true
     *   assert: metric.total_entropy &lt; threshold
     *   severity: critical
     * }
     * </pre>
     *
     * @param threshold the budget; section 6.4's own example uses 0.35, but
     *                  that number is a governance choice, not a value v0.4
     *                  asserts as universal, so it is a parameter here.
     */
    public static Invariant entropyBudget(double threshold) {
        return new Invariant(
                "entropy_budget",
                Scope.GRAPH,
                always(true),
                lt(metric("total_entropy"), value(threshold)),
                "critical");
    }
}
