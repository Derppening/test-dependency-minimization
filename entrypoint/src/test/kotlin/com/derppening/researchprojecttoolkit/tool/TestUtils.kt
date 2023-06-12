package com.derppening.researchprojecttoolkit.tool

import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.compilation.JUnitRunnerProxy
import com.derppening.researchprojecttoolkit.util.Logger
import com.derppening.researchprojecttoolkit.util.boundedTypes
import com.derppening.researchprojecttoolkit.util.posix.ExecutionOutput
import com.github.javaparser.resolution.declarations.ResolvedTypeParameterDeclaration
import com.github.javaparser.resolution.model.typesystem.NullType
import com.github.javaparser.resolution.types.*
import com.github.javaparser.symbolsolver.resolution.typeinference.InferenceVariable
import org.junit.jupiter.api.Assumptions
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.io.path.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private object TestUtils

const val JAVAC_MAXERRS = 100

val JAVA_8_HOME = Path("/usr/lib/jvm/java-8-adoptopenjdk")
val JAVA_11_HOME = Path("/usr/lib/jvm/java-11-temurin")
val JAVA_17_HOME = Path("/usr/lib/jvm/java-17-temurin")

fun getResourcePath(relPath: Path): Path {
    val url = checkNotNull(TestUtils::class.java.getResource(relPath.toString())) {
        "$relPath not found in resources"
    }
    return Paths.get(url.toURI())
}

/**
 * Asserts that a [ResolvedWildcard] represents an unbounded type.
 */
fun assertIsUnbounded(wildcard: ResolvedWildcard) {
    if (wildcard.isBounded) {
        assertTrue(wildcard.isExtends)

        val wildcardBounds = wildcard.boundedTypes
        assertEquals(1, wildcardBounds.size)
        val bound = wildcardBounds.single()

        val tp0BoundType = bound
            .also { assertTrue(it.isReferenceType) }
            .asReferenceType()
        assertTrue(tp0BoundType.isJavaLangObject)
    }
}

/**
 * Asserts that a [ResolvedTypeParameterDeclaration] represents an unbounded type.
 */
fun assertIsUnbounded(tp: ResolvedTypeParameterDeclaration) {
    if (tp.isBounded) {
        assertEquals(1, tp.bounds.size)
        val tpBound = tp.bounds.single()
        assertTrue(tpBound.isExtends)

        val tpBoundType = tpBound.type
        assertResolvedTypeIs<ResolvedReferenceType>(tpBoundType)
        assertTrue(tpBoundType.isJavaLangObject)
    }
}

/**
 * Asserts that a [ResolvedTypeVariable] represents an unbounded type.
 */
fun assertIsUnbounded(tv: ResolvedTypeVariable) = assertIsUnbounded(tv.asTypeParameter())

/**
 * Asserts that a [ResolvedType] is of a downcasted type [T].
 */
@OptIn(ExperimentalContracts::class)
inline fun <reified T : ResolvedType> assertResolvedTypeIs(type: ResolvedType): T {
    contract { returns() implies (type is T) }

    return when (T::class) {
        ResolvedArrayType::class -> {
            assertTrue(type.isArray)
            type.asArrayType()
        }
        ResolvedPrimitiveType::class -> {
            assertTrue(type.isPrimitive)
            type.asPrimitive()
        }
        NullType::class -> {
            assertTrue(type.isNull)
            NullType.INSTANCE
        }
        ResolvedUnionType::class -> {
            assertTrue(type.isUnionType)
            type.asUnionType()
        }
        ResolvedLambdaConstraintType::class -> {
            assertTrue(type.isConstraint)
            type.asConstraintType()
        }
        ResolvedReferenceType::class -> {
            assertTrue(type.isReferenceType)
            type.asReferenceType()
        }
        ResolvedVoidType::class -> {
            assertTrue(type.isVoid)
            ResolvedVoidType.INSTANCE
        }
        ResolvedTypeVariable::class -> {
            assertTrue(type.isTypeVariable)
            type.asTypeVariable()
        }
        ResolvedWildcard::class -> {
            assertTrue(type.isWildcard)
            type.asWildcard()
        }
        InferenceVariable::class -> {
            assertTrue(type.isInferenceVariable)
            type
        }
        else -> {
            assertIs<T>(type)
            type
        }
    } as T
}

