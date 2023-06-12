package com.derppening.researchprojecttoolkit.util

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.tool.assumeTrue
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.EnumConstantDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedEnumConstantDeclaration
import com.github.javaparser.resolution.declarations.ResolvedEnumDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import com.github.javaparser.resolution.model.typesystem.NullType
import com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl
import com.github.javaparser.resolution.types.ResolvedArrayType
import com.github.javaparser.resolution.types.ResolvedIntersectionType
import com.github.javaparser.resolution.types.ResolvedPrimitiveType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import org.junit.jupiter.api.Nested
import kotlin.jvm.optionals.getOrNull
import kotlin.test.*

class SymbolSolverUtilsTest {

    @Test
    fun `getOverriddenMethodInType Test - Generics`() {
        val reducerContext = TestProjects.Gson1f.getReducerContext()

        val derivedMethod = reducerContext.getCUByPrimaryTypeName("com.google.gson.DefaultTypeAdapters")
            .also { assumeTrue(it != null) }!!
            .primaryType.get()
            .members
            .single { it.isClassOrInterfaceDeclaration && it.asClassOrInterfaceDeclaration().nameAsString == "StringTypeAdapter" }
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("serialize", "String", "Type", "JsonSerializationContext").single()
        val derivedResolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(derivedMethod)

        val baseType = reducerContext.getCUByPrimaryTypeName("com.google.gson.JsonSerializer")
            .also { assumeTrue(it != null) }!!
            .primaryType.get()
        val baseResolvedType = reducerContext.resolveDeclaration<ResolvedReferenceTypeDeclaration>(baseType)

        val baseResolvedMethod = getOverriddenMethodInType(derivedResolvedMethod, baseResolvedType, reducerContext)
        assertNotNull(baseResolvedMethod)
        assertEquals("serialize", baseResolvedMethod.name)
        assertEquals("com.google.gson.JsonSerializer", baseResolvedMethod.declaringType().qualifiedName)
        assertEquals(3, baseResolvedMethod.numberOfParams)
        assertEquals("T", baseResolvedMethod.getParam(0).describeType())
        assertEquals("java.lang.reflect.Type", baseResolvedMethod.getParam(1).describeType())
        assertEquals("com.google.gson.JsonSerializationContext", baseResolvedMethod.getParam(2).describeType())
    }

    @Test
    fun `getOverriddenMethodInType Test - Generics in Type Param`() {
        val reducerContext = TestProjects.Collections27f.getReducerContext()

        val methodCU = reducerContext.getCUByPrimaryTypeName("org.apache.commons.collections4.bidimap.DualHashBidiMap")
        assumeTrue(methodCU != null)
        val method = methodCU.primaryType.get()
            .getMethodsBySignature("createBidiMap", "Map<V,K>", "Map<K,V>", "BidiMap<K,V>").single()
        val derivedResolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(method)

        val baseType = reducerContext.getCUByPrimaryTypeName("org.apache.commons.collections4.bidimap.AbstractDualBidiMap")
            .also { assumeTrue(it != null) }!!
            .primaryType.get()
        val baseResolvedType = reducerContext.resolveDeclaration<ResolvedReferenceTypeDeclaration>(baseType)

        val baseResolvedMethod = getOverriddenMethodInType(derivedResolvedMethod, baseResolvedType, reducerContext)
        assertNotNull(baseResolvedMethod)
        assertEquals("createBidiMap", baseResolvedMethod.name)
        assertEquals("org.apache.commons.collections4.bidimap.AbstractDualBidiMap", baseResolvedMethod.declaringType().qualifiedName)
        assertEquals(3, baseResolvedMethod.numberOfParams)
    }

