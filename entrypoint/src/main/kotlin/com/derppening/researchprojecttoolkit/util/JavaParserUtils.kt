package com.derppening.researchprojecttoolkit.util

import com.derppening.researchprojecttoolkit.model.ReferenceTypeLikeDeclaration
import com.derppening.researchprojecttoolkit.tool.ReducerContext
import com.derppening.researchprojecttoolkit.tool.facade.FuzzySymbolSolver
import com.derppening.researchprojecttoolkit.util.JavaParserUtils.LOGGER
import com.derppening.researchprojecttoolkit.visitor.LineRangeExtractor
import com.github.javaparser.HasParentNode
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.Range
import com.github.javaparser.ast.*
import com.github.javaparser.ast.Node.TreeTraversal
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.comments.BlockComment
import com.github.javaparser.ast.comments.JavadocComment
import com.github.javaparser.ast.comments.LineComment
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.modules.*
import com.github.javaparser.ast.stmt.*
import com.github.javaparser.ast.type.*
import com.github.javaparser.resolution.Navigator
import com.github.javaparser.resolution.SymbolResolver
import com.github.javaparser.resolution.declarations.*
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedType
import com.github.javaparser.resolution.types.ResolvedUnionType
import com.github.javaparser.resolution.types.ResolvedWildcard
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import java.nio.file.Path
import java.util.*
import kotlin.Pair
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrElse
import kotlin.jvm.optionals.getOrNull
import kotlin.streams.asSequence
import com.github.javaparser.utils.Pair as JavaParserPair

private object JavaParserUtils {

    val LOGGER = Logger<JavaParserUtils>()
}

/**
 * The [CompilationUnit.Storage] which this node is parsed from.
 */
val Node.storage: CompilationUnit.Storage?
    get() = findCompilationUnit().flatMap { it.storage }.getOrNull()

/**
 * The [Path] which this node is parsed from.
 */
val Node.sourcePath: Path?
    get() = storage?.path

/**
 * All ancestors of this [Node].
 */
val Node.ancestors: List<Node>
    get() = ancestorsAsSequence().toList()

fun Node.asSequence(traversal: TreeTraversal = TreeTraversal.PREORDER): Sequence<Node> =
    stream(traversal).asSequence()

fun HasParentNode<*>.ancestorsAsSequence(): Sequence<Node> =
    generateSequence(parentNode.getOrNull()) { it.parentNode.getOrNull() }

fun Node.ancestorsAsSequence(): Sequence<Node> = asSequence(TreeTraversal.PARENTS)

/**
 * The depth of this [Node] in an AST.
 */
val Node.astDepth: Int get() = parentNode.map { it.astDepth + 1 }.orElse(0)

/**
 * The depth of this [NodeList] in an AST.
 */
val NodeList<*>.astDepth: Int get() = parentNode.map { it.astDepth + 1 }.orElse(0)

typealias AstId = Pair<Path, Range>

/**
 * A unique identifier for this [Node] within a source root, generated using the absolute path of the
 * [compilation unit][Node.findCompilationUnit] containing this node, and the [range][Node.range] of this node.
 */
val Node.astBasedId: AstId
    get() {
        val storage = requireNotNull(storage) {
            "Node must have a valid storage"
        }
        val range = requireNotNull(range.getOrNull()) {
            "Node `$this` must have a valid range"
        }

        return storage.path to range
    }

/**
 * Comparator which compares two [AST ranges][Range].
 */
object AstRangeComparator : Comparator<Range> {

    override fun compare(o1: Range, o2: Range): Int {
        return o1.begin.compareTo(o2.begin)
            .takeIf { it != 0 }
            ?: o1.end.compareTo(o2.end)
    }
}

/**
 * Comparator which compares two [AstId].
 */
object AstIdComparator : Comparator<AstId> {

    override fun compare(o1: AstId, o2: AstId): Int {
        val (o1Path, o1Range) = o1
        val (o2Path, o2Range) = o2

        return o1Path.compareTo(o2Path).takeIf { it != 0 }
            ?: AstRangeComparator.compare(o1Range, o2Range)
    }
}

/**
 * Comparator which compares two [AST nodes][Node].
 */
class NodeAstComparator<N : Node> : Comparator<N> {

    override fun compare(o1: N, o2: N): Int = Companion.compare(o1, o2)

    companion object {

        fun <N : Node> compare(o1: N, o2: N): Int = AstIdComparator.compare(o1.astBasedId, o2.astBasedId)
    }
}

@Suppress("FunctionName")
fun <K : Node> NodeRangeTreeSet(vararg initial: K): SortedSet<K> =
    CachedSortedSet(Node::astBasedId, AstIdComparator, initial.toList())

@Suppress("FunctionName")
fun <K : Node> NodeRangeTreeSet(reducerContext: ReducerContext, vararg initial: K): SortedSet<K> =
    CachedSortedSet({ reducerContext.mapNodeInLoadedSet(it).astBasedId }, AstIdComparator, initial.toList())

@Suppress("FunctionName")
fun <K : Node> NodeRangeTreeSet(other: SortedSet<K>, vararg initial: K): SortedSet<K> {
    return if (other is CachedSortedSet<*, *> && other.keyComparator() is AstIdComparator) {
        @Suppress("UNCHECKED_CAST")
        NodeRangeTreeSet(other as CachedSortedSet<K, AstId>, *initial)
    } else {
        CachedSortedSet(Node::astBasedId, AstIdComparator, other).apply { addAll(initial) }
    }
}

@Suppress("FunctionName")
fun <K : Node> NodeRangeTreeSet(other: CachedSortedSet<K, AstId>, vararg initial: K): SortedSet<K> =
    CachedSortedSet(other).apply { addAll(initial) }

fun <K : Node> SortedSet<K>.copy(): SortedSet<K> = NodeRangeTreeSet(this)

@Suppress("FunctionName")
fun <K : Node, V> NodeRangeTreeMap(vararg initial: Pair<K, V>): SortedMap<K, V> =
    TreeMap<K, V>(NodeAstComparator()).apply { putAll(initial) }

@Suppress("FunctionName")
fun <K : Node, V> NodeRangeTreeMap(reducerContext: ReducerContext, vararg initial: Pair<K, V>): SortedMap<K, V> =
    TreeMap<K, V>(Comparator.comparing(reducerContext::mapNodeInLoadedSet, NodeAstComparator()))
        .apply { putAll(initial) }

@Suppress("FunctionName")
fun <K : Node, V> NodeRangeTreeMap(other: SortedMap<K, V>, vararg initial: Pair<K, V>): SortedMap<K, V> =
    TreeMap(other).apply { putAll(initial) }

fun <K : Node, V> SortedMap<K, V>.copy(): SortedMap<K, V> = NodeRangeTreeMap(this)

/**
 * Comparator which compares two type parameter lists.
 */
object TypeParamDeclListComparator : Comparator<List<ResolvedTypeParameterDeclaration>> {

    override fun compare(o1: List<ResolvedTypeParameterDeclaration>, o2: List<ResolvedTypeParameterDeclaration>): Int {
        // Compare by list size
        val cmpSz = o1.size.compareTo(o2.size)
        if (cmpSz != 0) {
            return cmpSz
        }

        // Compare by type parameter name
        val cmpTpName = o1.zip(o2)
            .asSequence()
            .map { (lhs, rhs) -> lhs.name.compareTo(rhs.name) }
            .firstOrNull { it != 0 }
        if (cmpTpName != null) {
            return cmpTpName
        }

        // Compare by type parameter bounds - Unbounded-first, tie-break by string representation of bounded type
        val cmpTpBound = o1.zip(o2)
            .asSequence()
            .map { (lhs, rhs) ->
                when {
                    lhs.isUnbounded && rhs.isUnbounded -> 0
                    lhs.isBounded && rhs.isUnbounded -> 1
                    lhs.isUnbounded && lhs.isBounded -> -1
                    else -> {
                        val lhsBounds = lhs.bounds as List<ResolvedTypeParameterDeclaration.Bound>
                        val rhsBounds = rhs.bounds as List<ResolvedTypeParameterDeclaration.Bound>

                        val cmpBoundSz = lhsBounds.size.compareTo(rhsBounds.size)
                        if (cmpBoundSz != 0) {
                            return@map cmpBoundSz
                        }

                        lhsBounds.zip(rhsBounds)
                            .asSequence()
                            .map { (lhsBound, rhsBound) ->
                                lhsBound.type.describe().compareTo(rhsBound.type.describe())
                            }
                            .firstOrNull { it != 0 }
                            ?: 0
                    }
                }
            }
            .firstOrNull { it != 0 }

        return cmpTpBound ?: 0
    }
}

/**
 * The [Node.getRange] of this node in string representation, with the filename of the [CompilationUnit].
 */
