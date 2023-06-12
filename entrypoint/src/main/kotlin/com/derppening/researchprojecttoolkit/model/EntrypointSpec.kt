package com.derppening.researchprojecttoolkit.model

import com.derppening.researchprojecttoolkit.util.TestCase
import com.derppening.researchprojecttoolkit.util.TestClass
import com.derppening.researchprojecttoolkit.util.TestUnit
import java.nio.file.Path

/**
 * An entrypoint which acts as the beginning of the class reduction search.
 */
sealed class EntrypointSpec(val file: Path, val testClass: TestClass) {

    /**
     * A class input.
     */
    class ClassInput(file: Path, className: TestClass) : EntrypointSpec(file, className) {

        override fun toString(): String = "FileInput(file=$file ($testClass))"
    }

    /**
     * A method input.
     */
    class MethodInput(file: Path, className: TestClass, val methodName: String) : EntrypointSpec(file, className) {

        fun toClassInput(): ClassInput = ClassInput(file, testClass)

        override fun toString(): String = "MethodInput(file=$file (className=$testClass), methodName=$methodName)"
    }

    companion object {

        fun fromArg(
            testCase: TestUnit,
            sourceRoots: List<Path>
        ): EntrypointSpec {
            // Assume we are parsing a fully qualified classname + optional method name
            val (className, methodName) = when (testCase) {
                is TestCase -> testCase.testClass to testCase.testName
                is TestClass -> testCase to null
            }

            val matchingPath = checkNotNull(className.findFileInSourceRoots(sourceRoots)) {
                "Cannot find class with name $className in source roots $sourceRoots"
            }
            return if (methodName != null) {
                MethodInput(matchingPath, className, methodName)
            } else {
                ClassInput(matchingPath, className)
            }
        }

        fun fromArg(
            input: String,
            sourceRoots: List<Path>
        ): EntrypointSpec = fromArg(TestUnit.fromEntrypointString(input), sourceRoots)
    }
}