    @Test
    fun `getOverriddenMethodInType Test - Method in Generic Anonymous Class`() {
        val reducerContext = TestProjects.Compress9f.getReducerContext()

        val derivedMethod = reducerContext.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.dump.DumpArchiveInputStream")
            .also { assumeTrue(it != null) }!!
            .primaryType.get()
            .getConstructorByParameterTypes("InputStream").get()
            .body
            .statements[5].asExpressionStmt()
            .expression.asAssignExpr()
            .value.asObjectCreationExpr()
            .arguments[1].asObjectCreationExpr()
            .anonymousClassBody.get()
            .single {
                it is MethodDeclaration &&
                        it.nameAsString == "compare" &&
                        it.parameters.size == 2 &&
                        it.parameters[0].typeAsString == "DumpArchiveEntry" &&
                        it.parameters[1].typeAsString == "DumpArchiveEntry"
            }
        val derivedResolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(derivedMethod)

        val tp0 = reducerContext.typeSolver.solveType("org.apache.commons.compress.archivers.dump.DumpArchiveEntry")
            .let { createResolvedRefType(it) }
        val baseResolvedType = ResolvedReflectionReferenceType<Comparator<*>>(
            reducerContext.typeSolver,
            tp0
        )

        val baseResolvedMethod = getOverriddenMethodInType(derivedResolvedMethod, baseResolvedType, reducerContext)
        assertNotNull(baseResolvedMethod)
    }

    @Test
    fun `getOverriddenMethodInType Test - Enum Constant`() {
        val reducerContext = TestProjects.Compress21f.getReducerContext()

        val derivedMethod = reducerContext.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.SevenZMethod")
            .also { assumeTrue(it != null) }!!
            .primaryType.get()
            .asEnumDeclaration()
            .entries
            .single { it.nameAsString == "LZMA2" }
            .classBody
            .single {
                it.isMethodDeclaration &&
                        it.asMethodDeclaration().nameAsString == "getProperties" &&
                        it.asMethodDeclaration().parameters.size == 0
            }
            .asMethodDeclaration()
        val derivedResolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(derivedMethod)

        val baseType = reducerContext.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.SevenZMethod")
            .also { assumeTrue(it != null) }!!
            .primaryType.get()
            .asEnumDeclaration()
        val baseResolvedType = reducerContext.resolveDeclaration<ResolvedEnumDeclaration>(baseType)

        val baseResolvedMethod = getOverriddenMethodInType(derivedResolvedMethod, baseResolvedType, reducerContext)
        assertNotNull(baseResolvedMethod)
        assertEquals("getProperties", baseResolvedMethod.name)
        assertEquals("org.apache.commons.compress.archivers.sevenz.SevenZMethod", baseResolvedMethod.declaringType().qualifiedName)
        assertEquals(0, baseResolvedMethod.numberOfParams)

        val baseMethodAstExpected = baseType.getMethodsBySignature("getProperties").single()
        val baseMethodAstActual = baseResolvedMethod.toAst()
            .map { reducerContext.mapNodeInLoadedSet(it) }
            .get()

        assertSame(baseMethodAstExpected, baseMethodAstActual)
    }

    @Test
    fun `getOverriddenMethodInType Test - Library Supertype`() {
        val reducerContext = TestProjects.Cli13f.getReducerContext()

        val derivedMethod = reducerContext.getCUByPrimaryTypeName("org.apache.commons.cli2.option.GroupImpl")
            .also { assumeTrue(it != null) }!!
            .types
            .single { it.nameAsString == "ReverseStringComparator" }.asClassOrInterfaceDeclaration()
            .getMethodsBySignature("compare", "Object", "Object").single()
        val derivedResolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(derivedMethod)

        val baseResolvedType = reducerContext.typeSolver.solveType("java.util.Comparator")

        val baseResolvedMethod = getOverriddenMethodInType(derivedResolvedMethod, baseResolvedType, reducerContext)
        assertNotNull(baseResolvedMethod)
        assertEquals("compare", baseResolvedMethod.name)
        assertEquals("java.util.Comparator", baseResolvedMethod.declaringType().qualifiedName)
        assertEquals(2, baseResolvedMethod.numberOfParams)
    }

