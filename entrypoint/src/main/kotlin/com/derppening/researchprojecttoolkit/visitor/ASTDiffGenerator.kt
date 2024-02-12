package com.derppening.researchprojecttoolkit.visitor

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.ObjectCreationExpr
import com.github.javaparser.ast.visitor.GenericListVisitorAdapter

class ASTDiffGenerator : GenericListVisitorAdapter<(CompilationUnit) -> Unit, CompilationUnit>() {

    private fun checkArgIsNotSelf(node: Node, arg: CompilationUnit) =
        check(node.findCompilationUnit().get() !== arg)

    override fun visit(n: ImportDeclaration, arg: CompilationUnit): List<(CompilationUnit) -> Unit> {
        checkArgIsNotSelf(n, arg)

        if (arg.imports.any { it.nameAsString == n.nameAsString }) {
            return super.visit(n, arg)
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

        return when (val argParentNode = methodParentPath(arg)) {
            is ClassOrInterfaceDeclaration -> {
                if (argParentNode.getMethodsBySignature(n.nameAsString, *n.parameters.map { it.typeAsString }.toTypedArray()).size == 1) {
                    return super.visit(n, arg)
                }

                listOf {
                    val methodParentNode = methodParentPath(it) as ClassOrInterfaceDeclaration

                    methodParentNode.addMethod(n.nameAsString, *n.modifiers.map { it.keyword }.toTypedArray()).apply {
                        setBody(n.body.get().clone())
                        setAnnotations(n.annotations)
                        thrownExceptions = n.thrownExceptions
                    }
                }
            }
            is ObjectCreationExpr -> {
                // We generally don't assume that test methods will be embedded within an anon class
                return super.visit(n, arg)
            }
            else -> throw UnsupportedOperationException("${argParentNode::class.qualifiedName}")
        }
    }

    companion object {

        fun from(from: CompilationUnit, to: CompilationUnit): List<(CompilationUnit) -> Unit> =
            ASTDiffGenerator().visit(to, from)
    }
}