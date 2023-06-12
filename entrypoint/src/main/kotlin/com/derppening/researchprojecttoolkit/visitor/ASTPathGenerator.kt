package com.derppening.researchprojecttoolkit.visitor

import com.derppening.researchprojecttoolkit.util.mapNotNull
import com.github.javaparser.ast.ArrayCreationLevel
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.comments.JavadocComment
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.nodeTypes.*
import com.github.javaparser.ast.stmt.*
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.ReferenceType
import com.github.javaparser.ast.type.Type
import com.github.javaparser.ast.type.TypeParameter
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import java.util.*
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

/**
 * A [com.github.javaparser.ast.visitor.GenericVisitor] which generates a path from the root [CompilationUnit] to the
 * given node.
 */
class ASTPathGenerator :
    GenericUnimplementedVisitor<CompilationUnitPathTrace<out Node>, Node>,
    GenericVisitorWithNodeDispatch<CompilationUnitPathTrace<out Node>, Node> {

    /**
     * Throws an exception if [parent] is not [child] or [parent] is not the parent of [child].
     */
    private fun throwIfNodeIsNotChildOrSelf(parent: Node, child: Node) {
        check(parent === child || child.parentNode.map { it === parent }.getOrDefault(false))
    }

    //<editor-fold desc="Interface Mappers">

    private fun <N : Node> visit(n: NodeWithAnnotations<N>, arg: Node): NodePathTrace<N, AnnotationExpr>? =
        n.annotations
            .indexOf(arg)
            .takeIf { it >= 0 }
            ?.let { idx ->
                NodePathTrace { (it as NodeWithAnnotations<*>).getAnnotation(idx) }
            }

    private fun <N : Node> visit(n: NodeWithArguments<N>, arg: Node): NodePathTrace<N, Expression>? =
        n.arguments
            .indexOf(arg)
            .takeIf { it >= 0 }
            ?.let { idx ->
                NodePathTrace { (it as NodeWithArguments<*>).getArgument(idx) }
            }

    private fun <N : Node> visit(n: NodeWithBlockStmt<N>, arg: Node): NodePathTrace<N, BlockStmt>? =
        n.body
            .takeIf { it === arg }
            ?.let {
                NodePathTrace { (it as NodeWithBlockStmt<*>).body }
            }

    private fun <N : Node> visit(n: NodeWithBody<N>, arg: Node): NodePathTrace<N, Statement>? =
        n.body
            .takeIf { it === arg }
            ?.let {
                NodePathTrace { (it as NodeWithBody<*>).body }
            }

    private fun <N : Node> visit(n: NodeWithCondition<N>, arg: Node): NodePathTrace<N, Expression>? =
        n.condition
            .takeIf { it === arg }
            ?.let {
                NodePathTrace { (it as NodeWithCondition<*>).condition }
            }

    private fun <N : Node> visit(n: NodeWithExpression<N>, arg: Node): NodePathTrace<N, Expression>? =
        n.expression
            .takeIf { it === arg }
            ?.let {
                NodePathTrace { (it as NodeWithExpression<*>).expression }
            }

    private fun <N : Node> visit(n: NodeWithExtends<N>, arg: Node): NodePathTrace<N, ClassOrInterfaceType>? =
        n.extendedTypes
            .indexOf(arg)
            .takeIf { it >= 0 }
            ?.let { idx ->
                NodePathTrace { (it as NodeWithExtends<*>).getExtendedTypes(idx) }
            }

    private fun <N : Node> visit(n: NodeWithImplements<N>, arg: Node): NodePathTrace<N, ClassOrInterfaceType>? =
        n.implementedTypes
            .indexOf(arg)
            .takeIf { it >= 0 }
            ?.let { idx ->
                NodePathTrace { (it as NodeWithImplements<*>).getImplementedTypes(idx) }
            }

    private fun <N : Node> visit(n: NodeWithJavadoc<N>, arg: Node): NodePathTrace<N, JavadocComment>? =
        n.javadocComment
            .mapNotNull { javadocComment -> javadocComment.takeIf { it === arg }}
            .map {
                NodePathTrace<N, JavadocComment> { (it as NodeWithJavadoc<*>).javadocComment.get() }
            }
            .getOrNull()

    private fun <N : Node> visit(n: NodeWithMembers<N>, arg: Node): NodePathTrace<N, BodyDeclaration<*>>? =
        n.members
            .indexOf(arg)
            .takeIf { it >= 0 }
            ?.let { idx ->
                NodePathTrace { (it as NodeWithMembers<*>).getMember(idx) }
            }

    private fun <N : Node> visit(n: NodeWithOptionalBlockStmt<N>, arg: Node): NodePathTrace<N, BlockStmt>? =
        n.body
            .mapNotNull { body -> body.takeIf { it === arg }}
            .map {
                NodePathTrace<N, BlockStmt> { (it as NodeWithOptionalBlockStmt<*>).body.get() }
            }
            .getOrNull()

    private fun <N : Node> visit(n: NodeWithOptionalScope<N>, arg: Node): NodePathTrace<N, Expression>? =
        n.scope
            .mapNotNull { scope -> scope.takeIf { it === arg } }
            .map {
                NodePathTrace<N, Expression> { (it as NodeWithOptionalScope<*>).scope.get() }
            }
            .getOrNull()

    private fun <N : Node> visit(n: NodeWithParameters<N>, arg: Node): NodePathTrace<N, Parameter>? =
        n.parameters
            .indexOf(arg)
            .takeIf { it >= 0 }
            ?.let { idx ->
                NodePathTrace<N, Parameter> { (it as NodeWithParameters<*>).getParameter(idx) }
            }

    private fun <N : Node> visit(n: NodeWithScope<N>, arg: Node): NodePathTrace<N, Expression>? =
        n.scope
            .takeIf { it === arg }
            ?.let {
                NodePathTrace { (it as NodeWithScope<*>).scope }
            }

    private fun <N : Node> visit(n: NodeWithSimpleName<N>, arg: Node): NodePathTrace<N, SimpleName>? =
        n.name
            .takeIf { it === arg }
            ?.let {
                NodePathTrace { (it as NodeWithSimpleName<*>).name }
            }

    private fun <N : Node> visit(n: NodeWithStatements<N>, arg: Node): NodePathTrace<N, Statement>? =
        n.statements
            .indexOf(arg)
            .takeIf { it >= 0 }
            ?.let { idx ->
                NodePathTrace<N, Statement> { (it as NodeWithStatements<*>).getStatement(idx) }
            }

    private fun <N : Node> visit(n: NodeWithThrownExceptions<N>, arg: Node): NodePathTrace<N, ReferenceType>? =
        n.thrownExceptions
            .indexOf(arg)
            .takeIf { it >= 0 }
            ?.let { idx ->
                NodePathTrace<N, ReferenceType> { (it as NodeWithThrownExceptions<*>).getThrownException(idx) }
            }

    private fun <N : Node, T : Type> visit(n: NodeWithType<N, T>, arg: Node): NodePathTrace<N, T>? =
        n.type
            .takeIf { it === arg }
            ?.let {
                NodePathTrace { @Suppress("UNCHECKED_CAST") (it as NodeWithType<N, T>).type }
            }

    private fun <N : Node> visit(n: NodeWithTypeArguments<N>, arg: Node): NodePathTrace<N, Type>? =
        n.typeArguments
            .mapNotNull { scope -> scope.indexOf(arg).takeIf { it >= 0 } }
            .map { idx ->
                NodePathTrace<N, Type> {
                    (it as NodeWithTypeArguments<*>).typeArguments.map { typeArgs -> typeArgs[idx] }.get()
                }
            }
            .getOrNull()

    private fun <N : Node> visit(n: NodeWithTypeParameters<N>, arg: Node): NodePathTrace<N, TypeParameter>? =
        n.typeParameters
            .indexOf(arg)
            .takeIf { it >= 0 }
            ?.let { idx ->
                NodePathTrace<N, TypeParameter> { (it as NodeWithTypeParameters<*>).getTypeParameter(idx) }
            }

    private fun <N : Node> visit(n: NodeWithVariables<N>, arg: Node): NodePathTrace<N, VariableDeclarator>? =
        n.variables
            .indexOf(arg)
            .takeIf { it >= 0 }
            ?.let { idx ->
                NodePathTrace<N, VariableDeclarator> { (it as NodeWithVariables<*>).getVariable(idx) }
            }

    private fun <N : Node> visit(n: SwitchNode, arg: Node): NodePathTrace<N, Node>? =
        n.entries
            .indexOf(arg)
            .takeIf { it >= 0 }
            ?.let { idx ->
                NodePathTrace { (it as SwitchNode).entries[idx] }
            }
            ?: n.selector
                .takeIf { it === arg }
                ?.let {
                    NodePathTrace { (it as SwitchNode).selector }
                }

    //</editor-fold>
    //<editor-fold desc="Abstract Class Mappers">

    private fun <N : BodyDeclaration<N>> visit(n: BodyDeclaration<N>, arg: Node): NodePathTrace<N, Node>? =
        visit(n as NodeWithAnnotations<N>, arg)

    private fun <N : CallableDeclaration<N>> visit(n: CallableDeclaration<N>, arg: Node): NodePathTrace<N, Node>? {
        return visit(n as BodyDeclaration<N>, arg)
            ?: visit(n as NodeWithSimpleName<N>, arg)
            ?: visit(n as NodeWithParameters<N>, arg)
            ?: visit(n as NodeWithThrownExceptions<N>, arg)
            ?: visit(n as NodeWithTypeParameters<N>, arg)
            ?: visit(n as NodeWithJavadoc<N>, arg)
            ?: n.modifiers.singleOrNull { it === arg }
                ?.let { m ->
                    NodePathTrace { n ->
                        n.modifiers.single { it.keyword == m.keyword }
                    }
                }
    }

    private fun <N : TypeDeclaration<N>> visit(n: TypeDeclaration<N>, arg: Node): NodePathTrace<N, Node>? {
        return visit(n as BodyDeclaration<N>, arg)
            ?: visit(n as NodeWithJavadoc<N>, arg)
            ?: visit(n as NodeWithSimpleName<N>, arg)
            ?: visit(n as NodeWithMembers<N>, arg)
            ?: n.modifiers.singleOrNull { it === arg }
                ?.let { m ->
                    NodePathTrace { n ->
                        n.modifiers.single { it.keyword == m.keyword }
                    }
                }
    }

    //</editor-fold>

    override fun visit(n: CompilationUnit, arg: Node): CompilationUnitPathTrace<out Node> {
        check(n.primaryTypeName.isPresent)

        return when {
            arg == n -> CompilationUnitPathTrace.identity()
            n.packageDeclaration.map { arg === it }.getOrDefault(false) ->
                CompilationUnitPathTrace { it.packageDeclaration.get() }

            n.imports.any { arg === it } -> {
                val idx = n.imports.indexOf(arg)
                CompilationUnitPathTrace { it.getImport(idx) }
            }

            n.types.any { arg === it } -> {
                val idx = n.types.indexOf(arg)
                CompilationUnitPathTrace { it.getType(idx) }
            }

            n.module.map { arg === it }.getOrDefault(false) ->
                CompilationUnitPathTrace { it.module.get() }

            else -> unreachable()
        }
    }

    override fun visit(n: ClassOrInterfaceDeclaration, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<ClassOrInterfaceDeclaration>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as TypeDeclaration<ClassOrInterfaceDeclaration>, arg)
                ?: visit(n as NodeWithTypeParameters<ClassOrInterfaceDeclaration>, arg)
                ?: visit(n as NodeWithExtends<ClassOrInterfaceDeclaration>, arg)
                ?: visit(n as NodeWithImplements<ClassOrInterfaceDeclaration>, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: EnumDeclaration, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<EnumDeclaration>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as TypeDeclaration<EnumDeclaration>, arg)
                ?: visit(n as NodeWithImplements<EnumDeclaration>, arg)
                ?: n.entries
                    .indexOf(arg)
                    .takeIf { it >= 0 }
                    ?.let { idx ->
                        NodePathTrace { it.getEntry(idx) }
                    }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: EnumConstantDeclaration, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<EnumConstantDeclaration>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as BodyDeclaration<EnumConstantDeclaration>, arg)
                ?: visit(n as NodeWithJavadoc<EnumConstantDeclaration>, arg)
                ?: visit(n as NodeWithSimpleName<EnumConstantDeclaration>, arg)
                ?: visit(n as NodeWithArguments<EnumConstantDeclaration>, arg)
                ?: n.classBody
                    .indexOf(arg)
                    .takeIf { it >= 0 }
                    ?.let { idx ->
                        NodePathTrace { it.classBody[idx] }
                    }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: AnnotationDeclaration, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<AnnotationDeclaration>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as TypeDeclaration<AnnotationDeclaration>, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: FieldDeclaration, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<FieldDeclaration>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as BodyDeclaration<FieldDeclaration>, arg)
                ?: visit(n as NodeWithJavadoc<FieldDeclaration>, arg)
                ?: visit(n as NodeWithVariables<FieldDeclaration>, arg)
                ?: n.modifiers.singleOrNull { it === arg }
                    ?.let { m ->
                        NodePathTrace { n -> n.modifiers.single { it.keyword == m.keyword } }
                    }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: VariableDeclarator, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<VariableDeclarator>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithType<VariableDeclarator, Type>, arg)
                ?: visit(n as NodeWithSimpleName<VariableDeclarator>, arg)
                ?: n.initializer
                    .mapNotNull { expr -> expr.takeIf { it === arg } }
                    .map {
                        NodePathTrace<VariableDeclarator, _> { it.initializer.get() }
                    }
                    .getOrNull()
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: ConstructorDeclaration, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<ConstructorDeclaration>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as CallableDeclaration<ConstructorDeclaration>, arg)
                ?: visit(n as NodeWithBlockStmt<ConstructorDeclaration>, arg)
                ?: visit(n as NodeWithJavadoc<ConstructorDeclaration>, arg)
                ?: visit(n as NodeWithSimpleName<ConstructorDeclaration>, arg)
                ?: visit(n as NodeWithParameters<ConstructorDeclaration>, arg)
                ?: visit(n as NodeWithThrownExceptions<ConstructorDeclaration>, arg)
                ?: visit(n as NodeWithTypeParameters<ConstructorDeclaration>, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: MethodDeclaration, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<MethodDeclaration>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as CallableDeclaration<MethodDeclaration>, arg)
                ?: visit(n as NodeWithType<MethodDeclaration, Type>, arg)
                ?: visit(n as NodeWithOptionalBlockStmt<MethodDeclaration>, arg)
                ?: visit(n as NodeWithJavadoc<MethodDeclaration>, arg)
                ?: visit(n as NodeWithSimpleName<MethodDeclaration>, arg)
                ?: visit(n as NodeWithParameters<MethodDeclaration>, arg)
                ?: visit(n as NodeWithThrownExceptions<MethodDeclaration>, arg)
                ?: visit(n as NodeWithTypeParameters<MethodDeclaration>, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: Parameter, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<Parameter>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithType<Parameter, Type>, arg)
                ?: visit(n as NodeWithAnnotations<Parameter>, arg)
                ?: visit(n as NodeWithSimpleName<Parameter>, arg)
                ?: n.varArgsAnnotations
                    .indexOf(arg)
                    .takeIf { it >= 0 }
                    ?.let { idx ->
                        NodePathTrace { it.varArgsAnnotations[idx] }
                    }
                ?: n.modifiers
                    .indexOf(arg)
                    .takeIf { it >= 0 }
                    ?.let { idx ->
                        NodePathTrace { it.modifiers[idx] }
                    }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: InitializerDeclaration, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<InitializerDeclaration>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as BodyDeclaration<InitializerDeclaration>, arg)
                ?: visit(n as NodeWithJavadoc<InitializerDeclaration>, arg)
                ?: visit(n as NodeWithBlockStmt<InitializerDeclaration>, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: ArrayCreationLevel, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<ArrayCreationLevel>

        val mapper: NodePathTrace<ArrayCreationLevel, Node> = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithAnnotations<ArrayCreationLevel>, arg)
                ?: n.dimension
                    .mapNotNull { expr -> expr.takeIf { it === arg } }
                    .map {
                        NodePathTrace<ArrayCreationLevel, _> { it.dimension.get() }
                    }
                    .getOrNull()
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: ArrayAccessExpr, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<ArrayAccessExpr>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            n.name
                .takeIf { it === arg }
                ?.let {
                    NodePathTrace<ArrayAccessExpr, _> { it.name }
                }
                ?: n.index
                    .takeIf { it === arg }
                    ?.let {
                        NodePathTrace<ArrayAccessExpr, _> { it.index }
                    }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: ArrayCreationExpr, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<ArrayCreationExpr>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            n.levels
                .indexOf(arg)
                .takeIf { it >= 0 }
                ?.let { idx ->
                    NodePathTrace<ArrayCreationExpr, _> { it.levels[idx] }
                }
                ?: n.elementType
                    .takeIf { it === arg }
                    ?.let {
                        NodePathTrace<ArrayCreationExpr, _> { it.elementType }
                    }
                ?: n.initializer
                    .mapNotNull { expr -> expr.takeIf { it === arg } }
                    .map {
                        NodePathTrace<ArrayCreationExpr, _> { it.initializer.get() }
                    }
                    .getOrNull()
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: ArrayInitializerExpr, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<ArrayInitializerExpr>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            n.values
                .indexOf(arg)
                .takeIf { it >= 0 }
                ?.let { idx ->
                    NodePathTrace<ArrayInitializerExpr, _> { it.values[idx] }
                }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: AssignExpr, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<AssignExpr>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            n.target
                .takeIf { it === arg }
                ?.let { NodePathTrace<AssignExpr, _> { it.target } }
                ?: n.value
                    .takeIf { it === arg }
                    ?.let { NodePathTrace<AssignExpr, _> { it.value } }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: CastExpr, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<CastExpr>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithType<CastExpr, Type>, arg)
                ?: visit(n as NodeWithExpression<CastExpr>, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: ConditionalExpr, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<ConditionalExpr>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithCondition<ConditionalExpr>, arg)
                ?: n.thenExpr
                    .takeIf { it === arg }
                    ?.let { NodePathTrace { it.thenExpr } }
                ?: n.elseExpr
                    .takeIf { it === arg }
                    ?.let { NodePathTrace { it.elseExpr } }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: BinaryExpr, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<BinaryExpr>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            n.left
                .takeIf { it === arg }
                ?.let { NodePathTrace<BinaryExpr, _> { it.left } }
                ?: n.right
                    .takeIf { it === arg }
                    ?.let { NodePathTrace<BinaryExpr, _> { it.right } }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: EnclosedExpr, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<EnclosedExpr>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            n.inner
                .takeIf { it === arg }
                ?.let { NodePathTrace<EnclosedExpr, _> { it.inner } }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: FieldAccessExpr, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<FieldAccessExpr>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithSimpleName<FieldAccessExpr>, arg)
                ?: visit(n as NodeWithTypeArguments<FieldAccessExpr>, arg)
                ?: visit(n as NodeWithScope<FieldAccessExpr>, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: InstanceOfExpr, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<InstanceOfExpr>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithType<InstanceOfExpr, ReferenceType>, arg)
                ?: visit(n as NodeWithExpression<InstanceOfExpr>, arg)
                ?: n.pattern
                    .mapNotNull { expr -> expr.takeIf { it === arg } }
                    .map {
                        NodePathTrace<InstanceOfExpr, _> { it.pattern.get() }
                    }
                    .getOrNull()
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: MethodCallExpr, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<MethodCallExpr>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithTypeArguments<MethodCallExpr>, arg)
                ?: visit(n as NodeWithArguments<MethodCallExpr>, arg)
                ?: visit(n as NodeWithSimpleName<MethodCallExpr>, arg)
                ?: visit(n as NodeWithOptionalScope<MethodCallExpr>, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: NameExpr, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<NameExpr>

        val mapper: NodePathTrace<NameExpr, Node> = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithSimpleName<NameExpr>, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: ObjectCreationExpr, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<ObjectCreationExpr>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithTypeArguments<ObjectCreationExpr>, arg)
                ?: visit(n as NodeWithType<ObjectCreationExpr, ClassOrInterfaceType>, arg)
                ?: visit(n as NodeWithArguments<ObjectCreationExpr>, arg)
                ?: visit(n as NodeWithOptionalScope<ObjectCreationExpr>, arg)
                ?: n.anonymousClassBody
                    .mapNotNull { bodyDecls -> bodyDecls.indexOf(arg).takeIf { it >= 0 } }
                    .map { idx ->
                        NodePathTrace<ObjectCreationExpr, _> {
                            it.anonymousClassBody.get()[idx]
                        }
                    }
                    .getOrNull()
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: UnaryExpr, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<UnaryExpr>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithExpression<UnaryExpr>, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: VariableDeclarationExpr, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<VariableDeclarationExpr>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithAnnotations<VariableDeclarationExpr>, arg)
                ?: visit(n as NodeWithVariables<VariableDeclarationExpr>, arg)
                ?: n.modifiers
                    .singleOrNull { it === arg }
                    ?.let { m ->
                        NodePathTrace<VariableDeclarationExpr, Node> { n ->
                            n.modifiers.single { it.keyword == m.keyword }
                        }
                    }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: ExplicitConstructorInvocationStmt, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<ExplicitConstructorInvocationStmt>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithTypeArguments<ExplicitConstructorInvocationStmt>, arg)
                ?: visit(n as NodeWithArguments<ExplicitConstructorInvocationStmt>, arg)
                ?: n.expression
                    .mapNotNull { expr -> expr.takeIf { it === arg } }
                    .map {
                        NodePathTrace<ExplicitConstructorInvocationStmt, _> { it.expression.get() }
                    }
                    .getOrNull()
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: LocalClassDeclarationStmt, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping =
            visit(n.parentNode.get(), n) as CompilationUnitPathTrace<LocalClassDeclarationStmt>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            n.classDeclaration
                .takeIf { it === arg }
                ?.let {
                    NodePathTrace<LocalClassDeclarationStmt, Node> {
                        it.classDeclaration
                    }
                }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: BlockStmt, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<BlockStmt>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithStatements<BlockStmt>, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: LabeledStmt, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<LabeledStmt>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            n.label
                .takeIf { it === arg }
                ?.let { NodePathTrace<LabeledStmt, _> { it.label } }
                ?: n.statement
                    .takeIf { it === arg }
                    ?.let { NodePathTrace<LabeledStmt, _> { it.statement } }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: ExpressionStmt, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<ExpressionStmt>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            n.expression
                .takeIf { it === arg }
                ?.let {
                    NodePathTrace<ExpressionStmt, Node> { it.expression }
                }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: SwitchStmt, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<SwitchStmt>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit<SwitchStmt>(n as SwitchNode, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: SwitchEntry, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<SwitchEntry>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithStatements<SwitchEntry>, arg)
                ?: n.labels
                    .indexOf(arg)
                    .takeIf { it >= 0 }
                    ?.let { idx ->
                        NodePathTrace<SwitchEntry, _> { it.labels[idx] }
                    }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: ReturnStmt, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<ReturnStmt>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            n.expression
                .mapNotNull { expr -> expr.takeIf { it === arg }}
                .map {
                    NodePathTrace<ReturnStmt, Node> { it.expression.get() }
                }
                .getOrNull()
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: IfStmt, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<IfStmt>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithCondition<IfStmt>, arg)
                ?: n.thenStmt
                    .takeIf { it === arg }
                    ?.let { NodePathTrace<IfStmt, Node> { it.thenStmt } }
                ?: n.elseStmt
                    .mapNotNull { stmt -> stmt.takeIf { it === arg } }
                    .map {
                        NodePathTrace<IfStmt, Node> { it.elseStmt.get() }
                    }
                    .getOrNull()
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: WhileStmt, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<WhileStmt>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithBody<WhileStmt>, arg)
                ?: visit(n as NodeWithCondition<WhileStmt>, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: DoStmt, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<DoStmt>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithBody<DoStmt>, arg)
                ?: visit(n as NodeWithCondition<DoStmt>, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: ForEachStmt, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<ForEachStmt>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithBody<ForEachStmt>, arg)
                ?: n.variable
                    .takeIf { it === arg }
                    ?.let {
                        NodePathTrace<ForEachStmt, _> { it.variable }
                    }
                ?: n.iterable
                    .takeIf { it === arg }
                    ?.let {
                        NodePathTrace<ForEachStmt, _> { it.iterable }
                    }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: ForStmt, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<ForStmt>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithBody<ForStmt>, arg)
                ?: n.initialization
                    .indexOf(arg)
                    .takeIf { it >= 0 }
                    ?.let { idx ->
                        NodePathTrace<ForStmt, _> { it.initialization[idx] }
                    }
                ?: n.compare
                    .mapNotNull { expr -> expr.takeIf { it === arg } }
                    .map {
                        NodePathTrace<ForStmt, _> { it.compare.get() }
                    }
                    .getOrNull()
                ?: n.update
                    .indexOf(arg)
                    .takeIf { it >= 0 }
                    ?.let { idx ->
                        NodePathTrace<ForStmt, _> { it.update[idx] }
                    }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: SynchronizedStmt, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<SynchronizedStmt>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithBlockStmt<SynchronizedStmt>, arg)
                ?: visit(n as NodeWithExpression<SynchronizedStmt>, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: ThrowStmt, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<ThrowStmt>

        val mapper: NodePathTrace<ThrowStmt, Node> = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithExpression<ThrowStmt>, arg)
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)

    }

    override fun visit(n: TryStmt, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<TryStmt>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            n.resources
                .indexOf(arg)
                .takeIf { it >= 0 }
                ?.let { idx ->
                    NodePathTrace<TryStmt, _> { it.resources[idx] }
                }
                ?: n.tryBlock
                    .takeIf { it === arg }
                    ?.let {
                        NodePathTrace<TryStmt, _> { it.tryBlock }
                    }
                ?: n.catchClauses
                    .indexOf(arg)
                    .takeIf { it >= 0 }
                    ?.let { idx ->
                        NodePathTrace<TryStmt, _> { it.catchClauses[idx] }
                    }
                ?: n.finallyBlock
                    .mapNotNull { block -> block.takeIf { it === arg } }
                    .map {
                        NodePathTrace<TryStmt, _> { it.finallyBlock.get() }
                    }
                    .getOrNull()
            ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    override fun visit(n: CatchClause, arg: Node): CompilationUnitPathTrace<out Node> {
        throwIfNodeIsNotChildOrSelf(n, arg)

        @Suppress("UNCHECKED_CAST") val parentMapping = visit(n.parentNode.get(), n) as CompilationUnitPathTrace<CatchClause>

        val mapper = if (arg === n) {
            NodePathTrace.identity()
        } else {
            visit(n as NodeWithBlockStmt<CatchClause>, arg)
                ?: n.parameter
                    .takeIf { it === arg }
                    ?.let { NodePathTrace<CatchClause, Node> { it.parameter } }
                ?: unreachable()
        }

        return parentMapping.andThen(mapper)
    }

    companion object {

        /**
         * Convenience method to generate a [CompilationUnitPathTrace] for node [n].
         */
        @Suppress("UNCHECKED_CAST")
        fun <N : Node> forNode(n: N): CompilationUnitPathTrace<N> = ASTPathGenerator().visit(n, n) as CompilationUnitPathTrace<N>
    }
}