val Node.fullRangeString: String
    get() {
        val range = range.orElse(null) ?: return "<${null}>"
        val cu = findCompilationUnit()
            .flatMap { it.storage }
            .map { it.fileName }
            .orElse("source")

        return if (range.begin == range.end) {
            "<$cu:${range.begin.line}:${range.begin.column}>"
        } else {
            "<$cu:${range.begin.line}:${range.begin.column}, $cu:${range.end.line}:${range.end.column}>"
        }
    }

/**
 * The [Node.getRange] of this node in string representation.
 */
val Node.rangeString: String
    get() {
        val range = range.orElse(null)
            ?: return "<${null}>"
        val parentNodeRange = parentNode.flatMap { it.range }.orElse(null)
            ?: return "<line:${range.begin.line}:${range.begin.column}, line:${range.end.line}:${range.end.column}>"

        return when {
            range.begin == range.end -> {
                if (parentNodeRange.begin.line == range.begin.line)
                    "col:${range.begin.column}"
                else
                    "line:${range.begin.line}:${range.begin.column}"
            }

            range.begin.line == parentNodeRange.begin.line -> {
                if (range.end.line == range.begin.line)
                    "col:${range.begin.column}, col:${range.end.column}"
                else
                    "col:${range.begin.column}, line:${range.end.line}:${range.end.column}"
            }

            range.begin.line == range.end.line -> {
                "line:${range.begin.line}:${range.begin.column}, col:${range.end.column}"
            }

            else -> {
                "line:${range.begin.line}:${range.begin.column}, line:${range.end.line}:${range.end.column}"
            }
        }.let { "<$it>" }
    }

/**
 * Returns the valid range of a line.
 *
 * The valid range of a line is the range of column numbers within a line which contains any AST nodes.
 */
fun CompilationUnit.getRangeOfLine(line: Int): Range {
    val begin = accept(LineRangeExtractor(LineRangeExtractor.Mode.BEGIN), line)
    val end = accept(LineRangeExtractor(LineRangeExtractor.Mode.END), line)

    checkNotNull(begin) {
        "Cannot find begin column for line $line for CU ${primaryType.flatMap { it.fullyQualifiedName }.getOrNull()}"
    }
    checkNotNull(end) {
        "Cannot find end column for line $line for CU ${primaryType.flatMap { it.fullyQualifiedName }.getOrNull()}"
    }

    return Range.range(line, begin, line, end)
}

/**
 * Outputs this node in a string representation, suitable for display in an AST.
 */
private fun Node.asASTString(): String {
    val commonPrefix = "${createPadding(astDepth)}${this::class.simpleName} $rangeString"

    return when (this) {
        is AnnotationDeclaration -> "annotation $nameAsString"
        is AnnotationMemberDeclaration -> nameAsString
        is ArrayCreationExpr -> "'${elementType.asString()}${"[]".repeat(levels.size)}'"
        is ArrayInitializerExpr -> "[${values.size}]"
        is ArrayType -> "'${componentType.asString()}'"
        is BinaryExpr -> "'${operator.asString()}'"
        is BooleanLiteralExpr -> "'boolean' $value"
        is CastExpr -> "'$typeAsString'"
        is CharLiteralExpr -> "'char' $value"
        is ClassExpr -> "'java.lang.Class<$typeAsString>'"
        is ClassOrInterfaceDeclaration -> "${if (isInterface) "interface" else "class"} $nameAsString"
        is CompilationUnit -> "'${storage.map { it.sourceRoot.relativize(it.path) }.orElse(null)}'"
        is ClassOrInterfaceType -> "'$nameAsString'"
        is ConstructorDeclaration ->
            "$nameAsString 'void (${parameters.joinToString(", ") { it.typeAsString }})'"

        is DoubleLiteralExpr -> "'double' $value"
        is EnumConstantDeclaration -> "'$nameAsString'"
        is EnumDeclaration -> "'$nameAsString'"
        is ExplicitConstructorInvocationStmt -> "'${if (isThis) "this" else "super"}'"
        is FieldAccessExpr -> nameAsString
        is ImportDeclaration -> "import ${if (isStatic) " static" else ""}${if (isAsterisk) " *" else ""}"
        is InitializerDeclaration -> "'void <${if (isStatic) "clinit" else "init"}>()'"
        is InstanceOfExpr -> "'$typeAsString'"
        is IntegerLiteralExpr -> "'int' $value"
        is LabeledStmt -> "$label"
        is LocalClassDeclarationStmt -> "'${classDeclaration.nameAsString}'"
        is LocalRecordDeclarationStmt -> "'${recordDeclaration.nameAsString}'"
        is LongLiteralExpr -> "'long' $value"
        is MarkerAnnotationExpr -> "'$nameAsString'"
        is MemberValuePair -> "'$nameAsString'"
        is MethodCallExpr -> "'$nameAsString'"
        is MethodDeclaration ->
            "$nameAsString '$typeAsString (${parameters.joinToString(", ") { it.typeAsString }})'"

        is Name -> "'${asString()}'"
        is NameExpr -> "'$nameAsString'"
        is NormalAnnotationExpr -> "'$nameAsString'"
        is NullLiteralExpr -> "${null}"
        is ObjectCreationExpr -> "'$typeAsString'"
        is PackageDeclaration -> "'$nameAsString'"
        is Parameter -> "'$typeAsString' $nameAsString"
        is PrimitiveType -> "'${type.asString()}'"
        is RecordDeclaration -> "'$nameAsString'"
        is CompactConstructorDeclaration -> nameAsString
        is SimpleName -> "'${asString()}'"
        is SingleMemberAnnotationExpr -> "'$nameAsString'"
        is StringLiteralExpr -> "'java.lang.String' \"$value\""
        is TypeParameter -> nameAsString
        is UnaryExpr -> "'${operator.asString()}'"
        is VariableDeclarator -> "$nameAsString '$typeAsString'"
        is VoidType -> "'void'"
        is ModuleDeclaration -> nameAsString
        is ModuleRequiresDirective -> nameAsString
        is ModuleExportsDirective -> nameAsString
        is ModuleProvidesDirective -> nameAsString
        is ModuleUsesDirective -> nameAsString
        is ModuleOpensDirective -> nameAsString
        is UnparsableStmt -> "'$this'"
        is ReceiverParameter -> "'$typeAsString'"
        is Modifier -> keyword.asString()
        is TextBlockLiteralExpr -> "'java.lang.String' \"${value.replace("\n", "\\n")}\""
        is PatternExpr -> "'$typeAsString'"
        is ArrayAccessExpr,
        is ArrayCreationLevel,
        is AssertStmt,
        is AssignExpr,
        is BlockComment,
        is BlockStmt,
        is BreakStmt,
        is CatchClause,
        is ConditionalExpr,
        is ContinueStmt,
        is DoStmt,
        is EmptyStmt,
        is EnclosedExpr,
        is ExpressionStmt,
        is FieldDeclaration,
        is ForStmt,
        is ForEachStmt,
        is IfStmt,
        is IntersectionType,
        is JavadocComment,
        is LambdaExpr,
        is LineComment,
        is MethodReferenceExpr,
        is ReturnStmt,
        is SuperExpr,
        is SwitchEntry,
        is SwitchStmt,
        is SynchronizedStmt,
        is ThisExpr,
        is ThrowStmt,
        is TryStmt,
        is TypeExpr,
        is UnionType,
        is UnknownType,
        is VariableDeclarationExpr,
        is WhileStmt,
        is WildcardType,
        is VarType,
        is SwitchExpr,
        is YieldStmt -> null

        else -> "[!`visit` unimplemented!]"
    }.let {
        it?.let { "$commonPrefix $it" } ?: commonPrefix
    }
}

/**
 * Dumps the AST of this node.
 *
 * @param indent The level of indentation to apply to all lines of the AST dump.
 * @param showAncestors Whether to show the ancestors of this node.
 * @param showChildren Whether to show all children (including indirect children) of this node.
 * @return String representation of the AST of the node.
 */
fun Node.astToString(
    indent: Int = 0,
    showAncestors: Boolean = true,
    showChildren: Boolean = true
): String = buildString {
    if (showAncestors) {
        ancestors.asReversed().forEach {
            append(' ' * indent)
            appendLine(it.asASTString())
        }
    }
    if (showChildren) {
        walk(TreeTraversal.PREORDER) {
            append(' ' * indent)
            appendLine(it.asASTString())
        }
    } else {
        append(' ' * indent)
        append(asASTString())
    }
}.trim('\n')

/**
 * Finds ancestors of this node with type [N] and matching the [predicate].
 */
inline fun <reified N> HasParentNode<*>.findAncestor(predicate: (N) -> Boolean = { true }): N? =
    ancestorsAsSequence().filterIsInstance<N>().firstOrNull(predicate)

/**
 * Finds the first node with type [N] and matching the [predicate].
 */
