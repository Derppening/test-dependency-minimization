package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.reducer.AbstractBaselineReducer.Companion.findMethodFromJacocoCoverage
import com.derppening.researchprojecttoolkit.model.ExecutableDeclaration
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForConstructor
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.util.findAll
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.PrimitiveType
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.*

class CoverageBasedReducerCollections25fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Collections25f

    @Nested
    inner class IteratorUtilsTest {

        @Nested
        inner class Apply : OnTestCase("org.apache.commons.collections4.IteratorUtilsTest::apply", true) {

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
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
            }

            @Nested
            inner class SymbolLookupTests {

                @Test
                fun `Find Correct Method Decl Matching Bytecode`() {
                    val coverageData = TestProjects.Collections25f
                        .getBaselineDir(entrypointSpec)
                        .readJacocoBaselineCoverage()
                    assumeTrue(coverageData != null)

                    val packageCov = coverageData.report
                        .packages.single { it.name == "org/apache/commons/collections4/trie" }
                    val classCov = packageCov
                        .classes.single { it.name == "org/apache/commons/collections4/trie/UnmodifiableTrieTest" }
                    val methodCov = classCov.methods
                        .single { it.name == "makeFullMap" && it.desc == "()Lorg/apache/commons/collections4/Trie;" }

                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.collections4.trie.UnmodifiableTrieTest")
                    assumeTrue(cu != null)

                    val matchedMethod = findMethodFromJacocoCoverage(
                        cu.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                            .map { ExecutableDeclaration.create(it) },
                        classCov,
                        methodCov,
                        reducer.context
                    )
                    assertNotNull(matchedMethod)
                    assertIs<ExecutableDeclaration.MethodDecl>(matchedMethod)
                    assertEquals("makeFullMap", matchedMethod.name)
                    assertEquals(0, matchedMethod.node.parameters.size)
                    val returnType = assertIs<ClassOrInterfaceType>(matchedMethod.node.type)
                    assertEquals("Trie", returnType.nameWithScope)
                }

                @Test
                fun `Find Correct Constructor by Parameter Types`() {
                    val coverageData = TestProjects.Collections25f
                        .getBaselineDir(entrypointSpec)
                        .readJacocoBaselineCoverage()
                    assumeTrue(coverageData != null)

                    val packageCov = coverageData.report
                        .packages.single { it.name == "org/apache/commons/collections4/set" }
                    val classCov = packageCov
                        .classes.single { it.name == "org/apache/commons/collections4/set/AbstractSortedSetTest\$TestSortedSetSubSet" }
                    val methodCov = classCov.methods
                        .single { it.name == "<init>" && it.desc == "(Lorg/apache/commons/collections4/set/AbstractSortedSetTest;IZ)V" }

                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.collections4.set.AbstractSortedSetTest")
                    assumeTrue(cu != null)

                    val type = cu.primaryType.get()
                        .members
                        .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "TestSortedSetSubSet" }.asClassOrInterfaceDeclaration()

                    run {
                        val matchedMethod = findMethodFromJacocoCoverage(
                            type.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                                .map { ExecutableDeclaration.create(it) },
                            classCov,
                            methodCov,
                            reducer.context
                        )

                        assertNotNull(matchedMethod)
                        assertIs<ExecutableDeclaration.CtorDecl>(matchedMethod)
                        assertEquals(2, matchedMethod.node.parameters.size)
                        val param0 = assertIs<PrimitiveType>(matchedMethod.node.parameters[0].type)
                        assertEquals(PrimitiveType.intType(), param0)
                        val param1 = assertIs<PrimitiveType>(matchedMethod.node.parameters[1].type)
                        assertEquals(PrimitiveType.booleanType(), param1)
                    }

                    run {
                        val matchedMethod = findMethodFromJacocoCoverage(
                            cu.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                                .map { ExecutableDeclaration.create(it) },
                            classCov,
                            methodCov,
                            reducer.context
                        )

                        assertNotNull(matchedMethod)
                        assertIs<ExecutableDeclaration.CtorDecl>(matchedMethod)
                        assertEquals(2, matchedMethod.node.parameters.size)
                        val param0 = assertIs<PrimitiveType>(matchedMethod.node.parameters[0].type)
                        assertEquals(PrimitiveType.intType(), param0)
                        val param1 = assertIs<PrimitiveType>(matchedMethod.node.parameters[1].type)
                        assertEquals(PrimitiveType.booleanType(), param1)
                    }
                }
            }

            @Nested
            inner class ReductionRegressionTests {

                @Test
                @Ignore
                fun `Regression-01`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.collections4.bidimap.DualTreeBidiMap2Test")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("makeObject").single()

                    assertTrue(method.inclusionReasonsData.isEmpty())
                    assertTrue(method.isUnusedForRemovalData)
                }

                @Test
                @Ignore("Obsolete")
                fun `Regression-02`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.collections4.collection.AbstractCollectionDecorator")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("retainAll", "Collection<?>").single()

                    assertTrue(method.isUnusedForDummyData)
                    assertFalse(method.isUnusedForRemovalData)
                }

                @Test
                @Ignore
                fun `Regression-03`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.collections4.iterators.AbstractEmptyIterator")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("hasNext").single()

                    assertTrue(method.isUnusedForDummyData)
                    assertFalse(method.isUnusedForRemovalData)
                }

                @Test
                @Ignore
                fun `Regression-04`() {
                    run {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("org.apache.commons.collections4.MapIterator")
                        assumeTrue(cu != null)

                        val method = cu.primaryType.get()
                            .getMethodsBySignature("setValue", "V").single()

                        assertFalse(method.isUnusedForRemovalData)
                    }

                    run {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("org.apache.commons.collections4.iterators.AbstractEmptyMapIterator")
                        assumeTrue(cu != null)

                        val method = cu.primaryType.get()
                            .getMethodsBySignature("setValue", "V").single()

                        assertTrue(method.isUnusedForDummyData)
                        assertFalse(method.isUnusedForRemovalData)
                    }
                }

                @Test
                @Ignore
                fun `Regression-05`() {
                    run {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("org.apache.commons.collections4.OrderedMapIterator")
                        assumeTrue(cu != null)

                        val method = cu.primaryType.get()
                            .getMethodsBySignature("previous").single()

//                    assertTrue(method.isUnusedForDummyData)
                        assertFalse(method.isUnusedForRemovalData)
                    }

                    run {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("org.apache.commons.collections4.trie.AbstractPatriciaTrie")
                        assumeTrue(cu != null)

                        val method = cu.primaryType.get()
                            .members
                            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "TrieMapIterator" }
                            .asClassOrInterfaceDeclaration()
                            .getMethodsBySignature("previous").single()

                        assertFalse(method.isUnusedForRemovalData)
                    }
                }

                /**
                 * ```
                 * src/main/java/org/apache/commons/collections4/list/CursorableLinkedList.java:53: error: CursorableLinkedList is not abstract and does not override abstract method retainAll(Collection<?>) in List
                 * public class CursorableLinkedList<E> extends AbstractLinkedList<E> implements Serializable {
                 *        ^
                 * ```
                 */
                @Test
                @Ignore
                fun `Regression-06`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.collections4.list.AbstractLinkedList")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("retainAll", "Collection<?>").single()

                    assertNotEquals(
                        NodeTransformDecision.REMOVE,
                        decideForMethod(reducer.context, method, enableAssertions, noCache = true)
                    )
                    assertFalse(method.isUnusedForRemovalData)
                }

                /**
                 * ```
                 * src/main/java/org/apache/commons/collections4/bidimap/TreeBidiMap.java:519: error: TreeBidiMap.Inverse is not abstract and does not override abstract method put(V,K) in BidiMap
                 *     class Inverse implements OrderedBidiMap<V, K> {
                 *     ^
                 *   where V,K are type-variables:
                 *     V extends Comparable<V> declared in class TreeBidiMap
                 *     K extends Comparable<K> declared in class TreeBidiMap
                 * ```
                 */
                @Test
                @Ignore("See `JavaParserTest.Solve Ancestors - Unexpected Type Parameters`.")
                fun `Regression-07`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.collections4.bidimap.TreeBidiMap")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .members
                        .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "Inverse" }
                        .asClassOrInterfaceDeclaration()
                        .getMethodsBySignature("put", "V", "K").single()

                    val methodDecision = decideForMethod(reducer.context, method, enableAssertions, noCache = true)

                    assertNotEquals(NodeTransformDecision.REMOVE, methodDecision)
                    assertFalse(method.isUnusedForRemovalData)
                }

                /**
                 * ```
                 * src/main/java/org/apache/commons/collections4/set/AbstractSortedSetDecorator.java:31: error: constructor AbstractSetDecorator in class AbstractSetDecorator<E#2> cannot be applied to given types;
                 * public abstract class AbstractSortedSetDecorator<E> extends AbstractSetDecorator<E> implements SortedSet<E> {
                 *                 ^
                 *   required: Set<E#1>
                 *   found:    no arguments
                 *   reason: actual and formal argument lists differ in length
                 *   where E#1,E#2 are type-variables:
                 *     E#1 extends Object declared in class AbstractSortedSetDecorator
                 *     E#2 extends Object declared in class AbstractSetDecorator
                 * src/main/java/org/apache/commons/collections4/bag/AbstractBagDecorator.java:30: error: constructor AbstractCollectionDecorator in class AbstractCollectionDecorator<E#2> cannot be applied to given types;
                 * public abstract class AbstractBagDecorator<E> extends AbstractCollectionDecorator<E> implements Bag<E> {
                 *                 ^
                 *   required: Collection<E#1>
                 *   found:    no arguments
                 *   reason: actual and formal argument lists differ in length
                 *   where E#1,E#2 are type-variables:
                 *     E#1 extends Object declared in class AbstractBagDecorator
                 *     E#2 extends Object declared in class AbstractCollectionDecorator
                 * src/main/java/org/apache/commons/collections4/queue/AbstractQueueDecorator.java:37: error: constructor AbstractCollectionDecorator in class AbstractCollectionDecorator<E#2> cannot be applied to given types;
                 * public abstract class AbstractQueueDecorator<E> extends AbstractCollectionDecorator<E> implements Queue<E> {
                 *                 ^
                 *   required: Collection<E#1>
                 *   found:    no arguments
                 *   reason: actual and formal argument lists differ in length
                 *   where E#1,E#2 are type-variables:
                 *     E#1 extends Object declared in class AbstractQueueDecorator
                 *     E#2 extends Object declared in class AbstractCollectionDecorator
                 * ```
                 */
                @Test
                fun `Regression-08`() {
                    val superclassCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.collections4.collection.AbstractCollectionDecorator")
                    assumeTrue(superclassCU != null)

                    val superclassDefaultCtor = superclassCU.primaryType.get()
                        .defaultConstructor.get()
                    val superclassAltCtor = superclassCU.primaryType.get()
                        .getConstructorByParameterTypes("Collection<E>").get()

                    val superclassDefaultCtorDecision = decideForConstructor(
                        reducer.context,
                        superclassDefaultCtor,
                        enableAssertions = enableAssertions,
                        noCache = true
                    )
                    val superclassAltCtorDecision = decideForConstructor(
                        reducer.context,
                        superclassAltCtor,
                        enableAssertions = enableAssertions,
                        noCache = true
                    )
                    val superclassHasAnyCtorDecl = superclassCU.primaryType.get()
                        .constructors
                        .any {
                            decideForConstructor(
                                reducer.context,
                                it,
                                enableAssertions = enableAssertions,
                                noCache = true
                            ) != NodeTransformDecision.REMOVE
                        }

                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.collections4.set.AbstractSetDecorator")
                    assumeTrue(cu != null)

                    val defaultCtor = cu.primaryType.get()
                        .defaultConstructor.get()
                    val altCtor = cu.primaryType.get()
                        .getConstructorByParameterTypes("Set<E>").get()

                    val defaultCtorDecision = decideForConstructor(
                        reducer.context,
                        defaultCtor,
                        enableAssertions = enableAssertions,
                        noCache = true
                    )
                    val altCtorDecision = decideForConstructor(
                        reducer.context,
                        altCtor,
                        enableAssertions = enableAssertions,
                        noCache = true
                    )

                    if (superclassDefaultCtorDecision == NodeTransformDecision.REMOVE && superclassAltCtorDecision == NodeTransformDecision.REMOVE && !superclassHasAnyCtorDecl) {
                        assertEquals(NodeTransformDecision.REMOVE, altCtorDecision)
                    } else if (superclassDefaultCtorDecision == NodeTransformDecision.REMOVE && superclassHasAnyCtorDecl) {
                        assertEquals(NodeTransformDecision.REMOVE, defaultCtorDecision)
                        assertNotEquals(NodeTransformDecision.REMOVE, altCtorDecision)
                    } else if (superclassAltCtorDecision == NodeTransformDecision.REMOVE) {
                        assertEquals(NodeTransformDecision.REMOVE, altCtorDecision)
                        // Assertion on defaultCtorDecision not necessary, since it is implied that the superclass will keep its ctor decl
                    }
                }

                /**
                 * ```
                 * [javac] src/main/java/org/apache/commons/collections4/iterators/AbstractIteratorDecorator.java:39: error: constructor AbstractUntypedIteratorDecorator in class AbstractUntypedIteratorDecorator<I,O> cannot be applied to given types;
                 * [javac]         super(iterator);
                 * [javac]         ^
                 * [javac]   required: no arguments
                 * [javac]   found:    Iterator<E>
                 * [javac]   reason: actual and formal argument lists differ in length
                 * [javac]   where E,I,O are type-variables:
                 * [javac]     E extends Object declared in class AbstractIteratorDecorator
                 * [javac]     I extends Object declared in class AbstractUntypedIteratorDecorator
                 * [javac]     O extends Object declared in class AbstractUntypedIteratorDecorator
                 * ```
                 */
                @Test
                fun `Regression-09`() {
                    val superclassCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.collections4.iterators.AbstractUntypedIteratorDecorator")
                    assumeTrue(superclassCU != null)

                    val superclassCtor = superclassCU.primaryType.get()
                        .getConstructorByParameterTypes("Iterator<I>").get()

                    val superclassCtorDecision = decideForConstructor(
                        reducer.context,
                        superclassCtor,
                        enableAssertions = enableAssertions,
                        noCache = true
                    )

                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.collections4.iterators.AbstractIteratorDecorator")
                    assumeTrue(cu != null)

                    val ctor = cu.primaryType.get()
                        .getConstructorByParameterTypes("Iterator<E>").get()

                    val ctorDecision = decideForConstructor(
                        reducer.context,
                        ctor,
                        enableAssertions = enableAssertions,
                        noCache = true
                    )

                    if (superclassCtorDecision == NodeTransformDecision.REMOVE) {
                        assertEquals(NodeTransformDecision.REMOVE, ctorDecision)
                    }
                }
            }
        }
    }
}