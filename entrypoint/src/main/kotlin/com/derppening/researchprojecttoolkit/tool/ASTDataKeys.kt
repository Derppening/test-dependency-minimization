package com.derppening.researchprojecttoolkit.tool

import com.derppening.researchprojecttoolkit.model.*
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.AppliedPasses
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.AssociatedBytecodeMethod
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.BaselineClassCreated
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.BaselineClassLoaded
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.CoberturaCUCovData
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.CoberturaClassCovData
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.CoberturaMethodCovData
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.ExceptionSources
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.InclusionReasons
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.InitializesField
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.IsUnused
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.IsUnusedForSupertypeRemoval
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.JacocoCUCovData
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.JacocoClassCovData
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.JacocoMethodCovData
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.Reassignments
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.RewriteNestedTypeName
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.TransformDecisionData
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.getDataOrNull
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.getTagData
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.getWithLazySet
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.setDataOnce
import com.derppening.researchprojecttoolkit.tool.ASTDataKeys.setTagData
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.Pass
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.DataKey
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.ObjectCreationExpr
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations
import com.github.javaparser.ast.nodeTypes.NodeWithExtends
import com.github.javaparser.ast.nodeTypes.NodeWithImplements
import com.github.javaparser.ast.stmt.CatchClause
import com.github.javaparser.ast.stmt.Statement
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.ReferenceType
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import java.util.*
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

private object ASTDataKeys {

    fun Node.getTagData(key: DataKey<Unit>): Boolean = containsData(key)
    fun Node.setTagData(key: DataKey<Unit>, value: Boolean) =
        if (value) setData(key, Unit) else removeData(key)

    fun <T> Node.getWithLazySet(key: DataKey<T>, lazyInit: () -> T): T {
        if (!containsData(key))
            setData(key, lazyInit())
        return getData(key)
    }

    fun <T> Node.setDataOnce(key: DataKey<Pair<T, List<StackTraceElement>>>, value: T, throwOnDuplicate: Boolean = false) =
        setDataOnce(key, value) { _, _ -> throwOnDuplicate }

    fun <T> Node.setDataOnce(
        key: DataKey<Pair<T, List<StackTraceElement>>>,
        value: T,
        throwIf: (oldValue: T, newValue: T) -> Boolean
    ) {
        if (!containsData(key)) {
            setData(key, value to getCallerStackTrace())
        } else {
            val (prevValue, stackTrace) = getData(key)

            if (throwIf(prevValue, value)) {
                val errorMsg = buildString {
                    append("Key ")
                    append(key::class.qualifiedName)
                    append(" is set more than once!")
                    appendLine()
                    append("Old Value: ")
                    append(prevValue)
                    appendLine()
                    append("New Value: ")
                    append(value)
                    appendLine()
                    append("Previous Stack Trace:")
                    appendLine()
                    stackTrace.forEach {
                        appendLine("${' ' * 4}$it")
                    }
                }
                error(errorMsg)
            }
        }
    }

    fun <T : Any> Node.getDataOrNull(key: DataKey<T>): T? {
        return if (containsData(key)) {
            getData(key)
        } else null
    }

    object AppliedPasses : DataKey<MutableList<Pass>>()
    object AssociatedBytecodeMethod : DataKey<Pair<BytecodeMethod, List<StackTraceElement>>>()
    object BaselineClassLoaded : DataKey<Pair<Boolean, List<StackTraceElement>>>()
    object BaselineClassCreated : DataKey<Pair<Boolean, List<StackTraceElement>>>()
    object CoberturaCUCovData : DataKey<Pair<Set<CoberturaXML.Class>, List<StackTraceElement>>>()
    object CoberturaClassCovData : DataKey<Pair<CoberturaXML.Class, List<StackTraceElement>>>()
    object CoberturaMethodCovData : DataKey<Pair<CoberturaXML.Method, List<StackTraceElement>>>()
    object ExceptionSources : DataKey<SortedMap<ReferenceType, MutableList<Node>>>()
    object InclusionReasons : DataKey<MutableList<ReachableReason>>()
    object InitializesField : DataKey<VariableDeclarator>()
    object IsUnused : DataKey<Unit>()
    object IsUnusedForSupertypeRemoval : DataKey<Unit>()
    object JacocoCUCovData : DataKey<Pair<Pair<JacocoXML.SourceFile, SortedSet<JacocoXML.Class>>, List<StackTraceElement>>>()
    object JacocoClassCovData : DataKey<Pair<JacocoXML.Class, List<StackTraceElement>>>()
    object JacocoMethodCovData : DataKey<Pair<JacocoXML.Method, List<StackTraceElement>>>()
    object RewriteNestedTypeName : DataKey<ResolvedReferenceTypeDeclaration>()
    object Reassignments : DataKey<Pair<Set<AssignExpr>, List<StackTraceElement>>>()
    object TransformDecisionData : DataKey<Pair<NodeTransformDecision, List<StackTraceElement>>>()
}

