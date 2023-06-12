package com.derppening.researchprojecttoolkit.util

import com.derppening.researchprojecttoolkit.tool.ReducerContext
import com.derppening.researchprojecttoolkit.tool.facade.FuzzySymbolSolver
import com.derppening.researchprojecttoolkit.tool.facade.GenericsSolver
import com.derppening.researchprojecttoolkit.tool.facade.typesolver.NamedJarTypeSolver
import com.derppening.researchprojecttoolkit.tool.facade.typesolver.NamedJavaParserTypeSolver
import com.derppening.researchprojecttoolkit.tool.facade.typesolver.PartitionedTypeSolver
import com.derppening.researchprojecttoolkit.util.SymbolSolverUtils.LOGGER
import com.derppening.researchprojecttoolkit.util.cache.*
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.type.Type
import com.github.javaparser.resolution.MethodUsage
import com.github.javaparser.resolution.Navigator
import com.github.javaparser.resolution.SymbolResolver
import com.github.javaparser.resolution.TypeSolver
import com.github.javaparser.resolution.declarations.*
import com.github.javaparser.resolution.logic.InferenceVariableType
import com.github.javaparser.resolution.model.SymbolReference
import com.github.javaparser.resolution.model.typesystem.LazyType
import com.github.javaparser.resolution.model.typesystem.NullType
import com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl
import com.github.javaparser.resolution.types.*
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.*
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionAnnotationDeclaration
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionClassDeclaration
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionEnumDeclaration
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionInterfaceDeclaration
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.jvm.optionals.getOrNull
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

private object SymbolSolverUtils {
    val LOGGER = Logger<SymbolSolverUtils>()
}

class SymbolSolverCache {

    private val refTypeDeclaredMethods = LazyCache(TreeMap(RESOLVED_REF_TYPE_TP_COMPARATOR)) {
        runCatching {
            val value = it.declaredMethods as Set<MethodUsage>

            CacheDecision.Cache(value)
        }.recoverCatching { tr ->
            throw RuntimeException("Unable to get declared methods for ${it.qualifiedName}", tr)
        }.getOrThrow()
    }
    private val refTypeAllMethods = LazyCache(TreeMap(RESOLVED_REF_TYPE_COMPARATOR)) {
        runCatching {
            val value = it.allMethods as List<ResolvedMethodDeclaration>

            CacheDecision.Cache(value)
        }.recoverCatching { tr ->
            throw RuntimeException("Unable to get all methods for ${it.qualifiedName}", tr)
        }.getOrThrow()
    }

    private val refTypeDeclAncestors = LazyCache(TreeMap(RESOLVED_TYPE_DECL_COMPARATOR)) {
        runCatching {
            val value = it.ancestors as List<ResolvedReferenceType>

            CacheDecision.Cache(value)
        }.recoverCatching { tr ->
            throw RuntimeException("Unable to get direct ancestors for ${it.qualifiedName}", tr)
        }.getOrThrow()
    }
    private val refTypeDeclAllAncestors = LazyCache(TreeMap(RESOLVED_TYPE_DECL_COMPARATOR)) {
        runCatching {
            val value = it.allAncestors as List<ResolvedReferenceType>

            CacheDecision.Cache(value)
        }.recoverCatching { tr ->
            throw RuntimeException("Unable to get all ancestors for ${it.qualifiedName}", tr)
        }.getOrThrow()
    }
    private val refTypeDeclConstructors = LazyCache(TreeMap(RESOLVED_TYPE_DECL_COMPARATOR)) {
        runCatching {
            val value = it.constructors as List<ResolvedConstructorDeclaration>

            CacheDecision.Cache(value)
        }.recoverCatching { tr ->
            throw RuntimeException("Unable to get constructors for ${it.qualifiedName}", tr)
        }.getOrThrow()
    }
    private val refTypeDeclDeclaredMethods = LazyCache(TreeMap(RESOLVED_TYPE_DECL_COMPARATOR)) {
        runCatching {
            val value = it.declaredMethods as Set<ResolvedMethodDeclaration>

            CacheDecision.Cache(value)
        }.recoverCatching { tr ->
            throw RuntimeException("Unable to get declared methods for ${it.qualifiedName}", tr)
        }.getOrThrow()
    }
    private val refTypeDeclAllMethods = LazyCache(TreeMap(RESOLVED_TYPE_DECL_COMPARATOR)) {
        runCatching {
            val value = it.allMethods as Set<MethodUsage>

            CacheDecision.Cache(value)
        }.recoverCatching { tr ->
            throw RuntimeException("Unable to get all methods for ${it.qualifiedName}", tr)
        }.getOrThrow()
    }

    // Delegates
    private val refTypeAncestors: SymbolSolverCacheAccess<ResolvedReferenceType, List<ResolvedReferenceType>>
        get() = CacheAccessDelegate(refTypeDeclAncestors) { it.toResolvedTypeDeclaration() }
    private val refTypeAllAncestors: SymbolSolverCacheAccess<ResolvedReferenceType, List<ResolvedReferenceType>>
        get() = CacheAccessDelegate(refTypeDeclAllAncestors) { it.toResolvedTypeDeclaration() }
    private val refTypeConstructors: SymbolSolverCacheAccess<ResolvedReferenceType, List<ResolvedConstructorDeclaration>>
        get() = CacheAccessDelegate(refTypeDeclConstructors) { it.toResolvedTypeDeclaration() }

    private val allCaches: List<LazyCache<*, *>>
        get() = listOf(
            refTypeDeclaredMethods,
            refTypeAllMethods,
            refTypeDeclAncestors,
            refTypeDeclAllAncestors,
            refTypeDeclConstructors,
            refTypeDeclDeclaredMethods,
            refTypeDeclAllMethods
        )

    fun clear() {
        allCaches.forEach { it.clear() }
    }

    fun getAncestors(resolvedType: ResolvedReferenceType): List<ResolvedReferenceType> =
        refTypeAncestors[resolvedType]
    fun getAncestors(resolvedTypeDecl: ResolvedReferenceTypeDeclaration): List<ResolvedReferenceType> =
        refTypeDeclAncestors[resolvedTypeDecl]
    fun getAllAncestors(resolvedType: ResolvedReferenceType): List<ResolvedReferenceType> =
        refTypeAllAncestors[resolvedType]
    fun getAllAncestors(resolvedTypeDecl: ResolvedReferenceTypeDeclaration): List<ResolvedReferenceType> =
        refTypeDeclAllAncestors[resolvedTypeDecl]
    fun getConstructors(resolvedType: ResolvedReferenceType): List<ResolvedConstructorDeclaration> =
        refTypeConstructors[resolvedType]
    fun getConstructors(resolvedTypeDecl: ResolvedReferenceTypeDeclaration): List<ResolvedConstructorDeclaration> =
        refTypeDeclConstructors[resolvedTypeDecl]
    fun getDeclaredMethods(resolvedType: ResolvedReferenceType): Set<MethodUsage> =
        refTypeDeclaredMethods[resolvedType]
    fun getDeclaredMethods(resolvedTypeDecl: ResolvedReferenceTypeDeclaration): Set<ResolvedMethodDeclaration> =
        refTypeDeclDeclaredMethods[resolvedTypeDecl]
    fun getAllMethods(resolvedType: ResolvedReferenceType): List<ResolvedMethodDeclaration> =
        refTypeAllMethods[resolvedType]
    fun getAllMethods(resolvedTypeDecl: ResolvedReferenceTypeDeclaration): Set<MethodUsage> =
        refTypeDeclAllMethods[resolvedTypeDecl]
}

