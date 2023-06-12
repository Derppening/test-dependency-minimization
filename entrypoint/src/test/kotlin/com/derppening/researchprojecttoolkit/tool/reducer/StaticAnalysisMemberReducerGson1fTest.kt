package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForFieldDecl
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.isTransitiveDependentReachable
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.getFieldVariableDecl
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerGson1fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Gson1f

    @Nested
    inner class TypeVariableTest {

        @Nested
        inner class TestSingle : OnTestCase("com.google.gson.functional.TypeVariableTest::testSingle", true) {

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
                    JAVA_17_HOME
                )
                assertCompileSuccess(compiler)

                val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
                assertFalse(junitRunner.exitCode.isSuccess)
            }

            /**
             * ```
             * java.lang.AssertionError: This method should not be reached! Signature: serialize(String, Type, JsonSerializationContext)
             *   at com.google.gson.DefaultTypeAdapters$StringTypeAdapter.serialize(DefaultTypeAdapters.java:772)
             *   at com.google.gson.DefaultTypeAdapters$StringTypeAdapter.serialize(DefaultTypeAdapters.java:769)
             *   at com.google.gson.JsonSerializationVisitor.findAndInvokeCustomSerializer(JsonSerializationVisitor.java:185)
             *   at com.google.gson.JsonSerializationVisitor.visitFieldUsingCustomHandler(JsonSerializationVisitor.java:203)
             *   at com.google.gson.ObjectNavigator.navigateClassFields(ObjectNavigator.java:154)
             *   at com.google.gson.ObjectNavigator.accept(ObjectNavigator.java:128)
             *   at com.google.gson.JsonSerializationContextDefault.serialize(JsonSerializationContextDefault.java:56)
             *   at com.google.gson.Gson.toJsonTree(Gson.java:207)
             *   at com.google.gson.Gson.toJson(Gson.java:247)
             *   at com.google.gson.Gson.toJson(Gson.java:227)
             *   at com.google.gson.functional.TypeVariableTest.testSingle(TypeVariableTest.java:39)
             * ```
             */
            @Test
            fun `Regression-00`() {
                // com.google.gson.functional.TypeVariableTest.testSingle()
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("com.google.gson.functional.TypeVariableTest")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("testSingle").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.Entrypoint>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // com.google.gson.Gson.toJson(Object)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("com.google.gson.Gson")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("toJson", "Object").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // com.google.gson.Gson.toJson(Object, Type)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("com.google.gson.Gson")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("toJson", "Object", "Type").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // com.google.gson.Gson.toJsonTree(Object, Type)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("com.google.gson.Gson")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("toJsonTree", "Object", "Type").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // com.google.gson.JsonSerializationContextDefault.serialize(Object, Type, boolean)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("com.google.gson.JsonSerializationContextDefault")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("serialize", "Object", "Type", "boolean").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // com.google.gson.ObjectNavigator.accept(Visitor)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("com.google.gson.ObjectNavigator")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("accept", "Visitor").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // com.google.gson.ObjectNavigator.navigateClassFields(Object, Class<?>, Visitor)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("com.google.gson.ObjectNavigator")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("navigateClassFields", "Object", "Class<?>", "Visitor").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // com.google.gson.ObjectNavigator.Visitor.visitFieldUsingCustomHandler(FieldAttributes, Type, Object)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("com.google.gson.ObjectNavigator")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .members
                        .single { it.isClassOrInterfaceDeclaration && it.asClassOrInterfaceDeclaration().nameAsString == "Visitor" }
                        .asClassOrInterfaceDeclaration()
                        .getMethodsBySignature("visitFieldUsingCustomHandler", "FieldAttributes", "Type", "Object")
                        .single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // com.google.gson.JsonSerializationVisitor.visitFieldUsingCustomHandler(FieldAttributes, Type, Object)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("com.google.gson.JsonSerializationVisitor")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("visitFieldUsingCustomHandler", "FieldAttributes", "Type", "Object")
                        .single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.TransitiveMethodCallTarget>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // com.google.gson.JsonSerializationVisitor.findAndInvokeCustomSerializer(ObjectTypePair)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("com.google.gson.JsonSerializationVisitor")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("findAndInvokeCustomSerializer", "ObjectTypePair").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // com.google.gson.JsonSerializer.serialize(T, Type, JsonSerializationContext)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("com.google.gson.JsonSerializer")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("serialize", "T", "Type", "JsonSerializationContext").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // com.google.gson.DefaultTypeAdapters.StringTypeAdapter.serialize(String, Type, JsonSerializationContext)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("com.google.gson.DefaultTypeAdapters")
                    assumeTrue(cu != null)

                    val nestedClass = cu.primaryType.get()
                        .members
                        .single { it.isClassOrInterfaceDeclaration && it.asClassOrInterfaceDeclaration().nameAsString == "StringTypeAdapter" }
                        .asClassOrInterfaceDeclaration()

                    assertTrue(nestedClass.instanceCreationData.isNotEmpty())
                    assertFalse(nestedClass.isUnusedForRemovalData)

                    val method = nestedClass
                        .getMethodsBySignature("serialize", "String", "Type", "JsonSerializationContext").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.TransitiveMethodCallTarget>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }
            }

            /**
             * ```
             *     => java.lang.AssertionError: This method should not be reached! Signature: serialize(String, Type, JsonSerializationContext)
             *        com.google.gson.DefaultTypeAdapters$StringTypeAdapter.serialize(DefaultTypeAdapters.java:770)
             *        com.google.gson.DefaultTypeAdapters$StringTypeAdapter.serialize(DefaultTypeAdapters.java:767)
             *        com.google.gson.JsonSerializationVisitor.findAndInvokeCustomSerializer(JsonSerializationVisitor.java:185)
             *        com.google.gson.JsonSerializationVisitor.visitFieldUsingCustomHandler(JsonSerializationVisitor.java:203)
             *        com.google.gson.ObjectNavigator.navigateClassFields(ObjectNavigator.java:154)
             *        com.google.gson.ObjectNavigator.accept(ObjectNavigator.java:128)
             *        com.google.gson.JsonSerializationContextDefault.serialize(JsonSerializationContextDefault.java:56)
             *        com.google.gson.Gson.toJsonTree(Gson.java:205)
             *        com.google.gson.Gson.toJson(Gson.java:245)
             *        com.google.gson.Gson.toJson(Gson.java:225)
             *        [...]
             * ```
             */
            @Test
            fun `Regression-00b`() {
                val cu = reducer.context.getCUByPrimaryTypeName("com.google.gson.JsonSerializationVisitor")
                assumeTrue(cu != null)

                val type = cu.primaryType.get()
                val method = type
                    .getMethodsBySignature("findAndInvokeCustomSerializer", "ObjectTypePair").single()
                val expr = method
                    .body.get()
                    .statements[5].asTryStmt()
                    .tryBlock
                    .statements[0].asExpressionStmt()
                    .expression.asVariableDeclarationExpr()
                    .variables.single()
                    .initializer.get().asMethodCallExpr()

                val dynamicTargets = reducer.inferDynamicMethodCallTypes(
                    reducer.context.resolveDeclaration<ResolvedMethodDeclaration>(expr),
                    expr,
                    expr.scope.getOrNull()
                )
                val stringTypeAdapter = dynamicTargets
                    .find { it.isReferenceType && it.asReferenceType().qualifiedName == "com.google.gson.DefaultTypeAdapters.StringTypeAdapter" }
                assertNotNull(stringTypeAdapter)
            }

            /**
             * ```
             * gson/src/test/java/com/google/gson/functional/CustomDeserializerTest.java:25: error: package com.google.gson.common.TestTypes does not exist
             * import com.google.gson.common.TestTypes.Base;
             *                                        ^
             * gson/src/test/java/com/google/gson/functional/CustomDeserializerTest.java:113: error: cannot find symbol
             *         Base[] bases;
             *         ^
             *   symbol:   class Base
             *   location: class ClassWithBaseArray
             * ```
             */
            @Test
            fun `Regression-01`() {
                val srcCU = reducer.context
                    .getCUByPrimaryTypeName("com.google.gson.functional.CustomDeserializerTest")
                assumeTrue(srcCU != null)

                val srcType = srcCU.primaryType.get()
                    .members
                    .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "ClassWithBaseArray" }
                    .asClassOrInterfaceDeclaration()
                assumeTrue {
                    isTypeReachable(
                        reducer.context,
                        srcType,
                        enableAssertions,
                        noCache = true
                    )
                }

                val srcFieldVar = srcType.getFieldVariableDecl("bases")
                assumeTrue {
                    decideForFieldDecl(
                        reducer.context,
                        srcFieldVar,
                        enableAssertions = enableAssertions,
                        noCache = true
                    ) == NodeTransformDecision.NO_OP
                }
                assumeTrue {
                    isTransitiveDependentReachable(
                        reducer.context,
                        srcFieldVar,
                        enableAssertions,
                        noCache = true
                    )
                }

                val tgtCU = reducer.context
                    .getCUByPrimaryTypeName("com.google.gson.common.TestTypes")
                assumeTrue(tgtCU != null)

                val tgtType = tgtCU.primaryType.get()

                val tgtNestedType = tgtType
                    .members
                    .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "Base" }
                    .asClassOrInterfaceDeclaration()

                val nestedTypeReachable = isTypeReachable(
                    reducer.context,
                    tgtNestedType,
                    enableAssertions,
                    noCache = true
                )
                assertTrue(nestedTypeReachable)
                assertFalse(tgtNestedType.isUnusedForRemovalData)

                val typeReachable = isTypeReachable(
                    reducer.context,
                    tgtType,
                    enableAssertions,
                    noCache = true
                )
                assertTrue(typeReachable)
                assertFalse(tgtType.isUnusedForRemovalData)
            }
        }
    }
}