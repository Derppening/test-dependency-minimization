package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertIsUnbounded
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.util.getTypeByName
import com.derppening.researchprojecttoolkit.util.toResolvedType
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.resolution.declarations.ResolvedAnnotationMemberDeclaration
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.declarations.ResolvedEnumDeclaration
import com.github.javaparser.resolution.types.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FuzzySymbolSolverJacksonDatabind1fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.JacksonDatabind1f

    @Test
    fun `Propagate Wildcard Bounds Across Fields and Methods`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/util/ClassUtil.java",
            symbolSolver
        )
        val argNode = cu.primaryType.get()
            .getMethodsBySignature("findEnumType", "EnumSet<?>").single()
            .body.get()
            .statements[0]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
            .arguments[0]
            .asMethodCallExpr()
        assumeTrue { argNode.toString() == "s.iterator().next()" }

        val iteratorCallNode = argNode.scope.get()
            .asMethodCallExpr()

        val sNameExprNode = iteratorCallNode.scope.get()
            .asNameExpr()

        val sType = symbolSolver.calculateType(sNameExprNode)
        assertResolvedTypeIs<ResolvedReferenceType>(sType)
        assertEquals("java.util.EnumSet", sType.qualifiedName)
        assertEquals(1, sType.typeParametersMap.size)
        val sTypeTp0 = sType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(sTypeTp0)
        assertIsUnbounded(sTypeTp0)

        val iteratorType = symbolSolver.calculateType(iteratorCallNode)
        assertResolvedTypeIs<ResolvedReferenceType>(iteratorType)
        assertEquals("java.util.Iterator", iteratorType.qualifiedName)
        assertEquals(1, iteratorType.typeParametersMap.size)
        val iteratorTypeTp0 = iteratorType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(iteratorTypeTp0)
        assertTrue(iteratorTypeTp0.isBounded)
        val iteratorTypeTp0Bound = iteratorTypeTp0.boundedType
        assertResolvedTypeIs<ResolvedReferenceType>(iteratorTypeTp0Bound)
        assertTrue(iteratorTypeTp0Bound.isJavaLangEnum)

        val argType = symbolSolver.calculateType(argNode)
        assertIs<ResolvedReferenceType>(argType)
        assertTrue(argType.isJavaLangEnum)
        assertEquals(1, argType.typeParametersMap.size)
        val argTypeTp0 = argType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(argTypeTp0)
        assertIsUnbounded(argTypeTp0)
    }

    @Test
    fun `Propagate Wildcard Bounds Across Fields and Methods (2)`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/util/ClassUtil.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("findEnumType", "EnumMap<?,?>").single()
            .body.get()
            .statements[0]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "findEnumType(m.keySet().iterator().next())" }

        val arg0Node = node.arguments[0].asMethodCallExpr()
        val iteratorNode = arg0Node.scope.get().asMethodCallExpr()
        val keySetNode = iteratorNode.scope.get().asMethodCallExpr()
        val mNode = keySetNode.scope.get().asNameExpr()

        val mType = symbolSolver.calculateType(mNode)

        assertResolvedTypeIs<ResolvedReferenceType>(mType)
        assertEquals("java.util.EnumMap", mType.qualifiedName)
        assertEquals(2, mType.typeParametersValues().size)
        val mTypeTp0 = mType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(mTypeTp0)
        assertIsUnbounded(mTypeTp0)
        val mTypeTp1 = mType.typeParametersValues()[1]
        assertResolvedTypeIs<ResolvedWildcard>(mTypeTp1)
        assertIsUnbounded(mTypeTp1)

        val keySetType = symbolSolver.calculateType(keySetNode)
        assertResolvedTypeIs<ResolvedReferenceType>(keySetType)
        assertEquals("java.util.Set", keySetType.qualifiedName)
        assertEquals(1, keySetType.typeParametersValues().size)
        val keySetTp0Type = keySetType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(keySetTp0Type)
        assertTrue(keySetTp0Type.isBounded)
        assertTrue(keySetTp0Type.isExtends)
        val keySetBoundType = keySetTp0Type.boundedType
        assertResolvedTypeIs<ResolvedReferenceType>(keySetBoundType)
        assertTrue(keySetBoundType.isJavaLangEnum)
        assertEquals(1, keySetBoundType.typeParametersValues().size)
        val keySetBoundTypeTp0 = keySetBoundType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(keySetBoundTypeTp0)
        assertIsUnbounded(keySetBoundTypeTp0)

        val iteratorType = symbolSolver.calculateType(iteratorNode)
        assertResolvedTypeIs<ResolvedReferenceType>(iteratorType)
        assertEquals("java.util.Iterator", iteratorType.qualifiedName)
        assertEquals(1, iteratorType.typeParametersValues().size)
        val iteratorTypeTp0 = iteratorType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(iteratorTypeTp0)
        assertTrue(iteratorTypeTp0.isBounded)
        assertTrue(iteratorTypeTp0.isExtends)
        val iteratorTypeTp0Bound = iteratorTypeTp0.boundedType
        assertResolvedTypeIs<ResolvedReferenceType>(iteratorTypeTp0Bound)
        assertTrue(iteratorTypeTp0Bound.isJavaLangEnum)
        assertEquals(1, iteratorTypeTp0Bound.typeParametersValues().size)
        val iteratorTypeTp0BoundTp0 = iteratorTypeTp0Bound.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(iteratorTypeTp0BoundTp0)
        assertIsUnbounded(iteratorTypeTp0BoundTp0)

        val arg0Type = symbolSolver.calculateType(arg0Node)
        assertResolvedTypeIs<ResolvedReferenceType>(arg0Type)
        assertTrue(arg0Type.isJavaLangEnum)
        assertEquals(1, arg0Type.typeParametersValues().size)
        val arg0Tp0Type = arg0Type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(arg0Tp0Type)
        assertIsUnbounded(arg0Tp0Type)

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.lang.Class", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(typeTp0)
        assertTrue(typeTp0.isBounded)
        assertTrue(typeTp0.isExtends)
        val typeTp0Bound = typeTp0.boundedType
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp0Bound)
        assertTrue(typeTp0Bound.isJavaLangEnum)
        assertEquals(1, typeTp0Bound.typeParametersValues().size)
        val typeTp0BoundTp0 = typeTp0Bound.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(typeTp0BoundTp0)
        assertIsUnbounded(typeTp0BoundTp0)
    }

    @Test
    fun `Solve Declaration of Annotation`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/introspect/VisibilityChecker.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "Std" }
            .asClassOrInterfaceDeclaration()
            .getConstructorByParameterTypes("JsonAutoDetect").get()
            .body
            .statements[0]
            .asExpressionStmt()
            .expression
            .asAssignExpr()
            .value
            .asMethodCallExpr()

        val decl = symbolSolver.resolveDeclaration(node, ResolvedDeclaration::class.java)
        assertIs<ResolvedAnnotationMemberDeclaration>(decl)
        assertEquals("getterVisibility", decl.name)
        assertEquals("com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility", decl.type.describe())
    }

    @Test
    fun `Recursively Solve Concreteness of LazyType`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/DeserializationConfig.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("getAnnotationIntrospector").single()
            .body.get()
            .statements[0]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
            .scope.get()
            .asSuperExpr()

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("com.fasterxml.jackson.databind.cfg.MapperConfigBase", type.qualifiedName)
        assertEquals(2, type.typeParametersValues().size)
        assertEquals("com.fasterxml.jackson.databind.DeserializationFeature", type.typeParametersValues()[0].asReferenceType().qualifiedName)
        assertEquals("com.fasterxml.jackson.databind.DeserializationConfig", type.typeParametersValues()[1].asReferenceType().qualifiedName)
    }

    @Test
    fun `Solve member declaration in library class type`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/util/TokenBuffer.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "Parser" }
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("getNumberType").single()
            .body.get()
            .statements[4]
            .asIfStmt()
            .thenStmt
            .asReturnStmt()
            .expression.get()
            .asFieldAccessExpr()
            .scope
            .asNameExpr()
        assumeTrue { node.toString() == "NumberType" }

        val decl = symbolSolver.resolveDeclaration(node, ResolvedDeclaration::class.java)
        assertIs<ResolvedEnumDeclaration>(decl)
        assertEquals("com.fasterxml.jackson.core.JsonParser.NumberType", decl.qualifiedName)
    }

    @Test
    fun `Ignore type param substitution for static expressions`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/deser/std/DateDeserializers.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "DateBasedDeserializer" }
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("_parseDate", "JsonParser", "DeserializationContext").single()
            .body.get()
            .statements[1]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
            .scope.get()
            .asSuperExpr()

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedTypeVariable>(typeTp0)
    }

    @Test
    fun `Specificity Logic for Generics Bound Substitution`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/ser/std/EnumMapSerializer.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("isEmpty", "EnumMap<? extends Enum<?>,?>")
            .single()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asBinaryExpr()
            .right
            .asMethodCallExpr()
            .scope.get()
            .asNameExpr()
        assumeTrue { node.toString() == "value" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.EnumMap", type.qualifiedName)
        assertEquals(2, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(typeTp0)
        assertTrue(typeTp0.isExtends)
        val typeTp0Bound = typeTp0.boundedType
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp0Bound)
        assertTrue(typeTp0Bound.isJavaLangEnum)
        val typeTp1 = type.typeParametersValues()[1]
        assertResolvedTypeIs<ResolvedWildcard>(typeTp1)
    }

    @Test
    fun `Solve field type with type variable scope`() {
        val cu = project.parseSourceFile(
            "src/test/java/com/fasterxml/jackson/databind/deser/TestGenericsBounded.java",
            symbolSolver
        )
        val fieldNode = cu.primaryType.get()
            .getMethodsBySignature("testLowerBound").single()
            .body.get()
            .statements[3]
            .asExpressionStmt()
            .expression
            .asMethodCallExpr()
            .arguments[1]
            .asFieldAccessExpr()
        val genericsNode = fieldNode.scope
            .asFieldAccessExpr()

        val genericsNodeType = symbolSolver.calculateType(genericsNode)
        assertResolvedTypeIs<ResolvedReferenceType>(genericsNodeType)
        assertEquals("com.fasterxml.jackson.databind.deser.TestGenericsBounded.IntBean", genericsNodeType.qualifiedName)

        val fieldNodeType = symbolSolver.calculateType(fieldNode)
        assertResolvedTypeIs<ResolvedPrimitiveType>(fieldNodeType)
        assertEquals(ResolvedPrimitiveType.INT, fieldNodeType)
    }

    @Test
    fun `Calculate Concrete Type of Array Length Field`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/ObjectReader.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("readValue", "byte[]").single()
            .body.get()
            .statements[0]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements
            .single()
            .asReturnStmt()
            .expression.get()
            .asCastExpr()
            .expression
            .asMethodCallExpr()
            .arguments[2]
            .asFieldAccessExpr()
        assumeTrue { node.toString() == "src.length" }

        val type = symbolSolver.calculateTypeFallback(node)
        assertResolvedTypeIs<ResolvedPrimitiveType>(type)
        assertEquals(ResolvedPrimitiveType.INT, type)
    }

    @Test
    fun `Solve Type with Intersection Type Variable Bound`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/cfg/MapperConfig.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("collectFeatureDefaults", "Class<F>").single()
            .body.get()
            .statements[1]
            .asForEachStmt()
            .iterable
            .asMethodCallExpr()
        assumeTrue { node.toString() == "enumClass.getEnumConstants()" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedArrayType>(type)
        assertEquals(1, type.arrayLevel())
        val componentType = type.componentType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("F", componentType.name)
        assertEquals("com.fasterxml.jackson.databind.cfg.MapperConfig.collectFeatureDefaults(java.lang.Class<F>)", componentType.containerQualifiedName)
    }

    @Test
    fun `Solve More Specific Type Differing in Tp Containing Type`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/util/LinkedNode.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("contains", "LinkedNode<ST>", "ST").single()
            .body.get()
            .statements[0]
            .asWhileStmt()
            .body
            .asBlockStmt()
            .statements[0]
            .asIfStmt()
            .condition
            .asBinaryExpr()
            .left
            .asMethodCallExpr()
        assumeTrue { node.toString() == "node.value()" }

        val type = symbolSolver.calculateTypeInternal(node)
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("ST", type.name)
        assertEquals("com.fasterxml.jackson.databind.util.LinkedNode.contains(com.fasterxml.jackson.databind.util.LinkedNode<ST>, ST)", type.containerQualifiedName)
    }

    @Test
    fun `Resolve Generic Type of Annotation Member`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/introspect/JacksonAnnotationIntrospector.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("findObjectIdInfo", "Annotated").single()
            .body.get()
            .statements[2]
            .asReturnStmt()
            .expression.get()
            .asObjectCreationExpr()
        assumeTrue { node.toString() == "new ObjectIdInfo(info.property(), info.scope(), info.generator())" }

        val arg0 = node.arguments[0]
            .asMethodCallExpr()
        val arg0Type = symbolSolver.calculateTypeInternal(arg0)
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it).asReferenceType() }
        assertEquals("java.lang.String", arg0Type.qualifiedName)

        val arg1 = node.arguments[1]
            .asMethodCallExpr()
        val arg1Type = symbolSolver.calculateTypeInternal(arg1)
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it).asReferenceType() }
        assertEquals("java.lang.Class", arg1Type.qualifiedName)
        assertEquals(1, arg1Type.typeParametersValues().size)
        val arg1TypeTp0 = arg1Type.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedWildcard>(it).asWildcard() }
        assertIsUnbounded(arg1TypeTp0)

        val arg2 = node.arguments[2]
            .asMethodCallExpr()
        val arg2Type = symbolSolver.calculateTypeInternal(arg2)
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it).asReferenceType() }
        assertEquals("java.lang.Class", arg2Type.qualifiedName)
        assertEquals(1, arg2Type.typeParametersValues().size)
        val arg2TypeTp0 = arg2Type.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedWildcard>(it).asWildcard() }
        assertTrue(arg2TypeTp0.isBounded)
        assertTrue(arg2TypeTp0.isExtends)
        val arg2TypeTp0Bound = arg2TypeTp0.boundedType
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it).asReferenceType() }
        assertEquals("com.fasterxml.jackson.annotation.ObjectIdGenerator", arg2TypeTp0Bound.qualifiedName)
        assertEquals(1, arg2TypeTp0Bound.typeParametersValues().size)
        val arg2TypeTp0BoundTp0 = arg2TypeTp0Bound.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedWildcard>(it).asWildcard() }
        assertIsUnbounded(arg2TypeTp0BoundTp0)

        val decl = symbolSolver.resolveDeclaration(node, ResolvedDeclaration::class.java)
        assertIs<ResolvedConstructorDeclaration>(decl)
        assertEquals(3, decl.numberOfParams)

        val declParam0Type = decl.getParam(0).type
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it).asReferenceType() }
        assertEquals("java.lang.String", declParam0Type.qualifiedName)

        val declParam1Type = decl.getParam(1).type
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it).asReferenceType() }
        assertEquals("java.lang.Class", declParam1Type.qualifiedName)
        assertEquals(1, declParam1Type.typeParametersValues().size)
        val declParam1Tp0Type = declParam1Type.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedWildcard>(it).asWildcard() }
        assertIsUnbounded(declParam1Tp0Type)

        val declParam2Type = decl.getParam(2).type
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it).asReferenceType() }
        assertEquals("java.lang.Class", declParam2Type.qualifiedName)
        assertEquals(1, declParam2Type.typeParametersValues().size)
        val declParam2Tp0Type = declParam2Type.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedWildcard>(it).asWildcard() }
        assertTrue(declParam2Tp0Type.isBounded)
        assertTrue(declParam2Tp0Type.isExtends)
        val declParam2Tp0BoundedType = declParam2Tp0Type.boundedType
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it).asReferenceType() }
        assertEquals("com.fasterxml.jackson.annotation.ObjectIdGenerator", declParam2Tp0BoundedType.qualifiedName)
        assertEquals(1, declParam2Tp0BoundedType.typeParametersValues().size)
        val declParam2Tp0BoundedTypeTp0 = declParam2Tp0BoundedType.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedWildcard>(it).asWildcard() }
        assertIsUnbounded(declParam2Tp0BoundedTypeTp0)
    }

    @Test
    fun `Solve ExplicitConstructorInvocationStmt with Vararg Candidate Constructor`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/deser/DataFormatReaders.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getConstructorByParameterTypes("ObjectReader").get()
            .body
            .statements[0]
            .asExplicitConstructorInvocationStmt()
        assumeTrue { node.toString() == "this(detectors, MatchStrength.SOLID_MATCH, MatchStrength.WEAK_MATCH, DEFAULT_MAX_INPUT_LOOKAHEAD);" }

        val arg0 = node.arguments[0]
            .asNameExpr()
        val arg0Type = symbolSolver.calculateTypeInternal(arg0)
            .let { assertResolvedTypeIs<ResolvedArrayType>(it).asArrayType() }
        assertEquals(1, arg0Type.arrayLevel())
        val arg0ComponentType = arg0Type.componentType
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it).asReferenceType() }
        assertEquals("com.fasterxml.jackson.databind.ObjectReader", arg0ComponentType.qualifiedName)

        val arg1 = node.arguments[1]
            .asFieldAccessExpr()
        val arg1Type = symbolSolver.calculateTypeInternal(arg1)
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it).asReferenceType() }
        assertEquals("com.fasterxml.jackson.core.format.MatchStrength", arg1Type.qualifiedName)

        val arg2 = node.arguments[2]
            .asFieldAccessExpr()
        val arg2Type = symbolSolver.calculateTypeInternal(arg2)
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it).asReferenceType() }
        assertEquals("com.fasterxml.jackson.core.format.MatchStrength", arg2Type.qualifiedName)

        val arg3 = node.arguments[3]
            .asNameExpr()
        val arg3Type = symbolSolver.calculateTypeInternal(arg3)
            .let { assertResolvedTypeIs<ResolvedPrimitiveType>(it).asPrimitive() }
        assertEquals(ResolvedPrimitiveType.INT, arg3Type)

        val decl = symbolSolver.resolveDeclaration(node, ResolvedDeclaration::class.java)
        assertIs<ResolvedConstructorDeclaration>(decl)
        assertEquals(4, decl.numberOfParams)

        val declParam0Type = decl.getParam(0).type
            .let { assertResolvedTypeIs<ResolvedArrayType>(it).asArrayType() }
        assertEquals(1, declParam0Type.arrayLevel())
        val declParam0ComponentType = declParam0Type.componentType
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it).asReferenceType() }
        assertEquals("com.fasterxml.jackson.databind.ObjectReader", declParam0ComponentType.qualifiedName)

        val declParam1Type = decl.getParam(1).type
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it).asReferenceType() }
        assertEquals("com.fasterxml.jackson.core.format.MatchStrength", declParam1Type.qualifiedName)

        val declParam2Type = decl.getParam(2).type
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it).asReferenceType() }
        assertEquals("com.fasterxml.jackson.core.format.MatchStrength", declParam2Type.qualifiedName)

        val declParam3Type = decl.getParam(3).type
            .let { assertResolvedTypeIs<ResolvedPrimitiveType>(it).asPrimitive() }
        assertEquals(ResolvedPrimitiveType.INT, declParam3Type)
    }

    @Test
    fun `Solve Nested Type Imported by Wildcard`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/SerializationConfig.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getConstructorByParameterTypes("SerializationConfig", "JsonInclude.Include").get()
            .parameters[1]
            .type
        assumeTrue { node.toString() == "JsonInclude.Include" }

        val type = symbolSolver.toResolvedType<ResolvedType>(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("com.fasterxml.jackson.annotation.JsonInclude.Include", type.qualifiedName)
    }

    @Test
    fun `Correctly Solve Protected Constructor Invocation on Anon Class`() {
        val cu = project.parseSourceFile(
            "src/test/java/com/fasterxml/jackson/databind/deser/TestBeanDeserializer.java",
            symbolSolver
        )
        val node = cu.primaryType.get().asClassOrInterfaceDeclaration()
            .getTypeByName<ClassOrInterfaceDeclaration>("ArrayDeserializerModifier")
            .getMethodsBySignature("modifyArrayDeserializer", "DeserializationConfig", "ArrayType", "BeanDescription", "JsonDeserializer<?>").single()
            .body.get()
            .statements[0].asReturnStmt()
            .expression.get().asCastExpr()
            .expression.asObjectCreationExpr()

        val symbolRef = symbolSolver.solveObjectCreationExprFallback(node)
        assertTrue(symbolRef.isSolved)
        val resolvedDecl = symbolRef.correspondingDeclaration
        assertEquals("com.fasterxml.jackson.databind.deser.std.StdDeserializer", resolvedDecl.declaringType().qualifiedName)
        assertEquals(1, resolvedDecl.numberOfParams)
        val declParam0 = resolvedDecl.getParam(0).type
        assertResolvedTypeIs<ResolvedReferenceType>(declParam0)
        assertEquals("java.lang.Class", declParam0.qualifiedName)
        val declParam0Tp0 = declParam0.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(declParam0Tp0)
        assertIsUnbounded(declParam0Tp0)
    }
}