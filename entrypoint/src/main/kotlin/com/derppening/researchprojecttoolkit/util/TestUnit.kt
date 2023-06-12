package com.derppening.researchprojecttoolkit.util

import com.derppening.researchprojecttoolkit.model.EntrypointSpec
import com.derppening.researchprojecttoolkit.model.ReferenceTypeLikeDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile

/**
 * A tagging interface representing a unit of test which JUnit may execute.
 */
sealed interface TestUnit : Comparable<TestUnit> {

    /**
     * Returns the string representation of this test unit.
     */
    override fun toString(): String

    override fun compareTo(other: TestUnit): Int = toString().compareTo(other.toString())

    companion object {

        /**
         * Creates a [TestUnit] from an entrypoint string.
         */
        fun fromEntrypointString(str: String): TestUnit {
            return if (str.contains("::")) {
                TestCase.fromD4JQualifiedName(str)
            } else {
                TestClass(str)
            }
        }
    }
}

/**
 * A test class.
 *
 * @property className Qualified name of the class.
 */
@JvmInline
value class TestClass(val className: String) : TestUnit {

    constructor(typeDecl: TypeDeclaration<*>) : this(typeDecl.fullyQualifiedName.get())
    constructor(resolvedTypeDecl: ResolvedReferenceTypeDeclaration) : this(resolvedTypeDecl.qualifiedName)

    /**
     * The path of this class relative to a source root.
     */
    val pathRelativeToSourceRoot: Path
        get() = Path(className.replace('.', File.separatorChar) + ".java")

    /**
     * Finds the source root which contains this file.
     */
    fun findSourceRoot(sourceRoots: List<Path>): Path? =
        sourceRoots.singleOrNull { it.resolve(pathRelativeToSourceRoot).isRegularFile() }

    /**
     * Finds the file containing this class in one of the [sourceRoots].
     *
     * @return The path to the test class file in one of the source roots. If zero or more than two source roots
     * containing this class is found, returns `null`.
     */
    fun findFileInSourceRoots(sourceRoots: List<Path>): Path? =
        findSourceRoot(sourceRoots)?.resolve(pathRelativeToSourceRoot)

    override fun toString(): String = className
}

/**
 * A single test case.
 *
 * @property testClass The class containing the test.
 * @property testName The name of the test case.
 */
data class TestCase(
    val testClass: TestClass,
    val testName: String
) : TestUnit {

    constructor(qname: String) : this(TestClass(qname.takeWhile { it != ':' }), qname.takeLastWhile { it != ':' })
    constructor(methodDecl: MethodDeclaration) :
            this(TestClass((methodDecl.containingType as ReferenceTypeLikeDeclaration.TypeDecl).node), methodDecl.nameAsString)
    constructor(resolvedTypeDecl: ResolvedReferenceTypeDeclaration, resolvedMethodDecl: ResolvedMethodDeclaration) :
            this(TestClass(resolvedTypeDecl.qualifiedName), resolvedMethodDecl.name)
    constructor(resolvedMethodDecl: ResolvedMethodDeclaration) : this(TestClass(resolvedMethodDecl.declaringType().qualifiedName), resolvedMethodDecl.name)

    /**
     * Converts this test case to a string representation accepted by JUnit Runner.
     */
    fun toJUnitMethod(): String = "$testClass#$testName"

    /**
     * Converts this test case to a string representation accepted by Defects4J.
     */
    fun toD4JMethod(): String = "$testClass::$testName"

    override fun toString(): String = toD4JMethod()
    override fun hashCode(): Int = toString().hashCode()
    override fun equals(other: Any?): Boolean = (other as? TestCase)?.let { it.toString() == toString() } == true

    companion object {

        /**
         * Creates an instance of [TestCase] from an [EntrypointSpec].
         *
         * The [EntrypointSpec] must be a [EntrypointSpec.MethodInput] and its [EntrypointSpec.testClass] must not be
         * null.
         */
        fun fromEntrypointSpec(entrypointSpec: EntrypointSpec): TestCase {
            check(entrypointSpec is EntrypointSpec.MethodInput)
            checkNotNull(entrypointSpec.testClass)

            return TestCase(entrypointSpec.testClass, entrypointSpec.methodName)
        }

        /**
         * Creates an instance of [TestCase] from a qualified name of a method returned by Defects4J.
         *
         * Defects4J emits its methods in the form of `className::testName`.
         */
        fun fromD4JQualifiedName(qname: String): TestCase {
            val components = qname.split("::").also { check(it.size == 2) }

            return TestCase(TestClass(components[0]), components[1])
        }

        /**
         * Creates an instance of [TestCase] from a qualified name of a method as represented by Java.
         *
         * Java methods are in the form of `className.testName`.
         */
        fun fromJavaQualifiedName(qname: String): TestCase {
            val components = qname.split(".").also { check(it.size >= 2) }

            return TestCase(TestClass(components.drop(1).joinToString(".")), components.last())
        }

        /**
         * Creates an instance of [TestCase] from a qualified signature of a method as represented by Java.
         */
        fun fromQualifiedSignature(qsig: String): TestCase =
            fromJavaQualifiedName(qsig.takeWhile { it != '.' && it != '<' })
    }
}