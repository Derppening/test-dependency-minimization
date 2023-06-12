package com.derppening.researchprojecttoolkit.visitor

import com.derppening.researchprojecttoolkit.model.ReferenceTypeLikeNode
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.*
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.*
import com.github.javaparser.ast.type.*
import com.github.javaparser.ast.visitor.CloneVisitor
import com.github.javaparser.ast.visitor.Visitable
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration
import com.github.javaparser.resolution.types.ResolvedPrimitiveType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedType
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import kotlin.jvm.optionals.getOrNull

/**
 * A [CloneVisitor] which selectively filters/transforms nodes.
 *
 * @param generateDummyAssertions Whether to generate assertion statements for method dummies.
 */
class TransformingCloneVisitor(
    private val reducerContext: ReducerContext,
    private val generateDummyAssertions: Boolean
) : CloneVisitor() {

    private object GeneratedAssertionStmtTag : DataKey<Unit>()

    /**
     * @see CloneVisitor.copyData
     */
    private fun copyData(source: Node, destination: Node) {
        source.dataKeys.forEach { dataKey ->
            try {
                @Suppress("UNCHECKED_CAST")
                destination.setData(dataKey as DataKey<Any?>, source.getData(dataKey))
            } catch (e: IllegalStateException) {
                destination.setData(dataKey, null)
            }
        }
    }

    private fun isThrowsAssertionStmt(stmt: Statement?): Boolean =
        (stmt as? ThrowStmt?)?.containsData(GeneratedAssertionStmtTag) == true

    private fun generateAssertionStmtImpl(msg: String): ThrowStmt? {
        return if (generateDummyAssertions) {
            // Simple Name of AssertionError is sufficient, because it is in the `java.lang` package
            ThrowStmt(
                ObjectCreationExpr(
                    null,
                    StaticJavaParser.parseClassOrInterfaceType(AssertionError::class.simpleName!!),
                    NodeList(StringLiteralExpr(msg))
                )
            ).apply {
                setData(GeneratedAssertionStmtTag, Unit)
            }
        } else null
    }

    private fun generateAssertionStmtContextless(n: Node): ThrowStmt? =
        generateAssertionStmtImpl("This ${n::class.simpleName} should not be reached! Original Node Context:\n${n.astToString(showChildren = false)}")

    /**
     * Generates an assertion statement for a given [CallableDeclaration].
     */
    private fun generateAssertionStmt(n: CallableDeclaration<*>): ThrowStmt? =
        generateAssertionStmtImpl("This method should not be reached! Signature: ${n.signature.asString()}")

    /**
     * Generates an assertion statement for a given [InitializerDeclaration].
     */
    private fun generateAssertionStmt(n: InitializerDeclaration): ThrowStmt? {
        val typeName = n.findAncestor(TypeDeclaration::class.java).flatMap { it.fullyQualifiedName }.getOrNull()
        val methodName = if (n.isStatic) "<clinit>" else "<init>"

        return generateAssertionStmtImpl("This method should not be reached! Signature: $typeName.$methodName")
    }

    /**
     * Converts the [ResolvedType] into an AST [Type].
     */
    private fun mapResolvedTypeToType(type: ResolvedType): Type {
        return when {
            type.isPrimitive -> {
                when (checkNotNull(type.asPrimitive())) {
                    ResolvedPrimitiveType.BYTE -> PrimitiveType.byteType()
                    ResolvedPrimitiveType.SHORT -> PrimitiveType.shortType()
                    ResolvedPrimitiveType.CHAR -> PrimitiveType.charType()
                    ResolvedPrimitiveType.INT -> PrimitiveType.intType()
                    ResolvedPrimitiveType.LONG -> PrimitiveType.longType()
                    ResolvedPrimitiveType.BOOLEAN -> PrimitiveType.booleanType()
                    ResolvedPrimitiveType.FLOAT -> PrimitiveType.floatType()
                    ResolvedPrimitiveType.DOUBLE -> PrimitiveType.doubleType()
                }
            }
            type.isArray -> ArrayType(mapResolvedTypeToType(type.asArrayType().componentType))
            type.isReferenceType -> StaticJavaParser.parseClassOrInterfaceType(type.asReferenceType().qualifiedName)
            type.isVoid -> VoidType()
            else -> unreachable("Unknown type ${type::class.simpleName}")
        }
    }

    /**
     * Generates a dummy expression with the given [type].
     */
    private fun generateDummyExprOfType(type: ResolvedType): Expression? =
        generateDummyExprOfType(mapResolvedTypeToType(type))

    /**
     * Generates a dummy expression with the given [type].
     */
    private fun generateDummyExprOfType(type: Type): Expression? {
        return when (type) {
            is PrimitiveType -> {
                when (type) {
                    PrimitiveType.byteType() -> {
                        // (byte) 0
                        CastExpr(PrimitiveType.byteType(), IntegerLiteralExpr())
                    }
                    PrimitiveType.shortType() -> {
                        // (short) 0
                        CastExpr(PrimitiveType.shortType(), IntegerLiteralExpr())
                    }
                    PrimitiveType.charType() -> {
                        // '\u0000'
                        CharLiteralExpr('\u0000')
                    }
                    PrimitiveType.intType() -> {
                        // 0
                        IntegerLiteralExpr()
                    }
                    PrimitiveType.longType() -> {
                        // 0L
                        LongLiteralExpr()
                    }
                    PrimitiveType.booleanType() -> {
                        // false
                        BooleanLiteralExpr()
                    }
                    PrimitiveType.floatType() -> {
                        // (float) 0.0
                        DoubleLiteralExpr("0.0f")
                    }
                    PrimitiveType.doubleType() -> {
                        // 0.0
                        DoubleLiteralExpr()
                    }
                    else -> error("Unknown primitive type $type")
                }
            }
            is ArrayType,
            is ClassOrInterfaceType -> {
                // null
                NullLiteralExpr()
            }
            is VoidType -> null
            else -> unreachable("Unknown return type ${type::class.simpleName}")
        }
    }

    /**
     * Generates a dummy assignment expression for a class field.
     */
    private fun generateDummyAssignExpr(assignExpr: AssignExpr): AssignExpr {
        // <target> = <dummy>;
        return AssignExpr(
            assignExpr.target,
            generateDummyExprOfType(assignExpr.initFieldVarData.type),
            AssignExpr.Operator.ASSIGN
        )
    }

    /**
     * Generates a dummy assignment expression for a class field.
     */
    private fun generateDummyAssignExpr(varDecl: VariableDeclarator): AssignExpr {
        check(varDecl.isFieldVar)

        // this.<target> = <dummy>;
        return AssignExpr(
            FieldAccessExpr(ThisExpr(), varDecl.nameAsString),
            generateDummyExprOfType(varDecl.type),
            AssignExpr.Operator.ASSIGN
        )
    }

    /**
     * Generates a set of dummy [ExpressionStmt] which initializes all final fields in a class.
     */
    private fun generateDummyAssignExprsForInit(typeDecl: TypeDeclaration<*>): List<ExpressionStmt> {
        check(typeDecl.isClassOrInterfaceDeclaration || typeDecl.isEnumDeclaration)

        return typeDecl
            .fields
            .filter { it.isFinal && !it.isStatic }
            .flatMap { it.variables }
            .filterNot { it.initializer.isPresent }
            .map { ExpressionStmt(generateDummyAssignExpr(it)) }
    }

    /**
     * Generates a set of dummy [ExpressionStmt] which initializes all final fields in a class.
     */
    private fun generateDummyAssignExprsForInit(blockStmt: BlockStmt): List<ExpressionStmt> {
        return blockStmt
            .findAll<AssignExpr> { it.containsInitFieldVarData }
            .filterNot { it.initFieldVarData.isUnusedForRemovalData }
            .filter { it.initFieldVarData.parentNode.map { (it as FieldDeclaration).isFinal }.get() }
            .distinctBy { it.initFieldVarData.nameAsString }
            .map { ExpressionStmt(generateDummyAssignExpr(it)) }
    }

    /**
     * Generates a default constructor for the [typeDecl].
     *
     * Requires the type declaration to have the superclass field set.
     */
    private fun generateDummyDefaultCtorBody(typeDecl: TypeDeclaration<*>): BlockStmt? {
        if (generateDummyAssertions) {
            return null
        }

        val constructors = when (typeDecl) {
            is AnnotationDeclaration -> {
                // Annotations do not have alternate constructors
                null
            }

            is ClassOrInterfaceDeclaration -> {
                if (typeDecl.isInterface) {
                    null
                } else {
                    typeDecl.constructors
                }
            }

            is EnumDeclaration -> null
            is RecordDeclaration -> {
                // Records do not have alternate constructors
                null
            }

            else -> unreachable("Unhandled type declaration of type `${typeDecl::class.simpleName}`")
        } ?: return null

        if (constructors.any { !it.isUnusedForRemovalData }) {
            return null
        }

        val superclassCtors = reducerContext.mapNodeInLoadedSet(typeDecl)
            .let { reducerContext.resolveDeclaration<ResolvedReferenceTypeDeclaration>(it) }
            .ancestors
            .map { it.toResolvedTypeDeclaration() }
            .single { !it.isInterface }
            .let { reducerContext.symbolSolverCache.getConstructors(it) }
            .toList()
        val superclassCtorsAst = superclassCtors
            .map { it.toTypedAstOrNull<ConstructorDeclaration>(reducerContext) }

        // No AST is associated with a constructor - Assume it is from a library type
        // Use the resolved types to generate a constructor
        val dummyArgs = if (superclassCtorsAst.any { it == null }) {
            if (superclassCtors.any { it.numberOfParams == 0 }) {
                emptyList()
            } else {
                val selectedCtor = superclassCtors.minBy { it.numberOfParams }
                selectedCtor.parameters.map { checkNotNull(generateDummyExprOfType(it.type)) }
            }
        } else {
            val superclassKeptCtorAsts = superclassCtorsAst.map { checkNotNull(it) }
                .filterNot { it.isUnusedForRemovalData }

            if (superclassKeptCtorAsts.isEmpty()) {
                emptyList()
            } else {
                val selectedCtor = superclassKeptCtorAsts.minBy { it.parameters.size }
                selectedCtor.parameters.map { checkNotNull(generateDummyExprOfType(it.type)) }
            }
        }

        val explicitStmt = dummyArgs.takeIf { it.isNotEmpty() }
            ?.let { ExplicitConstructorInvocationStmt(false, null, NodeList.nodeList(dummyArgs)) }
        val bodyStmts = listOfNotNull(
            explicitStmt,
            *generateDummyAssignExprsForInit(typeDecl).toTypedArray(),
            generateAssertionStmtImpl("This generated default constructor should not be reached! Signature: ${typeDecl.nameAsString}()")
        ).let { NodeList.nodeList(it) }

        return BlockStmt(bodyStmts)
    }

    override fun visit(n: CompilationUnit, arg: Any?): Visitable {
        val r = super.visit(n, arg) as CompilationUnit

        r.asSequence().forEach {
            it.clearDataAfterClone()
        }
        r.appliedPasses.clear()

        return r
    }

    override fun visit(n: AnnotationDeclaration, arg: Any?): Visitable? {
        return if (n.isUnusedForRemovalData) null else super.visit(n, arg)
    }

    override fun visit(n: ClassOrInterfaceDeclaration, arg: Any?): Visitable? {
        if (n.isUnusedForRemovalData) return null

        val r = super.visit(n, arg) as ClassOrInterfaceDeclaration

        if (n.isUnusedForSupertypeRemovalData) {
            r.extendedTypes = NodeList.nodeList()
            r.implementedTypes = NodeList.nodeList()
        }

        val dummyDefaultCtorBody = generateDummyDefaultCtorBody(n)
        if (dummyDefaultCtorBody != null) {
            r.constructors.forEach { it.remove() }
            r.addConstructor(Modifier.Keyword.PUBLIC).apply { body = dummyDefaultCtorBody }
        }

        return r
    }

    /**
     * Generates a dummy [BlockStmt] for a constructor [n] such that the constructor will still compile.
     *
     * @param n The constructor to generate a body for. The constructor must exist in a compilation unit.
     */
    private fun generateDummyBlockStmtForCtor(n: ConstructorDeclaration): BlockStmt {
        check(n.findCompilationUnit().isPresent)

        val explicitCtorStmt = n.explicitCtorInvocationStmt
            ?.takeIf { it.arguments.isNotEmpty() || !it.isThis }

        // throw new AssertionError("This method should not be reached! Signature: ...");
        val assertionStmt = generateAssertionStmt(n)

        val finalFieldInitStmts = generateDummyAssignExprsForInit(n.body)

        val stmts = buildList {
            explicitCtorStmt?.also { add(it) }

            // Assertion must be at the end, otherwise an `unreachable statement` compilation error is emitted
            addAll(finalFieldInitStmts)
            assertionStmt?.also { add(it) }
        }
        return BlockStmt(NodeList.nodeList(stmts))
    }

    /**
     * Checks whether the [ExplicitConstructorInvocationStmt] in [n] has been dummied or removed after transformation
     * into [r].
     */
    private fun isExplicitCtorStmtReplaced(n: ConstructorDeclaration, r: ConstructorDeclaration): Boolean {
        // Check the first statement of the constructor - If it is not an explicit ctor stmt, it is irrelevant
        if (n.explicitCtorInvocationStmt == null) {
            return false
        }

        // Check the first statement of the transformed ctor - If it was not changed, the statement was not replaced
        if (r.explicitCtorInvocationStmt != null) {
            return false
        }

        return true
    }

    override fun visit(n: ConstructorDeclaration, arg: Any?): Visitable? {
        if (n.isUnusedForRemovalData) return null

        val r = super.visit(n, arg) as ConstructorDeclaration
        if (n.isUnusedForDummyData || isExplicitCtorStmtReplaced(n, r) || isThrowsAssertionStmt(r.body.statements.firstOrNull())) {
            r.body = generateDummyBlockStmtForCtor(n)
        } /*else {
            with(reducerContext) {
                r.body.findAll<Statement> { !it.isReachable() }
                    .forEach { it.remove() }
            }
        }*/
        return r
    }

    override fun visit(n: EnumDeclaration, arg: Any?): Visitable? {
        if (n.isUnusedForRemovalData) return null

        val r = super.visit(n, arg) as EnumDeclaration
        if (n.isUnusedForSupertypeRemovalData) {
            r.implementedTypes = NodeList.nodeList()
        }
        r.entries.removeAll { it.isUnusedForRemovalData }

        // If no entries exist but some abstract methods are present, concrete-tize it with a dummy implementation
        // This fixes a compilation error where Javac requires an enum with abstract methods to contain at least an
        // enum constant
        if (r.entries.isEmpty()) {
            r.methods.forEach {
                if (it.isAbstract) {
                    it.isAbstract = false
                    it.setBody(generateDummyBlockStmtForMethod(it))
                }
            }
        }

        val dummyDefaultCtorBody = generateDummyDefaultCtorBody(n)
        if (dummyDefaultCtorBody != null) {
            r.constructors.forEach { it.remove() }
            r.addConstructor(Modifier.Keyword.PUBLIC).apply { body = dummyDefaultCtorBody }
        }

        return r
    }

    override fun visit(n: FieldDeclaration, arg: Any?): Visitable? {
        val r = super.visit(n, arg) as FieldDeclaration

        return if (r.variables.isNotEmpty()) r else null
    }

    override fun visit(n: ImportDeclaration, arg: Any?): Node? {
        return if (n.isUnusedForRemovalData) null else super.visit(n, arg)
    }

    /**
     * Generates a dummy [BlockStmt] for an [InitializerDeclaration].
     */
    private fun generateDummyBlockStmtForInitializerDecl(initDecl: InitializerDeclaration): BlockStmt {
        // Generate an if statement which unconditionally evaluates to `true`
        // Required to workaround javac complaining about "initializer must be able to complete normally"
        val dummyAssertionStmt = if (generateDummyAssertions) {
            // throw new AssertionError("This method should not be reached! Signature: ...");
            val assertionStmt = generateAssertionStmt(initDecl)

            // if (true) { ... }
            IfStmt(BooleanLiteralExpr(true), assertionStmt, null)
        } else null

        val finalFieldInitStmts = generateDummyAssignExprsForInit(initDecl.body)

        val stmts = finalFieldInitStmts + listOfNotNull(dummyAssertionStmt)

        return BlockStmt(NodeList.nodeList(stmts))
    }

    /**
     * Checks whether the [InitializerDeclaration] always results in unconditional assertion.
     */
    private fun isInitDeclUnconditionallyAsserts(r: InitializerDeclaration): Boolean = with(reducerContext) {
        r.body.statements.lastOrNull()?.canCompleteNormally() == false
    }

    override fun visit(n: InitializerDeclaration, arg: Any?): Visitable {
        val r = super.visit(n, arg) as InitializerDeclaration
        if (n.isUnusedForDummyData || isInitDeclUnconditionallyAsserts(r)) {
            r.body = generateDummyBlockStmtForInitializerDecl(n)
        }
        return r
    }

    private fun generateDummyReturnStmt(type: Type): ReturnStmt? {
        return generateDummyExprOfType(type)?.let { ReturnStmt(it) }
    }

    /**
     * Generates a dummy [BlockStmt] for the given return [Type] such that the method will still compile.
     */
    private fun generateDummyBlockStmtForMethod(methodDecl: MethodDeclaration): BlockStmt {
        // throw new AssertionError("This method should not be reached! Signature: ...");
        val assertionStmt = generateAssertionStmt(methodDecl)

        val dummyReturnStmt = if (!generateDummyAssertions) {
            generateDummyReturnStmt(methodDecl.type)
        } else null

        val stmts = listOfNotNull(assertionStmt, dummyReturnStmt)
        return BlockStmt(NodeList.nodeList(stmts))
    }

    override fun visit(n: MethodDeclaration, arg: Any?): Visitable? {
        if (n.isUnusedForRemovalData) return null

        val r = super.visit(n, arg) as MethodDeclaration
        if (n.body.isPresent) {
            if (n.isUnusedForDummyData) {
                r.setBody(generateDummyBlockStmtForMethod(r))
            }
        }

        return r
    }

    override fun visit(n: RecordDeclaration, arg: Any?): Visitable? {
        if (n.isUnusedForRemovalData) return null

        val r = super.visit(n, arg) as RecordDeclaration

        if (n.isUnusedForSupertypeRemovalData) {
            r.implementedTypes = NodeList.nodeList()
        }

        return r
    }

    override fun visit(n: VariableDeclarator, arg: Any?): Visitable? {
        return if (n.isUnusedForRemovalData) null else super.visit(n, arg)
    }

    /**
     * Removes all statements in [stmts] after an unreachable statement.
     */
    private fun removePostUnreachableStmts(stmts: NodeList<Statement>): NodeList<Statement> {
        return stmts.fold(NodeList.nodeList()) { acc, it ->
            // If the last statement in `acc` is an unconditional statement, do not add more nodes to it
            if (with(reducerContext) { it.isReachable() }) {
                acc.add(it)
            }

            acc
        }
    }

    override fun visit(n: AssertStmt, arg: Any?): Visitable? {
        return if (n.isUnusedData) generateAssertionStmtContextless(n) else super.visit(n, arg)
    }

    override fun visit(n: BlockStmt, arg: Any?): Visitable? {
        if (generateDummyAssertions && n.isUnusedData) {
            val assertionStmt = checkNotNull(generateAssertionStmtContextless(n))

            return BlockStmt(NodeList(assertionStmt))
        }

        val r = super.visit(n, arg) as BlockStmt

        r.statements = removePostUnreachableStmts(r.statements)

        return r
    }

    override fun visit(n: BreakStmt, arg: Any?): Visitable? {
        return if (generateDummyAssertions && n.isUnusedData) {
            generateAssertionStmtContextless(n)
        } else super.visit(n, arg)
    }

    override fun visit(n: ContinueStmt, arg: Any?): Visitable? {
        return if (generateDummyAssertions && n.isUnusedData) {
            generateAssertionStmtContextless(n)
        } else super.visit(n, arg)
    }

    override fun visit(n: DoStmt, arg: Any?): Visitable? {
        return if (n.isUnusedData) generateAssertionStmtContextless(n) else super.visit(n, arg)
    }

    override fun visit(n: ExpressionStmt, arg: Any?): Visitable? {
        if (!n.isUnusedData) return super.visit(n, arg)

        if (generateDummyAssertions) {
            return generateAssertionStmtContextless(n)
        }

        val expr = n.expression

        // Substitute the right-hand side value if the expression is an AssignExpr and we are not generating
        // assertions
        // This should fix "variable <...> might not have been initialized" compilation errors
        if (!expr.isAssignExpr) {
            return null
        }

        val assignExpr = expr.asAssignExpr()
        if (assignExpr.let { it.containsInitFieldVarData && !it.initFieldVarData.isUnusedForRemovalData && it.initFieldVarData.parentNode.map { (it as FieldDeclaration).isFinal }.get() }) {
            return ExpressionStmt(generateDummyAssignExpr(assignExpr))
        }

        val nodeInLoadedSet = reducerContext.mapNodeInLoadedSet(assignExpr.target)
        val varDecl = reducerContext.resolveDeclarationOrNull<ResolvedValueDeclaration>(nodeInLoadedSet)
            ?.toTypedAstOrNull<VariableDeclarator>(reducerContext)

        return if (varDecl?.findAncestor(Statement::class.java)?.get()?.isUnusedData == false) {
            val varType = nodeInLoadedSet
                .let { reducerContext.calculateType(it) }

            (super.visit(n, arg) as ExpressionStmt).apply {
                expression.asAssignExpr().value = generateDummyExprOfType(varType)
            }
        } else {
            null
        }
    }

    override fun visit(n: ForEachStmt, arg: Any?): Visitable? {
        return if (n.isUnusedData) generateAssertionStmtContextless(n) else super.visit(n, arg)
    }

    override fun visit(n: ForStmt, arg: Any?): Visitable? {
        return if (n.isUnusedData) generateAssertionStmtContextless(n) else super.visit(n, arg)
    }

    override fun visit(n: IfStmt, arg: Any?): Visitable? {
        return if (n.isUnusedData) generateAssertionStmtContextless(n) else super.visit(n, arg)
    }

    /**
     * @see LabeledStmt.clone
     */
    override fun visit(n: LabeledStmt, arg: Any?): Visitable? {
        // Taken from CloneVisitor.visit(LabeledStmt, Object)
        // The previous way would duplicate the node twice, which causes issues when cloning types in catch clauses
        val statement = cloneNode(n.statement, arg)
        return if (statement != null) {
            val label = cloneNode(n.label, arg)
            val comment = cloneNode(n.comment, arg)
            val r = LabeledStmt(n.tokenRange.getOrNull(), label, statement)
            r.setComment(comment)
            n.orphanComments.map { it.clone() }.forEach { r.addOrphanComment(it) }
            copyData(n, r)

            r
        } else {
            null
        }
    }

    override fun visit(n: ReturnStmt, arg: Any?): Visitable? {
        return if (n.isUnusedData) {
            return generateAssertionStmtContextless(n) ?: run {
                if (n.expression.isPresent) {
                    val parentMethod = n.findAncestor(CallableDeclaration::class.java).get()
                    check(parentMethod.isMethodDeclaration)

                    generateDummyReturnStmt(parentMethod.asMethodDeclaration().type)
                } else super.visit(n, arg)
            }
        } else super.visit(n, arg)
    }

    override fun visit(n: SwitchStmt, arg: Any?): Visitable? {
        if (n.isUnusedData) {
            return generateAssertionStmtContextless(n)
        }

        val r = super.visit(n, arg) as SwitchStmt

        r.entries.forEach {
            it.statements = removePostUnreachableStmts(it.statements)
        }

        return r
    }

    override fun visit(n: ThrowStmt, arg: Any?): Visitable? {
        return if (n.isUnusedData) {
            if (generateDummyAssertions) {
                generateAssertionStmtContextless(n)
            } else {
                // throw new Error();
                ThrowStmt(
                    ObjectCreationExpr(
                        null,
                        StaticJavaParser.parseClassOrInterfaceType(Error::class.simpleName!!),
                        NodeList.nodeList()
                    )
                )
            }
        } else super.visit(n, arg)
    }

    override fun visit(n: TryStmt, arg: Any?): Visitable? {
        val r = if (n.isUnusedData) generateAssertionStmtContextless(n) else super.visit(n, arg)

        if (r is TryStmt) {
            // Extract the body of the TryStmt if no catch clauses remain after the reduction, and that this is not a
            // try-with-resources statement and there is no finally block present
            if (r.catchClauses.isEmpty() && r.resources.isEmpty() && !r.finallyBlock.isPresent) {
                return r.tryBlock
            }

            r.finallyBlock.ifPresent {
                // Rewrite all generated assertion statements in the finally block into stderr messages
                // This ideally allows errors that are thrown in the try block to propagate to the JUnit Runner as
                // opposed to the exception thrown in the finally block

                it.findAll<ThrowStmt> { isThrowsAssertionStmt(it) }
                    .forEach { origThrowStmt ->
                        val message = origThrowStmt.expression.asObjectCreationExpr().arguments
                        val replacement = ExpressionStmt(
                            MethodCallExpr(
                                StaticJavaParser.parseExpression("System.err"),
                                "println",
                                NodeList(message)
                            )
                        )

                        when (val parentNode = origThrowStmt.parentNode.get()) {
                            is BlockStmt -> {
                                val idx = parentNode.statements.indexOf(origThrowStmt)
                                parentNode.statements.removeAt(idx)
                                parentNode.statements.add(idx, replacement)
                            }
                            else -> TODO("Unimplemented handling for parent node type ${parentNode::class.simpleName}")
                        }
                    }
            }
        }

        return r
    }

    override fun visit(n: CatchClause, arg: Any?): Visitable? {
        val r = super.visit(n, arg) as CatchClause

        // Here we handle this statement in JLS 8 § 11.2.3
        // It is a compile-time error if a catch clause can catch checked exception class E1 and it is not the case that
        // the try block corresponding to the catch clause can throw a checked exception class that is a subclass or
        // superclass of E1, unless E1 is Exception or a superclass of Exception.

        // Obtain the type of the clause, remove all caught checked exception types whose source expression is dummied
        // or removed
        val retainedExceptionTypes = n.parameter.type
            .let {
                if (it.isUnionType) {
                    it.asUnionType().elements
                } else {
                    listOf(it.asReferenceType())
                }
            }
            .filter {
                val resolvedType = reducerContext.toResolvedType<ResolvedReferenceType>(it)

                if (resolvedType.isUncheckedException() || resolvedType.qualifiedName in arrayOf(javaLangExceptionFqn, javaLangThrowableFqn)) {
                    true
                } else {
                    n.checkedExceptionSources[it]
                        .orEmpty()
                        .any { !(it.isUnusedForDummyData || it.isUnusedForRemovalData) }
                }
            }

        // Rebuild the type if there are exception types retained, otherwise strip away the entire clause
        return if (retainedExceptionTypes.isNotEmpty()) {
            val mergedType = if (retainedExceptionTypes.size > 1) {
                UnionType(NodeList.nodeList(retainedExceptionTypes))
            } else {
                retainedExceptionTypes.single()
            }

            r.apply {
                parameter.type = mergedType
            }
        } else {
            null
        }
    }

    override fun visit(n: WhileStmt, arg: Any?): Visitable? {
        return if (n.isUnusedData) generateAssertionStmtContextless(n) else super.visit(n, arg)
    }

    override fun visit(n: YieldStmt, arg: Any?): Visitable? {
        return if (n.isUnusedData) generateAssertionStmtContextless(n) else super.visit(n, arg)
    }

    override fun visit(n: FieldAccessExpr, arg: Any?): Visitable {
        val r = super.visit(n, arg) as FieldAccessExpr
        val refLikeNode = ReferenceTypeLikeNode.FieldAccessExpr(r)

        if (refLikeNode.rewriteNestedTypeNameData != null) {
            val qnamePrefix = refLikeNode.rewriteNestedTypeNameData!!
                .qualifiedName
                .split(".")
                .dropLast(1)
                .joinToString(".")

            r.scope = StaticJavaParser.parseExpression(qnamePrefix)
        }

        return r
    }

    override fun visit(n: MarkerAnnotationExpr, arg: Any?): Visitable? {
        return if (n.isUnusedForRemovalData) null else super.visit(n, arg)
    }

    override fun visit(n: NameExpr, arg: Any?): Visitable {
        val r = super.visit(n, arg) as NameExpr
        val refLikeNode = ReferenceTypeLikeNode.NameExpr(r)

        if (refLikeNode.rewriteNestedTypeNameData != null) {
            TODO()
        }

        return r
    }

    override fun visit(n: ClassOrInterfaceType, arg: Any?): Visitable {
        val r = super.visit(n, arg) as ClassOrInterfaceType
        val refLikeNode = ReferenceTypeLikeNode.ClassOrIfaceType(r)

        if (refLikeNode.rewriteNestedTypeNameData != null) {
            val qnamePrefix = refLikeNode.rewriteNestedTypeNameData!!
                .qualifiedName
                .split(".")
                .dropLast(1)
                .joinToString(".")

            r.setScope(StaticJavaParser.parseClassOrInterfaceType(qnamePrefix))
        }

        return r
    }
}