inline fun <reified N : Node> Node.findFirst(predicate: (N) -> Boolean = { true }): N? =
    asSequence().filterIsInstance<N>().firstOrNull { predicate(it) }

/**
 * Finds all nodes with type [N] and matching the [predicate], traversed using the [traversal] order.
 */
inline fun <reified N : Node> Node.findAll(
    traversal: TreeTraversal = TreeTraversal.PREORDER,
    crossinline predicate: (N) -> Boolean = { true }
): List<N> = asSequence(traversal).filterIsInstance<N>().filter { predicate(it) }.toList()

/**
 * @return The direct subtype of [TargetT] which is inherited (directly or indirectly) by [obj].
 */
inline fun <reified TargetT : Any> typeOfInstance(obj: TargetT): String {
    val targetClass = TargetT::class.java

    var clazz: Class<*> = obj::class.java
    while (clazz != targetClass) {
        if (clazz.superclass == targetClass || clazz.interfaces.any { it == targetClass }) {
            return clazz.simpleName
        }

        clazz = clazz.superclass
    }

    error("Cannot find direct subclass of ${TargetT::class.simpleName}")
}

/**
 * Creates a [ParserConfiguration] with the following defaults:
 *
 * - [ParserConfiguration.languageLevel] set to [ParserConfiguration.LanguageLevel.RAW].
 */
fun createParserConfiguration(
    symbolResolver: SymbolResolver? = null,
    configure: ParserConfiguration.() -> Unit = {}
): ParserConfiguration = ParserConfiguration().apply {
    languageLevel = ParserConfiguration.LanguageLevel.RAW

    configure()

    symbolResolver?.also {
        setSymbolResolver(it)
    }
}

/**
 * @return This expression with all nested [ArrayAccessExpr] removed.
 */
tailrec fun ArrayAccessExpr.getBaseExpression(): Expression =
    if (name !is ArrayAccessExpr) name else (name as ArrayAccessExpr).getBaseExpression()

val ArrayAccessExpr.arrayLevel: Int
    get() = generateSequence(this) { it.name as? ArrayAccessExpr }.count()

/**
 * Whether this type can be referenced by name, visibility notwithstanding.
 *
 * These include all top-level types and nested types. Local classes and anonymous classes are not referencable, since
 * no name can be formed to access these classes.
 */
val TypeDeclaration<*>.canBeReferencedByName: Boolean
    get() = fullyQualifiedName.isPresent && (this as? ClassOrInterfaceDeclaration)?.isLocalClassDeclaration != true

/**
 * Whether this method can be referenced by name, visibility notwithstanding.
 */
val CallableDeclaration<*>.canBeReferencedByName: Boolean
    get() = parentNode.map {
        when (it) {
            is TypeDeclaration<*> -> containingType.canBeReferencedByName
            is EnumConstantDeclaration -> true
            is ObjectCreationExpr -> false
            else -> unreachable("Don't know how to determine canBeReferencedByName for ${this::class.simpleName} with parentNode of type ${it::class.simpleName}")
        }
    }.get()

/**
 * @return The [ReferenceTypeLikeDeclaration] which this [CallableDeclaration] is contained within.
 */
val CallableDeclaration<*>.containingType: ReferenceTypeLikeDeclaration<*>
    get() = ancestorsAsSequence().firstNotNullOf { ReferenceTypeLikeDeclaration.createOrNull(it) }

/**
 * @return The [ReferenceTypeLikeDeclaration] which this [EnumConstantDeclaration] is contained within.
 */
val EnumConstantDeclaration.containingType: ReferenceTypeLikeDeclaration<*>
    get() = ancestorsAsSequence().firstNotNullOf { ReferenceTypeLikeDeclaration.createOrNull(it) }

/**
 * @return The [ReferenceTypeLikeDeclaration] which this [InitializerDeclaration] is contained within.
 */
val InitializerDeclaration.containingType: ReferenceTypeLikeDeclaration<*>
    get() = ancestorsAsSequence().firstNotNullOf { ReferenceTypeLikeDeclaration.createOrNull(it) }

/**
 * @return The [ReferenceTypeLikeDeclaration] which this [FieldDeclaration] is contained within.
 */
val FieldDeclaration.containingType: ReferenceTypeLikeDeclaration<*>
    get() = ancestorsAsSequence().firstNotNullOf { ReferenceTypeLikeDeclaration.createOrNull(it) }

operator fun <A, B> JavaParserPair<A, B>.component1(): A = a
operator fun <A, B> JavaParserPair<A, B>.component2(): B = b

val BodyDeclaration<*>.parentContainer: BodyDeclaration<*>?
    get() = ancestorsAsSequence().filterIsInstance<BodyDeclaration<*>>().firstOrNull()

val Expression.parentContainer: BodyDeclaration<*>?
    get() = ancestorsAsSequence()
        .filterIsInstance<BodyDeclaration<*>>()
        .firstOrNull { it is CallableDeclaration<*> || it is TypeDeclaration<*> }

/**
 * Obtains the qualified signature of this [CallableDeclaration].
 */
fun CallableDeclaration<*>.getQualifiedSignature(context: ReducerContext?): String {
    val enumConstantContext = parentNode.getOrNull() as? EnumConstantDeclaration

    val solBySS = runCatching {
        context?.symbolSolver?.resolveDeclaration<ResolvedMethodDeclaration>(this)
    }.getOrNull()
    if (solBySS != null) {
        return enumConstantContext?.getQualifiedName(context)
            ?.let { "$it.${solBySS.signature}" }
            ?: context?.let { solBySS.getFixedQualifiedSignature(it) }
            ?: solBySS.qualifiedSignature
    }

    return (enumConstantContext?.getQualifiedName(context) ?: findAncestor(TypeDeclaration::class.java).flatMap { it.fullyQualifiedName }.getOrNull())
        ?.let { "$it.${signature.asString()}" }
        ?: signature.asString()
}

context(ReducerContext)
fun CallableDeclaration<*>.getQualifiedSignature(): String =
    getQualifiedSignature(this@ReducerContext)

/**
 * Obtains the qualified name of this [EnumConstantDeclaration].
 */
fun EnumConstantDeclaration.getQualifiedName(context: ReducerContext?): String {
    val solBySS = runCatching {
        context?.symbolSolver?.resolveDeclaration<ResolvedEnumConstantDeclaration>(this)
    }.getOrNull()
    if (solBySS != null) {
        return "${solBySS.type.asReferenceType().qualifiedName}.$name"
    }

    return findAncestor(TypeDeclaration::class.java)
        .flatMap { it.fullyQualifiedName }
        .map { "$it.${nameAsString}" }
        .getOrElse { nameAsString }
}

context(ReducerContext)
fun EnumConstantDeclaration.getQualifiedName(): String =
    getQualifiedName(this@ReducerContext)

/**
 * Obtains the qualified name of this [VariableDeclarator].
 */
fun VariableDeclarator.getQualifiedName(context: ReducerContext?): String {
    val solBySS = runCatching {
        context?.symbolSolver?.resolveDeclaration<ResolvedFieldDeclaration>(this)
    }.getOrNull()
    if (solBySS != null) {
        return solBySS.qualifiedName
    }

    return findAncestor(TypeDeclaration::class.java)
        .flatMap { it.fullyQualifiedName }
        .map { "$it.${nameAsString}" }
        .getOrElse { nameAsString }
}

context(ReducerContext)
fun VariableDeclarator.getQualifiedName(): String =
    getQualifiedName(this@ReducerContext)

/**
 * The leftmost type in a [ClassOrInterfaceType], i.e. the least-nested class.
 */
val ClassOrInterfaceType.leftMostType: ClassOrInterfaceType
    get() = scope.map { it.leftMostType }.getOrDefault(this)

/**
 * [leftMostType] as a string representation.
 */
val ClassOrInterfaceType.leftMostTypeAsString: String
    get() = leftMostType.nameAsString

/**
 * The rightmost symbol referenced by this declaration, i.e. the most-specific symbol.
 */
val ImportDeclaration.rightMostSymbol: Name
    get() = name

/**
 * [rightMostSymbol] as a string representation.
 */
val ImportDeclaration.rightMostSymbolAsString: String
    get() = name.identifier

/**
 * The rightmost container type referenced by this declaration, i.e. the class containing the most-specific symbol.
 */
val ImportDeclaration.rightMostContainerType: Name
    get() = if (isStatic) rightMostSymbol.qualifier.get() else rightMostSymbol

/**
 * [rightMostContainerType] as a string representation.
 */
val ImportDeclaration.rightMostContainerTypeAsString: String
    get() = rightMostContainerType.identifier

/**
 * Left-most component of a [Name], i.e. the least-specific symbol name.
 */
val Name.leftMostComponent: String
    get() = qualifier.map { it.leftMostComponent }.getOrDefault(identifier)

