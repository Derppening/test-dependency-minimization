package com.derppening.researchprojecttoolkit.tool.transform

import com.derppening.researchprojecttoolkit.tool.ReducerContext
import com.derppening.researchprojecttoolkit.tool.inclusionReasonsData
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName
import com.github.javaparser.ast.nodeTypes.NodeWithTraversableScope
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.Type
import com.github.javaparser.resolution.declarations.*
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedType
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

/**
 * Tags all use-sites where an import is required.
 */
class TagImportUsage(override val reducerContext: ReducerContext) : TransformPass {

    private inline fun <reified T : ResolvedDeclaration> resolveDeclarationIgnoreErrors(node: Node): T? =
        node.runCatching { reducerContext.symbolSolver.resolveDeclaration<T>(this) }.getOrNull()

    private inline fun <reified T : ResolvedType> toResolvedTypeIgnoreErrors(javaparserType: Type): T? =
        javaparserType.runCatching { reducerContext.symbolSolver.toResolvedType<T>(this) }.getOrNull()

    private fun processStaticImportOnDemand(importDecl: ImportDeclaration, expr: Expression) {
        check(importDecl.isStatic && importDecl.isAsterisk)

        val isUsed = when (expr) {
            is FieldAccessExpr -> {
                resolveDeclarationIgnoreErrors<ResolvedFieldDeclaration>(expr)
                    ?.let { resolvedFieldDecl ->
                        val qualifiedNameOfContainer = resolvedFieldDecl.qualifiedName.dropLastWhile { it != '.' }.dropLast(1)

                        qualifiedNameOfContainer == importDecl.nameAsString
                    }
                    ?: false
            }
            is MethodCallExpr -> {
                resolveDeclarationIgnoreErrors<ResolvedMethodDeclaration>(expr)
                    ?.let { resolvedMethodDecl ->
                        val qualifiedNameOfContainer = resolvedMethodDecl.qualifiedName.dropLastWhile { it != '.' }.dropLast(1)

                        qualifiedNameOfContainer == importDecl.nameAsString
                    }
                    ?: false
            }
            is NameExpr -> {
                when (val resolvedDecl = resolveDeclarationIgnoreErrors<ResolvedDeclaration>(expr)) {
                    is ResolvedEnumConstantDeclaration -> {
                        resolvedDecl.type.asReferenceType().qualifiedName == importDecl.nameAsString
                    }
                    is ResolvedFieldDeclaration -> {
                        val qualifiedNameOfContainer = resolvedDecl.qualifiedName.dropLastWhile { it != '.' }.dropLast(1)

                        qualifiedNameOfContainer == importDecl.nameAsString
                    }
                    is ResolvedReferenceTypeDeclaration -> {
                        resolvedDecl.packageName == importDecl.nameAsString
                    }
                    else -> false
                }
            }
            else -> false
        }

        if (!isUsed) {
            return
        }

        importDecl.inclusionReasonsData.add(ReachableReason.ReferencedBySymbolName(expr))
    }

    private fun processSingleStaticImport(importDecl: ImportDeclaration, type: ClassOrInterfaceType) {
        check(importDecl.isStatic && !importDecl.isAsterisk)

        if (type.leftMostTypeAsString != importDecl.rightMostSymbolAsString) {
            return
        }

        val nestedComponents = type.nameAsString.removePrefix(type.leftMostTypeAsString)
        val resolvedType = toResolvedTypeIgnoreErrors<ResolvedReferenceType>(type) ?: return
        if (importDecl.nameAsString != resolvedType.qualifiedName.removeSuffix(nestedComponents).removeSuffix(".")) {
            return
        }

        importDecl.inclusionReasonsData.add(ReachableReason.ReferencedByTypeName(type))
    }

    private fun processSingleStaticImport(importDecl: ImportDeclaration, expr: Expression) {
        check(importDecl.isStatic && !importDecl.isAsterisk)

        val finalComponent = importDecl.rightMostSymbolAsString

        if ((expr as? NodeWithSimpleName<*>)?.nameAsString != finalComponent) {
            return
        }

        val isUsed = when (expr) {
            is FieldAccessExpr -> {
                resolveDeclarationIgnoreErrors<ResolvedFieldDeclaration>(expr)
                    ?.let { resolvedFieldDecl ->
                        resolvedFieldDecl.qualifiedName == importDecl.nameAsString
                    }
                    ?: false
            }
            is MethodCallExpr -> {
                resolveDeclarationIgnoreErrors<ResolvedMethodDeclaration>(expr)
                    ?.let { resolvedMethodDecl ->
                        resolvedMethodDecl.qualifiedName == importDecl.nameAsString
                    }
                    ?: false
            }
            is NameExpr -> {
                when (val resolvedDecl = resolveDeclarationIgnoreErrors<ResolvedDeclaration>(expr)) {
                    is ResolvedEnumConstantDeclaration -> {
                        resolvedDecl.qualifiedName == importDecl.nameAsString
                    }
                    is ResolvedFieldDeclaration -> {
                        resolvedDecl.qualifiedName == importDecl.nameAsString
                    }
                    is ResolvedReferenceTypeDeclaration -> {
                        resolvedDecl.qualifiedName == importDecl.nameAsString
                    }
                    else -> false
                }
            }
            else -> false
        }

        if (!isUsed) {
            return
        }

        importDecl.inclusionReasonsData.add(ReachableReason.ReferencedBySymbolName(expr))
    }