/**
 * The reasons this [ImportDeclaration] is reachable.
 */
val ImportDeclaration.inclusionReasonsData: MutableList<ReachableReason>
    get() = getWithLazySet(InclusionReasons) { Collections.synchronizedList(mutableListOf<ReachableReason>()) }

/**
 * The reasons this [TypeDeclaration] is reachable and therefore included in the reduced set.
 */
val TypeDeclaration<*>.inclusionReasonsData: MutableList<ReachableReason>
    get() = getWithLazySet(InclusionReasons) { Collections.synchronizedList(mutableListOf<ReachableReason>()) }

/**
 * The reasons this [CallableDeclaration] is reachable and therefore included in the reduced set.
 */
val CallableDeclaration<*>.inclusionReasonsData: MutableList<ReachableReason>
    get() = getWithLazySet(InclusionReasons) { Collections.synchronizedList(mutableListOf<ReachableReason>()) }

/**
 * The reasons this [VariableDeclarator] is reachable and therefore included in the reduced set.
 */
val EnumConstantDeclaration.inclusionReasonsData: MutableList<ReachableReason>
    get() = getWithLazySet(InclusionReasons) { Collections.synchronizedList(mutableListOf<ReachableReason>()) }

/**
 * The reasons this [VariableDeclarator] is reachable and therefore included in the reduced set.
 */
val VariableDeclarator.inclusionReasonsData: MutableList<ReachableReason>
    get() = getWithLazySet(InclusionReasons) { Collections.synchronizedList(mutableListOf<ReachableReason>()) }

private var Node.transformDecisionData: NodeTransformDecision?
    get() = getDataOrNull(TransformDecisionData)?.first
    set(v) = setDataOnce(TransformDecisionData, requireNotNull(v)) { oldValue, newValue ->
        newValue != oldValue
    }

var ImportDeclaration.transformDecisionData: NodeTransformDecision?
    get() = (this as Node).transformDecisionData
    set(v) {
        (this as Node).transformDecisionData = v
    }

/**
 * Whether this [ImportDeclaration] is unused and is eligible for removal.
 */
val ImportDeclaration.isUnusedForRemovalData: Boolean
    get() = transformDecisionData == NodeTransformDecision.REMOVE

var TypeDeclaration<*>.transformDecisionData: NodeTransformDecision?
    get() = (this as Node).transformDecisionData
    set(v) {
        (this as Node).transformDecisionData = v
    }

/**
 * Whether this [TypeDeclaration] is unused and is eligible for dummy.
 */
val TypeDeclaration<*>.isUnusedForDummyData: Boolean
    get() = transformDecisionData == NodeTransformDecision.DUMMY

/**
 * Whether this [TypeDeclaration] is unused and is eligible for removal.
 */
val TypeDeclaration<*>.isUnusedForRemovalData: Boolean
    get() = transformDecisionData == NodeTransformDecision.REMOVE

var CallableDeclaration<*>.transformDecisionData: NodeTransformDecision?
    get() = (this as Node).transformDecisionData
    set(v) {
        (this as Node).transformDecisionData = v
    }

/**
 * Whether this [CallableDeclaration] is unused and is eligible for dummy.
 */
val CallableDeclaration<*>.isUnusedForDummyData: Boolean
    get() = transformDecisionData == NodeTransformDecision.DUMMY

/**
 * Whether this [CallableDeclaration] is unused and is eligible for removal.
 */
val CallableDeclaration<*>.isUnusedForRemovalData: Boolean
    get() = transformDecisionData == NodeTransformDecision.REMOVE


var EnumConstantDeclaration.transformDecisionData: NodeTransformDecision?
    get() = (this as Node).transformDecisionData
    set(v) {
        (this as Node).transformDecisionData = v
    }

/**
 * Whether this [EnumConstantDeclaration] is unused and is eligible for removal.
 */
val EnumConstantDeclaration.isUnusedForRemovalData: Boolean
    get() = transformDecisionData == NodeTransformDecision.REMOVE

