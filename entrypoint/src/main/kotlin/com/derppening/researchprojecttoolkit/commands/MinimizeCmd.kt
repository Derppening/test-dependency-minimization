package com.derppening.researchprojecttoolkit.commands

import com.derppening.researchprojecttoolkit.model.EntrypointSpec
import com.derppening.researchprojecttoolkit.tool.isUnusedForRemovalData
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractReducer
import com.derppening.researchprojecttoolkit.tool.reducer.StaticAnalysisMemberReducer
import com.derppening.researchprojecttoolkit.util.*
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.javaparser.ast.body.CallableDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration
import java.nio.file.Path
import java.util.concurrent.ConcurrentSkipListMap
import java.util.function.Function
import java.util.stream.Collectors
import kotlin.io.path.*
import kotlin.time.*

class MinimizeCmd : CliktCommand(help = HELP_TEXT, name = "minimize") {

    private val classpath by option("-cp", "--classpath", help = "Classpath of the project.")
        .default("")
    private val enableAssertions by option("-ea", "--enableassertions", help = "")
        .flag("-da", "--disableassertions", default = true)
    private val maxPasses by option("--max-passes", help = "Number of reduction passes to execute for a single bug.")
        .int()
        .convert { if (it < 0) UInt.MAX_VALUE else it.toUInt() }
        .default(UInt.MAX_VALUE)
    private val noOptimize by option("-O", "--no-optimize", help = "Do not run optimizations when performing reduction.")
        .enum<AbstractReducer.Optimization> { it.asCmdlineOpt() }
        .multiple()
        .unique()
    private val outputDir by option("-o", "--output", help = "Directory to output all reduced source files.")
        .path(canBeFile = false)
        .required()
    private val sourcesClasspath by option("-scp", "--source-classpath", help = "Directories that contains sources which should be treated as a component in the classpath.")
        .default("")
    private val sourceRoot by option("-sr", "--source-root", help = "All source directories of the project.")
        .path(mustExist = true, canBeFile = false)
        .multiple()
    private val threads by option("-T", "--threads", help = "Number of threads to execute reduction task.")
        .int()
    private val tmpDir by option("--tmp-dir", help = "Temporary directory to store intermediate results to.")
        .path()
        .default(Path("/tmp"))
    private val inputSpec by argument(help = "List of classes, or methods to act as the entrypoint. Method should be specified in the form of <class>::<method>.")
        .multiple(required = true)

    data class PassInfo(
        val passNum: UInt,
        val outPath: TemporaryPath,
        val sourceRoots: List<Path>,
        val duration: Duration,
        val retainedMethods: Set<String>
    )

    private fun getCommonBasePath(paths: Collection<Path>): Path {
        check(paths.isNotEmpty())

        if (paths.size == 1) {
            return paths.single()
        }

        val minPath = paths.min()
        val otherPaths = paths.toSet() - minPath

        return generateSequence(minPath) { it.parent }
            .firstOrNull { pathPrefix -> otherPaths.all { it.startsWith(pathPrefix) } }
            ?: Path("/")
    }

    @OptIn(ExperimentalTime::class)
    private fun runPass(prevPassInfo: PassInfo?): PassInfo {
        val passNum = prevPassInfo?.passNum?.plus(1u) ?: 0u
        val sourceRoots = prevPassInfo?.sourceRoots ?: sourceRoot
        val entrypoints = inputSpec.map { EntrypointSpec.fromArg(it, sourceRoots) }

        val reducer = StaticAnalysisMemberReducer(
            classpath,
            sourcesClasspath,
            sourceRoots,
            entrypoints,
            enableAssertions,
            threads,
            AbstractReducer.Optimization.ALL - noOptimize
        )

        val markPhaseDuration = measureTime {
            if (threads != null) {
                reducer.run(checkNotNull(threads))
            } else {
                reducer.run()
            }
        }

        val tempDir = TemporaryPath.createDirectory(tmpDir)
        val sourceRootMapping = mutableMapOf<Path, Path>()
        val (retainedCU, sweepPhaseDuration) = measureTimedValue {
            reducer.getTransformedCompilationUnits(
                tempDir.path,
                getCommonBasePath(sourceRoots),
                sourceRootMapping = sourceRootMapping
            )
        }

        val totalDuration = markPhaseDuration + sweepPhaseDuration
        LOGGER.info("Reduction of Pass {} took {} s", passNum, totalDuration.toDouble(DurationUnit.SECONDS).toStringRounded(3))

        val retainedMethods = retainedCU.parallelStream()
            .flatMap { it.findAll<CallableDeclaration<*>> { it.canBeReferencedByName }.stream() }
            .filter { !it.isUnusedForRemovalData }
            .collect(
                Collectors.toConcurrentMap(
                    { reducer.context.symbolSolver.resolveDeclaration<ResolvedMethodLikeDeclaration>(it) },
                    Function.identity(),
                    ThrowingMergerForCallable,
                    { ConcurrentSkipListMap(ResolvedCallableDeclComparator(reducer.context.symbolSolver)) }
                )
            )

        LOGGER.info("Saving sources to ${tempDir.path}")
        retainedCU.forEach { cu -> cu.storage.ifPresent { it.save() } }

        prevPassInfo?.outPath?.close()

        return PassInfo(
            passNum,
            tempDir,
            sourceRootMapping.values.toList(),
            totalDuration,
            retainedMethods.keys.map { it.bytecodeQualifiedSignature }.toSet()
        )
    }

    @OptIn(ExperimentalPathApi::class)
    override fun run() {
        var passInfo: PassInfo? = null
        for (passNum in 0u until maxPasses) {
            val prevPassInfo = passInfo
            passInfo = runPass(prevPassInfo)

            if (passInfo.retainedMethods == prevPassInfo?.retainedMethods) {
                break
            }
        }

        checkNotNull(passInfo)

        LOGGER.info("Finalizing sources to $outputDir")
        passInfo.outPath.path.copyToRecursively(
            outputDir.apply { parent?.createDirectories() },
            followLinks = false,
            overwrite = true
        )
        outputDir.resolve("source-mapping.txt").deleteIfExists()
        passInfo.outPath.close()
    }

    companion object {

        private const val HELP_TEXT = "Performs minimization on a snapshot."

        private val LOGGER = Logger<MinimizeCmd>()
    }
}