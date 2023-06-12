package com.derppening.researchprojecttoolkit.tool.transform

import com.derppening.researchprojecttoolkit.defects4j.SourceRootOutput
import com.derppening.researchprojecttoolkit.model.*
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractBaselineReducer.Companion.isUnusedInCoverage
import com.derppening.researchprojecttoolkit.util.Logger
import com.derppening.researchprojecttoolkit.util.ToolOutputDirectory
import com.derppening.researchprojecttoolkit.util.findAll
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.stmt.Statement
import kotlin.io.path.bufferedReader

/**
 * Populates coverage data for a [CompilationUnit].
 *
 * @param coverageData The coverage data associated with this project.
 * @param baselineDir The baseline directory for this bug.
 */
class PopulateReachabilityInfo(
    override val reducerContext: ReducerContext,
    private val coverageData: CoverageData,
    private val baselineDir: ToolOutputDirectory
) : TransformPass {

    private val allSourceClasses by lazy {
        baselineDir.getAllClassesInSourceRootPath(SourceRootOutput.SOURCE)
            .bufferedReader()
            .use { it.readLines() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private val allTestClasses by lazy {
        baselineDir.getAllClassesInSourceRootPath(SourceRootOutput.TEST)
            .bufferedReader()
            .use { it.readLines() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun getCoverageSource(cu: CompilationUnit): CoverageSource =
        coverageData.getCUSource(cu, allSourceClasses, allTestClasses)

    private fun processTypeReachabilityInfo(cu: CompilationUnit) {
        val loadedClasses by lazy(LazyThreadSafetyMode.NONE) { LoadedClasses.fromBaseline(baselineDir) }
        val coverageSource by lazy(LazyThreadSafetyMode.NONE) { getCoverageSource(cu) }

        cu.findAll<Node> {
            ReferenceTypeLikeDeclaration.createOrNull(it) != null
        }.map {
            ReferenceTypeLikeDeclaration.create(it)
        }.forEach { typeDecl ->
            val mergedCov = MergedCoverageData.from(typeDecl)

            if (mergedCov.isPartiallyPresent) {
                if (!mergedCov.isReachable) {
                    typeDecl.isBaselineLoadedData = false
                    typeDecl.isBaselineCreatedData = false

                    return@forEach
                }

                val init = mergedCov.findMethodsWithSignature("<init>", null)
                if (init.isNotEmpty()) {
                    val isCreated = init.any { it.isReachable }

                    if (isCreated) {
                        typeDecl.isBaselineLoadedData = true
                        typeDecl.isBaselineCreatedData = true

                        return@forEach
                    }
                }

                val clinit = mergedCov.findMethodsWithSignature("<clinit>", null).singleOrNull()
                if (clinit != null) {
                    val isLoaded = clinit.isReachable

                    if (isLoaded) {
                        typeDecl.isBaselineLoadedData = true
                        if (typeDecl.isBaselineCreatedData == null) {
                            typeDecl.isBaselineCreatedData = false
                        }

                        return@forEach
                    }
                }

                if (init.isEmpty() && clinit == null) {
                    val allClasses = baselineDir.getAllClassesInSourceRootPath(SourceRootOutput.ALL)
                        .bufferedReader()
                        .use { it.readLines() }
                        .filter { it.isNotBlank() }
                        .toSet()
                    val className = run {
                        val coberturaName = mergedCov.coberturaCov?.name
                        val jacocoName = mergedCov.jacocoCov?.name?.replace('/', '.')

                        if (coberturaName != null && jacocoName != null) {
                            check(coberturaName == jacocoName) {
                                "Expected Cobertura and Jacoco class names to be the same, but got `$coberturaName` and `$jacocoName`"
                            }
                        }

                        coberturaName ?: jacocoName
                    }

                    if (className in allClasses) {
                        val allLoadedClasses = (loadedClasses.sourceClasses + loadedClasses.testClasses).toSet()

                        typeDecl.isBaselineLoadedData = className in allLoadedClasses
                    } else {
                        LOGGER.warn("Type `$className` does not contain <init> or <clinit> methods in coverage data, and is not present in all-classes list")
                    }
                }
            } else if (coverageSource is CoverageSource.ClassLoader) {
                val isPresent = cu.primaryType
                    .flatMap { it.fullyQualifiedName }
                    .map { it in (coverageSource as CoverageSource.ClassLoader).value }
                    .get()

                typeDecl.isBaselineLoadedData = isPresent
                typeDecl.isBaselineCreatedData = isPresent
            }
        }
    }

    private fun processStmtReachabilityInfo(cu: CompilationUnit) {
        if (cu.jacocoCoverageData == null && cu.coberturaCoverageData == null) {
            return
        }

        val jacocoCUCov = cu.jacocoCoverageData?.first?.lines
        val coberturaCUCov = cu.coberturaCoverageData?.flatMap { it.lines }

        cu.findAll<Statement>().forEach { stmt ->
            stmt.isUnusedData = stmt.isUnusedInCoverage(coberturaCUCov, jacocoCUCov, false)
        }
    }

    override fun transform(cu: CompilationUnit) {
        verifyIsInvokedOnce(cu)
        verifyIsInvokedAfter<PopulateCoverageData>(cu)

        processTypeReachabilityInfo(cu)
        processStmtReachabilityInfo(cu)
    }

    companion object {

        private val LOGGER = Logger<PopulateReachabilityInfo>()
    }
}