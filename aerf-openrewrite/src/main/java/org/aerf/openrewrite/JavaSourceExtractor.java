package org.aerf.openrewrite;

import org.aerf.extraction.EdgeFact;
import org.aerf.extraction.ExtractionRequest;
import org.aerf.extraction.ExtractionResult;
import org.aerf.extraction.JavaNodeIds;
import org.aerf.extraction.NodeFact;
import org.aerf.extraction.SourceExtractor;
import org.aerf.extraction.SymbolRef;
import org.aerf.model.Evidence;
import org.aerf.model.ExecutionContext;
import org.aerf.model.ExtractionFidelity;
import org.aerf.model.NodeId;
import org.aerf.model.NodeType;
import org.aerf.model.RelationType;
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
import java.util.Set;
import java.util.stream.Stream;

/**
 * Extracts class- and method-level facts from a Java source set, via
 * OpenRewrite's parser and type attribution: AERF v0.4 section 2.2's
 * {@code COMPONENT} and {@code FUNCTION} nodes, plus
 * {@code EXTENDS}/{@code IMPLEMENTS}/{@code DEPENDS}/{@code CALL}
 * relations, and (for a call observed inside a loop) the
 * {@link ExecutionContext#ITERATED} evidence the N+1 heuristic (section
 * 4.3) needs. Named for its full scope from Increment 13's narrower
 * {@code JavaClassExtractor}, once Increment 14 broadened it to also
 * cover methods — see the ExtractionAdapter Plan and
 * {@code docs/increment-14-*.md} for why this class, not a second
 * extractor, grew to cover both: one parse batch, one visitor pass.
 *
 * <p>Only this module may import an OpenRewrite type; every fact this
 * class emits is {@code aerf-extraction}'s technology-agnostic
 * {@code NodeFact}/{@code EdgeFact}, so no OpenRewrite type ever crosses
 * into the canonical graph (section 9).
 *
 * <p>Fidelity follows type attribution directly: a declaration or
 * reference whose type resolved is recorded as
 * {@link ExtractionFidelity#L2_SYMBOL_RESOLVED}; one that did not
 * (OpenRewrite's {@code JavaType.Unknown} sentinel for a type-tree
 * position, or a {@code null} {@code JavaType.Method} for a call) is
 * recorded as {@link ExtractionFidelity#L1_SYNTAX}, with the target
 * represented as an unresolved {@link SymbolRef} carrying its as-written
 * name rather than a fabricated one (section 8).
 *
 * <p><b>Name clash.</b> {@code org.openrewrite.ExecutionContext} (the
 * parser's execution context) collides by simple name with
 * {@link ExecutionContext} (this project's iteration-evidence enum,
 * needed far more often in this class); the OpenRewrite one is used
 * fully qualified at its two call sites instead.
 */
public final class JavaSourceExtractor implements SourceExtractor {

    private static final String ADAPTER_NAME = "openrewrite-java";
    private static final String SPRING_CONTROLLER_ANNOTATION = "org.springframework.stereotype.Controller";
    private static final String SPRING_SERVICE_ANNOTATION = "org.springframework.stereotype.Service";
    private static final String SPRING_REPOSITORY_ANNOTATION = "org.springframework.stereotype.Repository";

    /**
     * Spring Data's own marker-interface family (open question #18,
     * `docs/open-questions-register.md`): idiomatic Spring Data
     * repositories are recognized by Spring at runtime through one of
     * these interfaces, not through {@link #SPRING_REPOSITORY_ANNOTATION}
     * - confirmed the hard way in Increment 18, where every repository
     * interface in a real, idiomatic spring-petclinic checkout got
     * {@code Role.UNKNOWN} because none of them carried the annotation.
     * Matched by resolved FQN of a class's <b>directly declared</b>
     * {@code extends}/{@code implements} target only - not transitively
     * through an intermediate custom interface - the same conservative,
     * one-hop scope {@code InheritRoleFromSupertype} uses for graph
     * refinement, and a deliberate decision recorded here rather than
     * left implicit: a future increment may widen this if a real
     * repository is found extending a marker interface only through an
     * intermediate type.
     */
    private static final Set<String> SPRING_DATA_MARKER_INTERFACES = Set.of(
            "org.springframework.data.repository.Repository",
            "org.springframework.data.repository.CrudRepository",
            "org.springframework.data.repository.PagingAndSortingRepository",
            "org.springframework.data.repository.ListCrudRepository",
            "org.springframework.data.repository.ListPagingAndSortingRepository",
            "org.springframework.data.repository.reactive.ReactiveCrudRepository",
            "org.springframework.data.repository.reactive.ReactiveSortingRepository",
            "org.springframework.data.jpa.repository.JpaRepository");

