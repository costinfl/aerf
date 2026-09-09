package org.aerf.openrewrite;

import org.aerf.extraction.EdgeFact;
import org.aerf.extraction.ExtractionRequest;
import org.aerf.extraction.ExtractionResult;
import org.aerf.extraction.JavaNodeIds;
import org.aerf.extraction.NodeFact;
import org.aerf.extraction.SourceExtractor;
import org.aerf.extraction.SymbolRef;
import org.aerf.model.Evidence;
import org.aerf.model.ExtractionFidelity;
import org.aerf.model.NodeId;
import org.aerf.model.NodeType;
import org.aerf.model.RelationType;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeTree;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Extracts class-level facts — AERF v0.4 section 2.2's {@code COMPONENT}
 * nodes, plus {@code EXTENDS}/{@code IMPLEMENTS} relations — from a Java
 * source set, via OpenRewrite's parser and type attribution. Method-level
 * facts (call edges, execution context) are a later increment's scope;
 * see the ExtractionAdapter Plan.
 *
 * <p>Only this module may import an OpenRewrite type; every fact this
 * class emits is {@code aerf-extraction}'s technology-agnostic
 * {@code NodeFact}/{@code EdgeFact}, so no OpenRewrite type ever crosses
 * into the canonical graph (section 9).
 *
 * <p>Fidelity follows type attribution directly: a class/interface/etc.
 * whose own declared type resolved, and an extends/implements target
 * whose type resolved, are recorded as
 * {@link ExtractionFidelity#L2_SYMBOL_RESOLVED}; an unresolved target
 * (OpenRewrite's {@code JavaType.Unknown} sentinel, or a {@code null}
 * method type in later increments) is recorded as
 * {@link ExtractionFidelity#L1_SYNTAX}, with the target represented as an
 * unresolved {@link SymbolRef} carrying its as-written name rather than
 * a fabricated one (section 8).
 */
public final class JavaClassExtractor implements SourceExtractor {

    private static final String ADAPTER_NAME = "openrewrite-java";

    @Override
    public ExtractionResult extract(ExtractionRequest request) {
        List<Path> sourceFiles = collectJavaFiles(request.sourceRoots());

        JavaParser.Builder<? extends JavaParser, ?> builder = JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(false);
        if (request.hasClasspath()) {
            builder = builder.classpath(request.classpath());
        }
        JavaParser parser = builder.build();

        ExecutionContext ctx = new InMemoryExecutionContext();
        List<NodeFact> nodeFacts = new ArrayList<>();
        List<EdgeFact> edgeFacts = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();

        try (Stream<SourceFile> parsed = parser.parse(sourceFiles, null, ctx)) {
            parsed.forEach(sourceFile -> {
                if (!(sourceFile instanceof J.CompilationUnit cu)) {
                    diagnostics.add("not a Java compilation unit: " + sourceFile.getSourcePath());
                    return;
                }
                ClassFactVisitor visitor = new ClassFactVisitor(cu.getSourcePath());
                visitor.visit(cu, null);
                nodeFacts.addAll(visitor.nodeFacts);
                edgeFacts.addAll(visitor.edgeFacts);
            });
        }

        return new ExtractionResult(nodeFacts, edgeFacts, diagnostics);
    }

    private static List<Path> collectJavaFiles(List<Path> sourceRoots) {
        List<Path> files = new ArrayList<>();
        for (Path root : sourceRoots) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            } catch (IOException e) {
                throw new UncheckedIOException("failed to walk source root " + root, e);
            }
        }
        // Sorted so extraction is deterministic regardless of the filesystem's
        // own directory-listing order across platforms/runs.
        files.sort(Comparator.comparing(Path::toString));
        return files;
    }

    private static final class ClassFactVisitor extends JavaIsoVisitor<Object> {

        private final Path sourcePath;
        final List<NodeFact> nodeFacts = new ArrayList<>();
        final List<EdgeFact> edgeFacts = new ArrayList<>();

        ClassFactVisitor(Path sourcePath) {
            this.sourcePath = sourcePath;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, Object p) {
            JavaType.FullyQualified type = classDecl.getType();
            if (isResolved(type)) {
                String ownFqn = type.getFullyQualifiedName();
                NodeId id = JavaNodeIds.type(ownFqn);
                String kind = kindOf(classDecl);
                Evidence declarationEvidence = Evidence.builder(ADAPTER_NAME, kind + " declaration observed",
                                ExtractionFidelity.L2_SYMBOL_RESOLVED)
                        .location(sourcePath.toString())
                        .build();
                nodeFacts.add(new NodeFact(id, NodeType.COMPONENT, Map.of(), List.of(declarationEvidence)));

                if (classDecl.getExtends() != null) {
                    edgeFacts.add(typeEdge(ownFqn, classDecl.getExtends(), RelationType.EXTENDS));
                }
                if (classDecl.getImplements() != null) {
                    for (TypeTree implemented : classDecl.getImplements()) {
                        edgeFacts.add(typeEdge(ownFqn, implemented, RelationType.IMPLEMENTS));
                    }
                }
            }
            return super.visitClassDeclaration(classDecl, p);
        }

        private EdgeFact typeEdge(String ownFqn, TypeTree targetTree, RelationType relation) {
            JavaType targetType = targetTree.getType();
            String printedName = TypeTreeNames.print(targetTree);
            boolean resolved = isResolved(targetType);
            String key = resolved ? ((JavaType.FullyQualified) targetType).getFullyQualifiedName() : printedName;
            ExtractionFidelity fidelity = resolved ? ExtractionFidelity.L2_SYMBOL_RESOLVED : ExtractionFidelity.L1_SYNTAX;

            Evidence edgeEvidence = Evidence.builder(ADAPTER_NAME, relation.name() + " target observed: " + printedName, fidelity)
                    .location(sourcePath.toString())
                    .build();
            return new EdgeFact(SymbolRef.of(ownFqn), SymbolRef.of(key, printedName), relation, List.of(edgeEvidence));
        }

        private static boolean isResolved(JavaType type) {
            return type instanceof JavaType.FullyQualified && !(type instanceof JavaType.Unknown);
        }

        private static String kindOf(J.ClassDeclaration classDecl) {
            J.ClassDeclaration.Kind.Type kind = classDecl.getKind();
            return kind.name().toLowerCase(Locale.ROOT);
        }
    }
}
