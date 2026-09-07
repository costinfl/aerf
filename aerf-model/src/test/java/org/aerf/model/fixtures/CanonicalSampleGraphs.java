package org.aerf.model.fixtures;

import org.aerf.model.Evidence;
import org.aerf.model.ExtractionFidelity;
import org.aerf.model.Graph;
import org.aerf.model.Node;
import org.aerf.model.NodeId;
import org.aerf.model.NodeRef;
import org.aerf.model.NodeType;
import org.aerf.model.RelationType;
import org.aerf.model.Role;

import java.util.List;
import java.util.Map;

/**
 * Small, deterministic sample graphs shared across aerf-model tests and
 * intended for reuse once metric and invariant tests exist (AERF v0.4
 * implementation instructions, "Testing Strategy").
 *
 * <p>Currently lives in aerf-model's test sources. If a module consuming
 * these fixtures from outside aerf-model (e.g. a future aerf-analysis) is
 * introduced, this should move to a shared test-fixtures artifact rather
 * than being duplicated.
 */
public final class CanonicalSampleGraphs {

    public static final NodeId ORDER_CONTROLLER = NodeId.of("web.OrderController");
    public static final NodeId ORDER_SERVICE = NodeId.of("service.OrderService");
    public static final NodeId ORDER = NodeId.of("domain.Order");
    public static final NodeId ORDER_REPOSITORY = NodeId.of("repository.OrderRepository");

    private CanonicalSampleGraphs() {
    }

    /**
     * A minimal layered slice: Presentation -> Application -> Domain and
     * Application -> Persistence, plus one deliberate layering violation
     * (Presentation -> Persistence) and one relation to an unresolved
     * external system. Reused later by layer-entropy and invariant tests
     * (v0.4 sections 4.1 and 6.3).
     */
    public static Graph layeredOrderSlice() {
        Node controller = Node.of(ORDER_CONTROLLER, NodeType.COMPONENT, Role.PRESENTATION,
                Map.of("framework", "spring-mvc"),
                List.of(Evidence.of("spring", "@Controller annotation", ExtractionFidelity.L2_SYMBOL_RESOLVED)));

        Node service = Node.of(ORDER_SERVICE, NodeType.COMPONENT, Role.APPLICATION,
                Map.of("framework", "spring"),
                List.of(Evidence.of("spring", "@Service annotation", ExtractionFidelity.L2_SYMBOL_RESOLVED)));

        Node order = Node.of(ORDER, NodeType.DATA, Role.DOMAIN,
                Map.of(),
                List.of(Evidence.of("java", "plain class, no persistence annotations", ExtractionFidelity.L1_SYNTAX)));

        Node repository = Node.of(ORDER_REPOSITORY, NodeType.COMPONENT, Role.PERSISTENCE,
                Map.of("framework", "spring-data"),
                List.of(Evidence.of("spring-data", "Repository interface", ExtractionFidelity.L2_SYMBOL_RESOLVED)));

        Graph.Builder builder = Graph.builder()
                .addNode(controller)
                .addNode(service)
                .addNode(order)
                .addNode(repository);

        builder.addEdge(NodeRef.resolved(ORDER_CONTROLLER), NodeRef.resolved(ORDER_SERVICE), RelationType.CALL,
                List.of(Evidence.of("java", "controller method calls service method", ExtractionFidelity.L2_SYMBOL_RESOLVED)));

        builder.addEdge(NodeRef.resolved(ORDER_SERVICE), NodeRef.resolved(ORDER), RelationType.DEPENDS,
                List.of(Evidence.of("java", "service method parameter/return type", ExtractionFidelity.L2_SYMBOL_RESOLVED)));

        builder.addEdge(NodeRef.resolved(ORDER_SERVICE), NodeRef.resolved(ORDER_REPOSITORY), RelationType.CALL,
                List.of(Evidence.of("java", "service method calls repository method", ExtractionFidelity.L2_SYMBOL_RESOLVED)));

        // Deliberate layering violation: Presentation calling Persistence directly.
        builder.addEdge(NodeRef.resolved(ORDER_CONTROLLER), NodeRef.resolved(ORDER_REPOSITORY), RelationType.CALL,
                List.of(Evidence.of("java", "controller method calls repository method directly", ExtractionFidelity.L2_SYMBOL_RESOLVED)));

        builder.addEdge(NodeRef.resolved(ORDER_REPOSITORY),
                NodeRef.unresolved("external JDBC datasource, not modeled as a node"),
                RelationType.COMMUNICATES,
                List.of(Evidence.of("spring-data", "repository backed by a DataSource", ExtractionFidelity.L1_SYNTAX)));

        return builder.build();
    }
}
