package com.derppening.researchprojecttoolkit.visitor

import com.github.javaparser.ast.*
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.comments.BlockComment
import com.github.javaparser.ast.comments.JavadocComment
import com.github.javaparser.ast.comments.LineComment
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.modules.*
import com.github.javaparser.ast.stmt.*
import com.github.javaparser.ast.type.*
import com.github.javaparser.ast.visitor.GenericVisitor

/**
 * A [GenericVisitor] with a defaulted method which dispatches a generic [Node] to its specific [visit] implementation.
 */
interface GenericVisitorWithNodeDispatch<R, A> : GenericVisitor<R, A> {

    /**
     * Visits a [Node] of an unknown concrete type.
     */
    fun visit(n: Node, arg: A): R = when (n) {
        is CompilationUnit -> visit(n, arg)
        is PackageDeclaration -> visit(n, arg)
        is TypeParameter -> visit(n, arg)
        is LineComment -> visit(n, arg)
        is BlockComment -> visit(n, arg)
        is ClassOrInterfaceDeclaration -> visit(n, arg)
        is RecordDeclaration -> visit(n, arg)
        is CompactConstructorDeclaration -> visit(n, arg)
        is EnumDeclaration -> visit(n, arg)
        is EnumConstantDeclaration -> visit(n, arg)
        is AnnotationDeclaration -> visit(n, arg)
        is AnnotationMemberDeclaration -> visit(n, arg)
        is FieldDeclaration -> visit(n, arg)
        is VariableDeclarator -> visit(n, arg)
        is ConstructorDeclaration -> visit(n, arg)
        is MethodDeclaration -> visit(n, arg)
        is Parameter -> visit(n, arg)
        is InitializerDeclaration -> visit(n, arg)
        is JavadocComment -> visit(n, arg)
        is ClassOrInterfaceType -> visit(n, arg)
        is PrimitiveType -> visit(n, arg)
        is ArrayType -> visit(n, arg)
        is ArrayCreationLevel -> visit(n, arg)
        is IntersectionType -> visit(n, arg)
        is UnionType -> visit(n, arg)
        is VoidType -> visit(n, arg)
        is WildcardType -> visit(n, arg)
        is UnknownType -> visit(n, arg)
        is ArrayAccessExpr -> visit(n, arg)
        is ArrayCreationExpr -> visit(n, arg)
        is ArrayInitializerExpr -> visit(n, arg)
        is AssignExpr -> visit(n, arg)
        is BinaryExpr -> visit(n, arg)
        is CastExpr -> visit(n, arg)
        is ClassExpr -> visit(n, arg)
        is ConditionalExpr -> visit(n, arg)
        is EnclosedExpr -> visit(n, arg)
        is FieldAccessExpr -> visit(n, arg)
        is InstanceOfExpr -> visit(n, arg)
        is StringLiteralExpr -> visit(n, arg)
        is IntegerLiteralExpr -> visit(n, arg)
        is LongLiteralExpr -> visit(n, arg)
        is CharLiteralExpr -> visit(n, arg)
        is DoubleLiteralExpr -> visit(n, arg)
        is BooleanLiteralExpr -> visit(n, arg)
        is NullLiteralExpr -> visit(n, arg)
        is MethodCallExpr -> visit(n, arg)
        is NameExpr -> visit(n, arg)
        is ObjectCreationExpr -> visit(n, arg)
        is ThisExpr -> visit(n, arg)
        is SuperExpr -> visit(n, arg)
        is UnaryExpr -> visit(n, arg)
        is VariableDeclarationExpr -> visit(n, arg)
        is MarkerAnnotationExpr -> visit(n, arg)
        is SingleMemberAnnotationExpr -> visit(n, arg)
        is NormalAnnotationExpr -> visit(n, arg)
        is MemberValuePair -> visit(n, arg)
        is ExplicitConstructorInvocationStmt -> visit(n, arg)
        is LocalClassDeclarationStmt -> visit(n, arg)
        is LocalRecordDeclarationStmt -> visit(n, arg)
        is AssertStmt -> visit(n, arg)
        is BlockStmt -> visit(n, arg)
        is LabeledStmt -> visit(n, arg)
        is EmptyStmt -> visit(n, arg)
        is ExpressionStmt -> visit(n, arg)
        is SwitchStmt -> visit(n, arg)
        is SwitchEntry -> visit(n, arg)
        is BreakStmt -> visit(n, arg)
        is ReturnStmt -> visit(n, arg)
        is IfStmt -> visit(n, arg)
        is WhileStmt -> visit(n, arg)
        is ContinueStmt -> visit(n, arg)
        is DoStmt -> visit(n, arg)
        is ForEachStmt -> visit(n, arg)
        is ForStmt -> visit(n, arg)
        is ThrowStmt -> visit(n, arg)
        is SynchronizedStmt -> visit(n, arg)
        is TryStmt -> visit(n, arg)
        is CatchClause -> visit(n, arg)
        is LambdaExpr -> visit(n, arg)
        is MethodReferenceExpr -> visit(n, arg)
        is TypeExpr -> visit(n, arg)
        is Name -> visit(n, arg)
        is SimpleName -> visit(n, arg)
        is ImportDeclaration -> visit(n, arg)
        is ModuleDeclaration -> visit(n, arg)
        is ModuleRequiresDirective -> visit(n, arg)
        is ModuleExportsDirective -> visit(n, arg)
        is ModuleProvidesDirective -> visit(n, arg)
        is ModuleUsesDirective -> visit(n, arg)
        is ModuleOpensDirective -> visit(n, arg)
        is UnparsableStmt -> visit(n, arg)
        is ReceiverParameter -> visit(n, arg)
        is VarType -> visit(n, arg)
        is Modifier -> visit(n, arg)
        is SwitchExpr -> visit(n, arg)
        is YieldStmt -> visit(n, arg)
        is TextBlockLiteralExpr -> visit(n, arg)
        is PatternExpr -> visit(n, arg)
        else -> TODO()
    }
}