    @Test
    fun `getOverriddenMethodInType Test - Library Supertype with Wildcard`() {
        val reducerContext = TestProjects.Collections25f.getReducerContext()

        val derivedMethod = reducerContext.getCUByPrimaryTypeName("org.apache.commons.collections4.list.AbstractLinkedList")
            .also { assumeTrue(it != null) }!!
            .primaryType.get()
            .getMethodsBySignature("retainAll", "Collection<?>").single()
        val derivedResolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(derivedMethod)

        val baseResolvedType = reducerContext.typeSolver.solveType("java.util.List")

        val baseResolvedMethod = getOverriddenMethodInType(derivedResolvedMethod, baseResolvedType, reducerContext)
        assertNotNull(baseResolvedMethod)
        assertEquals("retainAll", baseResolvedMethod.name)
        assertEquals("java.util.List", baseResolvedMethod.declaringType().qualifiedName)
        assertEquals(1, baseResolvedMethod.numberOfParams)
    }

    @Ignore
    @Test
    fun `getOverriddenMethodInType Test - Supertype with Swapped Params`() {
        val reducerContext = TestProjects.Collections25f.getReducerContext()

        val derivedMethod = reducerContext.getCUByPrimaryTypeName("org.apache.commons.collections4.bidimap.TreeBidiMap")
            .also { assumeTrue(it != null) }!!
            .primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "Inverse" }
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("put", "V", "K").single()
        val derivedResolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(derivedMethod)

        val baseResolvedType = reducerContext.typeSolver.solveType("org.apache.commons.collections4.BidiMap")

