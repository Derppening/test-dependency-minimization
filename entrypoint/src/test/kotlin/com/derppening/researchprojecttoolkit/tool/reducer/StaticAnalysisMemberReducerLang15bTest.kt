package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.JAVA_8_HOME
import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertCompileSuccess
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.tryTestNoAssertion
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse

class StaticAnalysisMemberReducerLang15bTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Lang15b

    @Nested
    inner class TypeUtilsTest {

        @Nested
        inner class TestIsAssignable : OnTestCase("org.apache.commons.lang3.reflect.TypeUtilsTest::testIsAssignable", true) {

            @Test
            fun testExecution() {
                val sourceRootMapping = mutableMapOf<Path, Path>()
                reducer.getTransformedCompilationUnits(
                    outputDir!!.path,
                    project.getProjectResourcePath(""),
                    sourceRootMapping = sourceRootMapping
                )
                    .parallelStream()
                    .forEach { cu -> cu.storage.ifPresent { it.save() } }

                val sourceRoots = sourceRootMapping.values
                val compiler = CompilerProxy(
                    sourceRoots,
                    project.testCpJars.joinToString(":"),
                    emptyList(),
                    JAVA_8_HOME
                )
                assertCompileSuccess(compiler)

                // Failures (1):
                //  JUnit Vintage:TypeUtilsTest:testIsAssignable
                //    MethodSource [className = 'org.apache.commons.lang3.reflect.TypeUtilsTest', methodName = 'testIsAssignable', methodParameterTypes = '']
                //    => java.lang.NoSuchMethodException: org.apache.commons.lang3.reflect.TypeUtilsTest.dummyMethod(java.util.List, java.util.List, java.util.List, java.util.List, java.util.List, java.util.List, java.util.List, [Ljava.util.List;, [Ljava.util.List;, [Ljava.util.List;, [Ljava.util.List;, [Ljava.util.List;, [Ljava.util.List;, [Ljava.util.List;)
                //       java.lang.Class.getMethod(Class.java:1786)
                //       org.apache.commons.lang3.reflect.TypeUtilsTest.testIsAssignable(TypeUtilsTest.java:89)
                //       sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
                //       sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
                //       sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
                //       java.lang.reflect.Method.invoke(Method.java:498)
                //       org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:59)
                //       org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
                //       org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:56)
                //       org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)
                //       [...]
                val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_8_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
                assertFalse(junitRunner.exitCode.isSuccess)
            }
        }
    }
}