package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.isTypeReachable
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotEquals

class StaticAnalysisMemberReducerCollections27fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Collections27f

    @Nested
    inner class MultiValueMapTest {

        @Nested
        inner class TestUnsafeDeSerialization {

            private val entrypoint = "org.apache.commons.collections4.map.MultiValueMapTest::testUnsafeDeSerialization"

            @Nested
            inner class WithAssertions : OnTestCase(entrypoint, true) {

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
                        listOf("-Xmaxerrs", "$JAVAC_MAXERRS"),
                        JAVA_17_HOME
                    )

                    assertCompileSuccess(compiler)

                    assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                        add("-cp")
                        add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:build/classes")

                        add("-m")
                        add(testCase.toJUnitMethod())
                    }
                }

                /**
                * ```
                * /tmp/415803993207314119/src/main/java/org/apache/commons/collections4/bidimap/DualHashBidiMap.java:38: error: DualHashBidiMap is not abstract and does not override abstract method createBidiMap(Map<V,K>,Map<K,V>,BidiMap<K,V>) in AbstractDualBidiMap
                * public class DualHashBidiMap<K, V> extends AbstractDualBidiMap<K, V> implements Serializable {
                *        ^
                *   where V,K are type-variables:
                *     V extends Object declared in class DualHashBidiMap
                *     K extends Object declared in class DualHashBidiMap
                * ```
                */
                @Test
                fun `Regression-00`() {
                    val srcCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.collections4.bidimap.AbstractDualBidiMap")
                    assumeTrue(srcCU != null)

                    val srcType = srcCU.primaryType.get()
                    val srcMethod = srcType
                        .getMethodsBySignature("createBidiMap", "Map<V,K>", "Map<K,V>", "BidiMap<K,V>").single()

                    val srcDecision = decideForMethod(
                        reducer.context,
                        srcMethod,
                        enableAssertions,
                        noCache = true
                    )
                    assumeTrue(srcDecision != NodeTransformDecision.REMOVE)

                    val tgtCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.collections4.bidimap.DualHashBidiMap")
                    assumeTrue(tgtCU != null)

                    val tgtType = tgtCU.primaryType.get()
                    val tgtMethod = tgtType
                        .getMethodsBySignature("createBidiMap", "Map<V,K>", "Map<K,V>", "BidiMap<K,V>").single()

                    val tgtDecision = decideForMethod(
                        reducer.context,
                        tgtMethod,
                        enableAssertions,
                        noCache = true
                    )
                    assertNotEquals(NodeTransformDecision.REMOVE, tgtDecision)
                }

/**
* ```
* src/main/java/org/apache/commons/collections4/iterators/ArrayListIterator.java:39: error: ArrayListIterator is not abstract and does not override abstract method remove() in ListIterator
* public class ArrayListIterator<E> extends ArrayIterator<E> implements ResettableListIterator<E> {
*        ^
* ```
*/
                @Test
                fun `Regression-01`() {
                    val srcCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.collections4.iterators.ArrayListIterator")
                    assumeTrue(srcCU != null)

                    val srcType = srcCU.primaryType.get()

                    val srcDecision = isTypeReachable(
                        reducer.context,
                        srcType,
                        enableAssertions,
                        noCache = true
                    )
                    assumeTrue(srcDecision)

                    val tgtCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.collections4.iterators.ArrayIterator")
                    assumeTrue(tgtCU != null)

                    val tgtType = tgtCU.primaryType.get()
                    val tgtMethod = tgtType
                        .getMethodsBySignature("remove").single()

                    val tgtDecision = decideForMethod(
                        reducer.context,
                        tgtMethod,
                        enableAssertions,
                        noCache = true
                    )
                    assertNotEquals(NodeTransformDecision.REMOVE, tgtDecision)
                }

                /**
                * ```
                *   JUnit Vintage:MultiValueMapTest:testUnsafeDeSerialization
                *     MethodSource [className = 'org.apache.commons.collections4.map.MultiValueMapTest', methodName = 'testUnsafeDeSerialization', methodParameterTypes = '']
                *     => java.lang.NullPointerException: Cannot invoke "java.util.Map.size()" because the return value of "org.apache.commons.collections4.map.AbstractMapDecorator.decorated()" is null
                *        org.apache.commons.collections4.map.AbstractMapDecorator.size(AbstractMapDecorator.java:120)
                *        java.base/java.util.AbstractMap.equals(AbstractMap.java:481)
                *        org.apache.commons.collections4.map.AbstractMapDecorator.equals(AbstractMapDecorator.java:132)
                *        junit.framework.Assert.assertEquals(Assert.java:75)
                *        junit.framework.Assert.assertEquals(Assert.java:86)
                *        junit.framework.TestCase.assertEquals(TestCase.java:246)
                *        org.apache.commons.collections4.map.MultiValueMapTest.testUnsafeDeSerialization(MultiValueMapTest.java:46)
                * ```
                */
                @Test
                fun `Regression-02`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.collections4.map.MultiValueMap")
                    assumeTrue(cu != null)

                    val type = cu.primaryType.get()

                    val typeReachable = isTypeReachable(
                        reducer.context,
                        type,
                        enableAssertions,
                        noCache = true
                    )
                    assumeTrue(typeReachable)

                    val method = type
                        .getMethodsBySignature("readObject", "ObjectInputStream").single()
                    val tgtDecision = decideForMethod(
                        reducer.context,
                        method,
                        enableAssertions,
                        noCache = true
                    )
                    assertNotEquals(NodeTransformDecision.REMOVE, tgtDecision)
                }
            }
        }
    }
}