    private fun processTypeImportOnDemand(importDecl: ImportDeclaration, type: ClassOrInterfaceType) {
        check(!importDecl.isStatic && importDecl.isAsterisk)

        val resolvedType = toResolvedTypeIgnoreErrors<ResolvedReferenceType>(type) ?: return
        val resolvedTypeDecl = resolvedType.toResolvedTypeDeclaration()
        if (importDecl.nameAsString != resolvedTypeDecl.packageName) {
            return
        }

        importDecl.inclusionReasonsData.add(ReachableReason.ReferencedByTypeName(type))
    }

    private fun processTypeImportOnDemand(importDecl: ImportDeclaration, expr: Expression) {
        check(!importDecl.isStatic && importDecl.isAsterisk)

        when (expr) {
            is AnnotationExpr -> {
                val resolvedAnnoDecl = resolveDeclarationIgnoreErrors<ResolvedAnnotationDeclaration>(expr)
                    ?: return

                if (resolvedAnnoDecl.qualifiedName == "${importDecl.nameAsString}.${expr.nameAsString}") {
                    importDecl.inclusionReasonsData.add(ReachableReason.ReferencedBySymbolName(expr))
                }
            }

            is NodeWithTraversableScope -> {
                val scopeExpr = expr.traverseScope().getOrNull()
                if (scopeExpr !is NameExpr) {
                    return
                }

                val scopeNameExpr = scopeExpr.asNameExpr()
                val resolvedTypeDecl = resolveDeclarationIgnoreErrors<ResolvedReferenceTypeDeclaration>(scopeNameExpr)
                    ?: return

                if (importDecl.nameAsString != resolvedTypeDecl.packageName) {
                    return
                }

                importDecl.inclusionReasonsData.add(ReachableReason.ReferencedBySymbolName(scopeNameExpr))
            }
        }
    }

    private fun processSingleTypeImport(importDecl: ImportDeclaration, type: ClassOrInterfaceType) {
        check(!importDecl.isStatic && !importDecl.isAsterisk)

        if (type.leftMostTypeAsString != importDecl.rightMostContainerTypeAsString) {
            return
        }

        val nestedComponents = type.nameAsString.removePrefix(type.leftMostTypeAsString)
        val resolvedType = toResolvedTypeIgnoreErrors<ResolvedReferenceType>(type) ?: return
        if (importDecl.nameAsString != resolvedType.qualifiedName.removeSuffix(nestedComponents).removeSuffix(".")) {
            return
        }

        importDecl.inclusionReasonsData.add(ReachableReason.ReferencedByTypeName(type))
    }

    private fun processSingleTypeImport(importDecl: ImportDeclaration, expr: Expression) {
        check(!importDecl.isStatic && !importDecl.isAsterisk)

        when (expr) {
            is AnnotationExpr -> {
                val annoExpr = expr.asAnnotationExpr()
                val resolvedDecl = resolveDeclarationIgnoreErrors<ResolvedDeclaration>(annoExpr) ?: return
                if (resolvedDecl !is ResolvedAnnotationDeclaration) {
                    return
                }

                if (importDecl.nameAsString != resolvedDecl.qualifiedName) {
                    return
                }

                importDecl.inclusionReasonsData.add(ReachableReason.ReferencedBySymbolName(expr))
            }
            is NameExpr -> {
                val scopeNameExpr = expr.asNameExpr()
                val resolvedDecl = resolveDeclarationIgnoreErrors<ResolvedDeclaration>(scopeNameExpr) ?: return
                if (!resolvedDecl.isType) {
                    return
                }

                val resolvedTypeDecl = resolvedDecl.asType()
                if (importDecl.nameAsString != resolvedTypeDecl.qualifiedName) {
                    return
                }

                importDecl.inclusionReasonsData.add(ReachableReason.ReferencedBySymbolName(expr))
            }
            else -> {}
        }
    }

    override fun transform(cu: CompilationUnit) {
        verifyIsInvokedOnce(cu)

        val allRefTypes = cu.findAll<ClassOrInterfaceType> { clzOrIfaceType ->
            clzOrIfaceType.parentNode
                .map {
                    // Do not include types which are actually parts of a qualified name
                    it !is ClassOrInterfaceType || clzOrIfaceType in it.typeArguments.getOrNull().orEmpty()
                }
                .getOrDefault(false)
        }
        val allExprs = cu.findAll<Expression>()

        cu.imports.forEach { importDecl ->
            when {
                // import static <...>.*;
                // <...> must be a type
                // <...>.<name> must be a member
                importDecl.isStatic && importDecl.isAsterisk -> {
                    allExprs.forEach { processStaticImportOnDemand(importDecl, it) }
                }

                // import static <...>;
                // <...> must be a member or nested type
                importDecl.isStatic && !importDecl.isAsterisk -> {
                    allRefTypes.forEach { processSingleStaticImport(importDecl, it) }
                    allExprs.forEach { processSingleStaticImport(importDecl, it) }
                }

                // import <...>.*;
                // <...> must be a package or type
                // <...>.<name> must be a type
                !importDecl.isStatic && importDecl.isAsterisk -> {
                    allRefTypes.forEach { processTypeImportOnDemand(importDecl, it) }
                    allExprs.forEach { processTypeImportOnDemand(importDecl, it) }
                }

                // import <...>;
                // <...> must be a type
                !importDecl.isStatic && !importDecl.isAsterisk -> {
                    allRefTypes.forEach { processSingleTypeImport(importDecl, it) }
                    allExprs.forEach { processSingleTypeImport(importDecl, it) }
                }
                else -> unreachable("Unhandled case where isStatic=${importDecl.isStatic} isAsterisk=${importDecl.isAsterisk}")
            }
        }
    }
}