var VariableDeclarator.transformDecisionData: NodeTransformDecision?
    get() = (this as Node).transformDecisionData
    set(v) {
        (this as Node).transformDecisionData = v
    }

/**
 * Whether this [VariableDeclarator] is unused and is eligible for removal.
 */
val VariableDeclarator.isUnusedForRemovalData: Boolean
    get() = transformDecisionData == NodeTransformDecision.REMOVE

var AnnotationExpr.transformDecisionData: NodeTransformDecision?
    get() = (this as Node).transformDecisionData
    set(v) {
        (this as Node).transformDecisionData = v
    }

/**
 * Whether this [AnnotationExpr] is unused and eligible for removal.
 */
val AnnotationExpr.isUnusedForRemovalData: Boolean
    get() = transformDecisionData == NodeTransformDecision.REMOVE

var InitializerDeclaration.transformDecisionData: NodeTransformDecision?
    get() = (this as Node).transformDecisionData
    set(v) {
        (this as Node).transformDecisionData = v
    }

/**
 * Whether this [InitializerDeclaration] is unused and eligible for dummy.
 */
val InitializerDeclaration.isUnusedForDummyData: Boolean
    get() = transformDecisionData == NodeTransformDecision.DUMMY

/**
 * All [AssignExpr] which reassigns to this [Parameter].
 */
var Parameter.reassignmentsData: Set<AssignExpr>
    get() = if (containsData(Reassignments)) getData(Reassignments).first else emptySet()
    set(v) = setDataOnce(Reassignments, v)

/**
 * All [AssignExpr] which reassigns to this [VariableDeclarator].
 */
var VariableDeclarator.reassignmentsData: Set<AssignExpr>
    get() = if (containsData(Reassignments)) getData(Reassignments).first else emptySet()
    set(v) = setDataOnce(Reassignments, v)

/**
 * All [Expression] used to initialize, assign, or reassign this [VariableDeclarator].
 */
val VariableDeclarator.allAssignmentsData: Set<Expression>
    get() = (reassignmentsData.map { it.value } + initializer.getOrNull())
        .filterNotNullTo(NodeRangeTreeSet())

/**
 * Whether this [TypeDeclaration] is unused such that its supertypes can be removed.
 *
 * This usually applies to [TypeDeclaration] which are used only as nest parents.
 */
var TypeDeclaration<*>.isUnusedForSupertypeRemovalData: Boolean
    get() = getTagData(IsUnusedForSupertypeRemoval)
    set(v) = setTagData(IsUnusedForSupertypeRemoval, v)

/**
 * The [VariableDeclarator] of a field which this [AssignExpr] initializes.
 */
var AssignExpr.initFieldVarData: VariableDeclarator
    get() = getData(InitializesField)
    set(v) {
        require(v.parentNode.map { it is FieldDeclaration }.getOrDefault(false))

        setData(InitializesField, v)
    }

/**
 * Whether this expression contains information on its initialization target.
 */
val AssignExpr.containsInitFieldVarData: Boolean
    get() = containsData(InitializesField)

var Statement.isUnusedData: Boolean
    get() = getTagData(IsUnused)
    set(v) = setTagData(IsUnused, v)

/**
 * Whether this [Node] is unused and eligible for removal.
 */
val Node.isUnusedForRemovalData: Boolean
    get() {
        return when (this) {
            // Direct properties
            is ImportDeclaration -> isUnusedForRemovalData
            is TypeDeclaration<*> -> isUnusedForRemovalData
            is CallableDeclaration<*> -> isUnusedForRemovalData
            is EnumConstantDeclaration -> isUnusedForRemovalData
            is VariableDeclarator -> isUnusedForRemovalData
            is AnnotationExpr -> {
                isUnusedForRemovalData ||
                        findAncestor(NodeWithAnnotations::class.java)
                            .map { (it as Node).isUnusedForRemovalData }
                            .getOrDefault(false)
            }

            // Derived properties
            else -> {
                // If `this` is a type declared as part of an extends or implements clause, we are effectively
                // removable.
                if (this is ClassOrInterfaceType) {
                    val parentNodeAsTypeDecl = parentNode.mapNotNull { it as? TypeDeclaration<*> }.getOrNull()

                    if (parentNodeAsTypeDecl != null && parentNodeAsTypeDecl.isUnusedForSupertypeRemovalData) {
                        if ((parentNodeAsTypeDecl as? NodeWithExtends<*>)?.extendedTypes?.any { it == this } == true) {
                            return true
                        }
                        if ((parentNodeAsTypeDecl as? NodeWithImplements<*>)?.implementedTypes?.any { it == this } == true) {
                            return true
                        }
                    }
                }

                val ancestralContexts = ancestorsAsSequence()
                    .filter {
                        it is TypeDeclaration<*> ||
                                it is CallableDeclaration<*> ||
                                it is EnumConstantDeclaration ||
                                (it is VariableDeclarator && it.parentNode.map { it is FieldDeclaration }
                                    .getOrDefault(false)) ||
                                it is Statement
                    }

                // Find if any of our ancestors are marked for removal
                ancestralContexts.any {
                    when (it) {
                        is CallableDeclaration<*> -> it.isUnusedForRemovalData
                        is EnumConstantDeclaration -> it.isUnusedForRemovalData
                        is Statement -> it.isUnusedForRemovalData
                        is TypeDeclaration<*> -> it.isUnusedForRemovalData
                        is VariableDeclarator -> it.isUnusedForRemovalData
                        else -> unreachable("Unknown ancestral context of type ${it::class.simpleName}")
                    }
                }
            }
        }
    }