@OptIn(ExperimentalContracts::class)
fun assumeTrue(message: String? = null, block: () -> Boolean) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    assumeTrue(block(), message)
}

@OptIn(ExperimentalContracts::class)
fun assumeTrue(assumption: Boolean, message: String? = null) {
    contract { returns() implies assumption }
    if (message != null) {
        Assumptions.assumeTrue(assumption, message)
    } else {
        Assumptions.assumeTrue(assumption)
    }
}

@OptIn(ExperimentalContracts::class)
fun assumeFalse(message: String? = null, block: () -> Boolean) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    assumeFalse(block(), message)
}

@OptIn(ExperimentalContracts::class)
fun assumeFalse(assumption: Boolean, message: String? = null) {
    contract { returns() implies !assumption }
    if (message != null) {
        Assumptions.assumeFalse(assumption, message)
    } else {
        Assumptions.assumeFalse(assumption)
    }
}

fun tryCompileNoAssertion(compiler: CompilerProxy) {
    val logger = Logger<TestUtils>()

    compiler.run()
    if (compiler.exitCode.isSuccess) {
        logger.info("Compilation Succeeded")
    } else {
        logger.warn("Compilation Failed with Exit Code ${compiler.exitCode.code}\n${compiler.stderr.joinToString("\n")}")
    }
}

fun assertCompileSuccess(compiler: CompilerProxy) {
    compiler.run()
    assertTrue(compiler.exitCode.isSuccess, "Compilation Failed with Exit Code ${compiler.exitCode.code}\n${compiler.stderr.joinToString("\n")}")
}

fun assumeCompileSuccess(compiler: CompilerProxy) {
    compiler.run()
    assumeTrue(compiler.exitCode.isSuccess, "Compilation Failed with Exit Code ${compiler.exitCode.code}\n${compiler.stderr.joinToString("\n")}")
}

fun assertTestSuccess(junitCmd: MutableList<String>.() -> Unit): ExecutionOutput {
    val runner = JUnitRunnerProxy.run {
        junitCmd()
        add("--disable-banner" )
    }
    assertTrue(runner.exitCode.isSuccess, "Test Execution Failed with Exit Code ${runner.exitCode.code}\n${runner.stdout.joinToString("\n")}")
    return runner
}

fun tryTestNoAssertion(
    workingDir: Path? = null,
    jvmHome: Path? = null,
    junitCmd: MutableList<String>.() -> Unit
): JUnitRunnerProxy {
    val logger = Logger<TestUtils>()

    val runner = JUnitRunnerProxy(
        buildList(junitCmd) + "--disable-banner" + "--disable-ansi-colors",
        workingDir,
        overrideJvmHome = jvmHome
    ).apply { run() }

    if (runner.exitCode.isSuccess) {
        logger.info("Test Execution Succeeded")
    } else {
        logger.warn("Test Execution Failed with Exit Code ${runner.exitCode.code}\n${runner.stdout.joinToString("\n")}")
    }
    return runner
}

fun assertTestSuccess(
    workingDir: Path? = null,
    jvmHome: Path? = null,
    junitCmd: MutableList<String>.() -> Unit
): ExecutionOutput {
    val runner = JUnitRunnerProxy(
        buildList(junitCmd) + "--disable-banner" + "--disable-ansi-colors",
        workingDir,
        overrideJvmHome = jvmHome
    ).apply { run() }

    assertTrue(runner.exitCode.isSuccess, "Test Execution Failed with Exit Code ${runner.exitCode.code}\n${runner.stdout.joinToString("\n")}")
    return runner.processOutput
}

fun TestProjects.Project.copyProjectFile(relPath: Path, outDir: Path): Path {
    val outPath = outDir.resolve(relPath)
        .also { it.parent.createDirectories() }
    getProjectResourcePath(relPath).copyTo(outPath)

    return outPath
}

@OptIn(ExperimentalPathApi::class)
fun TestProjects.Project.copyProjectDir(relPath: Path, outDir: Path): Path {
    val outPath = outDir.resolve(relPath)
        .also { it.createDirectories() }
    getProjectResourcePath(relPath).copyToRecursively(outPath, followLinks = false, overwrite = false)

    return outPath
}