/**
 * Whether [Node] refers to a field variable, i.e. either a [FieldDeclaration] or a [VariableDeclarator] nested in a
 * [FieldDeclaration].
 */
val Node.isFieldVar: Boolean
    get() = this is FieldDeclaration || (this is VariableDeclarator && parentNode.map { it is FieldDeclaration }.getOrDefault(false))

/**
 * Obtains the [VariableDeclarator] in this [TypeDeclaration] matching [name].
 */
fun TypeDeclaration<*>.getFieldVariableDecl(name: String): VariableDeclarator =
    getFieldByName(name).map { it.getFieldVariableDecl(name) }.get()

/**
 * Obtains the [VariableDeclarator] in this [FieldDeclaration] matching [name].
 */
fun FieldDeclaration.getFieldVariableDecl(name: String): VariableDeclarator =
    variables.singleOrNull { it.nameAsString == name }
        ?: error("Cannot find variable in field declaration named `$name` - Found: ${variables.map { it.nameAsString }}")

/**
 * Returns a nested [TypeDeclaration] in this type declaration with the specified [name].
 */
inline fun <reified T : TypeDeclaration<*>> TypeDeclaration<*>.getTypeByNameOrNull(name: String): T? =
    members.filterIsInstance<T>().singleOrNull { it.nameAsString == name }

/**
 * Returns a nested [TypeDeclaration] in this type declaration with the specified [name].
 */
inline fun <reified T : TypeDeclaration<*>> TypeDeclaration<*>.getTypeByName(name: String): T =
    checkNotNull(getTypeByNameOrNull<T>(name))

/**
 * Obtains the lines which this [Node] covers.
 */
val Node.lineRange: Optional<IntRange>
    get() = range.map { it.begin.line..it.end.line }

/**
 * Whether [node] resides within an [ExplicitConstructorInvocationStmt].
 */
fun isNodeInExplicitCtorStmt(node: Node): Boolean {
    return node.ancestorsAsSequence().any { it is ExplicitConstructorInvocationStmt }
}

/**
 * Whether [node] resides within the header of a [CallableDeclaration].
 *
 * The header of a [CallableDeclaration] includes:
 *
 * - [Annotations declared on the method][CallableDeclaration.annotations]
 * - [Type parameters][CallableDeclaration.typeParameters]
 * - [Method parameters][CallableDeclaration.parameters]
 * - [Exceptions declared in the throws clause][CallableDeclaration.thrownExceptions]
 * - [Receiver parameters][CallableDeclaration.receiverParameter]
 * - [Return type][MethodDeclaration.type]
 */
fun isNodeInCallableHeader(node: Node): Boolean {
    val idxOfMethodAncestor = node.ancestorsAsSequence().indexOfFirst { it is CallableDeclaration<*> }
    if (idxOfMethodAncestor == -1) {
        return false
    }

    val callableDecl = node.ancestorsAsSequence().drop(idxOfMethodAncestor).first() as CallableDeclaration<*>
    val directChildOfCallable = if (idxOfMethodAncestor == 0) {
        node
    } else {
        node.ancestorsAsSequence().drop(idxOfMethodAncestor - 1).first()
    }
    return buildList {
        addAll(callableDecl.annotations)
        addAll(callableDecl.typeParameters)
        addAll(callableDecl.parameters)
        addAll(callableDecl.thrownExceptions)
        callableDecl.receiverParameter.ifPresent { add(it) }
        if (callableDecl is MethodDeclaration) {
            add(callableDecl.type)
        }
    }.any {
        it == directChildOfCallable
    }
}

/**
 * Returns a single [ImportDeclaration] matching the given [name].
 *
 * @param isStatic Whether the import declaration is a static import.
 * @param isOnDemand Whether the import declaration is an on-demand import (`*`).
 */
fun NodeList<ImportDeclaration>.getImportByName(
    isStatic: Boolean? = null,
    isOnDemand: Boolean? = null,
    name: String
): ImportDeclaration? = singleOrNull {
    name == it.nameAsString &&
            isStatic?.equals(it.isStatic) != false &&
            isOnDemand?.equals(it.isAsterisk) != false
}

private val BodyDeclaration<*>.isDeclaringTypeAnonymous: Boolean
    get() = Navigator.demandParentNode(this) is ObjectCreationExpr

/**
 * Whether the [declaring type][ResolvedAnnotationMemberDeclaration.declaringType] of this annotation member declaration
 * is an [anonymous class][ResolvedReferenceTypeDeclaration.isAnonymousClass].
 */
val ResolvedAnnotationMemberDeclaration.isDeclaringTypeAnonymous: Boolean
    get() = toTypedAstOrNull<AnnotationMemberDeclaration>(null)?.isDeclaringTypeAnonymous
        ?: declaringType().isAnonymousClass

/**
 * Whether the [declaring type][ResolvedMethodLikeDeclaration.declaringType] of this method-like declaration is an
 * [anonymous class][ResolvedReferenceTypeDeclaration.isAnonymousClass].
 */
val ResolvedMethodLikeDeclaration.isDeclaringTypeAnonymous: Boolean
    get() = toTypedAstOrNull<MethodDeclaration>(null)?.isDeclaringTypeAnonymous
        ?: declaringType().isAnonymousClass

/**
 * Whether the [declaring type][ResolvedFieldDeclaration.declaringType] of this field declaration is an
 * [anonymous class][ResolvedReferenceTypeDeclaration.isAnonymousClass].
 */
val ResolvedFieldDeclaration.isDeclaringTypeAnonymous: Boolean
    get() = toTypedAstOrNull<MethodDeclaration>(null)?.isDeclaringTypeAnonymous
        ?: declaringType().isAnonymousClass

/**
 * A [Comparator] which compares two [CallableDeclaration] by obtaining the
 * [bytecode-based qualified signature][ResolvedMethodLikeDeclaration.bytecodeQualifiedSignature].
 */
@Suppress("FunctionName")
fun SymbolSolvedCallableDeclComparator(symbolSolver: FuzzySymbolSolver): Comparator<CallableDeclaration<*>> =
    Comparator.comparing {
        symbolSolver.resolveDeclaration<ResolvedMethodLikeDeclaration>(it).bytecodeQualifiedSignature
    }

val ENUM_CONSTANT_DECL_COMPARATOR: Comparator<EnumConstantDeclaration> =
    Comparator.comparing { it.getQualifiedName(null) }
val FIELD_DECL_COMPARATOR: Comparator<VariableDeclarator> =
    Comparator.comparing { it.getQualifiedName(null) }
val TYPE_DECL_COMPARATOR = Comparator<TypeDeclaration<*>> { o1, o2 ->
    when {
        o1.fullyQualifiedName.isPresent && o2.fullyQualifiedName.isPresent ->
            o1.fullyQualifiedName.get().compareTo(o2.fullyQualifiedName.get())

        o1.fullyQualifiedName.isPresent -> 1
        o2.fullyQualifiedName.isPresent -> -1
        else -> NodeAstComparator.compare(o1, o2)
    }
}

@Suppress("FunctionName")
fun ResolvedCallableDeclComparator(reducerContext: ReducerContext): Comparator<ResolvedMethodLikeDeclaration> =
    ResolvedCallableDeclComparator(reducerContext.symbolSolver)

@Suppress("FunctionName")
fun ResolvedCallableDeclComparator(symbolSolver: FuzzySymbolSolver): Comparator<ResolvedMethodLikeDeclaration> =
    Comparator<ResolvedMethodLikeDeclaration> { o1, o2 ->
        val o1TypeIsAnon = o1.isDeclaringTypeAnonymous
        val o2TypeIsAnon = o2.isDeclaringTypeAnonymous

        when {
            !o1TypeIsAnon && !o2TypeIsAnon -> {
                o1.name.compareTo(o2.name).takeIf { it != 0 }
                    ?: o1.numberOfParams.compareTo(o2.numberOfParams).takeIf { it != 0 }
                    ?: o1.fixedQualifiedScopeName.compareTo(o2.fixedQualifiedScopeName).takeIf { it != 0 }
                    ?: o1.getFixedSignature(symbolSolver).compareTo(o2.getFixedSignature(symbolSolver)).takeIf { it != 0 }
                    ?: TypeParamDeclListComparator.compare(o1.typeParameters, o2.typeParameters)
            }

            !o1TypeIsAnon && o2TypeIsAnon -> 1
            o1TypeIsAnon && !o2TypeIsAnon -> -1
            else -> {
                val o1Ast = o1.toTypedAstOrNull<CallableDeclaration<*>>(null)
                val o2Ast = o2.toTypedAstOrNull<CallableDeclaration<*>>(null)

                when {
                    o1Ast != null && o2Ast != null -> NodeAstComparator.compare(o1Ast, o2Ast)
                    o1Ast != null -> -1
                    o2Ast != null -> 1
                    else -> o1.hashCode().compareTo(o2.hashCode())
                }
            }
        }
    }

