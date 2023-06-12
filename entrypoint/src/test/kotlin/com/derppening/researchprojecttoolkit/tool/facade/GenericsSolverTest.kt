package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.tool.facade.typesolver.PartitionedTypeSolver
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.resolution.model.typesystem.NullType
import com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedType
import com.github.javaparser.resolution.types.ResolvedTypeVariable
import com.github.javaparser.resolution.types.ResolvedWildcard
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionInterfaceDeclaration
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import org.junit.jupiter.api.*
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenericsSolverTest {

    private var typeSolver: PartitionedTypeSolver? = null
    private var symbolSolver: FuzzySymbolSolver? = null
    private var genericsSolver: GenericsSolver? = null

    private val solvedJavaLangObject
        get() = ResolvedObjectType(typeSolver!!)
    private val solvedJavaLangComparable
        get() = ResolvedReflectionReferenceType<Comparable<*>>(typeSolver!!)
    private val solvedJavaLangInteger
        get() = ResolvedReflectionReferenceType<Int>(typeSolver!!)
    private val solvedJavaLangString
        get() = ResolvedReflectionReferenceType<String>(typeSolver!!)

    private fun getSolvedJavaUtilCollection(typeArg: ResolvedType): ResolvedReferenceType =
        ResolvedReflectionReferenceType<Collection<*>>(typeSolver!!, typeArg)

    private fun getSolvedJavaUtilSet(typeArg: ResolvedType): ResolvedReferenceType =
        ResolvedReflectionReferenceType<Set<*>>(typeSolver!!, typeArg)

    private fun getSolvedJavaUtilMap(
        typeArgK: ResolvedType? = null,
        typeArgV: ResolvedType? = null
    ): ResolvedReferenceType {
        val resolvedDecl = ReflectionInterfaceDeclaration(java.util.Map::class.java, typeSolver!!)
        val taK = typeArgK ?: ResolvedTypeVariable(resolvedDecl.typeParameters.single { it.name == "K" })
        val taV = typeArgV ?: ResolvedTypeVariable(resolvedDecl.typeParameters.single { it.name == "V" })

        return ReferenceTypeImpl(resolvedDecl, listOf(taK, taV))
    }

    private fun setUp(typeSolver: PartitionedTypeSolver) {
        this.typeSolver = typeSolver
        symbolSolver = FuzzySymbolSolver(typeSolver)
        genericsSolver = GenericsSolver(symbolSolver!!, typeSolver)
    }

    @BeforeEach
    fun setUp() {
        setUp(PartitionedTypeSolver(emptyList(), listOf(ReflectionTypeSolver()), false))
    }

    @Test
    fun `simplifyWildcard() - Singular Extends Bound`() {
        val wildcard = ResolvedWildcard.extendsBound(solvedJavaLangString)

        // <? extends String> -> <? extends String>
        val result = genericsSolver!!.simplifyWildcard(listOf(wildcard))

        assertTrue(result.isExtends)
        assertResolvedTypeIs<ResolvedReferenceType>(result.boundedType)
        assertEquals(solvedJavaLangString, result.boundedType)
    }

    @Test
    fun `simplifyWildcard() - Singular Super Bound`() {
        val wildcard = ResolvedWildcard.superBound(solvedJavaLangString)

        // <? super String> -> <? super String>
        val result = genericsSolver!!.simplifyWildcard(listOf(wildcard))

        assertTrue(result.isSuper)
        assertResolvedTypeIs<ResolvedReferenceType>(result.boundedType)
        assertEquals(solvedJavaLangString, result.boundedType)
    }

    @Test
    fun `simplifyWildcard() - Duplicate Bounds`() {
        val boundedTypes = listOf(solvedJavaLangString, solvedJavaLangString)
        val wildcards = boundedTypes.map { ResolvedWildcard.extendsBound(it) }

        // <? extends String & String> -> <? extends String>
        val result = genericsSolver!!.simplifyWildcard(wildcards)

        assertTrue(result.isExtends)
        assertResolvedTypeIs<ResolvedReferenceType>(result.boundedType)
        assertEquals(solvedJavaLangString, result.boundedType)
    }

    @Test
    fun `simplifyWildcard() - Between Type Variable and Uncanonicalized Wildcard`() {
        val wildcards = listOf(
            ResolvedWildcard.superBound(getSolvedJavaUtilMap().asReferenceType().typeParametersValues()[0]),
            ResolvedWildcard.superBound(solvedJavaLangObject)
        )

        // <? super java.util.Map.K> & <? super ?> -> <? super java.util.Map.K>
        val result = genericsSolver!!.simplifyWildcard(wildcards)

        assertTrue(result.isSuper)
        assertResolvedTypeIs<ResolvedTypeVariable>(result.boundedType)
        assertEquals("java.util.Map.K", result.boundedType.asTypeVariable().qualifiedName())
    }

    @Test
    fun `simplifyWildcard() - Multiple Extends Bound with Common More-Specific Bound`() {
        val boundedTypes = listOf(solvedJavaLangComparable, solvedJavaLangString)
        val wildcards = boundedTypes.map { ResolvedWildcard.extendsBound(it) }

        // <? extends Comparable & String> -> <? extends String>
        val result = genericsSolver!!.simplifyWildcard(wildcards)

        assertTrue(result.isExtends)
        assertResolvedTypeIs<ResolvedReferenceType>(result.boundedType)
        assertEquals(solvedJavaLangString, result.boundedType)
    }

    @Test
    fun `simplifyWildcard() - Multiple Generic Extends Bound with Common More-Specific Bound`() {
        val commonType = ResolvedWildcard.extendsBound(solvedJavaLangString)

        val boundedTypes = listOf(
            getSolvedJavaUtilCollection(commonType),
            getSolvedJavaUtilSet(commonType)
        )
        val wildcards = boundedTypes.map { ResolvedWildcard.extendsBound(it) }

        // <? extends Collection<? extends String> & Set<? extends String>> -> <? extends Set<? extends String>>
        val result = genericsSolver!!.simplifyWildcard(wildcards)

        assertTrue(result.isExtends)
        assertResolvedTypeIs<ResolvedReferenceType>(result.boundedType)
        assertEquals(getSolvedJavaUtilSet(commonType), result.boundedType)
    }

    // Corresponding FuzzySymbolSolver test: Cli 40f - Implement isMoreSpecific for Array and Wildcard
    @Test
    fun `isMoreSpecific() - Array vs Wildcard`() {
        val lhs = ResolvedArrayType(ResolvedReflectionReferenceType<File>(typeSolver!!), 1)
        val rhs = ResolvedWildcard.UNBOUNDED

        val result = genericsSolver!!.isMoreSpecific(lhs, rhs)
        assertTrue(result)
    }

    // Corresponding FuzzySymbolSolver test: Closure 1f - Get Specific Wildcard for Effectively-Unbounded Wildcards
    @Test
    fun `getMoreSpecific() - Effectively Unbounded Wildcards`() {
        val lhs = ResolvedWildcard.extendsBound(ResolvedObjectType(typeSolver!!))
        val rhs = ResolvedWildcard.superBound(ResolvedReflectionReferenceType<String>(typeSolver!!))

        val result = genericsSolver!!.getMoreSpecific(lhs, rhs)
        assertEquals(rhs, result)
    }

    // Corresponding FuzzySymbolSolver test: Closure 1f - Get More Specific Wildcard Type Between Type Variable and Wildcard
    @Test
    fun `getMoreSpecific() - Delegate Generic Impl to Wildcard`() {
        val lhs = ResolvedWildcard.extendsBound(
            ReferenceTypeImpl.undeterminedParameters(
                ReflectionInterfaceDeclaration(java.util.Map::class.java, typeSolver!!)
            )
        )
        val rhs = ResolvedWildcard.extendsBound(
            ResolvedReflectionReferenceType<Map<*, *>>(
                typeSolver!!,
                ResolvedWildcard.extendsBound(ResolvedObjectType(typeSolver!!)),
                ResolvedWildcard.extendsBound(ResolvedObjectType(typeSolver!!))
            )
        )

        val result = genericsSolver!!.getMoreSpecific(lhs, rhs)
        assertEquals(rhs, result)
    }

    @Test
    fun `getMoreSpecific() - Get Specific Reference Type in Same Hierarchy`() {
        setUp(TestProjects.Closure1f.getTypeSolver())

        val lhs = typeSolver!!.solveType("com.google.javascript.rhino.jstype.StaticSlot")
            .let {
                val tp0 = createResolvedRefType(
                    typeSolver!!.solveType("com.google.javascript.rhino.jstype.JSType")
                )
                createResolvedRefType(it, listOf(tp0))
            }
        val rhs = createResolvedRefType(
            typeSolver!!.solveType("com.google.javascript.rhino.jstype.SimpleSlot")
        )

        val result = genericsSolver!!.getMoreSpecific(lhs, rhs)
        assertEquals(rhs, result)
    }

    @Test
    fun `getMoreSpecific() - Get Specific Reference Type by Common Supertype`() {
        setUp(TestProjects.Collections27f.getTypeSolver())

        val lhs = createResolvedRefType(typeSolver!!.solveType("java.lang.String"))
        val rhs = createResolvedRefType(typeSolver!!.solveType("java.lang.Integer"))

        val result = genericsSolver!!.getMoreSpecific(lhs, rhs)
        assertResolvedTypeIs<ResolvedReferenceType>(result)
        // Comparable is also accepted here
        assertEquals("java.io.Serializable", result.qualifiedName)
    }

    @Test
    fun `getMoreSpecific() - Get Specific Type Between Null and Wildcard`() {
        val lhs = NullType.INSTANCE
        val rhs = ResolvedWildcard.extendsBound(
            getSolvedJavaUtilCollection(
                ResolvedWildcard.extendsBound(solvedJavaLangInteger)
            )
        )

        val result = genericsSolver!!.getMoreSpecific(lhs, rhs)
        assertEquals(rhs, result)
    }

    @Test
    fun `getMoreSpecific() - Get Specific Wildcard between Unbounded Tp and Effectively Unbounded Tp`() {
        val lhs = ResolvedIntersectionWildcard(listOf(
            createResolvedRefType(
                typeSolver!!.solveType("java.lang.Comparable"),
                listOf(
                    ResolvedWildcard.extendsBound(ResolvedObjectType(typeSolver!!))
                )
            ),
            createResolvedRefType(typeSolver!!.solveType("java.lang.Cloneable"))
        ))
        val rhs = ResolvedWildcard.extendsBound(ResolvedIntersectionWildcard(listOf(
            createResolvedRefType(
                typeSolver!!.solveType("java.lang.Comparable"),
                listOf(
                    ResolvedWildcard.UNBOUNDED
                )
            ),
            createResolvedRefType(typeSolver!!.solveType("java.lang.Cloneable"))
        )))

        assertDoesNotThrow { genericsSolver!!.getMoreSpecific(lhs, rhs) }
    }

    @AfterEach
    fun tearDown() {
        genericsSolver = null
        typeSolver = null

        clearMemory()
    }

    companion object {

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            GlobalConfiguration()
        }
    }
}