val ANON_CLASS_REGEX = Regex("\\$\\d+")

fun getTypeSolverForSourceRoot(sourceRoot: Path): TypeSolver {
    require(sourceRoot.isDirectory()) { "$sourceRoot is not a directory" }
    return NamedJavaParserTypeSolver(sourceRoot)
}

fun getTypeSolversForSourceRoot(sourceRoots: Collection<Path>): List<TypeSolver> =
    sourceRoots.map { getTypeSolverForSourceRoot(it) }

fun getTypeSolversForClasspath(classpath: String): List<TypeSolver> = splitClasspath(classpath)
    .map { Path(it) }
    .mapNotNull {
        runCatching {
            when {
                it.isDirectory() -> ClassLoaderTypeSolver(FileSystemClassLoader(listOf(it)))
                it.isRegularFile() && it.extension == "jar" -> NamedJarTypeSolver(it)
                else -> {
                    LOGGER.warn("Unknown classpath entry: $it")
                    null
                }
            }
        }.recoverCatching { tr ->
            throw RuntimeException("Failed to get type solver for $it", tr)
        }.getOrThrow()
    }

val jreTypeSolver: TypeSolver
    get() = ReflectionTypeSolver()

/**
 * Converts [javaparserType] into a [ResolvedType] of [T].
 */
inline fun <reified T : ResolvedType> SymbolResolver.toResolvedType(javaparserType: Type): T =
    toResolvedType(javaparserType, T::class.java)

/**
 * Resolves a declaration in [node] into a [ResolvedDeclaration] of [T].
 */
inline fun <reified T : ResolvedDeclaration> SymbolResolver.resolveDeclaration(node: Node): T =
    resolveDeclaration(node, T::class.java)

/**
 * Creates an instance of [ResolvedArrayType] with the given [baseType] as the base component type with [level] number
 * of array levels.
 */
fun ResolvedArrayType(baseType: ResolvedType, level: Int): ResolvedArrayType {
    require(level > 0)

    var ty = baseType
    repeat(level) {
        ty = ResolvedArrayType(ty)
    }
    return ty as ResolvedArrayType
}

/**
 * Returns the base component type of this array type.
 */
tailrec fun ResolvedArrayType.baseComponentType(): ResolvedType =
    if (!componentType.isArray) componentType else (componentType as ResolvedArrayType).baseComponentType()

/**
 * Converts this [ResolvedType] instance to a Java source-like representation.
 */
fun ResolvedType?.toDescriptionString(): String {
    if (this == null)
        return "<Unresolved>"

    return "${typeOfInstance(this)} of `${describe()}`"
}

/**
 * Converts this [ResolvedDeclaration] instance to a Java source-like representation.
 */
fun ResolvedDeclaration?.toDescriptionString(): String {
    if (this == null)
        return "<Unresolved>"

    return when (this) {
        is ResolvedAnnotationDeclaration -> {
            "ResolvedAnnotationDeclaration of annotation @interface $qualifiedName"
        }
        is ResolvedClassDeclaration -> {
            val classType = if (isInterface) "interface" else "class"
            val typeArgs = typeParameters
                .joinToString(", ") { it.name }
                .let { if (it.isNotBlank()) "<$it>" else "" }

            "ResolvedClassDeclaration of $classType $qualifiedName$typeArgs"
        }
        is ResolvedConstructorDeclaration -> {
            val typeArgs = typeParameters
                .joinToString(", ") { it.name }
                .let { if (it.isNotBlank()) "<$it> " else "" }
            val returnType = "void"

            "ResolvedConstructorDeclaration of $typeArgs$returnType $qualifiedSignature"
        }
        is ResolvedEnumDeclaration -> {
            "ResolvedEnumDeclaration of enum $qualifiedName"
        }
        is ResolvedFieldDeclaration -> {
            "ResolvedFieldDeclaration of ${type.describe()} $qualifiedName"
        }
        is ResolvedInterfaceDeclaration -> {
            val typeArgs = typeParameters
                .joinToString(", ") { it.name }
                .let { if (it.isNotBlank()) "<$it>" else "" }

            "ResolvedClassDeclaration of interface $qualifiedName$typeArgs"
        }
        is ResolvedMethodDeclaration -> {
            val typeArgs = typeParameters
                .joinToString(", ") { it.name }
                .let { if (it.isNotBlank()) "<$it> " else "" }
            val returnType = returnType.describe()

            "ResolvedMethodDeclaration of $typeArgs$returnType $qualifiedSignature"
        }
        is ResolvedParameterDeclaration -> {
            val typeName = runCatching { type }
                .map { it.describe() }
                .getOrDefault("<UnresolvedType>")

            "ResolvedParameterDeclaration of $typeName $name"
        }
        is ResolvedValueDeclaration -> {
            val typeName = runCatching { type }
                .map { it.describe() }
                .getOrDefault("<UnresolvedType>")

            "ResolvedValueDeclaration of $typeName $name"
        }
        else -> "${this::class.simpleName} [!`toDescriptionString` unimplemented!]"
    }
}

/**
 * The equivalent types of this [InferenceVariableType].
 */
val InferenceVariableType.equivalentTypes: Set<ResolvedType>
    get() = getFieldValue("equivalentTypes")

/**
 * The supertypes of this [InferenceVariableType].
 */
val InferenceVariableType.superTypes: Set<ResolvedType>
    get() = getFieldValue("superTypes")

/**
 * The concrete type of the [LazyType].
 */
val LazyType.concrete: ResolvedType
    get() {
        return when {
            isArray -> asArrayType()
            isPrimitive -> asPrimitive()
            isNull -> NullType.INSTANCE
            isReferenceType -> asReferenceType()
            isVoid -> ResolvedVoidType.INSTANCE
            isTypeVariable -> asTypeVariable()
            isWildcard -> asWildcard()
            else -> error("Unknown conversion from LazyType to concrete type")
        }
    }

/**
 * The elements which make up this [ResolvedIntersectionType].
 */
val ResolvedIntersectionType.elements: List<ResolvedType>
    get() = getFieldValue<ResolvedIntersectionType, List<ResolvedType>>("elements").toList()

