package org.aerf.analysis.invariant;

import org.aerf.model.Edge;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Evaluates one {@link Invariant} against a graph, per AERF v0.4 section
 * 6. Evaluation is a pure function of the invariant, the graph, and (for
 * {@link Scope#GRAPH}) a supplied metrics map — no side effects, no
 * hidden state, satisfying section 6.2's "Deterministic evaluation" and
 * "Pure evaluation with no side effects."
 *
 * <p>For {@link Scope#EDGE}, an edge is only evaluable if both endpoints
 * are {@link NodeRef.Resolved} to a node present in the graph — an edge
 * with an unresolved endpoint is excluded from evaluation entirely (not
 * counted as passing, not counted as violating), the same treatment
 * every entropy calculator gives unresolved structure.
 */
public final class InvariantEvaluator {

    public InvariantEvaluationResult evaluate(Invariant invariant, Graph graph, Map<String, Double> metrics) {
        Objects.requireNonNull(invariant, "invariant");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(metrics, "metrics");

        List<InvariantViolation> violations = switch (invariant.scope()) {
            case NODE -> evaluateNodeScope(invariant, graph);
            case EDGE -> evaluateEdgeScope(invariant, graph);
            case GRAPH -> evaluateGraphScope(invariant, graph, metrics);
        };

        return new InvariantEvaluationResult(invariant.name(), invariant.severity(), violations);
    }

    private List<InvariantViolation> evaluateNodeScope(Invariant invariant, Graph graph) {
        List<InvariantViolation> violations = new ArrayList<>();
        for (Node node : graph.nodes()) {
            Function<ValueExpression.Property, Object> resolver = property -> switch (property.key()) {
                case NODE_ROLE -> node.role();
                case NODE_TYPE -> node.type();
                default -> throw new IllegalStateException(
                        "property " + property.key() + " is not valid in NODE scope (invariant '" + invariant.name() + "')");
            };
            if (!evaluatePredicate(invariant.when(), resolver)) {
                continue;
            }
            if (!evaluatePredicate(invariant.assertion(), resolver)) {
                violations.add(new InvariantViolation(new ViolationSubject.OfNode(node.id()),
                        "node " + node.id() + " violated invariant '" + invariant.name() + "'"));
            }
        }
        return violations;
    }

    private List<InvariantViolation> evaluateEdgeScope(Invariant invariant, Graph graph) {
        List<InvariantViolation> violations = new ArrayList<>();
        for (Edge edge : graph.edges()) {
            if (!(edge.source() instanceof NodeRef.Resolved sourceRef) || !(edge.target() instanceof NodeRef.Resolved targetRef)) {
                continue;
            }
            Node sourceNode = graph.node(sourceRef.id()).orElse(null);
            Node targetNode = graph.node(targetRef.id()).orElse(null);
            if (sourceNode == null || targetNode == null) {
                continue;
            }

            Function<ValueExpression.Property, Object> resolver = property -> switch (property.key()) {
                case SOURCE_ROLE -> sourceNode.role();
                case SOURCE_TYPE -> sourceNode.type();
                case TARGET_ROLE -> targetNode.role();
                case TARGET_TYPE -> targetNode.type();
                case EDGE_RELATION -> edge.relation();
                default -> throw new IllegalStateException(
                        "property " + property.key() + " is not valid in EDGE scope (invariant '" + invariant.name() + "')");
            };
            if (!evaluatePredicate(invariant.when(), resolver)) {
                continue;
            }
            if (!evaluatePredicate(invariant.assertion(), resolver)) {
                violations.add(new InvariantViolation(new ViolationSubject.OfEdge(edge),
                        "edge " + edge + " violated invariant '" + invariant.name() + "'"));
            }
        }
        return violations;
    }

    private List<InvariantViolation> evaluateGraphScope(Invariant invariant, Graph graph, Map<String, Double> metrics) {
        Function<ValueExpression.Property, Object> resolver = property -> switch (property.key()) {
            case METRIC -> {
                Double value = metrics.get(property.metricName());
                if (value == null) {
                    throw new IllegalStateException("metric '" + property.metricName()
                            + "' was not supplied for GRAPH-scope evaluation of invariant '" + invariant.name() + "'");
                }
                yield value;
            }
            default -> throw new IllegalStateException(
                    "property " + property.key() + " is not valid in GRAPH scope (invariant '" + invariant.name() + "')");
        };

        if (!evaluatePredicate(invariant.when(), resolver)) {
            return List.of();
        }
        if (evaluatePredicate(invariant.assertion(), resolver)) {
            return List.of();
        }
        return List.of(new InvariantViolation(new ViolationSubject.OfGraph(),
                "graph violated invariant '" + invariant.name() + "'"));
    }

    private static boolean evaluatePredicate(Predicate predicate, Function<ValueExpression.Property, Object> resolver) {
        return switch (predicate) {
            case Predicate.Always always -> always.value();
            case Predicate.Not not -> !evaluatePredicate(not.operand(), resolver);
            case Predicate.And and -> and.operands().stream().allMatch(p -> evaluatePredicate(p, resolver));
            case Predicate.Or or -> or.operands().stream().anyMatch(p -> evaluatePredicate(p, resolver));
            case Predicate.Equals eq -> Objects.equals(resolveValue(eq.left(), resolver), resolveValue(eq.right(), resolver));
            case Predicate.NotEquals ne -> !Objects.equals(resolveValue(ne.left(), resolver), resolveValue(ne.right(), resolver));
            case Predicate.LessThan lt -> compare(lt.left(), lt.right(), resolver) < 0;
            case Predicate.LessThanOrEqual lte -> compare(lte.left(), lte.right(), resolver) <= 0;
            case Predicate.GreaterThan gt -> compare(gt.left(), gt.right(), resolver) > 0;
            case Predicate.GreaterThanOrEqual gte -> compare(gte.left(), gte.right(), resolver) >= 0;
            case Predicate.In in -> {
                Object value = resolveValue(in.value(), resolver);
                yield in.candidates().stream().anyMatch(c -> Objects.equals(value, resolveValue(c, resolver)));
            }
        };
    }

    private static Object resolveValue(ValueExpression expression, Function<ValueExpression.Property, Object> resolver) {
        return switch (expression) {
            case ValueExpression.Property property -> resolver.apply(property);
            case ValueExpression.Constant constant -> constant.value();
        };
    }

    private static int compare(ValueExpression left, ValueExpression right, Function<ValueExpression.Property, Object> resolver) {
        Object leftValue = resolveValue(left, resolver);
        Object rightValue = resolveValue(right, resolver);
        if (!(leftValue instanceof Number leftNumber) || !(rightValue instanceof Number rightNumber)) {
            throw new IllegalStateException(
                    "ordering comparison requires numeric operands, got " + leftValue + " and " + rightValue);
        }
        return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
    }
}
