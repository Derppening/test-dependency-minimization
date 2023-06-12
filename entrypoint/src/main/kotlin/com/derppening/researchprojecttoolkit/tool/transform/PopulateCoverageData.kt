package com.derppening.researchprojecttoolkit.tool.transform

import com.derppening.researchprojecttoolkit.tool.reducer.AbstractBaselineReducer
import com.derppening.researchprojecttoolkit.defects4j.SourceRootOutput
import com.derppening.researchprojecttoolkit.model.*
import com.derppening.researchprojecttoolkit.tool.ReducerContext
import com.derppening.researchprojecttoolkit.tool.associatedBytecodeMethodData
import com.derppening.researchprojecttoolkit.tool.coberturaCoverageData
import com.derppening.researchprojecttoolkit.tool.jacocoCoverageData
import com.derppening.researchprojecttoolkit.util.Logger
import com.derppening.researchprojecttoolkit.util.ToolOutputDirectory
import com.derppening.researchprojecttoolkit.util.findAll
import com.derppening.researchprojecttoolkit.util.findAncestor
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import kotlin.io.path.bufferedReader
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

/**
 * Populates coverage data for a [CompilationUnit].
 *
 * @param coverageData The coverage data associated with this project.
 * @param baselineDir The baseline directory for this bug.
 */
class PopulateCoverageData(
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

    private fun tieCoberturaCUCoverageData(cu: CompilationUnit) {
        when (val coverageSource = getCoverageSource(cu)) {
            is CoverageSource.CoberturaCoverage -> {
                val pkg = cu.packageDeclaration.map { it.nameAsString }.get()
                val relPath = cu.storage.map { it.sourceRoot.relativize(it.path) }.get()

                val coverageClasses = coverageSource.value.coverage
                    .packages
                    .single { it.name == pkg }
                    .classes
                    .filter { it.filename == relPath }

                cu.coberturaCoverageData = coverageClasses.toSortedSet(Comparator.comparing(CoberturaXML.Class::name))
            }

            else -> {}
        }
    }

    private fun tieCoberturaClassCoverageData(cu: CompilationUnit, foundClass: ReferenceTypeLikeDeclaration<*>) {
        if (cu.coberturaCoverageData != null) {
            val coberturaCovClasses = checkNotNull(cu.coberturaCoverageData)

            foundClass.coberturaCoverageData = coberturaCovClasses
                .find { it.name == foundClass.jacocoCoverageData!!.name.replace('/', '.') }
                ?: error("Class `${foundClass.jacocoCoverageData!!.name.replace('/', '.')}` is missing in Cobertura")
        }
    }

    private fun tieCoberturaMethodCoverageData(
        foundClass: ReferenceTypeLikeDeclaration<*>,
        foundMethod: ExecutableDeclaration<*>,
        classCov: JacocoXML.Class,
        methodCov: JacocoXML.Method
    ) {
        if (foundClass.coberturaCoverageData != null) {
            val coberturaClassCov = checkNotNull(foundClass.coberturaCoverageData)

            val coberturaMethodCov = coberturaClassCov.methods
                .find {
                    coberturaClassCov.name == classCov.name.replace('/', '.') &&
                            it.name == methodCov.name &&
                            it.signature == methodCov.desc
                }
                ?: error("Method `${foundMethod.qualifiedName}` with signature `${methodCov.desc}` is missing in Cobertura")

            foundMethod.coberturaCoverageData = coberturaMethodCov
        }
    }

    private fun tieCoverageData(cu: CompilationUnit) {
        val jacocoCov = coverageData.jacocoCov?.value ?: run {
            LOGGER.error("Jacoco coverage data not present! Skipping tagging for compilation unit in ${cu.storage.map { it.path }.get()}")
            return
        }

        val packageName = cu.packageDeclaration
            .map { it.nameAsString.replace('.', '/') }
            .getOrDefault("")
        val primaryType = cu.primaryType
            .flatMap { it.fullyQualifiedName }
            .map { it.replace('.', '/') }
            .getOrNull()
            ?: return

        val jacocoPackage = jacocoCov.report
            .packages
            .singleOrNull { it.name == packageName }
            ?: error("Package `$packageName` is missing in Jacoco")
        val jacocoPrimaryType = jacocoPackage.classes
            .singleOrNull { it.name == primaryType }
            ?: error("Type `$primaryType` is missing in Jacoco")
        val jacocoSourceFile = jacocoPackage.sourceFiles
            .singleOrNull { it.name == jacocoPrimaryType.sourceFileName }
            ?: error("Source File `${jacocoPrimaryType.sourceFileName}` is missing in Jacoco")

        val jacocoCovClasses = jacocoPackage.classes
            .filter { it.sourceFileName == jacocoSourceFile.name }

        cu.jacocoCoverageData = jacocoSourceFile to sortedSetOf(Comparator.comparing(JacocoXML.Class::name), *jacocoCovClasses.toTypedArray())

        tieCoberturaCUCoverageData(cu)

        val allRefTypeLikeDecls = cu.findAll<Node> {
            ReferenceTypeLikeDeclaration.createOrNull(it) != null
        }.map {
            ReferenceTypeLikeDeclaration.create(it)
        }

        jacocoCovClasses.forEach { classCov ->
            val foundClass = AbstractBaselineReducer.findClassFromJacocoCoverage(allRefTypeLikeDecls, classCov)
            if (foundClass != null) {
                foundClass.jacocoCoverageData = classCov

                tieCoberturaClassCoverageData(cu, foundClass)

                val jacocoCovMethods = classCov.methods

                val allMethodDecls = foundClass.node
                    .findAll<Node> {
                        ExecutableDeclaration.createOrNull(it) != null &&
                                it.findAncestor<Node> { ReferenceTypeLikeDeclaration.createOrNull(it) != null }
                                    ?.let { ReferenceTypeLikeDeclaration.create(it).node === foundClass.node } == true
                    }
                    .map { ExecutableDeclaration.create(it) }

                jacocoCovMethods.forEach { methodCov ->
                    val foundMethod = AbstractBaselineReducer.findMethodFromJacocoCoverage(
                        allMethodDecls,
                        classCov,
                        methodCov,
                        reducerContext
                    )

                    if (foundMethod != null) {
                        if (foundMethod.jacocoCoverageData != null) {
                            val msg = buildString {
                                append("Jacoco Coverage for method is set more than once!")
                                append('\n')
                                append("This Method Signature: ${foundMethod.qualifiedSignature}")
                                append('\n')
                                append("Old Method Signature: ${foundMethod.associatedBytecodeMethodData}")
                                append('\n')
                                append("Old Value: ${foundMethod.jacocoCoverageData}")
                                append('\n')
                                append("New Method Signature: ${BytecodeMethod.fromJacocoCoverage(jacocoPackage, classCov, methodCov, null)}")
                                append('\n')
                                append("New Value: $methodCov")
                            }

                            error(msg)
                        }

                        foundMethod.jacocoCoverageData = methodCov

                        tieCoberturaMethodCoverageData(foundClass, foundMethod, classCov, methodCov)

                        foundMethod.associatedBytecodeMethodData = BytecodeMethod.fromJacocoCoverage(
                            jacocoPackage,
                            foundClass.jacocoCoverageData!!,
                            foundMethod.jacocoCoverageData!!,
                            foundMethod.coberturaCoverageData
                        )
                    }
                }
            }
        }
    }

    override fun transform(cu: CompilationUnit) {
        verifyIsInvokedOnce(cu)

        tieCoverageData(cu)
    }

    companion object {

        private val LOGGER = Logger<PopulateCoverageData>()
    }
}