/**
 * Whether this [Node] is unused and eligible for dummy.
 */
val Node.isUnusedForDummyData: Boolean
    get() {
        return when (this) {
            // Direct properties
            is TypeDeclaration<*> -> isUnusedForDummyData
            is CallableDeclaration<*> -> isUnusedForDummyData

            // Derived properties
            else -> {
                // If this is a statement except ExplicitCtorInvocationStmt and is unused, it is by default dummied
                // ExplicitCtorInvocationStmt is not considered because it must be retained for compilation purposes
                if (this is Statement && !isExplicitConstructorInvocationStmt && isUnusedData) {
                    return true
                }

                val ancestralContexts = ancestorsAsSequence()
                    .filter { it is TypeDeclaration<*> || it is CallableDeclaration<*> || it is Statement }

                // Find if any of our ancestors are marked for dummy
                ancestralContexts.any {
                    when (it) {
                        is CallableDeclaration<*> -> it.isUnusedForDummyData
                        is Statement -> !it.isExplicitConstructorInvocationStmt && it.isUnusedData
                        is TypeDeclaration<*> -> it.isUnusedForDummyData
                        else -> unreachable("Unknown ancestral context of type ${it::class.simpleName}")
                    }
                }
            }
        }
    }

/**
 * The [Node] which this node derived its [Node.inclusionReasonsData] from.
 */
private val Node.inclusionReasonsDataNode: Node
    get() = when (this) {
        // Direct properties
        is ImportDeclaration -> this
        is TypeDeclaration<*> -> this
        is CallableDeclaration<*> -> this
        is FieldDeclaration -> this
        is EnumConstantDeclaration -> this
        is VariableDeclarator -> this

        // Derived properties
        else -> {
            ancestorsAsSequence().firstOrNull {
                it is TypeDeclaration<*> ||
                        it is CallableDeclaration<*> ||
                        it is EnumConstantDeclaration ||
                        it is FieldDeclaration ||
                        (it is VariableDeclarator && it.parentNode.map { it is FieldDeclaration }
                            .getOrDefault(false))
            } ?: error("No ancestral context of type TypeDecl, CallableDecl, EnumConstantDecl, or VarDecl\n${astToString(showChildren = false)})")
        }
    }

val Node.inclusionReasonsData: List<ReachableReason>
    get() = when (this) {
        // Direct properties
        is ImportDeclaration -> inclusionReasonsData
        is TypeDeclaration<*> -> inclusionReasonsData
        is CallableDeclaration<*> -> inclusionReasonsData
        is EnumConstantDeclaration -> inclusionReasonsData
        is VariableDeclarator -> inclusionReasonsData

        // Derived properties
        is FieldDeclaration -> variables.flatMap { it.inclusionReasonsData.synchronizedWith { toList() } }
        else -> {
            // Find if any of our ancestors are marked for removal
            inclusionReasonsDataNode.inclusionReasonsData
        }
    }

/**
 * The [ObjectCreationExpr]s which creates an instance of this [TypeDeclaration].
 */
