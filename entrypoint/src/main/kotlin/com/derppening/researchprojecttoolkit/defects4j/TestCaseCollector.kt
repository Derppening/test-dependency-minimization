package com.derppening.researchprojecttoolkit.defects4j

import com.derppening.researchprojecttoolkit.tool.facade.FuzzySymbolSolver
import com.derppening.researchprojecttoolkit.tool.facade.typesolver.PartitionedTypeSolver
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.AccessSpecifier
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedAnnotationDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import java.nio.file.Path

/**
 * Collects a list of all test cases in a single source file.
 *
 * @param file The source file to collect test cases from.
 * @param sourceRoots The source roots of the project.
 * @param classpath The classpath of the project.
 */
class TestCaseCollector(file: Path, sourceRoots: Collection<Path>, classpath: String, cachedSymbolSolver: FuzzySymbolSolver?) {

    private val typeSolver = cachedSymbolSolver ?: FuzzySymbolSolver(
        PartitionedTypeSolver(
            getTypeSolversForSourceRoot(sourceRoots),
            getTypeSolversForClasspath(classpath),
            true
        )
    )

    private val parserConfiguration = createParserConfiguration(typeSolver) {
        languageLevel = ParserConfiguration.LanguageLevel.RAW
    }
    private val parser = JavaParser(parserConfiguration)
    val ast = parser.parse(file)
        .also {
            check(it.isSuccessful) { "Unable to parse $file: ${it.problems}" }
        }
        .result.get()

    init {
        check(sourceRoots.any { file.startsWith(it) })
    }

    private fun collectNames(resolvedTypeDecl: ResolvedReferenceTypeDeclaration): List<ResolvedMethodDeclaration> {
        val junitTestClasses = if (isJUnitTestClass(resolvedTypeDecl, typeSolver)) {
            resolvedTypeDecl.allMethods.filter { isJUnitTestCase(it.declaration, typeSolver) }
        } else {
            emptyList()
        }
        val junit3TestClasses = if (isJUnit3TestClass(resolvedTypeDecl)) {
            resolvedTypeDecl.allMethods.filter { isJUnit3TestCase(it.declaration) }
        } else {
            emptyList()
        }

        return (junitTestClasses + junit3TestClasses).map { it.declaration }.distinct()
    }

    /**
     * Collect methods which are test cases in this file as a collection of [TestCase].
     */
    fun collect(): List<TestCase> {
        val resolvedTypeDecl = ast.primaryType
            .map { it.resolve() }
            .get()
        return collectNames(resolvedTypeDecl).map { TestCase(resolvedTypeDecl, it) }
    }

    companion object {

        private fun isJUnitTestCaseImpl(methodDecl: ResolvedMethodDeclaration, symbolSolver: JavaSymbolSolver): Boolean {
            return methodDecl.toTypedAstOrNull<MethodDeclaration>(null)
                ?.let { ast ->
                    ast.annotations.any {
                        symbolSolver.resolveDeclaration<ResolvedAnnotationDeclaration>(it).qualifiedName in JUNIT_TEST_ANNO_QNAME
                    }
                }
                ?: false
        }

        private fun isJUnit3TestCaseImpl(methodDecl: ResolvedMethodDeclaration): Boolean {
            return methodDecl.toAst().isPresent &&
                    methodDecl.accessSpecifier() == AccessSpecifier.PUBLIC &&
                    methodDecl.parameters.isEmpty() &&
                    methodDecl.returnType.isVoid &&
                    methodDecl.name !in JUNIT3_BEFOREAFTER_METHODS
        }

        /**
         * @return `true` if [typeDecl] is a JUnit test class.
         */
        fun isJUnitTestClass(typeDecl: ResolvedReferenceTypeDeclaration, symbolSolver: JavaSymbolSolver): Boolean =
            !typeDecl.isAnnotation && typeDecl.allMethods.any { isJUnitTestCaseImpl(it.declaration, symbolSolver) }

        /**
         * @return `true` if [typeDecl] is a JUnit 3 test class.
         */
        fun isJUnit3TestClass(typeDecl: ResolvedReferenceTypeDeclaration): Boolean {
            return typeDecl.allAncestors
                .any { it.qualifiedName == JUNIT3_TESTCASE_QNAME }
        }

        /**
         * @return `true` if [methodDecl] is a JUnit test method.
         */
        fun isJUnitTestCase(methodDecl: ResolvedMethodDeclaration, symbolSolver: JavaSymbolSolver): Boolean {
            if (!isJUnitTestClass(methodDecl.declaringType(), symbolSolver)) {
                return false
            }

            return isJUnitTestCaseImpl(methodDecl, symbolSolver)
        }

        /**
         * @return `true` if [methodDecl] is a JUnit 3 test method.
         */
        fun isJUnit3TestCase(methodDecl: ResolvedMethodDeclaration): Boolean {
            if (!isJUnit3TestClass(methodDecl.declaringType())) {
                return false
            }

            return isJUnit3TestCaseImpl(methodDecl)
        }

        /**
         * Fully-qualified names of annotations which JUnit observes as a test method.
         */
        private val JUNIT_TEST_ANNO_QNAME = listOf(
            "org.junit.Test",
            "org.junit.jupiter.api.Test",
            "org.junit.jupiter.api.RepeatedTest",
            "org.junit.jupiter.params.ParameterizedTest",
            "org.junit.jupiter.api.TestFactory"
        )

        /**
         * Fully-qualified name of the JUnit 3 `TestCase` class.
         */
        private const val JUNIT3_TESTCASE_QNAME = "junit.framework.TestCase"

        /**
         * Name of methods which JUnit 3 observes as pre-/post-test case execution action.
         */
        private val JUNIT3_BEFOREAFTER_METHODS = listOf("setUp", "tearDown")
    }
}