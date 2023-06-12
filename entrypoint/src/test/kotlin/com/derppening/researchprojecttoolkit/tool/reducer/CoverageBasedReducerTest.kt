package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import com.derppening.researchprojecttoolkit.model.EntrypointSpec
import com.derppening.researchprojecttoolkit.model.ExecutableDeclaration
import com.derppening.researchprojecttoolkit.model.ReferenceTypeLikeDeclaration
import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.associatedBytecodeMethodData
import com.derppening.researchprojecttoolkit.tool.assumeTrue
import com.derppening.researchprojecttoolkit.tool.coberturaCoverageData
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.clearMemory
import com.derppening.researchprojecttoolkit.util.findAll
import com.github.javaparser.ast.Node
import org.junit.jupiter.api.BeforeAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CoverageBasedReducerTest {

    private fun testReductionCacheStability(project: TestProjects.Project, entrypoint: EntrypointSpec) {
        ClassDepReducerTestUtils.testBaselineReductionCacheStability {
            project.getCoverageBasedReducer(entrypoint, enableAssertions = true, null)
        }
    }

    private fun testParallelReductionCorrectness(project: TestProjects.Project, entrypoint: EntrypointSpec) {
        ClassDepReducerTestUtils.testParallelReductionCorrectness {
            project.getCoverageBasedReducer(entrypoint, enableAssertions = true, it)
        }
    }

    @Test
    fun `Closure-79f - Preprocess Pipeline Stability`() {
        val project = TestProjects.Closure79f
        val entrypoint = project.parseEntrypoint("com.google.javascript.jscomp.NormalizeTest::testIssue")

        assumeTrue(project.getBaselineDir(entrypoint).readCoberturaBaselineCoverage() != null)
        assumeTrue(project.getBaselineDir(entrypoint).readJacocoBaselineCoverage() != null)

        val reducer1 = project.getCoverageBasedReducer(entrypoint, true, 1)
            .also { it.preprocessCUs() }
        val reducer2 = project.getCoverageBasedReducer(entrypoint, true, null)
            .also { it.preprocessCUs() }

        val r1CUs = reducer1.context.loadedCompilationUnits.sortedBy { it.primaryType.flatMap { it.fullyQualifiedName }.get() }
        val r2CUs = reducer2.context.loadedCompilationUnits.sortedBy { it.primaryType.flatMap { it.fullyQualifiedName }.get() }
        assertEquals(r1CUs.size, r2CUs.size)

        r1CUs.zip(r2CUs).forEachIndexed { cuIdx, (lhsCU, rhsCU) ->
            assertEquals(
                lhsCU.primaryType.flatMap { it.fullyQualifiedName }.get(),
                rhsCU.primaryType.flatMap { it.fullyQualifiedName }.get(),
                "Expected `${lhsCU.primaryType.flatMap { it.fullyQualifiedName }.get()}` at index $cuIdx, got `${rhsCU.primaryType.flatMap { it.fullyQualifiedName }.get()}`"
            )

            val r1Types = lhsCU
                .findAll<Node> { ReferenceTypeLikeDeclaration.createOrNull(it) != null }
                .map { ReferenceTypeLikeDeclaration.create(it) }
            val r2Types = rhsCU
                .findAll<Node> { ReferenceTypeLikeDeclaration.createOrNull(it) != null }
                .map { ReferenceTypeLikeDeclaration.create(it) }
            assertEquals(r1Types.size, r2Types.size)

            r1Types.zip(r2Types).forEachIndexed { typeIdx, (lhsType, rhsType) ->
                assumeTrue(
                    lhsType.nameWithScope == rhsType.nameWithScope,
                    "Expected `${lhsType.nameWithScope}` at index $typeIdx, got `${rhsType.nameWithScope}` instead"
                )
                assertEquals(lhsType.coberturaCoverageData, rhsType.coberturaCoverageData)

                val r1Methods = lhsType.node
                    .findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                    .map { ExecutableDeclaration.create(it) }
                val r2Methods = rhsType.node
                    .findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                    .map { ExecutableDeclaration.create(it) }
                assertEquals(r1Methods.size, r2Methods.size)

                r1Methods.zip(r2Methods).forEachIndexed { methodIdx, (lhsMethod, rhsMethod) ->
                    assertEquals(
                        lhsMethod.node,
                        rhsMethod.node,
                        "Expected ${lhsMethod.qualifiedSignature} at index $methodIdx, got ${rhsMethod.qualifiedSignature} instead"
                    )
                    assertEquals(
                        lhsMethod.associatedBytecodeMethodData,
                        rhsMethod.associatedBytecodeMethodData
                    )
                    assertEquals(
                        lhsMethod.coberturaCoverageData,
                        rhsMethod.coberturaCoverageData
                    )
                }
            }
        }
    }

    @Test
    fun `Compress-21f - Check Consistency of Reduction Decision`() {
        val project = TestProjects.Compress21f
        val entrypoint =
            project.parseEntrypoint("org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest::testNineFilesSomeNotEmpty")

        val seqReducer = project.getCoverageBasedReducer(entrypoint, true, 1)
            .also { reducer ->
                reducer.run(1)
            }
        val parReducer = project.getCoverageBasedReducer(entrypoint, true, null)
            .also { reducer ->
                reducer.run()
            }

        val seqCU = seqReducer.context
            .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.ArchiveException")
        assumeTrue(seqCU != null)
        val parCU = parReducer.context
            .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.ArchiveException")
        assumeTrue(parCU != null)

        val seqType = seqCU.primaryType.get()
        val parType = parCU.primaryType.get()

        val seqDecision = isTypeReachable(
            seqReducer.context,
            seqType,
            true,
            noCache = true
        )
        val parDecision = isTypeReachable(
            parReducer.context,
            parType,
            true,
            noCache = true
        )

        assertEquals(seqDecision, parDecision)

        seqReducer.taggedCUs
        parReducer.taggedCUs

        val seqPostTagDecision = isTypeReachable(
            seqReducer.context,
            seqType,
            true,
            noCache = true
        )
        val parPostTagDecision = isTypeReachable(
            parReducer.context,
            parType,
            true,
            noCache = true
        )

        assertEquals(seqDecision, seqPostTagDecision)
        assertEquals(parDecision, parPostTagDecision)
        assertEquals(seqPostTagDecision, parPostTagDecision)
    }

    @Test
    fun `Compress-21f - Parallel Reduction Correctness`() {
        val project = TestProjects.Compress21f
        val entrypoint = project.parseEntrypoint("org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest::testNineFilesSomeNotEmpty")

        testParallelReductionCorrectness(project, entrypoint)
    }

    @Test
    fun `Compress-27f - Parallel Reduction Correctness`() {
        val project = TestProjects.Compress27f
        val entrypoint = project.parseEntrypoint("org.apache.commons.compress.archivers.tar.TarUtilsTest::testParseOctal")

        testParallelReductionCorrectness(project, entrypoint)
    }

    @Test
    fun `Compress-27f - Reduction Cache Stability`() {
        val project = TestProjects.Compress27f
        val entrypoint = project.parseEntrypoint("org.apache.commons.compress.archivers.tar.TarUtilsTest::testParseOctal")

        testReductionCacheStability(project, entrypoint)
    }

    @Test
    fun `Closure-79f - Parallel Reduction Correctness`() {
        val project = TestProjects.Closure79f
        val entrypoint = project.parseEntrypoint("com.google.javascript.jscomp.NormalizeTest::testIssue")

        testParallelReductionCorrectness(project, entrypoint)
    }

    @Test
    fun `JacksonDatabind-1f - Parallel Reduction Correctness`() {
        val project = TestProjects.JacksonDatabind1f
        val entrypoint = project.parseEntrypoint("com.fasterxml.jackson.databind.struct.TestPOJOAsArray::testNullColumn")

        testParallelReductionCorrectness(project, entrypoint)
    }

    @Test
    fun `JacksonDatabind-1f - Reduction Cache Stability`() {
        val project = TestProjects.JacksonDatabind1f
        val entrypoint = project.parseEntrypoint("com.fasterxml.jackson.databind.struct.TestPOJOAsArray::testNullColumn")

        testReductionCacheStability(project, entrypoint)
    }

    @Test
    fun `JacksonDatabind-20f - Parallel Reduction Correctness`() {
        val project = TestProjects.JacksonDatabind20f
        val entrypoint = project.parseEntrypoint("com.fasterxml.jackson.databind.introspect.TestNamingStrategyStd::testNamingWithObjectNode")

        testParallelReductionCorrectness(project, entrypoint)
    }

    @Test
    fun `JacksonDatabind-20f - Reduction Cache Stability`() {
        val project = TestProjects.JacksonDatabind20f
        val entrypoint = project.parseEntrypoint("com.fasterxml.jackson.databind.introspect.TestNamingStrategyStd::testNamingWithObjectNode")

        testReductionCacheStability(project, entrypoint)
    }

    @Test
    fun `Mockito-25f - Parallel Reduction Correctness`() {
        val project = TestProjects.Mockito25f
        val entrypoint = project.parseEntrypoint("org.mockito.internal.stubbing.defaultanswers.ReturnsGenericDeepStubsTest::will_return_default_value_on_non_mockable_nested_generic")

        testParallelReductionCorrectness(project, entrypoint)
    }

    @Test
    fun `Mockito-25f - Reduction Cache Stability`() {
        val project = TestProjects.Mockito25f
        val entrypoint = project.parseEntrypoint("org.mockito.internal.stubbing.defaultanswers.ReturnsGenericDeepStubsTest::will_return_default_value_on_non_mockable_nested_generic")

        testReductionCacheStability(project, entrypoint)
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