    @Override
    public ExtractionResult extract(ExtractionRequest request) {
        List<Path> sourceFiles = collectJavaFiles(request.sourceRoots());

        JavaParser.Builder<? extends JavaParser, ?> builder = JavaParser.fromJavaVersion()
                .logCompilationWarningsAndErrors(false);
        if (request.hasClasspath()) {
            builder = builder.classpath(request.classpath());
        }
        JavaParser parser = builder.build();

        org.openrewrite.ExecutionContext ctx = new InMemoryExecutionContext();
        List<NodeFact> nodeFacts = new ArrayList<>();
        List<EdgeFact> edgeFacts = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();

        try (Stream<SourceFile> parsed = parser.parse(sourceFiles, null, ctx)) {
            parsed.forEach(sourceFile -> {
                if (!(sourceFile instanceof J.CompilationUnit cu)) {
                    diagnostics.add("not a Java compilation unit: " + sourceFile.getSourcePath());
                    return;
                }
                FactVisitor visitor = new FactVisitor(cu.getSourcePath());
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

    /**
     * One traversal per file, emitting every fact this extractor knows
     * how to produce. Class-, field-, and method-level state is tracked
     * with save/restore instance fields rather than threading state
     * through visitor parameters, matching {@code JavaIsoVisitor}'s
     * established idiom for context that only certain subtrees need.
     */
    private static final class FactVisitor extends JavaIsoVisitor<Object> {

        private final Path sourcePath;
        final List<NodeFact> nodeFacts = new ArrayList<>();
        final List<EdgeFact> edgeFacts = new ArrayList<>();

        /** The enclosing class's resolved FQN, or {@code null} if unresolved/absent. */
        private String currentOwnerFqn;
        /**
         * The enclosing class's own role-seeding evidence - Spring
         * stereotype annotations (Increment 15) and, since this increment,
         * Spring Data marker-interface inheritance too (open question
         * #18) - copied onto each of its declared methods' FUNCTION
         * NodeFacts too (Increment 16) - see
         * {@code docs/increment-16-*.md} for why: the canonical graph has
         * no structural edge from a FUNCTION node to its declaring
         * COMPONENT node, so graph-relationship role refinement has no
         * way to propagate a class's role down to its own methods: only
         * copying the evidence at extraction time makes a method's own
         * role inferable at all.
         */
        private List<Evidence> currentOwnerRoleEvidence = List.of();
        /** The enclosing method's FUNCTION {@code NodeId} value, or {@code null} outside any method. */
        private String currentMethodId;
        private int loopDepth;

        FactVisitor(Path sourcePath) {
            this.sourcePath = sourcePath;
        }

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, Object p) {
            String previousOwnerFqn = this.currentOwnerFqn;
            List<Evidence> previousOwnerRoleEvidence = this.currentOwnerRoleEvidence;

            JavaType.FullyQualified type = classDecl.getType();
            if (isResolved(type)) {
                String ownFqn = type.getFullyQualifiedName();
                this.currentOwnerFqn = ownFqn;
                NodeId id = JavaNodeIds.type(ownFqn);
                String kind = kindOf(classDecl);
                Evidence declarationEvidence = Evidence.builder(ADAPTER_NAME, kind + " declaration observed",
                                ExtractionFidelity.L2_SYMBOL_RESOLVED)
                        .location(sourcePath.toString())
                        .build();
                List<TypeTree> declaredSupertypes = new ArrayList<>();
                if (classDecl.getExtends() != null) {
                    declaredSupertypes.add(classDecl.getExtends());
                }
                if (classDecl.getImplements() != null) {
                    declaredSupertypes.addAll(classDecl.getImplements());
                }
                List<Evidence> stereotypeEvidence = springStereotypeEvidence(classDecl.getLeadingAnnotations());
                List<Evidence> springDataMarkerEvidence = springDataMarkerInterfaceEvidence(declaredSupertypes);
                List<Evidence> roleEvidence = new ArrayList<>();
                roleEvidence.addAll(stereotypeEvidence);
                roleEvidence.addAll(springDataMarkerEvidence);
                this.currentOwnerRoleEvidence = List.copyOf(roleEvidence);
                List<Evidence> classEvidence = new ArrayList<>();
                classEvidence.add(declarationEvidence);
                classEvidence.addAll(roleEvidence);
                nodeFacts.add(new NodeFact(id, NodeType.COMPONENT, Map.of(), List.copyOf(classEvidence)));

                if (classDecl.getExtends() != null) {
                    edgeFacts.add(typeEdge(ownFqn, classDecl.getExtends(), RelationType.EXTENDS));
                }
                if (classDecl.getImplements() != null) {
                    for (TypeTree implemented : classDecl.getImplements()) {
                        edgeFacts.add(typeEdge(ownFqn, implemented, RelationType.IMPLEMENTS));
                    }
                }
            } else {
                this.currentOwnerFqn = null;
                this.currentOwnerRoleEvidence = List.of();
            }

            J.ClassDeclaration result = super.visitClassDeclaration(classDecl, p);
            this.currentOwnerFqn = previousOwnerFqn;
            this.currentOwnerRoleEvidence = previousOwnerRoleEvidence;
            return result;
        }

        @Override
        public J.VariableDeclarations visitVariableDeclarations(J.VariableDeclarations multiVariable, Object p) {
            // A field is a VariableDeclarations directly under a class body -
            // i.e. not nested inside a method (currentMethodId == null).
            // Method parameters and local variables share this same AST node
            // type but are visited with currentMethodId already set, so they
            // are deliberately excluded from this increment's DEPENDS scope
            // (see the increment doc); a static/instance initializer block's
            // own local variables are a known, undocumented-in-code edge case
            // this heuristic does not distinguish from a field, left for a
            // later increment since none occur in the sample project.
            if (currentOwnerFqn != null && currentMethodId == null) {
                TypeTree typeExpression = multiVariable.getTypeExpression();
                if (typeExpression != null) {
                    JavaType fieldType = typeExpression.getType();
                    if (!(fieldType instanceof JavaType.Primitive)) {
                        String printedName = TypeTreeNames.print(typeExpression);
                        boolean resolved = isResolved(fieldType);
                        String key = resolved ? ((JavaType.FullyQualified) fieldType).getFullyQualifiedName() : printedName;
                        ExtractionFidelity fidelity = resolved ? ExtractionFidelity.L2_SYMBOL_RESOLVED : ExtractionFidelity.L1_SYNTAX;
                        Evidence edgeEvidence = Evidence.builder(ADAPTER_NAME, "DEPENDS target observed: " + printedName, fidelity)
                                .location(sourcePath.toString())
                                .build();
                        edgeFacts.add(new EdgeFact(SymbolRef.of(currentOwnerFqn), SymbolRef.of(key, printedName),
                                RelationType.DEPENDS, List.of(edgeEvidence)));
                    }
                }
            }
            return super.visitVariableDeclarations(multiVariable, p);
        }

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDecl, Object p) {
            String previousMethodId = this.currentMethodId;
            int previousLoopDepth = this.loopDepth;
            this.loopDepth = 0;

            JavaType.Method methodType = methodDecl.getMethodType();
            if (isResolvedMethod(methodType)) {
                NodeId id = functionId(methodType);
                Evidence declarationEvidence = Evidence.builder(ADAPTER_NAME, "method declaration observed",
                                ExtractionFidelity.L2_SYMBOL_RESOLVED)
                        .location(sourcePath.toString())
                        .build();
                List<Evidence> methodEvidence = new ArrayList<>();
                methodEvidence.add(declarationEvidence);
                methodEvidence.addAll(currentOwnerRoleEvidence);
                nodeFacts.add(new NodeFact(id, NodeType.FUNCTION, Map.of(), List.copyOf(methodEvidence)));
                this.currentMethodId = id.value();

                if (currentOwnerFqn != null) {
                    edgeFacts.add(memberOfEdge(id.value(), currentOwnerFqn));
                }
            } else {
                // No FUNCTION node emitted for an unresolved method declaration
                // (section 8: never fabricate an id) - and, as a consequence,
                // no CALL edges are attributed to it either, since there is no
                // resolved source id to attribute them from. Not expected in
                // practice for a method declared in the parsed batch itself
                // (mirrors the class-level floor in visitClassDeclaration).
                this.currentMethodId = null;
            }

            J.MethodDeclaration result = super.visitMethodDeclaration(methodDecl, p);
            this.currentMethodId = previousMethodId;
            this.loopDepth = previousLoopDepth;
            return result;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, Object p) {
            if (currentMethodId != null) {
                JavaType.Method targetMethodType = invocation.getMethodType();
                String simpleName = invocation.getSimpleName();
                boolean resolved = isResolvedMethod(targetMethodType);
                String key = resolved ? functionId(targetMethodType).value() : simpleName;
                String description = "CALL target observed: " + (resolved ? key : simpleName);
                ExtractionFidelity fidelity = resolved ? ExtractionFidelity.L2_SYMBOL_RESOLVED : ExtractionFidelity.L1_SYNTAX;

                Evidence.Builder evidenceBuilder = Evidence.builder(ADAPTER_NAME, description, fidelity)
                        .location(sourcePath.toString());
                if (loopDepth > 0) {
                    evidenceBuilder.executionContext(ExecutionContext.ITERATED);
                }
                edgeFacts.add(new EdgeFact(SymbolRef.of(currentMethodId), SymbolRef.of(key, simpleName),
                        RelationType.CALL, List.of(evidenceBuilder.build())));
            }
            return super.visitMethodInvocation(invocation, p);
        }

        @Override
        public J.ForEachLoop visitForEachLoop(J.ForEachLoop forEachLoop, Object p) {
            loopDepth++;
            J.ForEachLoop result = super.visitForEachLoop(forEachLoop, p);
            loopDepth--;
            return result;
        }

        @Override
        public J.ForLoop visitForLoop(J.ForLoop forLoop, Object p) {
            loopDepth++;
            J.ForLoop result = super.visitForLoop(forLoop, p);
            loopDepth--;
            return result;
        }

        @Override
        public J.WhileLoop visitWhileLoop(J.WhileLoop whileLoop, Object p) {
            loopDepth++;
            J.WhileLoop result = super.visitWhileLoop(whileLoop, p);
            loopDepth--;
            return result;
        }

        @Override
        public J.DoWhileLoop visitDoWhileLoop(J.DoWhileLoop doWhileLoop, Object p) {
            loopDepth++;
            J.DoWhileLoop result = super.visitDoWhileLoop(doWhileLoop, p);
            loopDepth--;
            return result;
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

        /**
         * The AERF v0.4.1 patch Amendment 6 fix for open question #17: a
         * purely structural fact - "this method is declared by this
         * class" - always resolved within the same parse batch (a
         * method's declaring class is, by construction, present in the
         * same {@code J.CompilationUnit} this visitor is already
         * traversing), unlike every other edge this class emits, whose
         * target may be external. Not consumed by role inference (see the
         * amendment for why); available for any future graph-relationship
         * rule or centrality signal that wants to reason about a
         * method/class pair.
         */
        private EdgeFact memberOfEdge(String functionId, String ownerFqn) {
            Evidence edgeEvidence = Evidence.builder(ADAPTER_NAME, "method declared by " + ownerFqn,
                            ExtractionFidelity.L2_SYMBOL_RESOLVED)
                    .location(sourcePath.toString())
                    .build();
            return new EdgeFact(SymbolRef.of(functionId), SymbolRef.of(ownerFqn), RelationType.MEMBER_OF, List.of(edgeEvidence));
        }

        /**
         * Structured {@code spring-data} evidence for a class whose
         * directly declared {@code extends}/{@code implements} target
         * resolves to one of {@link #SPRING_DATA_MARKER_INTERFACES} - the
         * open question #18 fix. Deliberately parallel to
         * {@link #springStereotypeEvidence}: same {@code sourceAdapter}
         * string {@code PersistenceBySpringDataAdapter} already matches on
         * (no seed-rule change needed at all), matched by resolved FQN
         * only, never by simple name.
         */
        private List<Evidence> springDataMarkerInterfaceEvidence(List<TypeTree> declaredSupertypes) {
            List<Evidence> evidence = new ArrayList<>();
            for (TypeTree superType : declaredSupertypes) {
                JavaType type = superType.getType();
                if (!isResolved(type)) {
                    continue;
                }
                String fqn = ((JavaType.FullyQualified) type).getFullyQualifiedName();
                if (!SPRING_DATA_MARKER_INTERFACES.contains(fqn)) {
                    continue;
                }
                evidence.add(Evidence.builder("spring-data", "extends Spring Data marker interface " + fqn,
                                ExtractionFidelity.L2_SYMBOL_RESOLVED)
                        .location(sourcePath.toString())
                        .attribute("springDataMarkerInterface", fqn)
                        .build());
            }
            return evidence;
        }

        /**
         * Structured evidence for the three Spring stereotype annotations
         * {@code DefaultSeedRules} already knows how to read — literally
         * the {@code sourceAdapter} strings ("spring", "spring-data") and
         * {@code annotation} attribute key those rules match on (AERF
         * v0.4.1 patch Amendment 5), so this adapter's real evidence
         * satisfies rules written well before any adapter existed, with no
         * rule-side change. Only an annotation whose type resolved to
         * exactly one of these three FQNs produces evidence; every other
         * annotation (including an unresolved one that merely looks like
         * {@code @Controller} by simple name) is silently skipped, not
         * guessed at — matching a real FQN, not a name, is what makes this
         * conservative rather than a heuristic that could misattribute an
         * unrelated framework's same-named annotation.
         */
        private List<Evidence> springStereotypeEvidence(List<J.Annotation> annotations) {
            List<Evidence> evidence = new ArrayList<>();
            for (J.Annotation annotation : annotations) {
                JavaType type = annotation.getType();
                if (!isResolved(type)) {
                    continue;
                }
                String fqn = ((JavaType.FullyQualified) type).getFullyQualifiedName();
                String sourceAdapter = switch (fqn) {
                    case SPRING_CONTROLLER_ANNOTATION, SPRING_SERVICE_ANNOTATION -> "spring";
                    case SPRING_REPOSITORY_ANNOTATION -> "spring-data";
                    default -> null;
                };
                if (sourceAdapter == null) {
                    continue;
                }
                evidence.add(Evidence.builder(sourceAdapter, "@" + annotation.getSimpleName() + " annotation observed",
                                ExtractionFidelity.L2_SYMBOL_RESOLVED)
                        .location(sourcePath.toString())
                        .attribute("annotation", fqn)
                        .build());
            }
            return evidence;
        }

        private static NodeId functionId(JavaType.Method methodType) {
            String owner = methodType.getDeclaringType().getFullyQualifiedName();
            List<String> erasedParameterTypes = methodType.getParameterTypes().stream()
                    .map(FactVisitor::erasedTypeName)
                    .toList();
            return JavaNodeIds.method(owner, methodType.getName(), erasedParameterTypes);
        }

        private static String erasedTypeName(JavaType type) {
            if (type instanceof JavaType.Primitive primitive) {
                return primitive.getKeyword();
            }
            if (type instanceof JavaType.FullyQualified fullyQualified) {
                return fullyQualified.getFullyQualifiedName();
            }
            if (type instanceof JavaType.Array array) {
                return erasedTypeName(array.getElemType()) + "[]";
            }
            if (type instanceof JavaType.GenericTypeVariable genericTypeVariable) {
                List<JavaType> bounds = genericTypeVariable.getBounds();
                return bounds.isEmpty() ? "java.lang.Object" : erasedTypeName(bounds.get(0));
            }
            return String.valueOf(type);
        }

        private static boolean isResolved(JavaType type) {
            return type instanceof JavaType.FullyQualified && !(type instanceof JavaType.Unknown);
        }

        private static boolean isResolvedMethod(JavaType.Method methodType) {
            return methodType != null && isResolved(methodType.getDeclaringType());
        }

        private static String kindOf(J.ClassDeclaration classDecl) {
            J.ClassDeclaration.Kind.Type kind = classDecl.getKind();
            return kind.name().toLowerCase(Locale.ROOT);
        }
    }
}
