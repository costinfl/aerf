package org.aerf.extraction;

import org.aerf.model.NodeId;

import java.util.List;
import java.util.Objects;

/**
 * Java {@link NodeId} conventions. AERF v0.4 section 2.2 deliberately
 * leaves id derivation to the adapter ("AERF does not define how
 * adapters derive it; that is an extraction-level concern" —
 * {@code NodeId}'s own javadoc), so this is an implementation decision,
 * documented here rather than left implicit in adapter code, and shared
 * by every Java-parsing adapter (including a future OpenRewrite one) so
 * a reference built on one side and a declaration built on the other
 * always agree on the same string.
 *
 * <p><b>Nested and anonymous types.</b> A JVM binary name's {@code $}
 * separator is normalized to {@code .} ({@code Outer$Inner} becomes
 * {@code Outer.Inner}), uniformly for named nested types and anonymous
 * ones ({@code Outer$1} becomes {@code Outer.1}). Whether an adapter
 * should emit a node for an anonymous or local class at all — v0.4
 * gives no guidance, and the ExtractionAdapter Plan's own preference is
 * "not emitting a node over emitting an unstable id" where stability
 * cannot be guaranteed — is a policy decision for the adapter, not for
 * this low-level string convention.
 *
 * <p><b>Method overloads.</b> Parameter types are erased (no generic
 * type arguments) and joined positionally, so two overloads of the same
 * method name remain distinct ids without depending on anything beyond
 * the method's own signature.
 */
public final class JavaNodeIds {

    private JavaNodeIds() {
    }

    /** MODULE id: Maven coordinates, {@code groupId:artifactId}. */
    public static NodeId module(String groupId, String artifactId) {
        return NodeId.of(require(groupId, "groupId") + ":" + require(artifactId, "artifactId"));
    }

    /**
     * COMPONENT id for a type. {@code binaryOrQualifiedName} may be either
     * a JVM binary name ({@code com.example.Outer$Inner}) or already in
     * source form ({@code com.example.Outer.Inner}) — both normalize to
     * the same id.
     */
    public static NodeId type(String binaryOrQualifiedName) {
        return NodeId.of(require(binaryOrQualifiedName, "binaryOrQualifiedName").replace('$', '.'));
    }

    /**
     * FUNCTION id for a method or constructor:
     * {@code Owner#name(erasedParam1,erasedParam2,...)}.
     *
     * @param declaringTypeBinaryOrQualifiedName same convention as {@link #type(String)}
     * @param methodName                         the method's simple name
     * @param erasedParameterTypes                each parameter's erased type name, in declaration order
     */
    public static NodeId method(String declaringTypeBinaryOrQualifiedName, String methodName, List<String> erasedParameterTypes) {
        String owner = require(declaringTypeBinaryOrQualifiedName, "declaringTypeBinaryOrQualifiedName").replace('$', '.');
        require(methodName, "methodName");
        Objects.requireNonNull(erasedParameterTypes, "erasedParameterTypes");
        String parameters = String.join(",", erasedParameterTypes);
        return NodeId.of(owner + "#" + methodName + "(" + parameters + ")");
    }

    private static String require(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