/**
 * The elements which make up this [ResolvedUnionType].
 */
val ResolvedUnionType.elements: List<ResolvedType>
    get() = getFieldValue<ResolvedUnionType, List<ResolvedType>>("elements").toList()

/**
 * Converts the [type] into a raw and erased type.
 */
fun rawifyType(type: ResolvedType, typeSolver: PartitionedTypeSolver): ResolvedType {
    return when (type) {
        is ResolvedArrayType -> ResolvedArrayType(rawifyType(type.componentType, typeSolver))
        is ResolvedReferenceType -> createResolvedRefType(type.toResolvedTypeDeclaration())
        is ResolvedTypeVariable -> rawifyType(type.erasure(), typeSolver)
        is LazyType -> LazyType { rawifyType(type.concrete, typeSolver) }
        is NullType,
        is ResolvedPrimitiveType,
        is ResolvedVoidType,
        is ResolvedWildcard -> type
        is ResolvedIntersectionType -> {
            val rawElemTypes = type.elements
                .map { rawifyType(it, typeSolver) }
                .filterNot { it.isNull }

            rawElemTypes.distinct().singleOrNull() ?: ResolvedIntersectionType(rawElemTypes)
        }
        is ResolvedUnionType -> {
            val rawElemTypes = type.elements
                .map { rawifyType(it, typeSolver) }
                .filterNot { it.isNull }

            rawElemTypes.distinct().singleOrNull() ?: ResolvedUnionType(rawElemTypes)
        }
        else -> TODO("Rawifying type `${type.describe()}` (${type::class.simpleName}) not supported")
    }
}

/**
 * Creates a [createResolvedRefType] for [java.lang.Object] with the associated [typeSolver].
 */
@Suppress("FunctionName")
fun ResolvedObjectType(typeSolver: TypeSolver): ResolvedReferenceType =
    ResolvedReflectionReferenceType<Any>(typeSolver)
        .also { check(it.isJavaLangObject) }

/**
 * Creates a [createResolvedRefType] for [java.lang.Enum] with the associated [typeSolver].
 */
@Suppress("FunctionName")
fun ResolvedEnumType(typeSolver: TypeSolver, e: ResolvedType? = null): ResolvedReferenceType =
    ResolvedReflectionReferenceType<Enum<*>>(typeSolver, listOfNotNull(e))
        .also { check(it.isJavaLangEnum) }

/**
 * Creates a [createResolvedRefType] from the given [class][clazz] and an associated [typeSolver].
 */
@Suppress("FunctionName")
fun ResolvedReflectionReferenceType(
    kclazz: KClass<*>,
    typeSolver: TypeSolver,
    typeArgs: List<ResolvedType> = emptyList()
): ResolvedReferenceType {
    val clazz = kclazz.javaObjectType
    return when {
        clazz.isInterface -> ReflectionInterfaceDeclaration(clazz, typeSolver)
        clazz.isEnum -> ReflectionEnumDeclaration(clazz, typeSolver)
        clazz.isAnnotation -> ReflectionAnnotationDeclaration(clazz, typeSolver)
        else -> ReflectionClassDeclaration(clazz, typeSolver)
    }.let { createResolvedRefType(it, typeArgs) }
}

@Suppress("FunctionName")
inline fun <reified T : Any> ResolvedReflectionReferenceType(
    typeSolver: TypeSolver,
    typeArgs: List<ResolvedType> = emptyList()
): ResolvedReferenceType = ResolvedReflectionReferenceType(T::class, typeSolver, typeArgs)

@Suppress("FunctionName")
inline fun <reified T : Any> ResolvedReflectionReferenceType(
    typeSolver: TypeSolver,
    vararg typeArgs: ResolvedType
): ResolvedReferenceType = ResolvedReflectionReferenceType(T::class, typeSolver, typeArgs.toList())

/**
 * Creates a [createResolvedRefType] from the given [decl].
 */
fun createResolvedRefType(
    decl: ResolvedReferenceTypeDeclaration,
    typeArgs: List<ResolvedType> = emptyList()
): ResolvedReferenceType = ReferenceTypeImpl(decl, typeArgs)

/**
 * The level of this type with reference to the entire type hierarchy.
 *
 * [java.lang.Object] will have a level value of `0`, classes directly deriving `Object` will have a level of `1`, etc.
 */
val ResolvedReferenceType.classLevel: Int
    get() {
        return if (isJavaLangObject) {
            0
        } else {
            directAncestors.single { it in allClassesAncestors }.classLevel + 1
        }
    }

/**
 * Description of this [TypeSolver]
 */
fun TypeSolver.describe(): String {
    return when (this) {
        is ReflectionTypeSolver -> {
            val classLoader = getFieldValue<ClassLoaderTypeSolver, ClassLoader>("classLoader")
            val jreOnly = ReflectionTypeSolver::class.java
                .getDeclaredField("jreOnly")
                .apply {
                    isAccessible = true
                }
                .getBoolean(this)

            "ReflectionTypeSolver{classLoader=$classLoader, jreOnly=$jreOnly}"
        }
        is ClassLoaderTypeSolver -> {
            val classLoader = getFieldValue<ClassLoaderTypeSolver, ClassLoader>("classLoader")

            "ClassLoaderTypeSolver{classLoader=$classLoader}"
        }
        else -> toString()
    }
}

/**
 * @return The [ResolvedReferenceTypeDeclaration] of this [createResolvedRefType] if available.
 */
fun ResolvedReferenceType.toResolvedTypeDeclarationOrNull(): ResolvedReferenceTypeDeclaration? =
    typeDeclaration.getOrNull()

/**
 * @see toResolvedTypeDeclarationOrNull
 * @throws IllegalStateException if no [ResolvedReferenceTypeDeclaration] is associated with this type.
 */
fun ResolvedReferenceType.toResolvedTypeDeclaration(): ResolvedReferenceTypeDeclaration =
    checkNotNull(toResolvedTypeDeclarationOrNull()) { "Missing ResolvedTypeDeclaration for type `$this`" }

/**
 * @return The [TypeDeclaration] of this [createResolvedRefType] if available.
 */
fun ResolvedReferenceType.toTypeDeclaration(reducerContext: ReducerContext?): TypeDeclaration<*>? =
    toResolvedTypeDeclarationOrNull()?.toTypedAstOrNull(reducerContext)

/**
 * A read-only view of parameters in this method.
 */
val ResolvedMethodLikeDeclaration.parameters: List<ResolvedParameterDeclaration>
    get() = List(numberOfParams) { getParam(it) }

val ResolvedValueDeclaration.hasQualifiedName: Boolean
    get() = when (this) {
        is ResolvedAnnotationMemberDeclaration,
        is ResolvedEnumConstantDeclaration,
        is ResolvedFieldDeclaration -> true
        else -> false
    }