val RESOLVED_TYPE_DECL_COMPARATOR = Comparator<ResolvedReferenceTypeDeclaration> { o1, o2 ->
    when {
        !o1.isAnonymousClass && !o2.isAnonymousClass -> {
            o1.name.compareTo(o2.name).takeIf { it != 0 }
                ?: o1.qualifiedName.compareTo(o2.qualifiedName)
        }

        !o1.isAnonymousClass && o2.isAnonymousClass -> 1
        o1.isAnonymousClass && !o2.isAnonymousClass -> -1
        else -> {
            val o1Ast = o1.toTypedAstOrNull<ObjectCreationExpr>(null)
            val o2Ast = o2.toTypedAstOrNull<ObjectCreationExpr>(null)

            when {
                o1Ast != null && o2Ast != null -> NodeAstComparator.compare(o1Ast, o2Ast)
                o1Ast != null -> -1
                o2Ast != null -> 1
                else -> o1.hashCode().compareTo(o2.hashCode())
            }
        }
    }
}
val RESOLVED_REF_TYPE_COMPARATOR: Comparator<ResolvedReferenceType> =
    Comparator.comparing(ResolvedReferenceType::toResolvedTypeDeclaration, RESOLVED_TYPE_DECL_COMPARATOR)

/**
 * Similar to [RESOLVED_REF_TYPE_COMPARATOR], but also takes type parameters of the type in the comparison.
 */
val RESOLVED_REF_TYPE_TP_COMPARATOR: Comparator<ResolvedReferenceType> = Comparator<ResolvedReferenceType> { o1, o2 ->
    val resolvedTypeDeclCmp = RESOLVED_REF_TYPE_COMPARATOR.compare(o1, o2)

    when {
        resolvedTypeDeclCmp != 0 -> resolvedTypeDeclCmp
        o1.toResolvedTypeDeclaration().isAnonymousClass || o2.toResolvedTypeDeclaration().isAnonymousClass -> 0
        else -> {
            val o1Tps = o1.typeParametersMap.map { it.a.name to it.b }
            val o2Tps = o2.typeParametersMap.map { it.a.name to it.b }

            if (o1Tps.size != o2Tps.size) {
                o1Tps.size.compareTo(o2Tps.size)
            } else {
                o1Tps.zip(o2Tps)
                    .firstNotNullOfOrNull { (o1Tp, o2Tp) ->
                        o1Tp.first.compareTo(o2Tp.first).takeIf { it != 0 }
                            ?: RESOLVED_TYPE_COMPARATOR.compare(o1Tp.second, o2Tp.second).takeIf { it != 0 }
                    }
                    ?: 0
            }
        }
    }
}

val RESOLVED_TYPE_COMPARATOR: Comparator<ResolvedType> = Comparator<ResolvedType> { o1, o2 ->
    fun resolvedTypeComparatorImpl(o1: ResolvedType, o2: ResolvedType): Int {
        // Ordering: Null < Void < ResolvedReferenceType < ResolvedArrayType [1] ... [n] < Other Types
        return when {
            // NullType
            o1.isNull && o2.isNull -> 0
            o1.isNull -> -1
            o2.isNull -> 1

            // VoidType
            o1.isVoid && o2.isVoid -> 0
            o1.isVoid -> -1
            o2.isVoid -> 1

            // ResolvedReferenceType
            o1.isReferenceType && o2.isReferenceType -> RESOLVED_REF_TYPE_TP_COMPARATOR.compare(o1.asReferenceType(), o2.asReferenceType())
            o1.isReferenceType -> -1
            o2.isReferenceType -> 1

            // ResolvedArrayType
            o1.isArray && o2.isArray -> resolvedTypeComparatorImpl(o1.asArrayType().componentType, o2.asArrayType().componentType)
            o1.isArray -> -1
            o2.isArray -> 1

            else -> o1.hashCode().compareTo(o2.hashCode())
        }
    }

    resolvedTypeComparatorImpl(o1, o2)
}

val RESOLVED_VALUE_COMPARATOR = Comparator<ResolvedValueDeclaration> { o1, o2 ->
    val anonCmp by lazy(LazyThreadSafetyMode.NONE) {
        val o1Ast = o1.toTypedAstOrNull<Node>(null)
        val o2Ast = o2.toTypedAstOrNull<Node>(null)

        when {
            o1Ast != null && o2Ast != null -> NodeAstComparator.compare(o1Ast, o2Ast)
            o1Ast != null -> -1
            o2Ast != null -> 1
            else -> o1.hashCode().compareTo(o2.hashCode())
        }
    }

    val o1TypeIsAnon = when (o1) {
        is ResolvedAnnotationMemberDeclaration -> o1.isDeclaringTypeAnonymous
        is ResolvedEnumConstantDeclaration -> o1.type.asReferenceType().toResolvedTypeDeclaration().isAnonymousClass
        is ResolvedFieldDeclaration -> o1.isDeclaringTypeAnonymous
        else -> return@Comparator anonCmp
    }

    val o2TypeIsAnon = when (o2) {
        is ResolvedAnnotationMemberDeclaration -> o2.isDeclaringTypeAnonymous
        is ResolvedEnumConstantDeclaration -> o2.type.asReferenceType().toResolvedTypeDeclaration().isAnonymousClass
        is ResolvedFieldDeclaration -> o2.isDeclaringTypeAnonymous
        else -> return@Comparator anonCmp
    }

    when {
        !o1TypeIsAnon && !o2TypeIsAnon -> {
            o1.name.compareTo(o2.name).takeIf { it != 0 }
                ?: o1.qualifiedName.compareTo(o2.qualifiedName)
        }

        !o1TypeIsAnon && o2TypeIsAnon -> 1
        o1TypeIsAnon && !o2TypeIsAnon -> -1
        else -> anonCmp
    }
}

/**
 * Type-safe version of [AssociableToAST.toAst].
 *
 * @param reducerContext [ReducerContext] to remap the given node to a cached compilation unit.
 * @return The node associated with [this], or `null` if no AST is associated with this symbol.
 */
inline fun <reified N : Node> AssociableToAST.toTypedAstOrNull(reducerContext: ReducerContext?): N? =
    toAst(N::class.java).map { reducerContext?.mapNodeInLoadedSet(it) ?: it }.getOrNull()

context(ReducerContext)
inline fun <reified N : Node> AssociableToAST.toTypedAstOrNull(): N? = toTypedAstOrNull(this@ReducerContext)

/**
 * Type-safe and not-null version of [AssociableToAST.toAst].
 *
 * @param reducerContext [ReducerContext] to remap the given node to a cached compilation unit.
 * @return The node associated with [this].
 * @throws IllegalStateException if no AST is associated with this symbol.
 */
inline fun <reified N : Node> AssociableToAST.toTypedAst(reducerContext: ReducerContext?): N =
    toTypedAstOrNull(reducerContext) ?: error("No AST is associated with this symbol ${(this as? ResolvedDeclaration)?.describeQName()}")

context(ReducerContext)
inline fun <reified N : Node> AssociableToAST.toTypedAst(): N = toTypedAst(this@ReducerContext)

/**
 * Returns the [ExplicitConstructorInvocationStmt] in this [ConstructorDeclaration].
 */
val ConstructorDeclaration.explicitCtorInvocationStmt: ExplicitConstructorInvocationStmt?
    get() {
        val stmts = body.statements
        if (stmts.isEmpty()) {
            return null
        }

        val explicitCtorStmt = stmts
            .firstOrNull { it.isExplicitConstructorInvocationStmt }
            ?.asExplicitConstructorInvocationStmt()

        check(stmts.count { it.isExplicitConstructorInvocationStmt } in 0..1)
        check(explicitCtorStmt?.let { stmts.first() === explicitCtorStmt } != false)

        return explicitCtorStmt
    }

/**
 * Based on JLS 8 § 11.2.1.
 */
