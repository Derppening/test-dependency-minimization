package com.derppening.researchprojecttoolkit.tool.visitor

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assumeTrue
import com.derppening.researchprojecttoolkit.tool.isUnusedData
import com.derppening.researchprojecttoolkit.tool.transform.*
import com.derppening.researchprojecttoolkit.tool.transformDecisionData
import com.derppening.researchprojecttoolkit.util.findAll
import com.derppening.researchprojecttoolkit.visitor.ASTPathGenerator
import com.derppening.researchprojecttoolkit.visitor.TransformingCloneVisitor
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.stmt.ExpressionStmt
import kotlin.test.*

class TransformingCloneVisitorTest {

    private var visitor: TransformingCloneVisitor? = null

    /**
     * Source: `org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream.<init>(java.io.InputStream, boolean)`
     *
     * Post-Transform:
     * ```java
     *     public GzipCompressorInputStream(InputStream inputStream, boolean decompressConcatenated) {
     *         in = null;
     *         in = null;
     *         throw new java.lang.AssertionError("This method should not be reached! Signature: GzipCompressorInputStream(InputStream, boolean)");
     *     }
     * ```
     *
     * Compilation Error:
     * ```
     * /tmp/7645392848993560404/src/main/java/org/apache/commons/compress/compressors/gzip/GzipCompressorInputStream.java:119: error: variable in might already have been assigned
     *         in = null;
     *         ^
     * ```
     */
    @Test
    fun `Dummy Branched Initializers`() {
        val reducerContext = TestProjects.Compress10f.getReducerContext()
        visitor = TransformingCloneVisitor(reducerContext, true)
        val visitor = checkNotNull(visitor)

        val cu = reducerContext.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream")
        assumeTrue(cu != null)

        val ctor = cu.primaryType.get()
                .getConstructorByParameterTypes("InputStream", "boolean").get()
        val ctorAstPath = ASTPathGenerator.forNode(ctor)

        val taggingPipeline = TagClassFieldInitializers(reducerContext) andThen
                TransformPass(reducerContext) {
                    ctorAstPath(it).transformDecisionData = NodeTransformDecision.DUMMY
                }

        taggingPipeline(cu)

        val visitedCtor = ctorAstPath(cu.clone()).accept(visitor, Unit) as ConstructorDeclaration?
        assertNotNull(visitedCtor)

        val assignStmts = visitedCtor.body.findAll<ExpressionStmt> { it.expression.isAssignExpr }
        assertEquals(2, assignStmts.size)
        val assignExpr = assignStmts
            .map { it.expression.asAssignExpr() }
            .single {
                (it.target as? NameExpr)?.nameAsString == "in" ||
                        (it.target as? FieldAccessExpr)?.let { fieldAccessExpr ->
                            fieldAccessExpr.scope.isThisExpr && fieldAccessExpr.nameAsString == "in"
                        } == true
            }
        assertTrue(assignExpr.target.isNameExpr)
        assertEquals("in", assignExpr.target.asNameExpr().nameAsString)
    }

    /**
     * Source: `org.apache.commons.compress.archivers.zip.ScatterSample.<init>()`
     * Post-Transform:
     * ```java
     *     public ScatterSample() {}
     * ```
     *
     * Compilation Error:
     * ```
     * /tmp/4382858713583088921/src/test/java/org/apache/commons/compress/archivers/zip/ScatterSample.java:29: error: unreported exception IOException; must be caught or declared to be thrown
     *     ScatterZipOutputStream dirs = ScatterZipOutputStream.fileBased(File.createTempFile("scatter-dirs", "tmp"));
     *                                                                                       ^
     * /tmp/4382858713583088921/src/test/java/org/apache/commons/compress/archivers/zip/ScatterSample.java:29: error: unreported exception FileNotFoundException; must be caught or declared to be thrown
     *     ScatterZipOutputStream dirs = ScatterZipOutputStream.fileBased(File.createTempFile("scatter-dirs", "tmp"));
     *                                                                                       ^
     * ```
     */
    @Test
    fun `Keep Exception Specifier for Constructors with Field Initializers`() {
        val reducerContext = TestProjects.Compress31f.getReducerContext()
        visitor = TransformingCloneVisitor(reducerContext, true)
        val visitor = checkNotNull(visitor)

        val cu = reducerContext.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.zip.ScatterSample")
        assumeTrue(cu != null)

        val ctor = cu.primaryType.get()
            .defaultConstructor.get()
        val ctorAstPath = ASTPathGenerator.forNode(ctor)

        val taggingPipeline = TransformPass(reducerContext) {
            ctorAstPath(it).transformDecisionData = NodeTransformDecision.DUMMY
        }

        taggingPipeline(cu)

        val visitedCtor = ctorAstPath(cu.clone()).accept(visitor, Unit) as ConstructorDeclaration?
        assertNotNull(visitedCtor)
        assertEquals(1, visitedCtor.thrownExceptions.size)

        val thrownException = visitedCtor.thrownExceptions.single()
        assertEquals("IOException", thrownException.asString())
    }

    @Test
    fun `Remove Unreachable Statements after Unconditional Jump in Unconditional While`() {
        val reducerContext = TestProjects.Compress22f.getReducerContext()
        visitor = TransformingCloneVisitor(reducerContext, true)
        val visitor = checkNotNull(visitor)

        val cu = reducerContext.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream")
        assumeTrue(cu != null)

        val method = cu.primaryType.get()
            .getMethodsBySignature("initBlock").single()
        val methodAstPath = ASTPathGenerator.forNode(method)

        val taggingPipeline = TransformPass(reducerContext) {
            methodAstPath(it)
                .body.get()
                .statements[6].asWhileStmt()
                .body.asBlockStmt()
                .statements[6].asIfStmt()
                .thenStmt.asBlockStmt()
                .statements[0].asBreakStmt()
                .isUnusedData = true

            methodAstPath(it)
                .body.get()
                .statements[6].asWhileStmt()
                .body.asBlockStmt()
                .statements[7].asIfStmt()
                .isUnusedData = true
        }

        taggingPipeline(cu)

        val visitedMethod = methodAstPath(cu.clone()).accept(visitor, Unit) as MethodDeclaration?
        assertNotNull(visitedMethod)
        assertNull(visitedMethod.body.get().statements.getOrNull(7))
    }

    @Test
    fun `Remove Unreachable Statements after Unconditional Jump in Unconditional Try`() {
        val reducerContext = TestProjects.Compress29f.getReducerContext()
        visitor = TransformingCloneVisitor(reducerContext, true)
        val visitor = checkNotNull(visitor)

        val cu = reducerContext.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.ArchiveStreamFactory")
        assumeTrue(cu != null)

        val method = cu.primaryType.get()
            .getMethodsBySignature("createArchiveInputStream", "InputStream").single()
        val methodAstPath = ASTPathGenerator.forNode(method)

        val taggingPipeline = TransformPass(reducerContext) {
            methodAstPath(it)
                .body.get()
                .statements[4].asTryStmt()
                .tryBlock
                .statements[13]
                .isUnusedData = true

            methodAstPath(it)
                .body.get()
                .statements[4].asTryStmt()
                .catchClauses
                .single { it.parameter.typeAsString == "IOException" }
                .body
                .statements[0].asThrowStmt()
                .isUnusedData = true
        }

        taggingPipeline(cu)

        val visitedMethod = methodAstPath(cu.clone()).accept(visitor, Unit) as MethodDeclaration?
        assertNotNull(visitedMethod)
        assertNull(visitedMethod.body.get().statements.getOrNull(5))
    }

    @AfterTest
    fun tearDown() {
        visitor = null
    }
}