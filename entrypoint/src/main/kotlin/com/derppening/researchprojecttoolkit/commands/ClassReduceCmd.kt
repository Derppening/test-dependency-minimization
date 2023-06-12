package com.derppening.researchprojecttoolkit.commands

import com.derppening.researchprojecttoolkit.model.EntrypointSpec
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.inclusionReasonsData
import com.derppening.researchprojecttoolkit.tool.reducer.StaticAnalysisMemberReducer
import com.derppening.researchprojecttoolkit.util.Logger
import com.derppening.researchprojecttoolkit.util.findAll
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import com.github.javaparser.ast.body.TypeDeclaration
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class ClassReduceCmd : CliktCommand(help = HELP_TEXT, name = "class-reduce") {

    private val classpath by option("-cp", "--classpath", help = "Classpath of the project.")
        .default("")
    private val enableAssertions by option("-ea", "--enableassertions", help = "")
        .flag("-da", "--disableassertions", default = true)
    private val outputDetails by option("-oD", "--output-details", help = "Whether to emit the reason a class is retained to stdout.")
        .flag()
    private val output by option("-o", "--output", help = "Directory to output all reduced source files.")
        .path(mustExist = false)
    private val outputClassList by option("-ocl", "--output-class-list", help = "File to output all retained classes to.")
        .path(mustExist = false)
    private val sourcesClasspath by option("-scp", "--source-classpath", help = "Directories that contains sources which should be treated as a component in the classpath.")
        .default("")
    private val sourceRoot by option("-sr", "--source-root", help = "All source directories of the project.")
        .path(mustExist = true, canBeFile = false)
        .multiple()
    private val inputSpec by argument(help = "List of files, classes, or methods to act as the entrypoint.")
        .multiple(required = true)

    override fun run() {
        val deduplicatedClasspath = classpath.split(":").distinct().joinToString(":")
        val entrypoints = inputSpec.map { EntrypointSpec.fromArg(it, sourceRoot) }

        val reducer = StaticAnalysisMemberReducer(
            deduplicatedClasspath,
            sourcesClasspath,
            sourceRoot,
            entrypoints,
            enableAssertions,
            null
        )

        reducer.run()

        val sourceRootMapping = mutableMapOf<Path, Path>()
        val retainedCU = reducer.getTransformedCompilationUnits(
            output ?: Path("/tmp"),
            sourceRootMapping = sourceRootMapping
        )
        val retainedClasses = retainedCU.parallelStream()
            .flatMap { it.findAll<TypeDeclaration<*>>().stream() }
            .filter { it.fullyQualifiedName.isPresent }
            .collect(Collectors.toList()) as List<TypeDeclaration<*>>
        if (outputClassList != null) {
            val outputClassList = checkNotNull(outputClassList)
            outputClassList.bufferedWriter().use { writer ->
               retainedClasses.forEach { writer.write(it.fullyQualifiedName.get()) }
            }
        } else {
            LOGGER.info("Retained ${retainedClasses.toSet().size} classes:")
            retainedClasses.forEach {
                LOGGER.info(it.fullyQualifiedName.get())

                if (outputDetails) {
                    it.inclusionReasonsData.synchronizedWith {
                        forEach { reason ->
                            LOGGER.info("  ${reason.toReasonString()}")
                        }
                    }
                }
            }
        }

        if (output != null) {
            val outputDir = checkNotNull(output)

            if (outputDir.exists()) {
                outputDir.toFile().deleteRecursively()
            }

            outputDir.createDirectories()
            retainedCU.parallelStream().forEach { it.storage.get().save() }
            LOGGER.info("Sources dumped into $outputDir")

            LOGGER.info("Performing test compilation...")

            val compiler = CompilerProxy(sourceRootMapping.values, classpath, listOf("-Xmaxerrs", "100000", "-Xmaxwarns", "100000"))
            val isCompileSuccessful = compiler.run()

            if (isCompileSuccessful) {
                LOGGER.info("Sources successfully compiled")
            } else {
                LOGGER.error("Failed to compile sources")
            }
        }
    }

    companion object {

        private const val HELP_TEXT = ""

        private val LOGGER = Logger<ClassReduceCmd>()
    }
}