/**
 * The fully-qualified name of this [ResolvedValueDeclaration].
 */
val ResolvedValueDeclaration.qualifiedName: String
    get() = when (this) {
        is ResolvedAnnotationMemberDeclaration -> "${declaringType().asReferenceType().qualifiedName}.$name"
        is ResolvedEnumConstantDeclaration -> "${type.asReferenceType().qualifiedName}.$name"
        is ResolvedFieldDeclaration -> "${declaringType().asReferenceType().qualifiedName}.$name"
        else -> error("No qualified name for ${this::class.simpleName}")
    }

/**
 * If this is a [ResolvedWildcard] and contains an `extends` clause, extract the extends bounded type.
 *
 * E.g. If the type is `<? extends java.lang.Enum<?>>`, returns `java.lang.Enum<?>`.
 */
fun ResolvedType.unwrapExtendsWildcard(typeSolver: TypeSolver): ResolvedType =
    asWildcardOrNull()
        ?.let {
            when {
                it.isExtends -> it.boundedType
                !it.isBounded -> ResolvedObjectType(typeSolver)
                else -> it
            }
        }
        ?: this

/**
 * Creates a [ResolvedWildcard] with the intersection of all [boundedTypes] as its bounds.
 *
 * @param isSuper Whether the bound type should be `super`.
 */
@Suppress("FunctionName")
fun ResolvedIntersectionWildcard(boundedTypes: Collection<ResolvedType>, isSuper: Boolean = false): ResolvedWildcard =
    when (boundedTypes.size) {
        0 -> ResolvedWildcard.UNBOUNDED
        1 -> {
            val boundedType = boundedTypes.single()
            if (isSuper) {
                ResolvedWildcard.superBound(boundedType)
            } else {
                ResolvedWildcard.extendsBound(boundedType)
            }
        }
        else -> {
            ResolvedIntersectionType(boundedTypes).let {
                if (isSuper) {
                    ResolvedWildcard.superBound(it)
                } else {
                    ResolvedWildcard.extendsBound(it)
                }
            }
        }
    }

fun ResolvedType.describeQName(showType: Boolean = true): String {
    val typeDesc = describe()
    val typeName = this::class.simpleName!!

    return if (showType) {
        "$typeDesc ($typeName)"
    } else {
        typeDesc
    }
}

fun ResolvedDeclaration.describeQName(showType: Boolean = true): String {
    val declDesc = when {
        isField -> asField().qualifiedName
        isVariable -> TODO()
        isEnumConstant -> asEnumConstant().qualifiedName
        isPattern -> TODO()
        isParameter -> TODO()
        isType -> asType().qualifiedName
        isMethod -> asMethod().qualifiedSignature
        this is ResolvedAnnotationMemberDeclaration -> qualifiedName
        else -> TODO("describeQName for ${this::class.simpleName} not implemented")
    }

    return if (showType) {
        val typeName = this::class.simpleName ?: this::class.jvmName

        "$declDesc ($typeName)"
    } else {
        declDesc
    }
}

/**
 * All types bounded by this [ResolvedWildcard].
 *
 * Effectively unpacks all bounded types if this is a [ResolvedIntersectionType] or [ResolvedUnionType]; Otherwise
 * returns the singular (or non) bounded type.
 */
val ResolvedWildcard.boundedTypes: List<ResolvedType>
    get() = if (isBounded) {
        when {
            boundedType is ResolvedIntersectionType -> (boundedType as ResolvedIntersectionType).elements
            boundedType.isUnionType -> boundedType.asUnionType().elements
            else -> listOf(boundedType)
        }
    } else emptyList()

/**
 * Returns `true` if this wildcard is equivalent to an unbounded wildcard, i.e. `?`, `? extends Object`,
 * `? super Object`.
 */
val ResolvedWildcard.isEffectivelyUnbounded: Boolean
    get() = !isBounded || boundedTypes.all { it.isReferenceType && it.asReferenceType().isJavaLangObject }

/**
 * Creates a [SymbolReference] from a nullable [SymbolReference].
 */
inline fun <reified S : ResolvedDeclaration> SymbolReference(symbolRef: SymbolReference<out S>?): SymbolReference<out S> =
    if (symbolRef != null) SymbolReference.adapt(symbolRef, S::class.java) else SymbolReference.unsolved()

/**
 * Creates a [SymbolReference] from a nullable [ResolvedDeclaration].
 */
inline fun <reified S : ResolvedDeclaration> SymbolReference(symbolRef: S?): SymbolReference<out S> =
    if (symbolRef != null) SymbolReference.solved(symbolRef) else SymbolReference.unsolved()

/**
 * Indexed variation of [createResolvedRefType.transformTypeParameters].
 */
fun ResolvedReferenceType.transformTypeParametersIndexed(transform: (index: Int, type: ResolvedType) -> ResolvedType): ResolvedType {
    var index = 0
    return transformTypeParameters {
        transform(index++, it)
    }
}

fun List<ResolvedType>.unionTypes(): ResolvedType {
    require(isNotEmpty())

    return if (size == 1) {
        single()
    } else {
        ResolvedUnionType(flatMap { if (it.isUnionType) it.asUnionType().elements else listOf(it) })
    }
}

fun List<ResolvedType>.intersectTypes(): ResolvedType {
    require(isNotEmpty())

    return if (size == 1) {
        single()
    } else {
        ResolvedIntersectionType(flatMap { if (it is ResolvedIntersectionType) it.elements else listOf(it) })
    }
}

val ResolvedValueDeclaration.isArrayLengthDecl: Boolean
    get() = this !is ResolvedAnnotationMemberDeclaration &&
            this !is ResolvedEnumConstantDeclaration &&
            this !is ResolvedFieldDeclaration &&
            this !is ResolvedParameterDeclaration &&
            this !is ResolvedPatternDeclaration &&
            name == "length" &&
            type == ResolvedPrimitiveType.INT

/**
 * The "fixed" name of the scope of this method.
 *
 * Handles the case where the scope of the method is within an [EnumConstantDeclaration].
 */
val ResolvedMethodLikeDeclaration.fixedQualifiedScopeName: String
    get() = (this as? AssociableToAST)
        ?.toAst()
        ?.map { Navigator.demandParentNode(it) }
        ?.mapNotNull { it as? EnumConstantDeclaration }
        ?.map { it.getQualifiedName(null) }
        ?.getOrNull()
        ?: declaringType().qualifiedName

/**
 * The "fixed" qualified name of the method.
 *
 * Handles the case where the scope of the method is within an [EnumConstantDeclaration].
 */
val ResolvedMethodLikeDeclaration.fixedQualifiedName: String
    get() = "${fixedQualifiedScopeName}.$name"

/**
 * The "fixed" type of the parameter.
 *
 * Handles the case where nested classes in parameters are not resolved correctly.
 */
