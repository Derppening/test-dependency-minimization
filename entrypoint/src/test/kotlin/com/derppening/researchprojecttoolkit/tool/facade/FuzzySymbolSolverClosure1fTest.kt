package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertIsUnbounded
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import com.github.javaparser.resolution.types.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FuzzySymbolSolverClosure1fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Closure1f

    @Test
    fun `Calculate Type of AnnotationExpr`() {
        val cu = project.parseSourceFile(
            "test/com/google/javascript/jscomp/CommandLineRunnerTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("setUp").single()
            .getAnnotationByName("Override").get()
            .asMarkerAnnotationExpr()
        assumeTrue { node.nameAsString == "Override" }

        val resolvedType = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(resolvedType)
        assertEquals("java.lang.Override", resolvedType.qualifiedName)
    }

    @Test
    fun `Get Specific Wildcard for Effectively-Unbounded Wildcards`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/jscomp/CommandLineRunner.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single {
                it is ClassOrInterfaceDeclaration && it.nameAsString == "Flags"
            }
            .asClassOrInterfaceDeclaration()
            .members
            .single {
                it is ClassOrInterfaceDeclaration && it.nameAsString == "WarningGuardSetter"
            }
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("getType").single()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asCastExpr()
            .expression
            .asMethodCallExpr()
        assumeTrue { node.toString() == "proxy.getType()" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.lang.Class", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = assertResolvedTypeIs<ResolvedWildcard>(type.typeParametersValues()[0])
        assertTrue(typeTp0.isSuper)
        val typeTp0BoundedTy = typeTp0.boundedType
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp0BoundedTy)
        assertEquals("java.lang.String", typeTp0BoundedTy.qualifiedName)
    }

    @Test
    fun `Get Type of Unsupported TypeVariable Method`() {
        val cu = project.parseSourceFile(
            "test/com/google/javascript/jscomp/CommandLineRunnerTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("test", "String[]", "String[]", "DiagnosticType").single()
            .body.get()
            .statements[1]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[0]
            .asExpressionStmt()
            .expression
            .asMethodCallExpr()
            .arguments[0]
            .asBinaryExpr()
            .left
            .asBinaryExpr()
            .left
            .asBinaryExpr()
            .right
            .asMethodCallExpr()
        assumeTrue { node.nameAsString == "join" }

        val resolvedType = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(resolvedType)
        assertEquals("java.lang.String", resolvedType.qualifiedName)
    }

    @Test
    fun `Solve Inherited Member Types`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/jscomp/ClosureCodingConvention.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("applySubclassRelationship", "FunctionType", "FunctionType", "SubclassType").single()
            .body.get()
            .statements[1]
            .asIfStmt()
            .condition
            .asBinaryExpr()
            .right
            .asFieldAccessExpr()
            .scope
            .asNameExpr()
        assumeTrue { node.toString() == "SubclassType" }

        val resolvedDecl = symbolSolver.resolveDeclaration(node, ResolvedDeclaration::class.java)
        assertIs<ResolvedReferenceTypeDeclaration>(resolvedDecl)
        assertEquals("com.google.javascript.jscomp.CodingConvention.SubclassType", resolvedDecl.qualifiedName)
    }

    @Test
    fun `Solve Inherited Member Types In Anon Class`() {
        val cu = project.parseSourceFile(
            "test/com/google/javascript/jscomp/DataFlowAnalysisTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testMaxIterationsExceededException").single()
            .body.get()
            .statements[3]
            .asExpressionStmt()
            .expression
            .asVariableDeclarationExpr()
            .variables.single()
            .initializer.get()
            .asObjectCreationExpr()
            .anonymousClassBody.get()
            .single {
                it is MethodDeclaration &&
                        it.nameAsString == "getOptionalNodeComparator" &&
                        it.signature.parameterTypes.size == 1 &&
                        it.signature.parameterTypes[0].asString() == "boolean"
            }
            .asMethodDeclaration()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asObjectCreationExpr()
            .anonymousClassBody.get()
            .single {
                it is MethodDeclaration &&
                        it.nameAsString == "compare" &&
                        it.signature.parameterTypes.size == 2 &&
                        it.signature.parameterTypes[0].asString() == "DiGraphNode" &&
                        it.signature.parameterTypes[1].asString() == "DiGraphNode"
            }
            .asMethodDeclaration()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asBinaryExpr()
            .left
            .asFieldAccessExpr()
        assumeTrue { node.toString() == "o1.getValue().order" }

        val orderScope = node.scope.asMethodCallExpr()
        val o1 = orderScope.scope.get().asNameExpr()

        val o1Type = symbolSolver.calculateType(o1)
        assertResolvedTypeIs<ResolvedReferenceType>(o1Type)
        assertEquals("com.google.javascript.jscomp.graph.DiGraph.DiGraphNode", o1Type.qualifiedName)

        val orderScopeType = symbolSolver.calculateType(orderScope)
        assertResolvedTypeIs<ResolvedReferenceType>(orderScopeType)
        assertEquals("com.google.javascript.jscomp.DataFlowAnalysisTest.Instruction", orderScopeType.qualifiedName)

        val nodeType = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedPrimitiveType>(nodeType)
        assertEquals(ResolvedPrimitiveType.INT, nodeType)
    }

    @Test
    fun `Solve Nested Member Type By Static Import`() {
        val cu = project.parseSourceFile(
            "test/com/google/javascript/jscomp/JsMessageVisitorTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "CollectMessages" }
            .asClassOrInterfaceDeclaration()
            .getConstructorByParameterTypes("Compiler").get()
            .body
            .statements[0]
            .asExplicitConstructorInvocationStmt()
            .arguments[2]
            .asMethodCallExpr()
            .scope.get()
            .asNameExpr()
        assumeTrue { node.toString() == "Style" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("com.google.javascript.jscomp.JsMessage.Style", type.qualifiedName)
    }

    @Test
    fun `Implement Fallback Logic for Variadic Methods`() {
        val cu = project.parseSourceFile(
            "test/com/google/javascript/jscomp/CommandLineRunnerTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getFieldByName("args").get()
            .variables.single()
            .initializer.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "Lists.newArrayList()" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.ArrayList", type.qualifiedName)
    }

    @Test
    fun `Calculate Concrete Type of Enum Constant`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/jscomp/DisambiguateProperties.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "JSTypeSystem" }
            .asClassOrInterfaceDeclaration()
            .getConstructorByParameterTypes("AbstractCompiler").get()
            .body
            .statements[1]
            .asExpressionStmt()
            .expression
            .asAssignExpr()
            .value
            .asMethodCallExpr()
            .arguments[0]
            .asMethodCallExpr()
            .arguments[0]
            .asFieldAccessExpr()
        assumeTrue { node.toString() == "JSTypeNative.ALL_TYPE" }

        val type = symbolSolver.calculateTypeInternal(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("com.google.javascript.rhino.jstype.JSTypeNative", type.qualifiedName)
    }

    @Test
    fun `Calculate Concrete Type of Class-Bounded Type`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/jscomp/deps/SortedDependencies.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .asClassOrInterfaceDeclaration()
            .getConstructorByParameterTypes("List<INPUT>").get()
            .body
            .statements[5]
            .asExpressionStmt()
            .expression
            .asAssignExpr()
            .value
            .asMethodCallExpr()
        assumeTrue { node.toString() == "topologicalStableSort(inputs, deps)" }

        val type = symbolSolver.calculateTypeInternal(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.List", type.qualifiedName)
    }

    @Test
    fun `Solve for Method Calls to Generic Methods`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/jscomp/deps/SortedDependencies.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("findCycle", "List<INPUT>", "Multimap<INPUT,INPUT>").single()
            .body.get()
            .statements.single()
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "findCycle(subGraph.get(0), Sets.<INPUT>newHashSet(subGraph), deps, Sets.<INPUT>newHashSet())" }

        val arg0 = node.arguments[0].asMethodCallExpr()
        val arg0Scope = arg0.scope.get().asNameExpr()
        val arg0ScopeTy = symbolSolver.calculateType(arg0Scope)
        assertResolvedTypeIs<ResolvedReferenceType>(arg0ScopeTy)
        assertEquals("java.util.List", arg0ScopeTy.qualifiedName)
        assertEquals(1, arg0ScopeTy.typeParametersValues().size)
        val arg0ScopeTyTp0 = assertResolvedTypeIs<ResolvedTypeVariable>(arg0ScopeTy.typeParametersValues()[0]).asTypeParameter()
        assertEquals("com.google.javascript.jscomp.deps.SortedDependencies", arg0ScopeTyTp0.containerQualifiedName)
        assertEquals("INPUT", arg0ScopeTyTp0.name)

        val arg0Ty = symbolSolver.calculateType(arg0)
        assertResolvedTypeIs<ResolvedReferenceType>(arg0Ty)
        assertEquals("com.google.javascript.jscomp.deps.DependencyInfo", arg0Ty.qualifiedName)

        val arg1 = node.arguments[1].asMethodCallExpr()
        val arg1Ty = symbolSolver.calculateType(arg1)
        assertResolvedTypeIs<ResolvedReferenceType>(arg1Ty)
        assertEquals("java.util.HashSet", arg1Ty.qualifiedName)
        assertEquals(1, arg1Ty.typeParametersValues().size)
        val arg1Tp0Ty = arg1Ty.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertTrue(arg1Tp0Ty.isBounded)
        assertEquals(1, arg1Tp0Ty.bounds.size)
        val arg1Tp0TyBound0 = arg1Tp0Ty.bounds[0]
        assertTrue(arg1Tp0TyBound0.isExtends)
        val arg1Tp0TyBoundedTy = arg1Tp0TyBound0.type
        assertResolvedTypeIs<ResolvedReferenceType>(arg1Tp0TyBoundedTy)
        assertEquals("com.google.javascript.jscomp.deps.DependencyInfo", arg1Tp0TyBoundedTy.qualifiedName)

        val arg2 = node.arguments[2].asNameExpr()
        val arg2Ty = symbolSolver.calculateType(arg2)
        assertResolvedTypeIs<ResolvedReferenceType>(arg2Ty)
        assertEquals("com.google.common.collect.Multimap", arg2Ty.qualifiedName)
        assertEquals(2, arg2Ty.typeParametersValues().size)
        val arg2Tp0Tp = arg2Ty.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertTrue(arg2Tp0Tp.isBounded)
        assertEquals(1, arg2Tp0Tp.bounds.size)
        val arg2Tp0TpBoundedTy = arg2Tp0Tp.bounds.single()
            .also { assertTrue(it.isExtends) }
            .type
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it) }
        assertEquals("com.google.javascript.jscomp.deps.DependencyInfo", arg2Tp0TpBoundedTy.qualifiedName)
        val arg2Tp1Tp = arg2Ty.typeParametersValues()[1]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertTrue(arg2Tp1Tp.isBounded)
        assertEquals(1, arg2Tp1Tp.bounds.size)
        val arg2Tp1TyBoundedTy = arg2Tp1Tp.bounds.single()
            .also { assertTrue(it.isExtends) }
            .type
            .let { assertResolvedTypeIs<ResolvedReferenceType>(it) }
        assertEquals("com.google.javascript.jscomp.deps.DependencyInfo", arg2Tp1TyBoundedTy.qualifiedName)

        val arg3 = node.arguments[3].asMethodCallExpr()
        val arg3Ty = symbolSolver.calculateType(arg3)
        assertResolvedTypeIs<ResolvedReferenceType>(arg3Ty)
        assertEquals("java.util.HashSet", arg3Ty.qualifiedName)
        assertEquals(1, arg3Ty.typeParametersValues().size)
        val arg3Tp0Ty = arg3Ty.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertTrue(arg3Tp0Ty.isBounded)
        assertEquals(1, arg3Tp0Ty.bounds.size)
        val arg3Tp0TyBound0 = arg3Tp0Ty.bounds[0]
        assertTrue(arg3Tp0TyBound0.isExtends)
        val arg3Tp0TyBoundedTy = arg3Tp0TyBound0.type
        assertResolvedTypeIs<ResolvedReferenceType>(arg3Tp0TyBoundedTy)
        assertEquals("com.google.javascript.jscomp.deps.DependencyInfo", arg3Tp0TyBoundedTy.qualifiedName)

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.List", type.qualifiedName)
        assertEquals(1, type.typeParametersMap.size)
        val typeTp0Tp = type.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("INPUT", typeTp0Tp.name)
        assertEquals("com.google.javascript.jscomp.deps.SortedDependencies", typeTp0Tp.containerQualifiedName)
    }

    @Test
    fun `Get More Specific Wildcard Type Between Type Variable and Wildcard`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/jscomp/deps/SortedDependencies.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("topologicalStableSort", "List<T>", "Multimap<T,T>").single()
            .body.get()
            .statements[7]
            .asExpressionStmt()
            .expression
            .asMethodCallExpr()
        assumeTrue { node.toString() == "Multimaps.invertFrom(deps, reverseDeps)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("com.google.common.collect.Multimap", type.qualifiedName)
        assertEquals(2, type.typeParametersValues().size)
        val typeTp0 = assertResolvedTypeIs<ResolvedTypeVariable>(type.typeParametersValues()[0]).asTypeParameter()
        assertIsUnbounded(typeTp0)
        val typeTp1 = assertResolvedTypeIs<ResolvedTypeVariable>(type.typeParametersValues()[1]).asTypeParameter()
        assertIsUnbounded(typeTp1)
    }

    @Test
    fun `Solve Enum valueOf`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/jscomp/ProcessClosurePrimitives.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("processSetCssNameMapping", "NodeTraversal", "Node", "Node").single()
            .body.get()
            .statements[2]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[5]
            .asTryStmt()
            .tryBlock
            .statements[0]
            .asExpressionStmt()
            .expression
            .asAssignExpr()
            .value
            .asMethodCallExpr()
        assumeTrue { node.toString() == "CssRenamingMap.Style.valueOf(styleStr)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("com.google.javascript.jscomp.CssRenamingMap.Style", type.qualifiedName)
    }

    @Test
    fun `Solve MethodCallExpr Returning Method Generic Type`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/jscomp/DataFlowAnalysis.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("joinInputs", "DiGraphNode<N,Branch>").single()
            .body.get()
            .statements[1]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[0]
            .asIfStmt()
            .elseStmt.get()
            .asBlockStmt()
            .statements[1]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[0]
            .asExpressionStmt()
            .expression
            .asVariableDeclarationExpr()
            .variables
            .single()
            .initializer.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "inNodes.get(0).getAnnotation()" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("com.google.javascript.jscomp.graph.Annotation", type.qualifiedName)
    }

    @Test
    fun `Solve MethodCallExpr Returning Primitive Array Type`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/jscomp/regex/CharRanges.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("withMembers", "int").single()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asObjectCreationExpr()
            .arguments[0]
            .asMethodCallExpr()
        assumeTrue { node.toString() == "intArrayToRanges(members.clone())" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedArrayType>(type)
        assertEquals(1, type.arrayLevel())
        val componentType = type.componentType
        assertResolvedTypeIs<ResolvedPrimitiveType>(componentType)
        assertEquals(ResolvedPrimitiveType.INT, componentType)
    }

    @Test
    fun `Solve MethodCallExpr Returning Propagated Type Variable`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/jscomp/SymbolTable.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getFieldByName("SOURCE_NAME_ORDERING").get()
            .variables.single()
            .initializer.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "Ordering.natural().nullsFirst()" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("com.google.common.collect.Ordering", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(typeTp0)
        assertTrue(typeTp0.isBounded)
        assertTrue(typeTp0.isExtends)
        val typeTp0Bound = assertResolvedTypeIs<ResolvedReferenceType>(typeTp0.boundedType)
        assertEquals("java.lang.Comparable", typeTp0Bound.qualifiedName)
        assertEquals(1, typeTp0Bound.typeParametersValues().size)
        val typeTp0BoundTp0 = assertResolvedTypeIs<ResolvedWildcard>(typeTp0Bound.typeParametersValues()[0])
        assertIsUnbounded(typeTp0BoundTp0)
    }

    @Test
    fun `Get More Specific between Class Hierarchical Types`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/rhino/testing/MapBasedScope.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getConstructorByParameterTypes("Map<String,? extends JSType>").get()
            .body
            .statements[0]
            .asForEachStmt()
            .body
            .asBlockStmt()
            .statements[0]
            .asExpressionStmt()
            .expression
            .asMethodCallExpr()
        assumeTrue { node.toString() == "slots.put(entry.getKey(), new SimpleSlot(entry.getKey(), entry.getValue(), false))" }

        val arg0Node = node.arguments[0]
            .asMethodCallExpr()
        val arg1Node = node.arguments[1]
            .asObjectCreationExpr()

        val arg0Type = symbolSolver.calculateType(arg0Node)
        assertResolvedTypeIs<ResolvedReferenceType>(arg0Type)
        assertEquals("java.lang.String", arg0Type.qualifiedName)

        val arg1Type = symbolSolver.calculateType(arg1Node)
        assertResolvedTypeIs<ResolvedReferenceType>(arg1Type)
        assertEquals("com.google.javascript.rhino.jstype.SimpleSlot", arg1Type.qualifiedName)

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("com.google.javascript.rhino.jstype.StaticSlot", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp0)
        assertEquals("com.google.javascript.rhino.jstype.JSType", typeTp0.qualifiedName)
    }

    @Test
    fun `Find Inferred Tp In Unrelated Type`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/jscomp/CompilerOptions.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("setPropertyInvalidationErrors", "Map<String,CheckLevel>").single()
            .body.get()
            .statements[0]
            .asExpressionStmt()
            .expression
            .asAssignExpr()
            .value
            .asMethodCallExpr()
        assumeTrue { node.toString() == "Maps.newHashMap(propertyInvalidationErrors)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.HashMap", type.qualifiedName)
        assertEquals(2, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp0)
        assertEquals("java.lang.String", typeTp0.qualifiedName)
        val typeTp1 = type.typeParametersValues()[1]
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp1)
        assertEquals("com.google.javascript.jscomp.CheckLevel", typeTp1.qualifiedName)
    }

    @Test
    fun `Find Inferred Tp of Super-Wildcard Param with Ref-Type Arg`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/jscomp/DataFlowAnalysis.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getConstructorByParameterTypes("ControlFlowGraph<N>", "JoinOp<L>").get()
            .body
            .statements[3]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[0]
            .asExpressionStmt()
            .expression
            .asAssignExpr()
            .value
            .asMethodCallExpr()
        assumeTrue { node.toString() == "Sets.newTreeSet(nodeComparator)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.TreeSet", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp0)
        assertEquals("com.google.javascript.jscomp.graph.DiGraph.DiGraphNode", typeTp0.qualifiedName)
        assertEquals(2, typeTp0.typeParametersValues().size)
        val typeTp0Tp0 = assertResolvedTypeIs<ResolvedTypeVariable>(typeTp0.typeParametersValues()[0]).asTypeParameter()
        assertEquals("N", typeTp0Tp0.name)
        assertEquals("com.google.javascript.jscomp.DataFlowAnalysis", typeTp0Tp0.containerQualifiedName)
        val typeTp0Tp1 = typeTp0.typeParametersValues()[1]
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp0Tp1)
        assertEquals("com.google.javascript.jscomp.ControlFlowGraph.Branch", typeTp0Tp1.qualifiedName)
    }

    @Test
    fun `Find Inferred Tp of Vararg Array Type with Type Param Argument`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/jscomp/DataFlowAnalysis.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("join", "L", "L").single()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
            .arguments[0]
            .asMethodCallExpr()
        assumeTrue { node.toString() == "Lists.<L>newArrayList(latticeA, latticeB)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.ArrayList", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = assertResolvedTypeIs<ResolvedTypeVariable>(type.typeParametersValues()[0]).asTypeParameter()
        assertEquals("L", typeTp0.name)
        assertEquals("com.google.javascript.jscomp.DataFlowAnalysis", typeTp0.containerQualifiedName)
    }

    @Test
    fun `Detect Method Requiring Workaround Regression`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/jscomp/CodePrinter.java",
            symbolSolver
        )
        // CodePrinter.java:96
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "MappedCodePrinter" }
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("startSourceMapping", "Node").single()
            .body.get()
            .statements[2]
            .asIfStmt()
            .condition
            .asBinaryExpr()
            .right
            .asMethodCallExpr()
        assumeTrue { node.toString() == "sourceMapDetailLevel.apply(node)" }

        val decl = symbolSolver.resolveDeclaration(node, ResolvedDeclaration::class.java)
        assertIs<ResolvedMethodDeclaration>(decl)
        assertEquals("apply", decl.name)
        assertEquals("Predicate", decl.className)
        assertEquals("com.google.common.base", decl.packageName)
        assertEquals(1, decl.numberOfParams)
        val declParam0 = assertResolvedTypeIs<ResolvedTypeVariable>(decl.getParam(0).type).asTypeParameter()
        assertEquals("T", declParam0.name)
        assertEquals("com.google.common.base.Predicate", declParam0.containerQualifiedName)
    }

    @Test
    fun `Solve Declaration for Vararg Constructor with Null Argument`() {
        val cu = project.parseSourceFile(
            "src/com/google/javascript/jscomp/DiagnosticGroup.java",
            symbolSolver
        )
        // CodePrinter.java:96
        val node = cu.primaryType.get()
            .getConstructorByParameterTypes("DiagnosticType").get()
            .body
            .statements[0]
            .asExplicitConstructorInvocationStmt()
        assumeTrue { node.toString() == "this(null, types);" }

        val decl = symbolSolver.resolveDeclaration(node, ResolvedConstructorDeclaration::class.java)
        assertIs<ResolvedConstructorDeclaration>(decl)
        assertEquals("DiagnosticGroup", decl.className)
        assertEquals("com.google.javascript.jscomp", decl.packageName)
        assertEquals(2, decl.numberOfParams)
        val declParam0 = decl.getParam(0).type
        assertResolvedTypeIs<ResolvedReferenceType>(declParam0)
        assertEquals("java.lang.String", declParam0.qualifiedName)
        val declParam1 = decl.getParam(1).type
        assertResolvedTypeIs<ResolvedArrayType>(declParam1)
        assertEquals(1, declParam1.arrayLevel())
        val declParam1Component = declParam1.componentType
        assertResolvedTypeIs<ResolvedReferenceType>(declParam1Component)
        assertEquals("com.google.javascript.jscomp.DiagnosticType", declParam1Component.qualifiedName)
    }
}