package com.derppening.researchprojecttoolkit.util

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assumeTrue
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class JavaParserUtilsTest {

    @Test
    fun `Compare Two Methods with Same Signature and Different Type Param Bounds`() {
        val symbolSolver = TestProjects.Lang19f.getSymbolSolver()
        val cu = TestProjects.Lang19f.parseSourceFile(
            "src/main/java/org/apache/commons/lang3/Validate.java",
            symbolSolver
        )

        val methods = cu.primaryType.get()
            .getMethodsBySignature("notEmpty", "T", "String", "Object")
        assumeTrue(methods.size > 1)

        val extendsCollection = methods
            .single {
                it.typeParameters.size == 1 &&
                        it.typeParameters[0].nameAsString == "T" &&
                        it.typeParameters[0].typeBound.size == 1 &&
                        it.typeParameters[0].typeBound[0].nameAsString == "Collection"
            }
            .let { symbolSolver.resolveDeclaration<ResolvedMethodDeclaration>(it) }
        val extendsMap = methods
            .single {
                it.typeParameters.size == 1 &&
                        it.typeParameters[0].nameAsString == "T" &&
                        it.typeParameters[0].typeBound.size == 1 &&
                        it.typeParameters[0].typeBound[0].nameAsString == "Map"
            }
            .let { symbolSolver.resolveDeclaration<ResolvedMethodDeclaration>(it) }

        val cmp = TypeParamDeclListComparator.compare(extendsCollection.typeParameters, extendsMap.typeParameters)
        assertFalse(cmp == 0)
    }

    @Test
    fun `Compare Two Methods with Different Parameter Types and Same Class Name`() {
        val reducerContext = TestProjects.JacksonXml1f.getReducerContext()
        val symbolSolver = reducerContext.symbolSolver
        val cu = TestProjects.JacksonXml1f.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/dataformat/xml/XmlFactory.java",
            symbolSolver
        )

        val method1 = cu.primaryType.get()
            .getMethodsBySignature("configure", "FromXmlParser.Feature", "boolean").single()
        val method2 = cu.primaryType.get()
            .getMethodsBySignature("configure", "ToXmlGenerator.Feature", "boolean").single()

        val resolvedDecl1 = symbolSolver.resolveDeclaration<ResolvedMethodDeclaration>(method1)
        val resolvedDecl2 = symbolSolver.resolveDeclaration<ResolvedMethodDeclaration>(method2)

        val cmp = ResolvedCallableDeclComparator(reducerContext).compare(resolvedDecl1, resolvedDecl2)
        assertFalse(cmp == 0)
    }

    @Test
    fun `Compare Two Methods with Vararg and Non-Vararg Parameter Types`() {
        val project = TestProjects.Collections25f
        val reducerContext = project.getReducerContext()
        val symbolSolver = reducerContext.symbolSolver
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/collections4/collection/CompositeCollection.java",
            symbolSolver
        )

        val method1 = cu.primaryType.get()
            .constructors
            .single {
                it.parameters.size == 1 &&
                        it.parameters[0].let { it.typeAsString == "Collection<E>" && it.isVarArgs }
            }
        val method2 = cu.primaryType.get()
            .constructors
            .single {
                it.parameters.size == 1 &&
                        it.parameters[0].let { it.typeAsString == "Collection<E>" && !it.isVarArgs }
            }

        val resolvedDecl1 = symbolSolver.resolveDeclaration<ResolvedConstructorDeclaration>(method1)
        val resolvedDecl2 = symbolSolver.resolveDeclaration<ResolvedConstructorDeclaration>(method2)

        val cmp = ResolvedCallableDeclComparator(reducerContext).compare(resolvedDecl1, resolvedDecl2)
        assertFalse(cmp == 0)
    }

    @Test
    fun `Compare Same Method in Anon Class`() {
        val project = TestProjects.Closure79f

        val reducerContext1 = project.getReducerContext()
        val symbolSolver1 = reducerContext1.symbolSolver
        val cu1 = project.parseSourceFile(
            "test/com/google/javascript/jscomp/VarCheckTest.java",
            symbolSolver1
        )
        val method1 = cu1.primaryType.get()
            .getMethodsBySignature("testInvalidFunctionDecl1").single()
            .body.get()
            .statements[1].asExpressionStmt()
            .expression.asAssignExpr()
            .value.asObjectCreationExpr()
            .anonymousClassBody.get()
            .single {
                it is MethodDeclaration &&
                        it.nameAsString == "visit" &&
                        it.parameters.size == 1 &&
                        it.parameters[0].typeAsString == "Node"
            }

        val reducerContext2 = project.getReducerContext()
        val symbolSolver2 = reducerContext2.symbolSolver
        val cu2 = project.parseSourceFile(
            "test/com/google/javascript/jscomp/VarCheckTest.java",
            symbolSolver2
        )
        val method2 = cu2.primaryType.get()
            .getMethodsBySignature("testInvalidFunctionDecl1").single()
            .body.get()
            .statements[1].asExpressionStmt()
            .expression.asAssignExpr()
            .value.asObjectCreationExpr()
            .anonymousClassBody.get()
            .single {
                it is MethodDeclaration &&
                        it.nameAsString == "visit" &&
                        it.parameters.size == 1 &&
                        it.parameters[0].typeAsString == "Node"
            }

        val resolvedDecl1 = symbolSolver1.resolveDeclaration<ResolvedMethodDeclaration>(method1)
        val resolvedDecl2 = symbolSolver1.resolveDeclaration<ResolvedMethodDeclaration>(method2)

        val cmp = ResolvedCallableDeclComparator(reducerContext1).compare(resolvedDecl1, resolvedDecl2)
        assertEquals(0, cmp)
    }
}