fun ResolvedParameterDeclaration.getFixedType(symbolSolver: FuzzySymbolSolver): ResolvedType {
    val wrappedNode = toTypedAstOrNull<Parameter>(null)
    return if (wrappedNode != null) {
        symbolSolver.toResolvedType<ResolvedType>(wrappedNode.type)
            .let { if (isVariadic) ResolvedArrayType(it, 1) else it }
    } else type
}

fun ResolvedParameterDeclaration.getFixedType(reducerContext: ReducerContext): ResolvedType =
    getFixedType(reducerContext.symbolSolver)

/**
 * The "fixed" signature of the method.
 *
 * Handles the case where nested classes in parameters are not resolved correctly.
 */
fun ResolvedMethodLikeDeclaration.getFixedSignature(symbolSolver: FuzzySymbolSolver): String {
    val wrappedNode = toTypedAstOrNull<CallableDeclaration<*>>(null)
    return if (wrappedNode != null) {
        buildString {
            append(name)
            append('(')
            append(parameters.joinToString(", ") { it.getFixedType(symbolSolver).describe() })
            append(')')
        }
    } else signature
}

fun ResolvedMethodLikeDeclaration.getFixedSignature(reducerContext: ReducerContext): String =
    getFixedSignature(reducerContext.symbolSolver)

fun ResolvedMethodLikeDeclaration.getFixedQualifiedSignature(symbolSolver: FuzzySymbolSolver): String =
    "${fixedQualifiedScopeName}.${getFixedSignature(symbolSolver)}"

/**
 * The "fixed" qualified signature of the method.
 *
 * Handle cases as specified in [ResolvedMethodLikeDeclaration.fixedQualifiedScopeName] and
 * [ResolvedMethodLikeDeclaration.getFixedSignature].
 */
fun ResolvedMethodLikeDeclaration.getFixedQualifiedSignature(reducerContext: ReducerContext): String =
    getFixedQualifiedSignature(reducerContext.symbolSolver)

/**
 * The qualified name of this [ResolvedMethodLikeDeclaration] as it appears in the bytecode.
 *
 * Effectively renames all constructors with `<init>` instead of the class name.
 */
val ResolvedMethodLikeDeclaration.bytecodeQualifiedName: String
    get() = if (this is ResolvedConstructorDeclaration) {
        "${declaringType().qualifiedName}.<init>"
    } else fixedQualifiedName

/**
 * Describes the type of [this] as a type present in bytecode.
 */
fun ResolvedType.describeTypeAsBytecode(): String {
    return when {
        isArray -> "${asArrayType().componentType.describeTypeAsBytecode()}[]"
        isReferenceType -> asReferenceType().qualifiedName
        isTypeVariable -> asTypeVariable().erasure().describeTypeAsBytecode()
        isPrimitive || isVoid -> describe()
        else -> throw UnsupportedOperationException("Don't know how to describe type `${describe()}` (${this::class.simpleName}) as a bytecode type")
    }
}

/**
 * Describes the type of the parameter as a type which is present in the bytecode.
 */
fun ResolvedParameterDeclaration.describeTypeAsBytecode(): String {
    return type.describeTypeAsBytecode()
}

/**
 * The qualified signature of this [ResolvedMethodLikeDeclaration] as it appears in the bytecode.
 *
 * Effectively applies the following transformations:
 *
 * - Renames all constructors with `<init>` instead of the class name.
 * - Adds parameters of type `java.lang.String, int` to all constructors of enums.
 * - Adds a parameter of the parent container type to all constructors of non-static nested classes.
 */
val ResolvedMethodLikeDeclaration.bytecodeQualifiedSignature: String
    get() {
        val astTypeDecl = declaringType().toTypedAst<TypeDeclaration<*>>(null)
        val astDeclaredParams = parameters.map { p -> p.describeTypeAsBytecode() }
        val parameters = if (this is ResolvedConstructorDeclaration) {
            if (astTypeDecl.isNestedType && !astTypeDecl.isStatic) {
                listOf(astTypeDecl.parentContainer!!.asTypeDeclaration().fullyQualifiedName.get()) + astDeclaredParams
            } else if (astTypeDecl.isEnumDeclaration) {
                listOf("java.lang.String", "int") + astDeclaredParams
            } else astDeclaredParams
        } else astDeclaredParams

        return "${bytecodeQualifiedName}(${parameters.joinToString(", ")})"
    }

/**
 * Find a method in [baseType] which [derivedMethod] overrides.
 */
fun getOverriddenMethodInType(
    derivedMethod: ResolvedMethodDeclaration,
    baseType: ResolvedReferenceTypeDeclaration,
    reducerContext: ReducerContext,
    derivedType: ResolvedReferenceTypeDeclaration = derivedMethod.declaringType(),
    cache: SymbolSolverCache = SymbolSolverCache()
): ResolvedMethodDeclaration? {
    val derivedAstMethod = derivedMethod.toTypedAst<MethodDeclaration>(null)
    if (derivedAstMethod.isStatic) {
        return null
    }

    if (derivedAstMethod.parentNode.map { it is EnumConstantDeclaration }.get()) {
        val declInEnumConstant = cache.getDeclaredMethods(derivedType)
            .filter { it.name == derivedMethod.name }
            .filter { it.numberOfParams == derivedMethod.numberOfParams }
            .singleOrNull { it.signature == derivedMethod.signature }
        if (declInEnumConstant != null) {
            return declInEnumConstant
        }
    }

    val baseTypeFromMethod = cache.getAllAncestors(derivedType)
        .firstOrNull { it.qualifiedName == baseType.qualifiedName }
        ?: return null

    return cache.getDeclaredMethods(baseTypeFromMethod)
        .filter { it.name == derivedMethod.name }
        .filter { it.paramTypes.size == derivedMethod.numberOfParams }
        .map { methodUsage ->
            baseTypeFromMethod.transformTypeParameters {
                // If the parameter is a type variable and is declared by us, normalize it so that we can match them
                // with concrete types in the possibly-inherited method
                if (it.isTypeVariable && (it.asTypeParameter().container as? ResolvedReferenceTypeDeclaration)?.qualifiedName == baseTypeFromMethod.qualifiedName) {
                    GenericsSolver.normalizeType(it, reducerContext.typeSolver)
                } else {
                    it
                }
            }
            .asReferenceType()
            .typeParametersMap
            .fold(methodUsage) { acc, (typeDecl, type) ->
                acc.replaceTypeParameter(typeDecl, type)
            }
        }
        .singleOrNull {
            // We assume that if both types are assignable to each other it is effectively the same
            val derivedMethodRawParams = derivedMethod.parameters
                .map { rawifyType(it.getFixedType(reducerContext), reducerContext.typeSolver) }
            val itRawParams = it.paramTypes.map { rawifyType(it, reducerContext.typeSolver) }

            derivedMethodRawParams.zip(itRawParams).all { (expected, actual) ->
                expected.isAssignableBy(actual) && actual.isAssignableBy(expected)
            }
        }
        ?.declaration
}

