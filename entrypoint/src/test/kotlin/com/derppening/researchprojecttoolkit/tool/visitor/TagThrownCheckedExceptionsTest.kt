package com.derppening.researchprojecttoolkit.tool.visitor

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assumeTrue
import com.derppening.researchprojecttoolkit.tool.checkedExceptionSources
import com.derppening.researchprojecttoolkit.tool.transform.TagThrownCheckedExceptions
import com.derppening.researchprojecttoolkit.util.NodeAstComparator
import com.derppening.researchprojecttoolkit.util.clearMemory
import org.junit.jupiter.api.BeforeAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class TagThrownCheckedExceptionsTest {

    @Test
    fun `Tag Checked Exceptions as Subclass of Caught Type`() {
        val project = TestProjects.Compress41f
        val entrypoint = project.parseEntrypoint("org.apache.commons.compress.archivers.zip.ZipArchiveInputStreamTest::testThrowOnInvalidEntry")

        val reducer = project.getCoverageBasedReducer(entrypoint, true, null)
        val cu = reducer.context
            .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.zip.ZipArchiveInputStreamTest")
        assumeTrue(cu != null)

        TagThrownCheckedExceptions(reducer.context)(cu)

        val tryStmt = cu.primaryType.get()
            .getMethodsBySignature("testThrowOnInvalidEntry").single()
            .body.get()
            .statements[2].asTryStmt()
        val zipExceptionClause = tryStmt.catchClauses
            .single { it.parameter.typeAsString == "ZipException" }
        assertTrue(zipExceptionClause.checkedExceptionSources.isNotEmpty())

        val srcExpr = tryStmt.tryBlock
            .statements[0].asExpressionStmt()
            .expression.asMethodCallExpr()
        assertTrue {
            zipExceptionClause.checkedExceptionSources
                .values
                .single()
                .any { NodeAstComparator.compare(it, srcExpr) == 0 }
        }
    }

    @Test
    fun `Tag Checked Exceptions Include AssignExpr Value`() {
        val project = TestProjects.Compress42f
        val entrypoint = project.parseEntrypoint("org.apache.commons.compress.archivers.zip.ZipArchiveEntryTest::isUnixSymlinkIsFalseIfMoreThanOneFlagIsSet")

        val reducer = project.getCoverageBasedReducer(entrypoint, true, null)
        val cu = reducer.context
            .getCUByPrimaryTypeName("org.apache.commons.compress.AbstractTestCase")
        assumeTrue(cu != null)

        TagThrownCheckedExceptions(reducer.context)(cu)

        val tryStmt = cu.primaryType.get()
            .getMethodsBySignature("getFile", "String").single()
            .body.get()
            .statements[3].asTryStmt()
        val uriSyntaxExceptionClause = tryStmt.catchClauses
            .single { it.parameter.typeAsString == "java.net.URISyntaxException" }
        assertTrue(uriSyntaxExceptionClause.checkedExceptionSources.isNotEmpty())

        val srcExpr = tryStmt.tryBlock
            .statements[0].asExpressionStmt()
            .expression.asAssignExpr()
            .value.asMethodCallExpr()
        assertTrue {
            uriSyntaxExceptionClause.checkedExceptionSources
                .values
                .single()
                .any { NodeAstComparator.compare(it, srcExpr) == 0 }
        }
    }

    @Test
    fun `Tag Checked Exceptions Include CastExpr Inner Expression`() {
        val project = TestProjects.Compress42f
        val entrypoint = project.parseEntrypoint("org.apache.commons.compress.archivers.zip.ZipArchiveEntryTest::isUnixSymlinkIsFalseIfMoreThanOneFlagIsSet")

        val reducer = project.getCoverageBasedReducer(entrypoint, true, null)
        val cu = reducer.context
            .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.zip.ExtraFieldUtils")
        assumeTrue(cu != null)

        TagThrownCheckedExceptions(reducer.context)(cu)

        val tryStmt = cu.primaryType.get()
            .getMethodsBySignature("register", "Class<?>").single()
            .body.get()
            .statements[0].asTryStmt()
        val instantiationExceptionClause = tryStmt.catchClauses
            .single { it.parameter.typeAsString == "InstantiationException" }
        assertTrue(instantiationExceptionClause.checkedExceptionSources.isNotEmpty())

        val srcExpr = tryStmt.tryBlock
            .statements[0].asExpressionStmt()
            .expression.asVariableDeclarationExpr()
            .variables.single()
            .initializer.get().asCastExpr()
            .expression.asMethodCallExpr()
        assertTrue {
            instantiationExceptionClause.checkedExceptionSources
                .values
                .single()
                .any { NodeAstComparator.compare(it, srcExpr) == 0 }
        }
    }

    @Test
    fun `Tag exceptions caught in union type`() {
        val project = TestProjects.Compress47f
        val entrypoint = project.parseEntrypoint("org.apache.commons.compress.archivers.zip.ZipArchiveInputStreamTest::nameSourceDefaultsToName")

        val reducer = project.getCoverageBasedReducer(entrypoint, true, null)
        val cu = reducer.context
            .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.zip.ExtraFieldUtils")
        assumeTrue(cu != null)

        TagThrownCheckedExceptions(reducer.context)(cu)

        val tryStmt = cu.primaryType.get()
            .getMethodsBySignature("parse", "byte[]", "boolean", "UnparseableExtraField").single()
            .body.get()
            .statements[2].asLabeledStmt()
            .statement.asWhileStmt()
            .body.asBlockStmt()
            .statements[3].asTryStmt()
        val unionExceptionClause = tryStmt.catchClauses.single()
        assertTrue(unionExceptionClause.checkedExceptionSources.isNotEmpty())

        val srcExpr = tryStmt.tryBlock
            .statements[0].asExpressionStmt()
            .expression.asVariableDeclarationExpr()
            .variables.single()
            .initializer.get().asMethodCallExpr()
        assertTrue {
            unionExceptionClause.checkedExceptionSources.all { (_, it) ->
                it.any { NodeAstComparator.compare(it, srcExpr) == 0 }
            }
        }
    }

    @Test
    fun `Tag exceptions caught by multiple clauses`() {
        val project = TestProjects.JacksonDatabind1f
        val entrypoint = project.parseEntrypoint("com.fasterxml.jackson.databind.struct.TestPOJOAsArray::testNullColumn")

        val reducer = project.getCoverageBasedReducer(entrypoint, true, null)
        val cu = reducer.context
            .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.ObjectMapper")
        assumeTrue(cu != null)

        TagThrownCheckedExceptions(reducer.context)(cu)

        val tryStmt = cu.primaryType.get()
            .getMethodsBySignature("writeValueAsString", "Object").single()
            .body.get()
            .statements[1].asTryStmt()
        val jsonProcessingExceptionClause = tryStmt.catchClauses
            .single { it.parameter.typeAsString == "JsonProcessingException" }
        val ioExceptionClause = tryStmt.catchClauses
            .single { it.parameter.typeAsString == "IOException" }
        assertTrue(jsonProcessingExceptionClause.checkedExceptionSources.isNotEmpty())
        assertTrue(ioExceptionClause.checkedExceptionSources.isNotEmpty())

        val srcExpr = tryStmt.tryBlock
            .statements[0].asExpressionStmt()
            .expression.asMethodCallExpr()
        assertTrue {
            jsonProcessingExceptionClause.checkedExceptionSources.all { (_, it) ->
                it.any { NodeAstComparator.compare(it, srcExpr) == 0 }
            }
        }
        assertTrue {
            ioExceptionClause.checkedExceptionSources.all { (_, it) ->
                it.any { NodeAstComparator.compare(it, srcExpr) == 0 }
            }
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