val TypeDeclaration<*>.instanceCreationData: SortedSet<ObjectCreationExpr>
    get() {
        return buildCollection(NodeRangeTreeSet()) {
            inclusionReasonsData
                .synchronizedWith { filterIsInstance<ReachableReason.ReferencedBySymbolName>() }
                .mapNotNull { it.expr as? ObjectCreationExpr }
                .also { addAll(it) }

            constructors
                .flatMap {
                    it.inclusionReasonsData.synchronizedWith { filterIsInstance<ReachableReason.ReferencedBySymbolName>() }
                }
                .mapNotNull { it.expr as? ObjectCreationExpr }
                .also { addAll(it) }
        }
    }

/**
 * Whether this [TypeDeclaration] is determined to be loaded by the baseline.
 *
 * If the information is not present, returns `null` by default.
 */
var ReferenceTypeLikeDeclaration<*>.isBaselineLoadedData: Boolean?
    get() = node.getDataOrNull(BaselineClassLoaded)?.first
    set(v) = node.setDataOnce(BaselineClassLoaded, requireNotNull(v))

/**
 * Whether an instance of this [TypeDeclaration] is determined to be created by the baseline.
 *
 * If the information is not present, returns `null` by default.
 */
var ReferenceTypeLikeDeclaration<*>.isBaselineCreatedData: Boolean?
    get() = node.getDataOrNull(BaselineClassCreated)?.first
    set(v) = node.setDataOnce(BaselineClassCreated, requireNotNull(v))

var CompilationUnit.coberturaCoverageData: Set<CoberturaXML.Class>?
    get() = getDataOrNull(CoberturaCUCovData)?.first
    set(v) = setDataOnce(CoberturaCUCovData, requireNotNull(v), true)

var ReferenceTypeLikeDeclaration<*>.coberturaCoverageData: CoberturaXML.Class?
    get() = node.getDataOrNull(CoberturaClassCovData)?.first
    set(v) = node.setDataOnce(CoberturaClassCovData, requireNotNull(v), true)

var ExecutableDeclaration<*>.coberturaCoverageData: CoberturaXML.Method?
    get() = node.getDataOrNull(CoberturaMethodCovData)?.first
    set(v) = node.setDataOnce(CoberturaMethodCovData, requireNotNull(v), true)

var CompilationUnit.jacocoCoverageData: Pair<JacocoXML.SourceFile, SortedSet<JacocoXML.Class>>?
    get() = getDataOrNull(JacocoCUCovData)?.first
    set(v) = setDataOnce(JacocoCUCovData, requireNotNull(v), true)

var ReferenceTypeLikeDeclaration<*>.jacocoCoverageData: JacocoXML.Class?
    get() = node.getDataOrNull(JacocoClassCovData)?.first
    set(v) = node.setDataOnce(JacocoClassCovData, requireNotNull(v), true)

var ExecutableDeclaration<*>.jacocoCoverageData: JacocoXML.Method?
    get() = node.getDataOrNull(JacocoMethodCovData)?.first
    set(v) = node.setDataOnce(JacocoMethodCovData, requireNotNull(v), true)

var ExecutableDeclaration<*>.associatedBytecodeMethodData: BytecodeMethod?
    get() = node.getDataOrNull(AssociatedBytecodeMethod)?.first
    set(v) = node.setDataOnce(AssociatedBytecodeMethod, requireNotNull(v), true)

var ReferenceTypeLikeNode<*>.rewriteNestedTypeNameData: ResolvedReferenceTypeDeclaration?
    get() = node.getDataOrNull(RewriteNestedTypeName)
    set(v) = node.setData(RewriteNestedTypeName, v)

val CompilationUnit.appliedPasses: MutableList<Pass>
    get() = getWithLazySet(AppliedPasses) { mutableListOf() }

val CatchClause.checkedExceptionSources: SortedMap<ReferenceType, MutableList<Node>>
    get() = getWithLazySet(ExceptionSources) { TreeMap(NodeAstComparator()) }

/**
 * Locks all [inclusionReasonsData] within this [CompilationUnit].
 */
fun CompilationUnit.lockInclusionReasonsData(): CompilationUnit {
    return apply {
        findAll<Node> {
            when (it) {
                is ImportDeclaration,
                is TypeDeclaration<*>,
                is CallableDeclaration<*>,
                is EnumConstantDeclaration,
                is VariableDeclarator -> true
                else -> false
            }
        }.forEach {
            if (it.containsData(InclusionReasons)) {
                it.setData(InclusionReasons, Collections.unmodifiableList(it.getData(InclusionReasons)))
            }
        }
    }
}

/**
 * Clears all data after a clone operation.
 */
fun Node.clearDataAfterClone() {
    removeData(ExceptionSources)
    removeData(InitializesField)
}