/**
 * Find a method in [baseType] which [derivedMethod] overrides.
 *
 * @param noTrySolveDecl If `true`, directly solve using the [createResolvedRefType].
 */
fun getOverriddenMethodInType(
    derivedMethod: ResolvedMethodDeclaration,
    baseType: ResolvedReferenceType,
    reducerContext: ReducerContext,
    noTrySolveDecl: Boolean = false,
    cache: SymbolSolverCache = SymbolSolverCache()
): ResolvedMethodDeclaration? {
    if (noTrySolveDecl) {
        val solveByDecl = getOverriddenMethodInType(
            derivedMethod,
            baseType.toResolvedTypeDeclaration(),
            reducerContext,
            cache = cache
        )

        if (solveByDecl != null) {
            return solveByDecl
        }
    }

    return cache.getDeclaredMethods(baseType)
        .filter { it.name == derivedMethod.name }
        .filter { it.paramTypes.size == derivedMethod.numberOfParams }
        .map { methodUsage ->
            baseType.transformTypeParameters { GenericsSolver.normalizeType(it, reducerContext.typeSolver) }
                .asReferenceType()
                .typeParametersMap
                .fold(methodUsage) { acc, (typeDecl, type) ->
                    acc.replaceTypeParameter(typeDecl, type)
                }
        }
        .singleOrNull { it.signature == derivedMethod.getFixedSignature(reducerContext) }
        ?.declaration
}

/**
 * Find a method in [derivedType] which overrides [baseMethod].
 */
fun getOverridingMethodInType(
    baseMethod: ResolvedMethodDeclaration,
    derivedType: ResolvedReferenceTypeDeclaration,
    reducerContext: ReducerContext,
    traverseClassHierarchy: Boolean = false,
    cache: SymbolSolverCache = SymbolSolverCache()
): ResolvedMethodDeclaration? =
    getOverridingMethodsInType(baseMethod, derivedType, reducerContext, traverseClassHierarchy, cache).singleOrNull()

/**
 * Find a method in [derivedType] which overrides [baseMethod].
 */
fun getOverridingMethodInType(
    baseMethod: ResolvedMethodDeclaration,
    derivedType: ResolvedReferenceType,
    reducerContext: ReducerContext,
    traverseClassHierarchy: Boolean = false,
    cache: SymbolSolverCache = SymbolSolverCache()
): ResolvedMethodDeclaration? =
    getOverridingMethodInType(baseMethod, derivedType.toResolvedTypeDeclaration(), reducerContext, traverseClassHierarchy, cache)

/**
 * Find a method in [derivedType] which overrides [baseMethod].
 */
fun getOverridingMethodsInType(
    baseMethod: ResolvedMethodDeclaration,
    derivedType: ResolvedReferenceTypeDeclaration,
    reducerContext: ReducerContext,
    traverseClassHierarchy: Boolean = false,
    cache: SymbolSolverCache = SymbolSolverCache()
): List<ResolvedMethodDeclaration> {
    // Assume annotation classes never override baseMethod
    if (derivedType.isAnnotation) {
        return emptyList()
    }

    val baseTypeDecl = baseMethod.declaringType()

    val baseTypeFromMethod = if (baseTypeDecl.typeParameters.isNotEmpty()) {
        val baseTypeDeclQName = baseTypeDecl.qualifiedName

        cache.getAllAncestors(derivedType)
            .filter { it.qualifiedName == baseTypeDeclQName }
            .let { it.singleOrNull() ?: it.firstOrNull() }
            ?: return emptyList()
    } else {
        createResolvedRefType(baseTypeDecl)
    }

    val baseMethodSignature by lazy(LazyThreadSafetyMode.NONE) {
        baseTypeFromMethod.typeParametersMap
            .takeIf { it.isNotEmpty() }
            ?.fold(MethodUsage(baseMethod)) { acc, (typeDecl, type) ->
                acc.replaceTypeParameter(typeDecl, type)
            }
            ?.signature
            ?: baseMethod.getFixedSignature(reducerContext)
    }

    val methodList = if (traverseClassHierarchy) {
        cache.getAllMethods(createResolvedRefType(derivedType))
    } else {
        cache.getDeclaredMethods(derivedType)
    }

    val filteredMethodList = methodList
        .filter { it.name == baseMethod.name }
        .filter { it.numberOfParams == baseMethod.numberOfParams }
        .filterNot { it.isStatic }

    return if (traverseClassHierarchy) {
        filteredMethodList.singleOrNull { it.declaringType() == derivedType }
            ?.takeIf { it.getFixedSignature(reducerContext) == baseMethodSignature }
            ?.let { listOf(it) }
            ?: filteredMethodList
                .filter { it.getFixedSignature(reducerContext) == baseMethodSignature }
                .fold(mutableListOf<ResolvedMethodDeclaration>()) { acc, resolvedMethodDecl ->
                    acc.apply {
                        if (isEmpty()) {
                            add(resolvedMethodDecl)
                            return@apply
                        }

                        val resolvedMethodDeclType = createResolvedRefType(resolvedMethodDecl.declaringType())

                        val itMoreSpecificThanMethods = filter {
                            val itDeclType = createResolvedRefType(it.declaringType())

                            with(reducerContext) {
                                !itDeclType.isLooselyAssignableBy(resolvedMethodDeclType) &&
                                        !resolvedMethodDeclType.isLooselyAssignableBy(itDeclType)
                            }
                        }
                        if (itMoreSpecificThanMethods.isNotEmpty()) {
                            removeAll(itMoreSpecificThanMethods)
                            add(resolvedMethodDecl)
                        }
                    }
                }
                .toList()
    } else {
        filteredMethodList
            .singleOrNull { it.getFixedSignature(reducerContext) == baseMethodSignature }
            .let { listOfNotNull(it) }
    }
}

/**
 * Find a method in [derivedType] which overrides [baseMethod].
 */
fun getOverridingMethodsInType(
    baseMethod: ResolvedMethodDeclaration,
    derivedType: ResolvedReferenceType,
    reducerContext: ReducerContext,
    traverseClassHierarchy: Boolean = false,
    cache: SymbolSolverCache = SymbolSolverCache()
): List<ResolvedMethodDeclaration> =
    getOverridingMethodsInType(baseMethod, derivedType.toResolvedTypeDeclaration(), reducerContext, traverseClassHierarchy, cache)

/**
 * Find a method in [enumConst] which overrides [baseMethod].
 */
