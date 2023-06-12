package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertIsUnbounded
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.util.resolveDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.types.ResolvedArrayType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedTypeVariable
import com.github.javaparser.resolution.types.ResolvedWildcard
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FuzzySymbolSolverCollections25fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Collections25f

    @Test
    fun `Solve for Most Common Type of Wildcard with Type Variables`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/collections4/comparators/TransformingComparator.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getConstructorByParameterTypes("Transformer<? super I,? extends O>", "Comparator<O>").get()
            .body
            .statements[1]
            .asExpressionStmt()
            .expression
            .asAssignExpr()
            .target
            .asFieldAccessExpr()
        assumeTrue { node.toString() == "this.transformer" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("org.apache.commons.collections4.Transformer", type.qualifiedName)
        assertEquals(2, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(typeTp0)
        assertTrue(typeTp0.isBounded)
        assertTrue(typeTp0.isSuper)
        val typeTp0Bound = typeTp0.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("org.apache.commons.collections4.comparators.TransformingComparator", typeTp0Bound.containerQualifiedName)
        assertEquals("I", typeTp0Bound.name)
        val typeTp1 = type.typeParametersValues()[1]
        assertResolvedTypeIs<ResolvedWildcard>(typeTp1)
        assertTrue(typeTp1.isBounded)
        assertTrue(typeTp1.isExtends)
        val typeTp1Bound = typeTp1.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("org.apache.commons.collections4.comparators.TransformingComparator", typeTp1Bound.containerQualifiedName)
        assertEquals("O", typeTp1Bound.name)
    }

    @Test
    fun `Solve Generic in Array Type and Nested Method`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/collections4/IterableUtils.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("zippingIterable", "Iterable<? extends E>").single()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asObjectCreationExpr()
            .anonymousClassBody.get()
            .single {
                it is MethodDeclaration &&
                        it.nameAsString == "iterator" &&
                        it.signature.parameterTypes.isEmpty()
            }
            .asMethodDeclaration()
            .body.get()
            .statements[1]
            .asForStmt()
            .body
            .asBlockStmt()
            .statements[0]
            .asExpressionStmt()
            .expression
            .asAssignExpr()
            .value
            .asMethodCallExpr()
        assumeTrue { node.toString() == "emptyIteratorIfNull(iterables[i])" }

        val arg0Node = node.arguments[0]
            .asArrayAccessExpr()
        val arg0NameNode = arg0Node.name
            .asNameExpr()

        val arg0NameType = symbolSolver.calculateType(arg0NameNode)
        assertResolvedTypeIs<ResolvedArrayType>(arg0NameType)
        assertEquals(1, arg0NameType.arrayLevel())
        val arg0NameComponentType = arg0NameType.componentType
        assertResolvedTypeIs<ResolvedReferenceType>(arg0NameComponentType)
        assertEquals("java.lang.Iterable", arg0NameComponentType.qualifiedName)
        assertEquals(1, arg0NameComponentType.typeParametersValues().size)
        val arg0NameComponentTypeTp0 = arg0NameComponentType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(arg0NameComponentTypeTp0)
        assertTrue(arg0NameComponentTypeTp0.isBounded)
        assertTrue(arg0NameComponentTypeTp0.isExtends)
        val arg0NameComponentTypeTp0Bound = arg0NameComponentTypeTp0.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("E", arg0NameComponentTypeTp0Bound.name)
        assertEquals("org.apache.commons.collections4.IterableUtils.zippingIterable(java.lang.Iterable<? extends E>...)", arg0NameComponentTypeTp0Bound.containerQualifiedName)

        val arg0Type = symbolSolver.calculateType(arg0Node)
        assertResolvedTypeIs<ResolvedReferenceType>(arg0Type)
        assertEquals("java.lang.Iterable", arg0Type.qualifiedName)
        assertEquals(1, arg0Type.typeParametersValues().size)
        val arg0TypeTp0 = arg0Type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(arg0TypeTp0)
        assertTrue(arg0TypeTp0.isBounded)
        assertTrue(arg0TypeTp0.isExtends)
        val arg0TypeTp0Bound = arg0TypeTp0.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("E", arg0TypeTp0Bound.name)
        assertEquals("org.apache.commons.collections4.IterableUtils.zippingIterable(java.lang.Iterable<? extends E>...)", arg0TypeTp0Bound.containerQualifiedName)

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.Iterator", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(typeTp0)
        assertTrue(typeTp0.isBounded)
        assertTrue(typeTp0.isExtends)
        val typeTp0Bound = typeTp0.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("E", typeTp0Bound.name)
        assertEquals("org.apache.commons.collections4.IterableUtils.zippingIterable(java.lang.Iterable<? extends E>...)", typeTp0Bound.containerQualifiedName)
    }

    @Test
    fun `Solve Type of MethodCallExpr Resolving to Wildcard of Scoped Type Variable`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/collections4/functors/SwitchTransformer.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("transform", "I").single()
            .body.get()
            .statements[0]
            .asForStmt()
            .body
            .asBlockStmt()
            .statements[0]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "iTransformers[i].transform(input)" }

        val scopeNode = node.scope.get()
            .asArrayAccessExpr()

        val scopeNameNode = scopeNode.name.asNameExpr()
        val scopeNameType = symbolSolver.calculateType(scopeNameNode)
        assertResolvedTypeIs<ResolvedArrayType>(scopeNameType)
        val scopeNameComponentType = scopeNameType.componentType
        assertResolvedTypeIs<ResolvedReferenceType>(scopeNameComponentType)
        assertEquals("org.apache.commons.collections4.Transformer", scopeNameComponentType.qualifiedName)
        assertEquals(2, scopeNameComponentType.typeParametersValues().size)
        val scopeNameComponentTypeTp0 = scopeNameComponentType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(scopeNameComponentTypeTp0)
        assertTrue(scopeNameComponentTypeTp0.isBounded)
        assertTrue(scopeNameComponentTypeTp0.isSuper)
        val scopeComponentTypeTp0Bound = scopeNameComponentTypeTp0.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("I", scopeComponentTypeTp0Bound.name)
        assertEquals("org.apache.commons.collections4.functors.SwitchTransformer", scopeComponentTypeTp0Bound.containerQualifiedName)
        val scopeNameComponentTypeTp1 = scopeNameComponentType.typeParametersValues()[1]
        assertResolvedTypeIs<ResolvedWildcard>(scopeNameComponentTypeTp1)
        assertTrue(scopeNameComponentTypeTp1.isBounded)
        assertTrue(scopeNameComponentTypeTp1.isExtends)
        val scopeNameComponentTypeTp1Bound = scopeNameComponentTypeTp1.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("O", scopeNameComponentTypeTp1Bound.name)
        assertEquals("org.apache.commons.collections4.functors.SwitchTransformer", scopeNameComponentTypeTp1Bound.containerQualifiedName)

        val scopeType = symbolSolver.calculateType(scopeNode)
        assertResolvedTypeIs<ResolvedReferenceType>(scopeType)
        assertEquals("org.apache.commons.collections4.Transformer", scopeType.qualifiedName)
        assertEquals(2, scopeType.typeParametersValues().size)
        val scopeTypeTp0 = scopeType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(scopeTypeTp0)
        assertTrue(scopeTypeTp0.isBounded)
        assertTrue(scopeTypeTp0.isSuper)
        val scopeTypeTp0Bound = scopeTypeTp0.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("I", scopeTypeTp0Bound.name)
        assertEquals("org.apache.commons.collections4.functors.SwitchTransformer", scopeTypeTp0Bound.containerQualifiedName)
        val scopeTypeTp1 = scopeType.typeParametersValues()[1]
        assertResolvedTypeIs<ResolvedWildcard>(scopeTypeTp1)
        assertTrue(scopeTypeTp1.isBounded)
        assertTrue(scopeTypeTp1.isExtends)
        val scopeTypeTp1Bound = scopeTypeTp1.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("O", scopeTypeTp1Bound.name)
        assertEquals("org.apache.commons.collections4.functors.SwitchTransformer", scopeTypeTp1Bound.containerQualifiedName)

        val internalType = symbolSolver.calculateTypeInternal(node)

        assertResolvedTypeIs<ResolvedWildcard>(internalType)
        assertTrue(internalType.isBounded)
        assertTrue(internalType.isExtends)
        val internalBoundType = internalType.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("O", internalBoundType.name)
        assertEquals("org.apache.commons.collections4.functors.SwitchTransformer", internalBoundType.containerQualifiedName)

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertTrue(type.isJavaLangObject)
    }

    @Test
    fun `Solving MethodCallExpr with ArrayAccessExpr argument`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/collections4/functors/AllPredicate.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("allPredicate", "Predicate<? super T>").single()
            .body.get()
            .statements[2]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "coerce(predicates[0])" }

        val arg0Node = node.arguments[0].asArrayAccessExpr()
        val arg0Type = symbolSolver.calculateType(arg0Node)
        assertResolvedTypeIs<ResolvedReferenceType>(arg0Type)
        assertEquals("org.apache.commons.collections4.Predicate", arg0Type.qualifiedName)
        assertEquals(1, arg0Type.typeParametersValues().size)
        val arg0Tp0Type = arg0Type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(arg0Tp0Type)
        assertTrue(arg0Tp0Type.isBounded)
        assertTrue(arg0Tp0Type.isSuper)
        val arg0Tp0BoundType = arg0Tp0Type.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("T", arg0Tp0BoundType.name)
        assertEquals("org.apache.commons.collections4.functors.AllPredicate.allPredicate(org.apache.commons.collections4.Predicate<? super T>...)", arg0Tp0BoundType.containerQualifiedName)

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("org.apache.commons.collections4.Predicate", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val tp0Type = type.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("T", tp0Type.name)
        assertEquals("org.apache.commons.collections4.functors.AllPredicate.allPredicate(org.apache.commons.collections4.Predicate<? super T>...)", tp0Type.containerQualifiedName)
    }

    @Test
    fun `Solve Inferred Tp Type For Array Type`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/collections4/functors/SwitchClosure.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getConstructorByParameterTypes("boolean", "Predicate<? super E>[]", "Closure<? super E>[]", "Closure<? super E>").get()
            .body
            .statements[1]
            .asExpressionStmt()
            .expression
            .asAssignExpr()
            .value
            .asConditionalExpr()
            .thenExpr
            .asMethodCallExpr()
        assumeTrue { node.toString() == "FunctorUtils.copy(predicates)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedArrayType>(type)
        assertEquals(1, type.arrayLevel())
        val componentType = type.componentType
        assertResolvedTypeIs<ResolvedReferenceType>(componentType)
        assertEquals("org.apache.commons.collections4.Predicate", componentType.qualifiedName)
        assertEquals(1, componentType.typeParametersValues().size)
        val componentTypeTp0 = componentType.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("E", componentTypeTp0.name)
        assertEquals("org.apache.commons.collections4.functors.SwitchClosure", componentTypeTp0.containerQualifiedName)
    }

    @Test
    fun `Solve Inferred Tp Type For Array Type with Explicit Type Param`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/collections4/functors/SwitchClosure.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("getPredicates").single()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "FunctorUtils.<E>copy(iPredicates)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedArrayType>(type)
        assertEquals(1, type.arrayLevel())
        val componentType = type.componentType
        assertResolvedTypeIs<ResolvedReferenceType>(componentType)
        assertEquals("org.apache.commons.collections4.Predicate", componentType.qualifiedName)
        assertEquals(1, componentType.typeParametersValues().size)
        val componentTypeTp0 = componentType.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("E", componentTypeTp0.name)
        assertEquals("org.apache.commons.collections4.functors.SwitchClosure", componentTypeTp0.containerQualifiedName)
    }

    @Test
    fun `Solve Inferred Tp Type for Extends Bound`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/collections4/functors/ChainedTransformer.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("getTransformers").single()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "FunctorUtils.<T, T>copy(iTransformers)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedArrayType>(type)
        assertEquals(1, type.arrayLevel())
        val componentType = type.componentType
        assertResolvedTypeIs<ResolvedReferenceType>(componentType)
        assertEquals("org.apache.commons.collections4.Transformer", componentType.qualifiedName)
        assertEquals(2, componentType.typeParametersValues().size)
        val componentTypeTp0 = componentType.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("T", componentTypeTp0.name)
        assertEquals("org.apache.commons.collections4.functors.ChainedTransformer", componentTypeTp0.containerQualifiedName)
        val componentTypeTp1 = componentType.typeParametersValues()[1]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("T", componentTypeTp1.name)
        assertEquals("org.apache.commons.collections4.functors.ChainedTransformer", componentTypeTp1.containerQualifiedName)
    }

    @Test
    fun `Solve Inferred Tp Type for Primitive matching Vararg Array Param`() {
        val cu = project.parseSourceFile(
            "src/test/java/org/apache/commons/collections4/IteratorUtilsTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("setUp").single()
            .body.get()
            .statements[12]
            .asExpressionStmt()
            .expression
            .asAssignExpr()
            .value
            .asMethodCallExpr()
        assumeTrue { node.toString() == "Arrays.asList(2, 4, 6, 8, 10, 12)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.List", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp0)
        assertEquals("java.lang.Integer", typeTp0.qualifiedName)
    }

    @Test
    fun `Solve Inferred Tp Type for Null matching Ref Type Param`() {
        val cu = project.parseSourceFile(
            "src/test/java/org/apache/commons/collections4/IteratorUtilsTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testAsIterableNull").single()
            .body.get()
            .statements[0]
            .asTryStmt()
            .tryBlock
            .statements[0]
            .asExpressionStmt()
            .expression
            .asMethodCallExpr()
        assumeTrue { node.toString() == "IteratorUtils.asIterable(null)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.lang.Iterable", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(typeTp0)
        assertIsUnbounded(typeTp0)
    }

    @Test
    fun `Solve Inferred Tp Type for Extends Wildcard matching Super Wildcard Param`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/collections4/TransformerUtils.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("switchTransformer", "Predicate<? super I>", "Transformer<? super I,? extends O>", "Transformer<? super I,? extends O>").single()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "SwitchTransformer.switchTransformer(new Predicate[] { predicate }, new Transformer[] { trueTransformer }, falseTransformer)" }

        val type = symbolSolver.calculateType(node)

        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("org.apache.commons.collections4.Transformer", type.qualifiedName)
        assertEquals(2, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp0)
        assertTrue(typeTp0.isJavaLangObject)
        val typeTp1 = type.typeParametersValues()[1]
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp1)
        assertTrue(typeTp1.isJavaLangObject)
    }

    @Test
    fun `Solve Two Type Variables in Different Scopes`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/collections4/CollectionUtils.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("collate", "Iterable<? extends O>", "Iterable<? extends O>").single()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "collate(a, b, ComparatorUtils.<O>naturalComparator(), true)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.List", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("O", typeTp0.name)
        assertEquals("org.apache.commons.collections4.CollectionUtils.collate(java.lang.Iterable<? extends O>, java.lang.Iterable<? extends O>)", typeTp0.containerQualifiedName)
    }

    @Test
    fun `Solve MethodCallExpr Imported by Static Import`() {
        val cu = project.parseSourceFile(
            "src/test/java/org/apache/commons/collections4/functors/AllPredicateTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("oneTruePredicate").single()
            .body.get()
            .statements[1].asExpressionStmt()
            .expression.asMethodCallExpr()
            .arguments[1].asMethodCallExpr()
            .scope.get().asMethodCallExpr()
        assumeTrue { node.toString() == "allPredicate(predicate)" }

        val resolvedDecl = symbolSolver.resolveDeclaration<ResolvedDeclaration>(node)
        assertIs<ResolvedMethodDeclaration>(resolvedDecl)
        assertEquals("allPredicate", resolvedDecl.name)
        assertEquals("AllPredicate", resolvedDecl.className)
        assertEquals("org.apache.commons.collections4.functors", resolvedDecl.packageName)
        assertEquals(1, resolvedDecl.numberOfParams)
        val param0 = resolvedDecl.getParam(0)
        assertTrue(param0.isVariadic)
        val param0Type = assertResolvedTypeIs<ResolvedArrayType>(param0.type)
        assertEquals(1, param0Type.arrayLevel())
        val param0ComponentType = assertResolvedTypeIs<ResolvedReferenceType>(param0Type.componentType)
        assertEquals("org.apache.commons.collections4.Predicate", param0ComponentType.qualifiedName)
    }

    @Test
    fun `Solve ConstructorDeclaration Regression`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/collections4/map/CompositeMap.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .defaultConstructor.get()
            .body
            .statements[0].asExplicitConstructorInvocationStmt()
        assumeTrue { node.toString() == "this(new Map[] {}, null);" }

        val resolvedDecl = symbolSolver.resolveDeclaration<ResolvedDeclaration>(node)
        assertIs<ResolvedConstructorDeclaration>(resolvedDecl)
        assertEquals(2, resolvedDecl.numberOfParams)
        val param0Type = assertIs<ResolvedArrayType>(resolvedDecl.getParam(0).type)
        assertEquals(1, param0Type.arrayLevel())
        val param0TypeComponentType = assertIs<ResolvedReferenceType>(param0Type.componentType)
        assertEquals("java.util.Map", param0TypeComponentType.qualifiedName)
        val param1Type = assertIs<ResolvedReferenceType>(resolvedDecl.getParam(1).type)
        assertEquals("org.apache.commons.collections4.map.CompositeMap.MapMutator", param1Type.qualifiedName)
    }
}