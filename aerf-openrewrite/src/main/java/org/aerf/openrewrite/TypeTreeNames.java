package org.aerf.openrewrite;

import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;

/**
 * Reconstructs a type reference's syntactic (as-written) name from its
 * OpenRewrite parse tree, for use as a {@code SymbolRef} key when type
 * attribution did not resolve it (see {@link JavaClassExtractor}).
 * Handles the extends/implements-target shapes that actually occur in
 * practice: a simple name, a dotted qualified name, and a generic type's
 * raw (unparameterized) name. Anything else falls back to the tree's own
 * {@code toString()} — a degraded but still-usable key, since an
 * unresolved reference this class can't print precisely will end up
 * {@link org.aerf.model.NodeRef.Unresolved} either way.
 */
final class TypeTreeNames {

    private TypeTreeNames() {
    }

    static String print(TypeTree tree) {
        return printNode(tree);
    }

    private static String printNode(J node) {
        if (node instanceof J.Identifier identifier) {
            return identifier.getSimpleName();
        }
        if (node instanceof J.FieldAccess fieldAccess) {
            return printNode(fieldAccess.getTarget()) + "." + fieldAccess.getSimpleName();
        }
        if (node instanceof J.ParameterizedType parameterizedType) {
            return printNode(parameterizedType.getClazz());
        }
        return String.valueOf(node);
    }
}