fun getOverridingMethodInEnumConst(
    baseMethod: ResolvedMethodDeclaration,
    enumConst: ResolvedEnumConstantDeclaration,
    reducerContext: ReducerContext,
    traverseClassHierarchy: Boolean = false,
    cache: SymbolSolverCache = SymbolSolverCache()
): ResolvedMethodDeclaration? =
    getOverridingMethodsInEnumConst(baseMethod, enumConst, reducerContext, traverseClassHierarchy, cache).singleOrNull()

/**
 * Find a method in [enumConst] which overrides [baseMethod].
 */
fun getOverridingMethodsInEnumConst(
    baseMethod: ResolvedMethodDeclaration,
    enumConst: ResolvedEnumConstantDeclaration,
    reducerContext: ReducerContext,
    traverseClassHierarchy: Boolean = false,
    cache: SymbolSolverCache = SymbolSolverCache()
): List<ResolvedMethodDeclaration> {
    val enumConstAst = enumConst.toTypedAstOrNull<EnumConstantDeclaration>(null)
        ?: TODO("Unhandled resolution of method in enum constant")

    val baseType = baseMethod.declaringType()
    val baseTypeQName = baseType.qualifiedName
    val enumConstType = enumConst.type.asReferenceType()
    val enumConstTypeDecl = enumConstType.toResolvedTypeDeclaration()
    val baseTypeFromMethod = enumConstType.takeIf { it.qualifiedName == baseTypeQName }
        ?: cache.getAllAncestors(enumConstTypeDecl).filter { it.qualifiedName == baseTypeQName }
            .let { it.singleOrNull() ?: it.firstOrNull() }
        ?: error("Cannot find ${baseType.qualifiedName} in ancestors of ${enumConstType.qualifiedName}\nAncestors:\n${enumConstTypeDecl.allAncestors.joinToString("\n") { "- ${it.describe()}" }}")

    val methodList = enumConstAst.classBody
        .filterIsInstance<MethodDeclaration>()
        .map { reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(it) }

    val matchingMethodInEnumConst = if (methodList.isNotEmpty()) {
        val filteredMethodList = methodList
            .filterNot { it.isStatic }
            .filter { it.name == baseMethod.name }
            .filter { it.numberOfParams == baseMethod.numberOfParams }

        filteredMethodList
            .singleOrNull { methodUsage ->
                val baseMethodUsage = baseTypeFromMethod.typeParametersMap
                    .fold(MethodUsage(baseMethod)) { acc, (typeDecl, type) ->
                        acc.replaceTypeParameter(typeDecl, type)
                    }
                methodUsage.signature == baseMethodUsage.signature
            }
    } else null

    return matchingMethodInEnumConst?.let { listOf(it) } ?: if (traverseClassHierarchy) {
        getOverridingMethodsInType(
            baseMethod,
            enumConstType,
            reducerContext,
            traverseClassHierarchy = true,
            cache = cache
        )
    } else emptyList()
}

fun ResolvedReferenceTypeDeclaration.getTopLevelType(symbolSolver: FuzzySymbolSolver): ResolvedReferenceTypeDeclaration {
    return if (toAst().isPresent) {
        val typedAst = toTypedAst<TypeDeclaration<*>>(null)

        (typedAst.findAncestor<TypeDeclaration<*>> { it.parentNode.map { it is CompilationUnit }.get() } ?: typedAst)
            .let { symbolSolver.resolveDeclaration(it) }
    } else {
        val qnameComponents = qualifiedName.split(".")
        for (componentLen in 1..qnameComponents.size) {
            val prefix = qnameComponents.subList(0, componentLen).joinToString(".")
            val solvedType = symbolSolver.typeSolver.tryToSolveType(prefix)
            if (solvedType.isSolved) {
                return solvedType.correspondingDeclaration
            }
        }
        error("Cannot find a type to resolve for $qualifiedName")
    }
}

fun ResolvedReferenceTypeDeclaration.isTopLevelType(symbolSolver: FuzzySymbolSolver): Boolean =
    getTopLevelType(symbolSolver).qualifiedName == qualifiedName

fun ResolvedReferenceTypeDeclaration.getTopLevelType(reducerContext: ReducerContext): ResolvedReferenceTypeDeclaration =
    getTopLevelType(reducerContext.symbolSolver)

fun ResolvedReferenceTypeDeclaration.isTopLevelType(reducerContext: ReducerContext): Boolean =
    isTopLevelType(reducerContext.symbolSolver)

/**
 * Finds the direct constructor which this [ResolvedConstructorDeclaration] will call. If this method returns `null`,
 * [this] constructor resides in a library class, and its constructor dependencies cannot be determined.
 */
context(ReducerContext)
fun ResolvedConstructorDeclaration.findDirectlyDependentConstructor(): ResolvedConstructorDeclaration? {
    val resolvedDeclType = declaringType()
    val ctorDecl = toTypedAstOrNull<ConstructorDeclaration>()
        ?: return if (!typeSolver.isSolvedBySourceSolvers(resolvedDeclType)) {
            null
        } else {
            val typeDecl = resolvedDeclType.toTypedAst<ClassOrInterfaceDeclaration>()
            findSuperclassNoArgConstructor(typeDecl, resolvedDeclType)
        }

    val explicitCtorStmt = ctorDecl.explicitCtorInvocationStmt
    return if (explicitCtorStmt == null) {
        val typeDecl = resolvedDeclType.toTypedAst<ClassOrInterfaceDeclaration>()
        findSuperclassNoArgConstructor(typeDecl, resolvedDeclType)
    } else {
        resolveDeclaration(explicitCtorStmt)
    }
}

/**
 * Finds all constructors which this [ResolvedConstructorDeclaration] will call.
 */
context(ReducerContext)
fun ResolvedConstructorDeclaration.findDependentConstructors(): List<ResolvedConstructorDeclaration> {
    val directlyDependentCtor = findDirectlyDependentConstructor()

    return if (directlyDependentCtor == null) {
        emptyList()
    } else {
        directlyDependentCtor.findDependentConstructors() + directlyDependentCtor
    }
}

/**
 * Finds the constructor in the direct supertype which this [ctorDecl] depends on. Returns `null` if [ctorDecl] depends
 * on the no-argument constructor of the superclass, but the superclass does not declare one (i.e. provides a default
 * constructor).
 */
