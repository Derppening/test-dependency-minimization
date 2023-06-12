package com.derppening.researchprojecttoolkit.util

import com.github.javaparser.JavaParser
import com.github.javaparser.ParseResult
import com.github.javaparser.ast.CompilationUnit
import hk.ust.cse.castle.toolkit.jvm.jsl.PredicatedFileCollector
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.jvm.optionals.getOrNull

/**
 * Collector for obtaining a list of [CompilationUnit] under a source root directory.
 *
 * @property sourceRoot The root directory of all classes.
 */
class SourceRootCompilationUnitsParser(sourceRoot: Path, private val parserCreator: () -> JavaParser) {

    private val sourceRoot = sourceRoot.toAbsolutePath()

    private val cachedValues by lazy {
        PredicatedFileCollector(sourceRoot)
            .collect { it.extension == "java" }
            .parallelStream()
            .collect(
                Collectors.toConcurrentMap(
                    { it },
                    {
                        val result = parserCreator().parse(it)

                        result.ifSuccessful {
                            val fileSrcRoot = it.storage.get().sourceRoot
                            check(fileSrcRoot == sourceRoot) {
                                "Source root for file ($fileSrcRoot) is different from source root set in class ($sourceRoot)"
                            }
                        }

                        result
                    }
                )
            )
    }

    init {
        require(sourceRoot.isDirectory())
    }

    /**
     * All files under the [sourceRoot] which contains compilation units.
     */
    val files: Set<Path>
        get() = cachedValues.keys

    /**
     * All [CompilationUnit] found under the [sourceRoot].
     */
    val compilationUnits: Set<CompilationUnit>
        get() = cachedValues.asSequence().mapNotNull { (_, v) -> v.result.getOrNull() }.toSet()

    /**
     * Whether the parsing operation have succeeded for all files in the source directory.
     */
    val isAllSuccessful: Boolean
        get() = getFailed().isEmpty()

    /**
     * Obtains all Java source files mapped to the parsed [CompilationUnit].
     *
     * Files which failed to be parsed into a [CompilationUnit] will not be present in this [Map].
     */
    fun getSuccess(): Map<Path, CompilationUnit> =
        cachedValues
            .filterValues { it.isSuccessful }
            .mapValues { (_, v) -> v.result.get() }

    /**
     * Obtains all Java source files which failed to be parsed.
     */
    fun getFailed(): Map<Path, ParseResult<CompilationUnit>> = cachedValues.filterValues { !it.isSuccessful }

    /**
     * Obtains all Java source files mapped to the result of parsing the file into a [CompilationUnit].
     */
    fun getAll(): Map<Path, ParseResult<CompilationUnit>> = cachedValues
}