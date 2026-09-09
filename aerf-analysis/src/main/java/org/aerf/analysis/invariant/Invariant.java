package org.aerf.analysis.invariant;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One governance invariant (AERF v0.4 section 6), corresponding directly
 * to the conceptual rule syntax in section 6.3:
 *
 * <pre>
 * invariant name {
 *   scope: ...
 *   when: ...
 *   assert: ...
 *   severity: ...
 * }
 * </pre>
 *
 * <p>{@code severity} is a free-text label, not a closed enum — AERF
 * v0.4 never enumerates a fixed severity vocabulary; both worked
 * examples in the specification (sections 6.3 and 6.4) use only
 * {@code "critical"}. This follows the same reasoning as
 * {@code SecurityOpportunityRule.Finding}'s {@code concern} field
 * (Increment 7): inventing a closed set here would assert a taxonomy the
 * specification does not.
 */
public record Invariant(String name, Scope scope, Predicate when, Predicate assertion, String severity) {

    public Invariant {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(when, "when");
        Objects.requireNonNull(assertion, "assertion");
        Objects.requireNonNull(severity, "severity");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (severity.isBlank()) {
            throw new IllegalArgumentException("severity must not be blank");
        }
    }

    /**
     * Every {@code metric(...)} name this invariant's {@code when} or
     * {@code assertion} predicate references (added in Increment 18, when
     * running the pipeline against a real repository surfaced the need).
     * {@link InvariantEvaluator#evaluate} deliberately throws rather
     * than silently passing or failing when a GRAPH-scope invariant
     * references a metric that was not supplied — a caller that cannot
     * guarantee every referenced metric is available (a MVP entropy
     * dimension can be legitimately undefined, section 3.5/5.1) needs a
     * way to check *before* calling {@code evaluate}, without duplicating
     * this record's own predicate tree walk. Empty for a NODE/EDGE-scope
     * invariant, or any invariant that references no metric at all (e.g.
     * {@code always(true)}).
     */
    public Set<String> referencedMetricNames() {
        Set<String> names = new LinkedHashSet<>();
        collectMetricNames(when, names);
        collectMetricNames(assertion, names);
        return names;
    }

    private static void collectMetricNames(Predicate predicate, Set<String> names) {
        switch (predicate) {
            case Predicate.Always ignored -> {
            }
            case Predicate.Not not -> collectMetricNames(not.operand(), names);
            case Predicate.And and -> and.operands().forEach(p -> collectMetricNames(p, names));
            case Predicate.Or or -> or.operands().forEach(p -> collectMetricNames(p, names));
            case Predicate.Equals eq -> collectMetricNames(List.of(eq.left(), eq.right()), names);
            case Predicate.NotEquals ne -> collectMetricNames(List.of(ne.left(), ne.right()), names);
            case Predicate.LessThan lt -> collectMetricNames(List.of(lt.left(), lt.right()), names);
            case Predicate.LessThanOrEqual lte -> collectMetricNames(List.of(lte.left(), lte.right()), names);
            case Predicate.GreaterThan gt -> collectMetricNames(List.of(gt.left(), gt.right()), names);
            case Predicate.GreaterThanOrEqual gte -> collectMetricNames(List.of(gte.left(), gte.right()), names);
            case Predicate.In in -> {
                collectMetricNames(List.of(in.value()), names);
                collectMetricNames(in.candidates(), names);
            }
        }
    }

    private static void collectMetricNames(List<ValueExpression> expressions, Set<String> names) {
        for (ValueExpression expression : expressions) {
            if (expression instanceof ValueExpression.Property property && property.key() == PropertyKey.METRIC) {
                names.add(property.metricName());
            }
        }
    }
}