        val baseResolvedMethod = getOverriddenMethodInType(derivedResolvedMethod, baseResolvedType, reducerContext)
        assertNotNull(baseResolvedMethod)
        assertEquals("put", baseResolvedMethod.name)
        assertEquals("org.apache.commons.collections4.BidiMap", baseResolvedMethod.declaringType().qualifiedName)
        assertEquals(2, baseResolvedMethod.numberOfParams)
    }

    @Test
    fun `getOverridingMethodInType Test - Generics`() {
        val reducerContext = TestProjects.Gson1f.getReducerContext()

        val derivedType = reducerContext.getCUByPrimaryTypeName("com.google.gson.DefaultTypeAdapters")
            .also { assumeTrue(it != null) }!!
            .primaryType.get()
            .members
            .single { it.isClassOrInterfaceDeclaration && it.asClassOrInterfaceDeclaration().nameAsString == "StringTypeAdapter" }
            .asClassOrInterfaceDeclaration()
        val derivedResolvedType = reducerContext.resolveDeclaration<ResolvedReferenceTypeDeclaration>(derivedType)

        val baseMethod = reducerContext.getCUByPrimaryTypeName("com.google.gson.JsonSerializer")
            .also { assumeTrue(it != null) }!!
            .primaryType.get()
            .getMethodsBySignature("serialize", "T", "Type", "JsonSerializationContext").single()
        val baseResolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(baseMethod)

        val derivedResolvedMethod = getOverridingMethodInType(baseResolvedMethod, derivedResolvedType, reducerContext)
        assertNotNull(derivedResolvedMethod)
        assertEquals("serialize", derivedResolvedMethod.name)
        assertEquals("com.google.gson.DefaultTypeAdapters.StringTypeAdapter", derivedResolvedMethod.declaringType().qualifiedName)
        assertEquals(3, derivedResolvedMethod.numberOfParams)
        assertEquals("java.lang.String", derivedResolvedMethod.getParam(0).describeType())
        assertEquals("java.lang.reflect.Type", derivedResolvedMethod.getParam(1).describeType())
        assertEquals("com.google.gson.JsonSerializationContext", derivedResolvedMethod.getParam(2).describeType())
    }

    @Test
    fun `getOverridingMethodInType Test - Find Method with Covariant Return Type in Subclass`() {
        val reducerContext = TestProjects.Codec14f.getReducerContext()

        val derivedType = reducerContext.getCUByPrimaryTypeName("org.apache.commons.codec.language.bm.Rule")
            .also { assumeTrue(it != null) }!!
            .primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "PhonemeList" }
            .asClassOrInterfaceDeclaration()
        val derivedResolvedType = reducerContext.resolveDeclaration<ResolvedReferenceTypeDeclaration>(derivedType)

        val baseMethod = reducerContext.getCUByPrimaryTypeName("org.apache.commons.codec.language.bm.Rule")
            .also { assumeTrue(it != null) }!!
            .primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "PhonemeExpr" }
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("getPhonemes").single()
        val baseResolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(baseMethod)

        val derivedResolvedMethodInType = getOverridingMethodInType(baseResolvedMethod, derivedResolvedType, reducerContext)
        assertNotNull(derivedResolvedMethodInType)
        assertEquals("getPhonemes", derivedResolvedMethodInType.name)
        assertEquals("org.apache.commons.codec.language.bm.Rule.PhonemeList", derivedResolvedMethodInType.declaringType().qualifiedName)
        assertEquals(0, derivedResolvedMethodInType.numberOfParams)

        val derivedResolvedMethodInHierarchy = getOverridingMethodInType(baseResolvedMethod, derivedResolvedType, reducerContext, true)
        assertNotNull(derivedResolvedMethodInHierarchy)
        assertEquals("getPhonemes", derivedResolvedMethodInHierarchy.name)
        assertEquals("org.apache.commons.codec.language.bm.Rule.PhonemeList", derivedResolvedMethodInHierarchy.declaringType().qualifiedName)
        assertEquals(0, derivedResolvedMethodInHierarchy.numberOfParams)
    }

    @Test
    fun `getOverridingMethodsInType Test - Temp`() {
        val reducerContext = TestProjects.Cli21f.getReducerContext()

        val derivedType = reducerContext.getCUByPrimaryTypeName("org.apache.commons.cli2.commandline.WriteableCommandLineImpl")
            .also { assumeTrue(it != null) }!!
            .primaryType.get()
            .asClassOrInterfaceDeclaration()
        val derivedResolvedType = reducerContext.resolveDeclaration<ResolvedReferenceTypeDeclaration>(derivedType)

        val baseMethod = reducerContext.getCUByPrimaryTypeName("org.apache.commons.cli2.CommandLine")
            .also { assumeTrue(it != null) }!!
            .primaryType.get()
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("hasOption", "String").single()
        val baseResolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(baseMethod)

        val derivedResolvedMethodInType = getOverridingMethodInType(baseResolvedMethod, derivedResolvedType, reducerContext)
        assertNull(derivedResolvedMethodInType)

        val derivedResolvedMethodInHierarchy = getOverridingMethodInType(baseResolvedMethod, derivedResolvedType, reducerContext, true)
        assertNotNull(derivedResolvedMethodInHierarchy)
        assertEquals("hasOption", derivedResolvedMethodInHierarchy.name)
        assertEquals("org.apache.commons.cli2.commandline.CommandLineImpl", derivedResolvedMethodInHierarchy.declaringType().qualifiedName)
        assertEquals(1, derivedResolvedMethodInHierarchy.numberOfParams)
        val param0Type = assertIs<ResolvedReferenceType>(derivedResolvedMethodInHierarchy.getParam(0).type)
        assertEquals("java.lang.String", param0Type.qualifiedName)
    }

    @Test
    fun `getOverridingMethodInEnumConst - Solve for Method in Enum`() {
        val reducerContext = TestProjects.Compress21f.getReducerContext()

        val enumConstant = reducerContext.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.SevenZMethod")
            .also { assumeTrue(it != null) }!!
            .primaryType.get().asEnumDeclaration()
            .entries.single { it.nameAsString == "LZMA2" }
        val resolvedEnumConstant = reducerContext.resolveDeclaration<ResolvedEnumConstantDeclaration>(enumConstant)

        val baseMethod = reducerContext.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.SevenZMethod")
            .also { assumeTrue(it != null) }!!
            .primaryType.get().asEnumDeclaration()
            .getMethodsBySignature("getProperties").single()
        val baseResolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(baseMethod)

        val resolvedMethodInEnumConst = getOverridingMethodInEnumConst(baseResolvedMethod, resolvedEnumConstant, reducerContext)
        assertNotNull(resolvedMethodInEnumConst)
        assertEquals("getProperties", resolvedMethodInEnumConst.name)
        assertEquals(0, resolvedMethodInEnumConst.numberOfParams)
        val methodInEnumConstant = resolvedMethodInEnumConst.toTypedAst<MethodDeclaration>(reducerContext)
        val declaringDecl = assertIs<EnumConstantDeclaration>(methodInEnumConstant.parentNode.getOrNull())
        val resolvedDeclaringDecl = reducerContext.resolveDeclaration<ResolvedEnumConstantDeclaration>(declaringDecl)
        assertEquals("LZMA2", resolvedDeclaringDecl.name)
        assertEquals("org.apache.commons.compress.archivers.sevenz.SevenZMethod", resolvedDeclaringDecl.type.asReferenceType().qualifiedName)
    }

    @Test
    fun `getOverridingMethodInEnumConst - Solve for Method in Enum 2`() {
        val reducerContext = TestProjects.Compress33f.getReducerContext()

        val enumConstant = reducerContext.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.pack200.Pack200Strategy")
            .also { assumeTrue(it != null) }!!
            .primaryType.get().asEnumDeclaration()
            .entries.single { it.nameAsString == "IN_MEMORY" }
        val resolvedEnumConstant = reducerContext.resolveDeclaration<ResolvedEnumConstantDeclaration>(enumConstant)

        val baseMethod = reducerContext.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.pack200.Pack200Strategy")
            .also { assumeTrue(it != null) }!!
            .primaryType.get().asEnumDeclaration()
            .getMethodsBySignature("newStreamBridge").single()
        val baseResolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(baseMethod)

        val resolvedMethodInEnumConst = getOverridingMethodInEnumConst(baseResolvedMethod, resolvedEnumConstant, reducerContext)
        assertNotNull(resolvedMethodInEnumConst)
        assertEquals("newStreamBridge", resolvedMethodInEnumConst.name)
        assertEquals(0, resolvedMethodInEnumConst.numberOfParams)
        val methodInEnumConstant = resolvedMethodInEnumConst.toTypedAst<MethodDeclaration>(reducerContext)
        val declaringDecl = assertIs<EnumConstantDeclaration>(methodInEnumConstant.parentNode.getOrNull())
        val resolvedDeclaringDecl = reducerContext.resolveDeclaration<ResolvedEnumConstantDeclaration>(declaringDecl)
        assertEquals("IN_MEMORY", resolvedDeclaringDecl.name)
        assertEquals("org.apache.commons.compress.compressors.pack200.Pack200Strategy", resolvedDeclaringDecl.type.asReferenceType().qualifiedName)
    }

    @Test
    fun `findSuperclassDependentConstructor - Find Vararg Constructor`() {
        val reducerContext = TestProjects.Compress36f.getReducerContext()

        val cu = reducerContext.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.Coders")
        assumeTrue(cu != null)
        val type = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "BCJDecoder" }
            .asClassOrInterfaceDeclaration()
        val ctor = type.getConstructorByParameterTypes("FilterOptions").get()

        val superclassCtor = with(reducerContext) { findSuperclassDependentConstructor(ctor) }
        val superclassType = superclassCtor.declaringType()
        assertEquals(1, superclassCtor.numberOfParams)
        assertEquals("org.apache.commons.compress.archivers.sevenz.CoderBase", superclassType.qualifiedName)
        val superclassCtorParams = superclassCtor.parameters
        val superclassCtorParam0 = superclassCtorParams[0]
        assertTrue(superclassCtorParam0.isVariadic)
        val superclassCtorParam0Type = assertIs<ResolvedArrayType>(superclassCtorParam0.type)
        assertEquals(1, superclassCtorParam0Type.arrayLevel())
        val superclassCtorParam0ComponentType = assertIs<ResolvedReferenceType>(superclassCtorParam0Type.componentType)
        assertEquals("java.lang.Class", superclassCtorParam0ComponentType.qualifiedName)
    }

    @Test
    fun `rawifyType - Erased Reference Type`() {
        val reducerContext = TestProjects.Collections27f.getReducerContext()

        val objTypeDecl = reducerContext.typeSolver
            .solveType("org.apache.commons.collections4.bidimap.AbstractSortedBidiMapTest")
        val type = ReferenceTypeImpl(objTypeDecl)

        val rawType = rawifyType(type, reducerContext.typeSolver)
        assertResolvedTypeIs<ResolvedReferenceType>(rawType)
        assertEquals("org.apache.commons.collections4.bidimap.AbstractSortedBidiMapTest", rawType.qualifiedName)
        assertTrue(rawType.isRawType)
        assertTrue(rawType.typeParametersValues().isEmpty())
    }

    @Test
    fun `rawifyType - Intersection of Null and Object Type`() {
        val reducerContext = TestProjects.Collections27f.getReducerContext()

        val objTypeDecl = reducerContext.typeSolver
            .solveType("org.apache.commons.collections4.list.TreeList.AVLNode")
        val type = ResolvedIntersectionType(listOf(
            NullType.INSTANCE,
            ReferenceTypeImpl(objTypeDecl)
        ))

        val rawType = rawifyType(type, reducerContext.typeSolver)
        assertResolvedTypeIs<ResolvedReferenceType>(rawType)
        assertEquals("org.apache.commons.collections4.list.TreeList.AVLNode", rawType.qualifiedName)
        assertTrue(rawType.isRawType)
        assertTrue(rawType.typeParametersValues().isEmpty())
    }

    @Test
    fun `rawifyType - Intersection of Object and Array Type`() {
        val reducerContext = TestProjects.Math1f.getReducerContext()

        val objType = createResolvedRefType(reducerContext.typeSolver.solvedJavaLangObject)
        val arrayType = ResolvedArrayType(ResolvedPrimitiveType.DOUBLE, 1)
        val intersectionType = ResolvedIntersectionType(listOf(objType, arrayType))

        val rawType = rawifyType(intersectionType, reducerContext.typeSolver)
        assertResolvedTypeIs<ResolvedIntersectionType>(rawType)
        assertEquals(intersectionType, rawType)
    }

    @Test
    fun `rawifyType - Intersection of Array Types`() {
        val reducerContext = TestProjects.Collections27f.getReducerContext()

        val objTypeDecl = reducerContext.typeSolver
            .solveType("org.apache.commons.collections4.Predicate")
        val objType = ReferenceTypeImpl(objTypeDecl)
        val objExtendsType = ReferenceTypeImpl(
            objTypeDecl,
            listOf(ResolvedIntersectionWildcard(listOf(objType.typeParametersValues()[0])))
        )
        val type = ResolvedIntersectionType(listOf(
            ResolvedArrayType(objType, 1),
            ResolvedArrayType(objExtendsType, 1)
        ))

        val rawType = rawifyType(type, reducerContext.typeSolver)
        assertResolvedTypeIs<ResolvedArrayType>(rawType)
        assertEquals(1, rawType.arrayLevel())

        val rawComponentType = rawType.componentType
        assertResolvedTypeIs<ResolvedReferenceType>(rawComponentType)
        assertEquals("org.apache.commons.collections4.Predicate", rawComponentType.qualifiedName)
        assertTrue(rawComponentType.isRawType)
        assertTrue(rawComponentType.typeParametersValues().isEmpty())
    }

    @Nested
    inner class SymbolSolverCacheTests {

        private var cache: SymbolSolverCache? = null

        @BeforeTest
        fun setUp() {
            cache = SymbolSolverCache()
        }

        @Test
        fun `Test Key Collision Avoidance - Same Class Name on refTypeDeclDeclaredMethods`() {
            val cache = checkNotNull(cache)
            val typeSolver = TestProjects.Lang57f.getTypeSolver()

            val type1Name = "org.apache.commons.lang.enum.Broken4OperationEnum"
            val type1 = typeSolver.solveType(type1Name)
            val methods1 = cache.getDeclaredMethods(type1)
            assertTrue {
                methods1.all {
                    it.declaringType().qualifiedName == type1Name
                }
            }

            val type2Name = "org.apache.commons.lang.enums.Broken4OperationEnum"
            val type2 = typeSolver.solveType(type2Name)
            val methods2 = cache.getDeclaredMethods(type2)
            assertTrue {
                methods2.all {
                    it.declaringType().qualifiedName == type2Name
                }
            }
        }

        @AfterTest
        fun tearDown() {
            cache?.clear()
            cache = null
        }
    }

    @AfterTest
    fun tearDown() {
        clearMemory()
    }
}