context(ReducerContext)
fun getCanThrowExceptionSet(expr: Expression): Map<Node, List<ResolvedReferenceType>> {
    return buildMap {
        when (expr) {
            is AnnotationExpr -> {}
            is ArrayAccessExpr -> {
                putAll(getCanThrowExceptionSet(expr.name))
                putAll(getCanThrowExceptionSet(expr.index))
            }

            is ArrayCreationExpr -> {
                expr.levels.forEach { lvl -> lvl.dimension.ifPresent { putAll(getCanThrowExceptionSet(it)) } }
                expr.initializer.ifPresent { putAll(getCanThrowExceptionSet(it)) }
            }

            is ArrayInitializerExpr -> expr.values.forEach {
                putAll(getCanThrowExceptionSet(it))
            }

            is AssignExpr -> {
                putAll(getCanThrowExceptionSet(expr.target))
                putAll(getCanThrowExceptionSet(expr.value))
            }

            is BinaryExpr -> {
                putAll(getCanThrowExceptionSet(expr.left))
                putAll(getCanThrowExceptionSet(expr.right))
            }

            is CastExpr -> putAll(getCanThrowExceptionSet(expr.expression))
            is ClassExpr -> {}
            is ConditionalExpr -> {
                putAll(getCanThrowExceptionSet(expr.condition))
                putAll(getCanThrowExceptionSet(expr.thenExpr))
                putAll(getCanThrowExceptionSet(expr.elseExpr))
            }

            is EnclosedExpr -> putAll(getCanThrowExceptionSet(expr.inner))
            is FieldAccessExpr -> putAll(getCanThrowExceptionSet(expr.scope))
            is InstanceOfExpr -> putAll(getCanThrowExceptionSet(expr.expression))
            is LambdaExpr -> {
                // A lambda expression (§15.27) can throw no exception classes.
            }

            is LiteralExpr -> {}
            is MethodCallExpr -> {
                // A method invocation expression (§15.12) can throw an exception class E iff either:

                // - The method invocation expression is of the form Primary . [TypeArguments] Identifier and the
                //   Primary expression can throw E; or
                expr.scope.ifPresent { putAll(getCanThrowExceptionSet(it)) }

                // - Some expression of the argument list can throw E; or
                expr.arguments.forEach { putAll(getCanThrowExceptionSet(it)) }

                // - E is one of the exception types of the invocation type of the chosen method (§15.12.2.6).
                val resolvedDecl = resolveDeclaration<ResolvedDeclaration>(expr) as? ResolvedMethodDeclaration
                val checkedExceptions = resolvedDecl?.specifiedExceptions
                    ?.map { it.asReferenceType() }
                    ?.filter { it.isCheckedException() }
                    .orEmpty()
                if (checkedExceptions.isNotEmpty()) {
                    put(expr, checkedExceptions)
                }
            }

            is MethodReferenceExpr -> putAll(getCanThrowExceptionSet(expr.scope))
            is NameExpr -> {}
            is ObjectCreationExpr -> {
                // A class instance creation expression (§15.9) can throw an exception class E iff either:

                // - The expression is a qualified class instance creation expression and the qualifying expression
                //   can throw E; or
                expr.scope.ifPresent { putAll(getCanThrowExceptionSet(it)) }

                // - Some expression of the argument list can throw E; or
                expr.arguments.forEach { putAll(getCanThrowExceptionSet(it)) }

                // - E is one of the exception types of the invocation type of the chosen constructor (§15.12.2.6);
                //   or
                val resolvedDecl = resolveDeclaration<ResolvedConstructorDeclaration>(expr)
                val checkedExceptions = resolvedDecl.specifiedExceptions
                    .map { it.asReferenceType() }
                    .filter { it.isCheckedException() }
                if (checkedExceptions.isNotEmpty()) {
                    put(expr, checkedExceptions)
                }

                // - The class instance creation expression includes a ClassBody, and some instance initializer or
                //   instance variable initializer in the ClassBody can throw E.
                expr.anonymousClassBody.ifPresent { anonClassBody ->
                    anonClassBody.filterIsInstance<InitializerDeclaration>()
                        .forEach { putAll(getCanThrowExceptionSet(it.body)) }
                    anonClassBody.filterIsInstance<FieldDeclaration>()
                        .flatMap { it.variables }
                        .forEach { varDecl ->
                            varDecl.initializer.ifPresent {
                                putAll(getCanThrowExceptionSet(it))
                            }
                        }
                }
            }

            is SuperExpr -> {}
            is SwitchExpr -> {
                // A switch expression (§15.28) can throw an exception class E iff either:

                // - The selector expression can throw E; or
                putAll(getCanThrowExceptionSet(expr.selector))

                // - Some switch rule expression, switch rule block, switch rule throw statement, or switch labeled
                //   statement group in the switch block can throw E.
                expr.entries
                    .forEach {
                        it.labels.forEach { putAll(getCanThrowExceptionSet(it)) }
                        it.statements.forEach { putAll(getCanThrowExceptionSet(it)) }
                    }
            }

            is ThisExpr -> {}
            is TypeExpr -> {}
            is UnaryExpr -> putAll(getCanThrowExceptionSet(expr.expression))
            is VariableDeclarationExpr -> {
                expr.variables
                    .forEach { varDecl ->
                        varDecl.initializer.ifPresent {
                            putAll(getCanThrowExceptionSet(it))
                        }
                    }
            }

            else -> {
                LOGGER.warn("getCanThrowExceptionSet unimplemented for expression type `${expr::class.simpleName}`")

                // For every other kind of expression, the expression can throw an exception class E iff one of its
                // immediate subexpressions can throw E.
                expr.childNodes
                    .filterIsInstance<Expression>()
                    .forEach { putAll(getCanThrowExceptionSet(it)) }
            }
        }
    }
}

/**
 * Based on JLS 8 § 11.2.2.
 */
context(ReducerContext)
fun getCanThrowExceptionSet(stmt: Statement): Map<Node, List<ResolvedReferenceType>> {
    return buildMap {
        when (stmt) {
            is AssertStmt -> putAll(getCanThrowExceptionSet(stmt.check))
            is BlockStmt -> stmt.statements.forEach {
                putAll(getCanThrowExceptionSet(it))
            }

            is BreakStmt,
            is ContinueStmt -> {}

            is DoStmt -> {
                putAll(getCanThrowExceptionSet(stmt.condition))
                putAll(getCanThrowExceptionSet(stmt.body))
            }

            is EmptyStmt -> {}
            is ExplicitConstructorInvocationStmt -> {
                // An explicit constructor invocation statement (§8.8.7.1) can throw an exception class E iff
                // either:

                // - Some expression of the constructor invocation's parameter list can throw E; or
                stmt.arguments.forEach {
                    putAll(getCanThrowExceptionSet(it))
                }

                // - E is determined to be an exception class of the throws clause of the constructor that is
                // invoked (§15.12.2.6).
                val resolvedDecl = resolveDeclaration<ResolvedConstructorDeclaration>(stmt)
                val checkedExceptions = resolvedDecl.specifiedExceptions
                    .map { it.asReferenceType() }
                    .filter { it.isCheckedException() }
                if (checkedExceptions.isNotEmpty()) {
                    put(stmt, checkedExceptions)
                }
            }

            is ExpressionStmt -> putAll(getCanThrowExceptionSet(stmt.expression))
            is ForStmt -> {
                stmt.initialization.forEach { putAll(getCanThrowExceptionSet(it)) }
                stmt.compare.ifPresent { putAll(getCanThrowExceptionSet(it)) }
                stmt.update.forEach { putAll(getCanThrowExceptionSet(it)) }
                putAll(getCanThrowExceptionSet(stmt.body))
            }

            is ForEachStmt -> {
                putAll(getCanThrowExceptionSet(stmt.variable))
                putAll(getCanThrowExceptionSet(stmt.iterable))
                putAll(getCanThrowExceptionSet(stmt.body))
            }

            is IfStmt -> {
                putAll(getCanThrowExceptionSet(stmt.condition))
                putAll(getCanThrowExceptionSet(stmt.thenStmt))
                stmt.elseStmt.ifPresent { putAll(getCanThrowExceptionSet(it)) }
            }

            is LabeledStmt -> putAll(getCanThrowExceptionSet(stmt.statement))
            is LocalClassDeclarationStmt,
            is LocalRecordDeclarationStmt -> {}

            is ReturnStmt -> stmt.expression.ifPresent { putAll(getCanThrowExceptionSet(it)) }
            is SwitchStmt -> {
                stmt.entries
                    .flatMap { it.statements }
                    .forEach { putAll(getCanThrowExceptionSet(it)) }
            }

            is SynchronizedStmt -> putAll(getCanThrowExceptionSet(stmt.body))
            is ThrowStmt -> {
                // A throw statement (§14.18) whose thrown expression has static type E and is not a final or
                // effectively final exception parameter can throw E or any exception class that the thrown
                // expression can throw.
                calculateType(stmt.expression)
                    .takeIf { it.isReferenceType }
                    ?.asReferenceType()
                    ?.takeIf { it.isCheckedException() }
                    ?.let { put(stmt.expression, listOf(it)) }

                putAll(getCanThrowExceptionSet(stmt.expression))

                // Note: We do not handle the case where the thrown expression is a final or effectively final
                // exception parameter of a catch clause, since those rules only affect the compile-time rules for
                // checking whether throwing an exception E within the try block is legal
            }

            is TryStmt -> {
                // A try statement (§14.20) can throw an exception class E iff either:

                // - The try block can throw E, or an expression used to initialize a resource (in a
                //   try-with-resources statement) can throw E, or the automatic invocation of the close() method of
                //   a resource (in a try-with-resources statement) can throw E, and E is not assignment compatible
                //   with any catchable exception class of any catch clause of the try statement, and either no
                //   finally block is present or the finally block can complete normally; or
                if (stmt.finallyBlock.map { it.canCompleteNormally() }.getOrDefault(true)) {
                    val catchClauseCaughtTypes = stmt.catchClauses
                        .map { it.parameter.type }
                        .map { toResolvedType<ResolvedType>(it) }

                    val allExceptionsInInitStmt = stmt.resources.map { getCanThrowExceptionSet(it) }
                    val allExceptionsInTryStmt = getCanThrowExceptionSet(stmt.tryBlock)
                    val allExceptionsInClose = stmt.resources
                        .asSequence()
                        .map { it.asVariableDeclarationExpr().variables.single().initializer.get() }
                        .map { (it as Node) to calculateType(it) }
                        .map { (k, v) -> k to v.asReferenceType().toResolvedTypeDeclaration() }
                        .map { (k, v) ->
                            k to symbolSolverCache.getAllMethods(v).single { it.name == "close" && it.noParams == 0 }
                        }
                        .map { (k, v) -> k to v.declaration.specifiedExceptions.map { it.asReferenceType() } }
                        .toList()

                    val propagatedExceptions = buildMap {
                            allExceptionsInInitStmt.forEach { putAll(it) }
                            putAll(allExceptionsInTryStmt)
                            putAll(allExceptionsInClose)
                        }
                        .asSequence()
                        .map { (k, v) ->
                            // Remove all exceptions which can be caught by any catch clause
                            k to v.filter { ex ->
                                catchClauseCaughtTypes.none { it.isAssignableBy(ex) }
                            }
                        }
                        .mapNotNull { (k, v) -> v.takeIf { it.isNotEmpty() }?.let { k to it } }

                    putAll(propagatedExceptions)
                }

                // - Some catch block of the try statement can throw E and either no finally block is present or the
                //   finally block can complete normally; or
                if (stmt.finallyBlock.map { it.canCompleteNormally() }.getOrDefault(true)) {
                    stmt.catchClauses.forEach { catchClause ->
                        putAll(getCanThrowExceptionSet(catchClause.body))
                    }
                }

                // - A finally block is present and can throw E.
                stmt.finallyBlock.ifPresent {
                    putAll(getCanThrowExceptionSet(it))
                }
            }

            is UnparsableStmt -> {}
            is WhileStmt -> {
                putAll(getCanThrowExceptionSet(stmt.condition))
                putAll(getCanThrowExceptionSet(stmt.body))
            }

            else -> {
                LOGGER.warn("getCanThrowExceptionSet unimplemented for statement type `${stmt::class.simpleName}`")

                // Any other statement S can throw an exception class E iff an expression or statement immediately
                // contained in S can throw E.
                stmt.childNodes
                    .forEach {
                        when (it) {
                            is Expression -> putAll(getCanThrowExceptionSet(it))
                            is Statement -> putAll(getCanThrowExceptionSet(it))
                        }
                    }
            }
        }
    }
}