context(ReducerContext)
fun findSuperclassDependentConstructor(
    ctorDecl: ConstructorDeclaration,
): ResolvedConstructorDeclaration {
    val explicitCtorStmt = ctorDecl.explicitCtorInvocationStmt

    // If there is an ExplicitConstructorInvocationStmt in the constructor, resolve the target constructor.
    // If the target constructor is within our own class, re-solve the superclass constructor recursively; otherwise,
    // assume the constructor is from the superclass and return the constructor.
    if (explicitCtorStmt != null) {
        val resolvedExplicitTargetCtor = resolveDeclaration<ResolvedConstructorDeclaration>(explicitCtorStmt)

        return if (explicitCtorStmt.isThis) {
            val explicitTargetCtor = resolvedExplicitTargetCtor.toTypedAst<ConstructorDeclaration>(null)
            check(NodeAstComparator.compare(ctorDecl, explicitTargetCtor) != 0) {
                "`${explicitCtorStmt}` resolves to the same constructor `${resolvedExplicitTargetCtor.qualifiedSignature}`"
            }

            findSuperclassDependentConstructor(explicitTargetCtor)
        } else resolvedExplicitTargetCtor
    }

    val typeDecl = ctorDecl.findAncestor(TypeDeclaration::class.java).get()
    val resolvedTypeDecl = resolveDeclaration<ResolvedReferenceTypeDeclaration>(typeDecl)

    // A class without any ExplicitConstructorInvocationStmt delegates to the default constructor of its superclass.
    // The exception is java.lang.Enum, which has a single two-argument constructor instead.
    return if (resolvedTypeDecl.isEnum) {
        ResolvedEnumType(typeSolver)
            .let { symbolSolverCache.getConstructors(it) }
            .single()
    } else {
        checkNotNull(findSuperclassNoArgConstructor(typeDecl, resolvedTypeDecl)) {
            val qsig = resolveDeclaration<ResolvedConstructorDeclaration>(ctorDecl)
                .qualifiedSignature

            "Cannot find superclass no-argument constructor for $qsig"
        }
    }
}

/**
 * Finds the no-argument constructor of the supertype and mark as reachable.
 *
 * This is required when the subclass has a no-argument defaulted constructor but its superclass has a no-argument
 * non-defaulted constructor.
 */
context(ReducerContext)
fun findSuperclassNoArgConstructor(
    typeDecl: TypeDeclaration<*>,
    resolvedTypeDecl: ResolvedReferenceTypeDeclaration = resolveDeclaration(typeDecl)
): ResolvedConstructorDeclaration? {
    val classAncestor = resolvedTypeDecl.ancestors
        .map { it.toResolvedTypeDeclaration() }
        .singleOrNull { it.isClass }
        ?: typeSolver.solvedJavaLangObject

    return symbolSolverCache.getConstructors(classAncestor)
        .let { ctors ->
            ctors.singleOrNull { it.numberOfParams == 0 }
                ?: ctors.singleOrNull { it.numberOfParams == 1 && it.getParam(0).isVariadic }
        }
}

/**
 * Returns the JVM descriptor for this method/constructor.
 */
fun ResolvedMethodLikeDeclaration.getJvmDescriptor(): String {
    return buildString {
        append("(")
        parameters.forEach {
            append(it.type.toDescriptor())
        }
        append(")")

        when (val method = this@getJvmDescriptor) {
            is ResolvedConstructorDeclaration -> {
                append("V")
            }

            is ResolvedMethodDeclaration -> {
                append(method.returnType.toDescriptor())
            }

            else -> unreachable()
        }
    }
}

val javaLangThrowableFqn by lazy { java.lang.Throwable::class.jvmName }
val javaLangExceptionFqn by lazy { java.lang.Exception::class.jvmName }
val javaLangErrorFqn by lazy { java.lang.Error::class.jvmName }
val javaLangRuntimeExceptionFqn by lazy { java.lang.RuntimeException::class.jvmName }

private fun ResolvedReferenceType.isThrowable(allAncestorsFqn: Set<String>): Boolean =
    qualifiedName == javaLangThrowableFqn || allAncestorsFqn.any { it == javaLangThrowableFqn }

private fun ResolvedReferenceType.isUncheckedException(allAncestorsFqn: Set<String>): Boolean {
    if (!isThrowable(allAncestorsFqn)) {
        return false
    }

    val qualifiedName = this.qualifiedName
    return qualifiedName in arrayOf(javaLangRuntimeExceptionFqn, javaLangErrorFqn) ||
            javaLangRuntimeExceptionFqn in allAncestorsFqn ||
            javaLangErrorFqn in allAncestorsFqn
}

/**
 * Whether this [ResolvedReferenceType] is [Throwable] or a descendent of [Throwable].
 */
fun ResolvedReferenceType.isThrowable(): Boolean {
    val allAncestorsFqn = allAncestors
        .map { it.qualifiedName }
        .toSet()

    return isThrowable(allAncestorsFqn)
}

/**
 * Whether this [ResolvedReferenceType] is an unchecked exception.
 */
fun ResolvedReferenceType.isUncheckedException(): Boolean {
    val allAncestorsFqn = allAncestors
        .map { it.qualifiedName }
        .toSet()

    return isUncheckedException(allAncestorsFqn)
}

/**
 * Whether this [ResolvedReferenceType] is a checked exception.
 */
fun ResolvedReferenceType.isCheckedException(): Boolean {
    val allAncestorsFqn = allAncestors
        .map { it.qualifiedName }
        .toSet()

    return isThrowable(allAncestorsFqn) && !isUncheckedException(allAncestorsFqn)
}

/**
 * Whether [this] is loosely assignable by [other].
 */
context(ReducerContext)
fun ResolvedType.isLooselyAssignableBy(other: ResolvedType): Boolean {
    val rawType = rawifyType(other, typeSolver)
    val isAssignable = try {
        if (isReferenceType && rawType.isReferenceType) {
            // Shortcut for reusing cached all-ancestors
            val thisResolvedDecl = asReferenceType().toResolvedTypeDeclaration()
            val thisQName = thisResolvedDecl.qualifiedName
            val thisAstDecl = thisResolvedDecl.toTypedAstOrNull<ClassOrInterfaceDeclaration>(null)
            val otherResolvedDecl = rawType.asReferenceType().toResolvedTypeDeclaration()
            val otherAstDecl = otherResolvedDecl.toTypedAstOrNull<ClassOrInterfaceDeclaration>(null)

            if (otherResolvedDecl.qualifiedName == thisQName) {
                true
            } else if (thisAstDecl?.isLocalClassDeclaration == true) {
                if (otherAstDecl?.isLocalClassDeclaration != true) {
                    false
                } else {
                    NodeAstComparator.compare(thisAstDecl, otherAstDecl) == 0
                }
            } else {
                val rawTypeAssignableTypes = if (otherAstDecl?.isLocalClassDeclaration == true) {
                    otherAstDecl.let { it.extendedTypes + it.implementedTypes }
                        .map { toResolvedType<ResolvedReferenceType>(it) }
                        .flatMap { listOf(it) + symbolSolverCache.getAllAncestors(it) }
                } else {
                    symbolSolverCache.getAllAncestors(otherResolvedDecl)
                }

                thisQName in rawTypeAssignableTypes.map { it.qualifiedName }.toSet()
            }
        } else {
            isAssignableBy(rawType)
        }
    } catch (tr: Throwable) {
        throw RuntimeException("Failed to compute assignability between ${describe()} and ${rawType.describe()}", tr)
    }

    return isAssignable
}
