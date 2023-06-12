package com.derppening.researchprojecttoolkit.tool.visitor

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assumeTrue
import com.derppening.researchprojecttoolkit.tool.inclusionReasonsData
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.TagImportUsage
import com.derppening.researchprojecttoolkit.util.NodeAstComparator
import com.derppening.researchprojecttoolkit.util.clearMemory
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import com.github.javaparser.ast.body.InitializerDeclaration
import org.junit.jupiter.api.BeforeAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class TagImportUsageTest {

    @Test
    fun `Tag Expressions Imported By Non-Static Glob with Optional Scope`() {
        val project = TestProjects.JacksonDatabind8f
        val entrypoint = project.parseEntrypoint("com.fasterxml.jackson.databind.deser.TestJdkTypes::testStringBuilder")

        val reducer = project.getCoverageBasedReducer(entrypoint, true, null)

        val cu = reducer.context
            .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.deser.BasicDeserializerFactory")
        assumeTrue(cu != null)

        val methodCall = cu
            .primaryType.get()
            .getMethodsBySignature("findDefaultDeserializer", "DeserializationContext", "JavaType", "BeanDescription").single()
            .body.get()
            .statements[6].asIfStmt()
            .thenStmt.asBlockStmt()
            .statements[0].asExpressionStmt()
            .expression.asVariableDeclarationExpr()
            .variables.single()
            .initializer.get().asMethodCallExpr()
        val methodCallScope = methodCall.scope.get().asNameExpr()

        TagImportUsage(reducer.context)(cu)

        val import = cu.imports
            .single {
                it.nameAsString == "com.fasterxml.jackson.databind.deser.std" && it.isAsterisk && !it.isStatic
            }
        val inclusionReasons = import.inclusionReasonsData
            .synchronizedWith { toList() }
            .map { it as ReachableReason.DirectlyReferencedByNode }

        assertTrue {
            inclusionReasons.any { NodeAstComparator.compare(it.node, methodCallScope) == 0 }
        }
    }

    @Test
    fun `Tag Type in ClassExpr Imported By Non-Static Glob`() {
        val project = TestProjects.JacksonDatabind8f
        val entrypoint = project.parseEntrypoint("com.fasterxml.jackson.databind.deser.TestJdkTypes::testStringBuilder")

        val reducer = project.getCoverageBasedReducer(entrypoint, true, null)

        val cu = reducer.context
            .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.ser.BasicSerializerFactory")
        assumeTrue(cu != null)

        val methodCall = cu
            .primaryType.get()
            .members
            .filterIsInstance<InitializerDeclaration>()
            .first { it.isStatic }
            .body
            .statements[15].asExpressionStmt()
            .expression.asMethodCallExpr()
        val methodCallClassExprType = methodCall
            .arguments[1].asClassExpr()
            .type

        TagImportUsage(reducer.context)(cu)

        val import = cu.imports
            .single {
                it.nameAsString == "com.fasterxml.jackson.databind.ser.std" && it.isAsterisk && !it.isStatic
            }
        val inclusionReasons = import.inclusionReasonsData
            .synchronizedWith { toList() }
            .map { it as ReachableReason.DirectlyReferencedByNode }

        assertTrue {
            inclusionReasons.any { NodeAstComparator.compare(it.node, methodCallClassExprType) == 0 }
        }
    }

    @AfterTest
    fun tearDown() {
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