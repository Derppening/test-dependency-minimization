package com.derppening.researchprojecttoolkit.visitor

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.visitor.GenericListVisitorAdapter

class ASTDiffGenerator : GenericListVisitorAdapter<(CompilationUnit) -> Unit, CompilationUnit>() {

    private fun checkArgIsNotSelf(node: Node, arg: CompilationUnit) =
        check(node.findCompilationUnit().get() !== arg)

    override fun visit(n: ImportDeclaration, arg: CompilationUnit): List<(CompilationUnit) -> Unit> {
        checkArgIsNotSelf(n, arg)

        if (arg.imports.any { it.nameAsString == n.nameAsString }) {
            return emptyList()
        }

        return listOf {
            it.addImport(n.nameAsString, n.isStatic, n.isAsterisk)
        }
    }

    override fun visit(n: MethodDeclaration, arg: CompilationUnit): List<(CompilationUnit) -> Unit> {
        checkArgIsNotSelf(n, arg)

        val methodParentPath = try {
            ASTPathGenerator.forNode(n.parentNode.get())
        } catch (ex: Exception) {
            return emptyList()
        }
        val argParentNode = methodParentPath(arg) as ClassOrInterfaceDeclaration

        if (argParentNode.getMethodsBySignature(n.nameAsString, *n.parameters.map { it.typeAsString }.toTypedArray()).size == 1) {
            return emptyList()
        }

        return listOf {
            val methodParentNode = methodParentPath(it) as ClassOrInterfaceDeclaration

            methodParentNode.addMethod(n.nameAsString, *n.modifiers.map { it.keyword }.toTypedArray()).apply {
                setBody(n.body.get().clone())
                setAnnotations(n.annotations)
                thrownExceptions = n.thrownExceptions
            }
        }
    }

    companion object {

        fun from(from: CompilationUnit, to: CompilationUnit): List<(CompilationUnit) -> Unit> =
            ASTDiffGenerator().visit(to, from)
    }
}