context(ReducerContext)
private fun BreakStmt.exits(stmt: Statement): Boolean {
    if (label.isPresent && (stmt as? LabeledStmt)?.label != label.getOrNull()) {
        return false
    }

    return ancestorsAsSequence()
        .takeWhile {
            if (label.isPresent) {
                check(!(it is MethodDeclaration || it is ConstructorDeclaration || it is InitializerDeclaration || it is LambdaExpr)) {
                    "Reached immediately enclosing method, constructor, initializer, or lambda body without finding a candidate for labeled BreakStmt"
                }
            } else {
                check(!(it is MethodDeclaration || it is ConstructorDeclaration || it is InitializerDeclaration)) {
                    "Reached immediately enclosing method, constructor, or initializer without finding a candidate for BreakStmt"
                }
            }

            it !== stmt
        }
        .all {
            val parentNode = it.parentNode.get()
            if (parentNode is TryStmt && parentNode.tryBlock.statements.any { tryStmt -> tryStmt === it }) {
                parentNode.finallyBlock
                    .map { it.canCompleteNormally() }
                    .getOrDefault(false)
            } else {
                true
            }
        }
}

context(ReducerContext)
private fun ContinueStmt.continues(stmt: DoStmt): Boolean {
    if (label.isPresent && (stmt.parentNode.getOrNull() as? LabeledStmt)?.label != label.getOrNull()) {
        return false
    }

    return ancestorsAsSequence()
        .takeWhile {
            if (label.isPresent) {
                check(!(it is MethodDeclaration || it is ConstructorDeclaration || it is InitializerDeclaration || it is LambdaExpr)) {
                    "Reached immediately enclosing method, constructor, initializer, or lambda body without finding a candidate for labeled BreakStmt"
                }
            } else {
                check(!(it is MethodDeclaration || it is ConstructorDeclaration || it is InitializerDeclaration)) {
                    "Reached immediately enclosing method, constructor, or initializer without finding a candidate for BreakStmt"
                }
            }

            it !== stmt
        }
        .all {
            if (!label.isPresent && (it is WhileStmt || it is DoStmt || it is ForStmt)) {
                return@all false
            }

            val parentNode = it.parentNode.get()
            if (parentNode is TryStmt && parentNode.tryBlock.statements.any { tryStmt -> tryStmt === it }) {
                parentNode.finallyBlock
                    .map { it.canCompleteNormally() }
                    .getOrDefault(false)
            } else {
                true
            }
        }
}

/**
 * Checks whether this [Statement] can complete normally under the definition specified in the Java Language Standard.
 */
context(ReducerContext)
fun Statement.canCompleteNormally(): Boolean {
    when (this) {
        is AssertStmt -> {
            // An assert statement can complete normally iff it is reachable.
            return isReachable()
        }

        is BlockStmt -> {
            return if (statements.isEmpty()) {
                // An empty block that is not a switch block can complete normally iff it is reachable.
                isReachable()
            } else {
                // A non-empty block that is not a switch block can complete normally iff the last statement in it can
                // complete normally.
                statements.last().canCompleteNormally()
            }
        }

        is DoStmt -> {
            // A do statement can complete normally iff at least one of the following is true:

            // The contained statement can complete normally and the condition expression is not a constant expression
            // (§15.28) with value true.
            if (body.canCompleteNormally() && condition != BooleanLiteralExpr(true)) {
                return true
            }

            // The do statement contains a reachable continue statement with no label, and the do statement is the
            // innermost while, do, or for statement that contains that continue statement, and the continue statement
            // continues that do statement, and the condition expression is not a constant expression with value true.

            // The do statement contains a reachable continue statement with a label L, and the do statement has label
            // L, and the continue statement continues that do statement, and the condition expression is not a constant
            // expression with value true.
            if (findFirst<ContinueStmt> { it.isReachable() && it.continues(this) } != null && condition != BooleanLiteralExpr(true)) {
                return true
            }

            // There is a reachable break statement that exits the do statement.
            return findFirst<BreakStmt> { it.isReachable() && it.exits(this) } != null
        }

        is EmptyStmt -> {
            // An empty statement can complete normally iff it is reachable.
            return isReachable()
        }

        is ExpressionStmt -> {
            val isLocalVarDeclStmt = expression is VariableDeclarationExpr

            if (isLocalVarDeclStmt) {
                // A local variable declaration statement can complete normally iff it is reachable.
                return isReachable()
            }

            // An expression statement can complete normally iff it is reachable.
            return isReachable()
        }

        is ExplicitConstructorInvocationStmt -> {
            return true
        }

        is ForStmt -> {
            // A basic for statement can complete normally iff at least one of the following is true:

            // The for statement is reachable, there is a condition expression, and the condition expression is not a
            // constant expression (§15.28) with value true.
            if (isReachable() && compare.isPresent && compare.get() != BooleanLiteralExpr(true)) {
                return true
            }

            // There is a reachable break statement that exits the for statement.
            return findFirst<BreakStmt> { it.isReachable() && it.exits(this) } != null
        }

        is ForEachStmt -> {
            // An enhanced for statement can complete normally iff it is reachable.
            return isReachable()
        }

        is IfStmt -> {
            return if (elseStmt.isPresent) {
                // An if-then-else statement can complete normally iff the then-statement can complete normally or the
                // else-statement can complete normally.
                thenStmt.canCompleteNormally() || elseStmt.get().canCompleteNormally()
            } else {
                // An if-then statement can complete normally iff it is reachable.
                isReachable()
            }
        }

        is LabeledStmt -> {
            // A labeled statement can complete normally if at least one of the following is true:
            if (statement.canCompleteNormally()) {
                // The contained statement can complete normally.
                return true
            }

            // There is a reachable break statement that exits the labeled statement.
            return findFirst<BreakStmt> { it.isReachable() && it.exits(this) } != null
        }

        is LocalClassDeclarationStmt -> {
            // A local class declaration statement can complete normally iff it is reachable.
            return isReachable()
        }

        is SwitchStmt -> {
            val stmts = entries.flatMap { it.statements }

            // A switch statement can complete normally iff at least one of the following is true:
            val isEmptyOrContainsOnlyLabels = entries.isEmpty() || stmts.isEmpty()
            if (isEmptyOrContainsOnlyLabels) {
                // The switch block is empty or contains only switch labels.
                return true
            }

            // The last statement in the switch block can complete normally.
            if (stmts.last().canCompleteNormally()) {
                return true
            }

            // There is at least one switch label after the last switch block statement group.
            if (entries.indexOfLast { it.statements.isNotEmpty() } < entries.lastIndex) {
                return true
            }

            // The switch block does not contain a default label.
            if (entries.none { it.labels.isEmpty() && it.statements.isNotEmpty() }) {
                return true
            }

            // There is a reachable break statement that exits the switch statement.
            return findFirst<BreakStmt> { it.isReachable() && it.exits(this) } != null
        }

        is SynchronizedStmt -> {
            // A synchronized statement can complete normally iff the contained statement can complete normally.
            return body.canCompleteNormally()
        }

        is TryStmt -> {
            // A try statement can complete normally iff both of the following are true:

            //    The try block can complete normally or any catch block can complete normally.
            if (!(tryBlock.canCompleteNormally() || catchClauses.any { it.body.canCompleteNormally() })) {
                return false
            }

            //    If the try statement has a finally block, then the finally block can complete normally.
            return finallyBlock.map { it.canCompleteNormally() }.getOrDefault(true)
        }

        is WhileStmt -> {
            // A while statement can complete normally iff at least one of the following is true:
            // - The while statement is reachable and the condition expression is not a constant expression (§15.28)
            // with value true.
            if (isReachable() && condition != BooleanLiteralExpr(true)) {
                return true
            }

            // There is a reachable break statement that exits the while statement.
            return findFirst<BreakStmt> { it.isReachable() && it.exits(this) } != null
        }

        is BreakStmt,
        is ContinueStmt,
        is ReturnStmt,
        is ThrowStmt -> {
            // A break, continue, return, or throw statement cannot complete normally.
            return false
        }

        else -> unreachable(astToString(showChildren = false))
    }
}

