package org.aerf.analysis.role.seed;

import org.aerf.analysis.role.RoleInferenceRule;
import org.aerf.model.Node;
import org.aerf.model.NodeType;
import org.aerf.model.Role;

import java.util.List;
import java.util.Optional;

/**
 * A small, illustrative catalog of seed rules.
 *
 * <p><b>These rules are an implementation decision, not part of AERF
 * v0.4.</b> The specification defines evidence classes (section 3.3:
 * structural, semantic, graph, governance) and requires seed rules to
 * exist, but does not define a concrete rule catalog — concrete rules are
 * explicitly extraction-adapter work ("Concrete APIs... belong to the
 * next engineering artifact"). This catalog exists to make
 * {@code SeedRoleInferenceEngine} exercisable and testable; it is
 * intentionally small and is expected to be replaced or extended per
 * organization/adapter, per the framework's governance-configurability
 * principle.
 */
public final class DefaultSeedRules {

    private DefaultSeedRules() {
    }

    public static List<RoleInferenceRule> illustrativeRules() {
        return List.of(
                new PersistenceBySpringDataAdapter(),
                new PresentationBySpringControllerAnnotation(),
                new ApplicationBySpringServiceAnnotation(),
                new DomainByDataNodeType(),
                new InfrastructureByConfigNodeType());
    }

    /** Structural+semantic seed: any evidence attributed to the spring-data adapter. */
    static final class PersistenceBySpringDataAdapter implements RoleInferenceRule {
        @Override
        public String name() {
            return "persistence-by-spring-data-adapter";
        }

        @Override
        public Optional<Candidate> evaluate(Node node) {
            return node.evidence().stream()
                    .filter(e -> e.sourceAdapter().equals("spring-data"))
                    .findFirst()
                    .map(e -> new Candidate(Role.PERSISTENCE, "spring-data evidence observed: " + e.description()));
        }
    }

    /** Semantic seed: a Spring @Controller annotation observed by the spring adapter. */
    static final class PresentationBySpringControllerAnnotation implements RoleInferenceRule {
        @Override
        public String name() {
            return "presentation-by-spring-controller-annotation";
        }

        @Override
        public Optional<Candidate> evaluate(Node node) {
            return node.evidence().stream()
                    .filter(e -> e.sourceAdapter().equals("spring") && e.description().contains("@Controller"))
                    .findFirst()
                    .map(e -> new Candidate(Role.PRESENTATION, "@Controller evidence observed: " + e.description()));
        }
    }

    /** Semantic seed: a Spring @Service annotation observed by the spring adapter. */
    static final class ApplicationBySpringServiceAnnotation implements RoleInferenceRule {
        @Override
        public String name() {
            return "application-by-spring-service-annotation";
        }

        @Override
        public Optional<Candidate> evaluate(Node node) {
            return node.evidence().stream()
                    .filter(e -> e.sourceAdapter().equals("spring") && e.description().contains("@Service"))
                    .findFirst()
                    .map(e -> new Candidate(Role.APPLICATION, "@Service evidence observed: " + e.description()));
        }
    }

    /** Structural seed: a node's canonical node type is DATA (section 3.3, "Structural: node type"). */
    static final class DomainByDataNodeType implements RoleInferenceRule {
        @Override
        public String name() {
            return "domain-by-data-node-type";
        }

        @Override
        public Optional<Candidate> evaluate(Node node) {
            if (node.type() != NodeType.DATA) {
                return Optional.empty();
            }
            return Optional.of(new Candidate(Role.DOMAIN, "node type is DATA"));
        }
    }

    /** Structural seed: a node's canonical node type is CONFIG. */
    static final class InfrastructureByConfigNodeType implements RoleInferenceRule {
        @Override
        public String name() {
            return "infrastructure-by-config-node-type";
        }

        @Override
        public Optional<Candidate> evaluate(Node node) {
            if (node.type() != NodeType.CONFIG) {
                return Optional.empty();
            }
            return Optional.of(new Candidate(Role.INFRASTRUCTURE, "node type is CONFIG"));
        }
    }
}
