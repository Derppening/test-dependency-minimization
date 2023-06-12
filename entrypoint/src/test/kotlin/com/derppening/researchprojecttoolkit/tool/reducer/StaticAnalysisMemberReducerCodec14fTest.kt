package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.getFieldVariableDecl
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.*

class StaticAnalysisMemberReducerCodec14fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Codec14f

    @Nested
    inner class PhoneticEngineRegressionTest {

        @Nested
        inner class TestCompatibilityWithOriginalVersion : OnTestCase("org.apache.commons.codec.language.bm.PhoneticEngineRegressionTest::testCompatibilityWithOriginalVersion", true) {

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

                // Copy `src/main/resources/org/apache/commons/codec/language/bm/` into directory
                val resDir = Path("src/main/resources")
                project.copyProjectDir(resDir.resolve("org/apache/commons/codec/language/bm"), outputDir!!.path)

                assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:${outputDir!!.path.resolve(resDir)}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
            }

            /**
             * ```
             * junit.framework.AssertionFailedError: This method should not be reached! Signature: isMatch(CharSequence)
             *   at org.apache.commons.codec.language.bm.Rule$3.isMatch(Rule.java:457)
             *   at org.apache.commons.codec.language.bm.Rule.patternAndContextMatches(Rule.java:638)
             *   at org.apache.commons.codec.language.bm.PhoneticEngine$RulesApplication.invoke(PhoneticEngine.java:212)
             *   at org.apache.commons.codec.language.bm.PhoneticEngine.encode(PhoneticEngine.java:441)
             *   at org.apache.commons.codec.language.bm.PhoneticEngine.encode(PhoneticEngine.java:361)
             *   at org.apache.commons.codec.language.bm.PhoneticEngineRegressionTest.encode(PhoneticEngineRegressionTest.java:97)
             *   at org.apache.commons.codec.language.bm.PhoneticEngineRegressionTest.testCompatibilityWithOriginalVersion(PhoneticEngineRegressionTest.java:55)
             * ```
             */
            @Test
            fun `Regression-00`() {
                // org.apache.commons.codec.language.bm.PhoneticEngineRegressionTest.testCompatibilityWithOriginalVersion()
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.language.bm.PhoneticEngineRegressionTest"
                        }

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("testCompatibilityWithOriginalVersion").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.Entrypoint>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.codec.language.bm.PhoneticEngineRegressionTest.encode(Map<String, String>, boolean, String)
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.language.bm.PhoneticEngineRegressionTest"
                        }

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("encode", "Map<String,String>", "boolean", "String").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.codec.language.bm.PhoneticEngine.encode(String)
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.language.bm.PhoneticEngine"
                        }

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("encode", "String").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.codec.language.bm.PhoneticEngine.encode(String, Languages.LanguageSet)
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.language.bm.PhoneticEngine"
                        }

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("encode", "String", "Languages.LanguageSet").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.codec.language.bm.PhoneticEngine.RulesApplication.invoke()
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.language.bm.PhoneticEngine"
                        }

                    val ctor = cu.primaryType.get()
                        .members
                        .single {
                            it.isClassOrInterfaceDeclaration && it.asClassOrInterfaceDeclaration().nameAsString == "RulesApplication"
                        }
                        .asClassOrInterfaceDeclaration()
                        .getMethodsBySignature("invoke").single()

                    assertTrue(ctor.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        ctor.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(ctor.isUnusedForRemovalData)
                    assertFalse(ctor.isUnusedForDummyData)
                }

                // org.apache.commons.codec.language.bm.Rule.patternAndContextMatches(CharSequence, int)
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.language.bm.Rule"
                        }

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("patternAndContextMatches", "CharSequence", "int").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.codec.language.bm.Rule.RPattern.isMatch(CharSequence)
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.language.bm.Rule"
                        }

                    val method = cu.primaryType.get()
                        .members
                        .single {
                            it.isClassOrInterfaceDeclaration && it.asClassOrInterfaceDeclaration().nameAsString == "RPattern"
                        }
                        .asClassOrInterfaceDeclaration()
                        .getMethodsBySignature("isMatch", "CharSequence").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.codec.language.bm.Rule$3.isMatch(CharSequence)
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.language.bm.Rule"
                        }

                    val type = cu.primaryType.get()
                    val anonType = type
                        .getMethodsBySignature("pattern", "String").single()
                        .body.get()
                        .statements[4].asIfStmt()
                        .thenStmt.asBlockStmt()
                        .statements[0].asIfStmt()
                        .thenStmt.asBlockStmt()
                        .statements[0].asIfStmt()
                        .thenStmt.asBlockStmt()
                        .statements[0].asReturnStmt()
                        .expression.get().asObjectCreationExpr()
                        .anonymousClassBody.get()
                    val method = anonType
                        .single {
                            it.isMethodDeclaration &&
                                    it.asMethodDeclaration().nameAsString == "isMatch" &&
                                    it.asMethodDeclaration().parameters.size == 1 &&
                                    it.asMethodDeclaration().parameters[0].typeAsString == "CharSequence"
                        }
                        .asMethodDeclaration()
                    val methodDecision = decideForMethod(
                        reducer.context,
                        method,
                        enableAssertions,
                        noCache = true
                    )
                    assertEquals(NodeTransformDecision.NO_OP, methodDecision)
                }
            }

            /**
             * ```
             *     => java.lang.AssertionError: This method should not be reached! Signature: getPhonemes()
             *        org.apache.commons.codec.language.bm.Rule$PhonemeList.getPhonemes(Rule.java:156)
             *        org.apache.commons.codec.language.bm.Rule$PhonemeList.getPhonemes(Rule.java:146)
             *        org.apache.commons.codec.language.bm.PhoneticEngine$PhonemeBuilder.apply(PhoneticEngine.java:107)
             *        org.apache.commons.codec.language.bm.PhoneticEngine$RulesApplication.invoke(PhoneticEngine.java:213)
             *        org.apache.commons.codec.language.bm.PhoneticEngine.encode(PhoneticEngine.java:441)
             *        org.apache.commons.codec.language.bm.PhoneticEngine.encode(PhoneticEngine.java:361)
             *        org.apache.commons.codec.language.bm.PhoneticEngineRegressionTest.encode(PhoneticEngineRegressionTest.java:82)
             *        org.apache.commons.codec.language.bm.PhoneticEngineRegressionTest.testCompatibilityWithOriginalVersion(PhoneticEngineRegressionTest.java:40)
             *        java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
             *        java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
             *        [...]
             * ```
             */
            @Test
            fun `Regression-01`() {
                val srcCU = reducer.context
                    .getCUByPrimaryTypeName("org.apache.commons.codec.language.bm.PhoneticEngine")
                assumeTrue(srcCU != null)

                val srcType = srcCU.primaryType.get()
                    .members
                    .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "PhonemeBuilder" }
                    .asClassOrInterfaceDeclaration()
                val srcMethod = srcType.getMethodsBySignature("apply", "Rule.PhonemeExpr", "int").single()
                assumeTrue {
                    decideForMethod(
                        reducer.context,
                        srcMethod,
                        enableAssertions,
                        noCache = true
                    ) == NodeTransformDecision.NO_OP
                }

                val tgtCU = reducer.context
                    .getCUByPrimaryTypeName("org.apache.commons.codec.language.bm.Rule")
                assumeTrue(tgtCU != null)

                val tgtType = tgtCU.primaryType.get()
                    .members
                    .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "PhonemeList" }
                    .asClassOrInterfaceDeclaration()
                val tgtMethod = tgtType.getMethodsBySignature("getPhonemes").single()

                val decision = decideForMethod(
                    reducer.context,
                    tgtMethod,
                    enableAssertions,
                    noCache = true
                )
                assertEquals(NodeTransformDecision.NO_OP, decision)
            }

            /**
             * ```
             *     => java.lang.AssertionError: This method should not be reached! Signature: compare(Phoneme, Phoneme)
             *        org.apache.commons.codec.language.bm.Rule$Phoneme$1.compare(Rule.java:88)
             *        org.apache.commons.codec.language.bm.Rule$Phoneme$1.compare(Rule.java:84)
             *        java.base/java.util.TreeMap.compare(TreeMap.java:1570)
             *        java.base/java.util.TreeMap.addEntryToEmptyMap(TreeMap.java:776)
             *        java.base/java.util.TreeMap.put(TreeMap.java:785)
             *        java.base/java.util.TreeMap.put(TreeMap.java:534)
             *        org.apache.commons.codec.language.bm.PhoneticEngine.applyFinalRules(PhoneticEngine.java:345)
             *        org.apache.commons.codec.language.bm.PhoneticEngine.encode(PhoneticEngine.java:446)
             *        org.apache.commons.codec.language.bm.PhoneticEngine.encode(PhoneticEngine.java:361)
             *        org.apache.commons.codec.language.bm.PhoneticEngineRegressionTest.encode(PhoneticEngineRegressionTest.java:82)
             *        [...]
             * ```
             */
            @Test
            fun `Regression-02`() {
                val tgtCU = reducer.context
                    .getCUByPrimaryTypeName("org.apache.commons.codec.language.bm.Rule")
                assumeTrue(tgtCU != null)

                val tgtType = tgtCU.primaryType.get()
                    .members
                    .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "Phoneme" }
                    .asClassOrInterfaceDeclaration()
                val tgtMethod = tgtType
                    .getFieldVariableDecl("COMPARATOR")
                    .initializer.get().asObjectCreationExpr()
                    .anonymousClassBody.get()
                    .single { it is MethodDeclaration && it.nameAsString == "compare" }
                    .asMethodDeclaration()

                val decision = decideForMethod(
                    reducer.context,
                    tgtMethod,
                    enableAssertions,
                    noCache = true
                )
                assertEquals(NodeTransformDecision.NO_OP, decision)
            }

            /**
             * ```
             * src/main/java/org/apache/commons/codec/language/bm/Languages.java:147: error: cannot find symbol
             *             LANGUAGES.put(s, getInstance(langResourceName(s)));
             *                                          ^
             *   symbol:   method langResourceName(NameType)
             *   location: class Languages
             * ```
             */
            @Test
            fun `Regression-03`() {
                val cu = reducer.context
                    .getCUByPrimaryTypeName("org.apache.commons.codec.language.bm.Languages")
                assumeTrue(cu != null)

                val type = cu.primaryType.get()
                    .asClassOrInterfaceDeclaration()
                assumeTrue {
                    isTypeReachable(
                        reducer.context,
                        type,
                        enableAssertions,
                        noCache = true
                    )
                }

                val tgtMethod = type
                    .getMethodsBySignature("langResourceName", "NameType").single()

                val decision = decideForMethod(
                    reducer.context,
                    tgtMethod,
                    enableAssertions,
                    noCache = true
                )
                assertNotEquals(NodeTransformDecision.REMOVE, decision)
            }
        }
    }
}