/**
 * Checks whether this [Statement] is reachable under the definition specified in the Java Language Standard.
 */
context(ReducerContext)
fun Statement.isReachable(): Boolean {
    val parentNode = parentNode.getOrNull() ?: return true
    when (parentNode) {
        is BlockStmt -> {
            val stmtIdx = parentNode.statements.indexOfFirst { it === this }
            return if (stmtIdx == 0) {
                // The first statement in a non-empty block that is not a switch block is reachable iff the block is
                // reachable.
                parentNode.isReachable()
            } else {
                // Every other statement S in a non-empty block that is not a switch block is reachable iff the
                // statement preceding S can complete normally.
                parentNode.statements[stmtIdx - 1].canCompleteNormally()
            }
        }

        is CatchClause -> {
            // If the compilation unit cannot be found, assume that we are in the cloning process and the cloning
            // process will handle catch clauses
            if (!findCompilationUnit().isPresent) {
                return true
            }

            val tryStmt = checkNotNull(parentNode.parentNode.get() as? TryStmt)

            // A catch block C is reachable iff both of the following are true:

            // Either the type of C's parameter is an unchecked exception type or Exception or a superclass of
            // Exception, or some expression or throw statement in the try block is reachable and can throw a checked
            // exception whose type is assignable to the type of C's parameter. (An expression is reachable iff the
            // innermost statement containing it is reachable.)
            val catchClauseType = toResolvedType<ResolvedType>(parentNode.parameter.type)
            val isEffectivelyUncheckedException by lazy(LazyThreadSafetyMode.NONE) {
                (if (catchClauseType is ResolvedUnionType) catchClauseType.elements else listOf(catchClauseType))
                    .map { it.asReferenceType() }.any { it.isUncheckedException() } ||
                        catchClauseType.asReferenceTypeOrNull()?.qualifiedName == javaLangExceptionFqn ||
                        catchClauseType.isAssignableBy(createResolvedRefType(typeSolver.solveType(javaLangExceptionFqn)))
            }
            val hasReachableCheckedExceptionThrown by lazy(LazyThreadSafetyMode.NONE) {
                getCanThrowExceptionSet(tryStmt.tryBlock).any { (node, exTypes) ->
                    val stmt = if (node is Expression) {
                        node.findAncestor(Statement::class.java).get()
                    } else {
                        checkNotNull(node as? Statement)
                    }

                    if (!stmt.isReachable()) {
                        return@any false
                    }

                    exTypes.any { catchClauseType.isAssignableBy(it) }
                }
            }

            if (!(isEffectivelyUncheckedException || hasReachableCheckedExceptionThrown)) {
                return false
            }

            // There is no earlier catch block A in the try statement such that the type of C's parameter is the same as
            // or a subclass of the type of A's parameter.
            val catchClauseIdx = tryStmt.catchClauses.indexOfFirst { it === parentNode }
            return tryStmt.catchClauses.take(catchClauseIdx)
                .none { toResolvedType<ResolvedType>(it.parameter.type).isAssignableBy(catchClauseType) }
        }

        is DoStmt -> {
            check(parentNode.body === this)

            // The contained statement is reachable iff the do statement is reachable.
            return parentNode.isReachable()
        }

        is ForStmt -> {
            check(parentNode.body === this)

            // The contained statement is reachable iff the for statement is reachable and the condition expression
            // is not a constant expression whose value is false.
            return parentNode.isReachable() && parentNode.compare.map { it != BooleanLiteralExpr(false) }.getOrDefault(true)
        }

        is ForEachStmt -> {
            check(parentNode.body === this)

            return parentNode.isReachable()
        }

        is IfStmt -> {
            check(parentNode.thenStmt === this || parentNode.elseStmt.map { it === this }.getOrDefault(false))

            // The then-statement is reachable iff the if-then statement is reachable.
            // The then-statement is reachable iff the if-then-else statement is reachable.
            // The else-statement is reachable iff the if-then-else statement is reachable.
            return parentNode.isReachable()
        }

        is LabeledStmt -> {
            check(parentNode.statement === this)

            // The contained statement is reachable iff the labeled statement is reachable.
            return parentNode.isReachable()
        }

        is SwitchEntry -> {
            // A statement in a switch block is reachable iff its switch statement is reachable
            if (!parentNode.findAncestor(SwitchStmt::class.java).get().isReachable()) {
                return false
            }

            // and at least one of the following is true:
            val stmtIdx = parentNode.statements.indexOfLast { it === this }
            return if (stmtIdx == 0) {
                // It bears a case or default label.
                true
            } else {
                // There is a statement preceding it in the switch block and that preceding statement can complete
                // normally.
                parentNode.statements[stmtIdx - 1].canCompleteNormally()
            }
        }

        is SynchronizedStmt -> {
            // The contained statement is reachable iff the synchronized statement is reachable.
            return parentNode.isReachable()
        }

        is TryStmt -> {
            // The try block is reachable iff the try statement is reachable.
            if (this === parentNode.tryBlock) {
                return parentNode.isReachable()
            }

            // If a finally block is present, it is reachable iff the try statement is reachable.
            if (parentNode.finallyBlock.map { it === this }.getOrDefault(false)) {
                return parentNode.isReachable()
            }
        }

        is WhileStmt -> {
            // The contained statement is reachable iff the while statement is reachable and the condition expression
            // is not a constant expression whose value is false.
            return parentNode.isReachable() && parentNode.condition != BooleanLiteralExpr(false)
        }
    }

    when (this) {
        is BlockStmt -> {
            val isBodyStmt = parentNode.let {
                (it is ConstructorDeclaration && it.body === this) ||
                        (it is MethodDeclaration && it.body.getOrNull() === this) ||
                        (it is InitializerDeclaration && it.body === this)
            }

            // The block that is the body of a constructor, method, instance initializer, or static initializer is
            // reachable.
            if (isBodyStmt) {
                return true
            }

            unreachable(astToString(showChildren = false))
        }

        else -> unreachable(astToString(showChildren = false))
    }
}

/**
 * @return This [ResolvedType] as a [ResolvedReferenceType], or `null` if this is not a reference type.
 */
fun ResolvedType.asReferenceTypeOrNull(): ResolvedReferenceType? =
    takeIf { it.isReferenceType }?.asReferenceType()

/**
 * @return This [ResolvedType] as a [ResolvedWildcard], or `null` if this is not a wildcard.
 */
fun ResolvedType.asWildcardOrNull(): ResolvedWildcard? =
    takeIf { it.isWildcard }?.asWildcard()
