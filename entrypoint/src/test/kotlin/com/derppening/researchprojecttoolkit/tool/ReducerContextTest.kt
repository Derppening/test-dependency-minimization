package com.derppening.researchprojecttoolkit.tool

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import com.derppening.researchprojecttoolkit.model.VariableLikeDeclaration
import com.derppening.researchprojecttoolkit.util.clearMemory
import com.derppening.researchprojecttoolkit.util.toTypedAstOrNull
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserAnonymousClassDeclaration
import org.junit.jupiter.api.BeforeAll
import kotlin.jvm.optionals.getOrNull
import kotlin.test.*

class ReducerContextTest {

    private var reducerContext: ReducerContext? = null

    @Test
    fun `Cli-21f - Resolution of argument in ParentImpl constructor`() {
        reducerContext = TestProjects.Cli21f.getReducerContext()
        val reducerContext = checkNotNull(reducerContext)

        val cu = reducerContext.loadedCompilationUnits
            .single {
                it.primaryType.flatMap { it.fullyQualifiedName }
                    .orElse(null) == "org.apache.commons.cli2.option.ParentImpl"
            }

        val assignExpr = cu.primaryType.get()
            .getConstructorByParameterTypes("Argument", "Group", "String", "int", "boolean").get()
            .body
            .statements[2].asExpressionStmt()
            .expression.asAssignExpr()

        val assignExprTarget = reducerContext.getAssignExprTargetAst(assignExpr)
        assertIs<VariableLikeDeclaration.ClassField>(assignExprTarget)
        val assignExprNode = assignExprTarget.varDecl
        assertEquals("org.apache.commons.cli2.option.ParentImpl", assignExprNode.findAncestor(TypeDeclaration::class.java).flatMap { it.fullyQualifiedName }.getOrNull())
        assertEquals("argument", assignExprNode.nameAsString)

        val expectedVarDecl = cu.primaryType.get()
            .getFieldByName("argument").get()
            .variables[0]
        assertSame(expectedVarDecl, assignExprNode)
    }

    @Test
    fun `Cli-13f - Resolution of Overridden Library Methods`() {
        reducerContext = TestProjects.Cli13f.getReducerContext()
        val reducerContext = checkNotNull(reducerContext)

        val cu = reducerContext.getCUByPrimaryTypeName("org.apache.commons.cli2.option.GroupImpl")
        assumeTrue(cu != null)

        val method = cu.types
            .single { it.nameAsString == "ReverseStringComparator" }.asClassOrInterfaceDeclaration()
            .getMethodsBySignature("compare", "Object", "Object").single()
        val resolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(method)

        val libraryMethodOverrides = reducerContext.getLibraryMethodOverrides(resolvedMethod)
        assertEquals(1, libraryMethodOverrides.size)
    }

    @Test
    fun `Lang-17f - Find Overrides`() {
        reducerContext = TestProjects.Lang17f.getReducerContext()
        val reducerContext = checkNotNull(reducerContext)

        val cu = reducerContext.getCUByPrimaryTypeName("org.apache.commons.lang3.tuple.ImmutablePair")
        assumeTrue(cu != null)

        val methodDecl = cu.primaryType.get()
            .getMethodsBySignature("setValue", "R").single()
        val resolvedMethodDecl = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(methodDecl)

        val methodOverrides = reducerContext.getOverriddenMethods(resolvedMethodDecl, true)
        assertTrue(methodOverrides.isNotEmpty())
    }

    @Test
    fun `Collections-25f - Find Overridden Methods for Superclass-Inherited Method`() {
        reducerContext = TestProjects.Collections25f.getReducerContext()
        val reducerContext = checkNotNull(reducerContext)

        val methodCU = reducerContext.getCUByPrimaryTypeName("org.apache.commons.collections4.iterators.AbstractEmptyIterator")
        assumeTrue(methodCU != null)
        val method = methodCU.primaryType.get()
            .getMethodsBySignature("hasNext").single()
        val resolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(method)

        val baseTypeCU = reducerContext.getCUByPrimaryTypeName("org.apache.commons.collections4.iterators.EmptyListIterator")
        assumeTrue(baseTypeCU != null)
        val baseType = baseTypeCU.primaryType.get()
        val resolvedBaseType = reducerContext.resolveDeclaration<ResolvedReferenceTypeDeclaration>(baseType)

        val overriddenMethods = reducerContext.getOverriddenMethods(resolvedMethod, true, resolvedBaseType)
        assertTrue(overriddenMethods.isNotEmpty())
    }

    @Test
    fun `Collections-27f - Regression`() {
        reducerContext = TestProjects.Collections25f.getReducerContext()
        val reducerContext = checkNotNull(reducerContext)

        val methodCU = reducerContext.getCUByPrimaryTypeName("org.apache.commons.collections4.iterators.ArrayIterator")
        assumeTrue(methodCU != null)
        val method = methodCU.primaryType.get()
            .getMethodsBySignature("remove").single()
        val resolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(method)

        val derivedTypeCU = reducerContext.getCUByPrimaryTypeName("org.apache.commons.collections4.iterators.ArrayListIterator")
        assumeTrue(derivedTypeCU != null)
        val derivedType = derivedTypeCU.primaryType.get()
        val resolvedDerivedType = reducerContext.resolveDeclaration<ResolvedReferenceTypeDeclaration>(derivedType)

        val overriddenMethods = reducerContext.isMethodInheritedFromClass(resolvedDerivedType, resolvedMethod)
        assertTrue(overriddenMethods)

        val resolvedTypesUsingImplFromMethod = reducerContext.getMethodImplDependentClasses(resolvedMethod)
            .filter { it.toTypedAstOrNull<TypeDeclaration<*>>(reducerContext) != null }
        assertTrue(resolvedTypesUsingImplFromMethod.any {
            it.qualifiedName == "org.apache.commons.collections4.iterators.ArrayListIterator"
        })
    }

    @Test
    fun `Closure-79f - Check Overridden-ness of Method in Anonymous Class`() {
        reducerContext = TestProjects.Closure79f.getReducerContext()
        val reducerContext = checkNotNull(reducerContext)

        val cu = reducerContext.getCUByPrimaryTypeName("com.google.javascript.jscomp.AmbiguateProperties")
        assumeTrue(cu != null)
        val anonClass = cu.primaryType.get()
            .getFieldByName("FREQUENCY_COMPARATOR").get()
            .variables.single()
            .initializer.get().asObjectCreationExpr()
        val anonClassMethod = anonClass.anonymousClassBody.get()
            .single {
                it is MethodDeclaration &&
                        it.nameAsString == "compare" &&
                        it.parameters.size == 2 &&
                        it.parameters[0].typeAsString == "Property" &&
                        it.parameters[1].typeAsString == "Property"
            }

        val resolvedAnonClass = JavaParserAnonymousClassDeclaration(anonClass, reducerContext.typeSolver) as ResolvedReferenceTypeDeclaration
        val resolvedMethod = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(anonClassMethod)

        assertTrue(reducerContext.isMethodInheritedFromClass(resolvedAnonClass, resolvedMethod))
    }

    @AfterTest
    fun tearDown() {
        reducerContext = null

        clearMemory()
    }

    companion object {

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            GlobalConfiguration()
        }
    }
}