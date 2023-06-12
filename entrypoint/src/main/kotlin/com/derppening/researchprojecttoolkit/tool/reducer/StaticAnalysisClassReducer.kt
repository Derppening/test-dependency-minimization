package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.model.EntrypointSpec
import com.derppening.researchprojecttoolkit.tool.inclusionReasonsData
import com.derppening.researchprojecttoolkit.tool.lockInclusionReasonsData
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.*
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.resolution.declarations.ResolvedEnumConstantDeclaration
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

class StaticAnalysisClassReducer(
    classpath: String,
    sourcesClasspath: String,
    sourceRoots: List<Path>,
    entrypoints: List<EntrypointSpec>,
    enableAssertions: Boolean,
    threads: Int?,
    enabledOptimizations: Set<Optimization> = Optimization.ALL
) : AbstractReducer(
    classpath,
    sourcesClasspath,
    sourceRoots,
    entrypoints,
    enableAssertions,
    enabledOptimizations,
    threads
) {

    private val queueLock = ReentrantLock()
    private val noDeclInFlight = queueLock.newCondition()

    private val typeQueue = ConcurrentProcessingQueue<_, TypeDeclaration<*>>(RESOLVED_TYPE_DECL_COMPARATOR)

    private fun hasDeclInFlight(): Boolean = queueLock.withLock {
        !typeQueue.isQueueEmpty()
    }

    private fun ExecutorService.queueJob(
        decl: ResolvedReferenceTypeDeclaration,
        reasons: Set<ReachableReason>
    ) {
        if (reasons.isEmpty()) return

        typeQueue.push(decl)

        runCatching {
            processClass(decl, reasons, this)
        }.onFailure { tr ->
            LOGGER.error("Error while submitting Job(reachableDecl=${decl.qualifiedName}) to executor", tr)
        }
    }

    private fun ExecutorService.queueJob(
        decl: ResolvedReferenceTypeDeclaration,
        reason: ReachableReason
    ) = queueJob(decl, setOf(reason))

    private fun findAndAddNodesForProcessing(node: Node, executor: ExecutorService) {
        resolveTypesToDeclAsSequence(node).forEach { (node, reachableDecl) ->
            executor.queueJob(reachableDecl, mapToReason(node, reachableDecl))
        }

        resolveFieldAccessExprsToDeclAsSequence(node)
            .mapNotNull { (expr, resolvedValDecl) ->
                when (resolvedValDecl) {
                    is ResolvedEnumConstantDeclaration -> resolvedValDecl.type.asReferenceType().toResolvedTypeDeclaration()
                    is ResolvedFieldDeclaration -> resolvedValDecl.declaringType().asReferenceType()
                    else -> null
                }?.let { expr to it }
            }
            .forEach { (node, reachableDecl) ->
                executor.queueJob(reachableDecl, ReachableReason.ReferencedByUnspecifiedNode(node))
            }
        resolveNameExprsToDeclAsSequence(node)
            .filter { it.second.hasQualifiedName }
            .mapNotNull { (expr, resolvedValDecl) ->
                when (resolvedValDecl) {
                    is ResolvedEnumConstantDeclaration -> resolvedValDecl.type.asReferenceType().toResolvedTypeDeclaration()
                    is ResolvedFieldDeclaration -> resolvedValDecl.declaringType().asReferenceType()
                    else -> null
                }?.let { expr to it }
            }
            .forEach { (node, reachableDecl) ->
                executor.queueJob(reachableDecl, ReachableReason.ReferencedByUnspecifiedNode(node))
            }

        resolveCallExprsToDeclAsSequence(node)
            .forEach { (expr, reachableDecl) ->
                executor.queueJob(reachableDecl.declaringType(), ReachableReason.ReferencedByUnspecifiedNode(expr))
            }
    }

    private fun findAndAddNodesInScope(scope: TypeDeclaration<*>, executor: ExecutorService) {
        findAndAddNodesForProcessing(scope, executor)

        // Include the container type as reachable if this class is a nested class
        if (scope.isNestedType) {
            val nestParentType = scope.findAncestor(TypeDeclaration::class.java).get()
            executor.queueJob(
                context.resolveDeclaration<ResolvedReferenceTypeDeclaration>(nestParentType),
                ReachableReason.NestParent(scope)
            )
        }
    }

    private fun processClass(
        resolvedDecl: ResolvedReferenceTypeDeclaration,
        inclusionReasons: Set<ReachableReason>,
        executor: ExecutorService
    ) {
        try {
            val astDecl = resolvedDecl.toTypedAstOrNull<TypeDeclaration<*>>(null)
                ?.takeIf { decl ->
                    decl.findCompilationUnit()
                        .flatMap { it.storage }
                        .map { it.path in context.compilationUnits }
                        .getOrDefault(false)
                }
                ?.let { context.mapNodeInLoadedSet(it) }
                ?: return

            val keyExists = typeQueue.put(resolvedDecl, astDecl)
            astDecl.inclusionReasonsData.addAll(inclusionReasons)
            if (keyExists) {
                return
            }

            val qname = resolvedDecl.qualifiedName
            LOGGER.info("Processing $qname")

            findAndAddNodesInScope(astDecl, executor)
        } catch (ex: Throwable) {
            LOGGER.error("Unexpected error while executing Job(resolvedDecl=${resolvedDecl.describeQName()})", ex)
        } finally {
            typeQueue.pop(resolvedDecl)

            queueLock.withLock {
                if (!hasDeclInFlight()) {
                    noDeclInFlight.signal()
                }
            }
        }
    }

    override fun run(parallelism: Int) {
        val executor = Executors.newFixedThreadPool(parallelism)

        try {
            val initialClasses = entrypoints.map { entrypoint ->
                val primaryType = context.compilationUnits[entrypoint.file]!!
                    .result
                    .flatMap { it.primaryType }
                    .get()

                context.resolveDeclaration<ResolvedReferenceTypeDeclaration>(primaryType) to entrypoint
            }

            initialClasses.forEach { (classDecl, entrypoint) ->
                executor.queueJob(classDecl, ReachableReason.Entrypoint(entrypoint))
            }

            while (hasDeclInFlight()) {
                queueLock.withLock {
                    noDeclInFlight.await()
                }
            }

            val typesToRetain = typeQueue.entries.map { it.key.qualifiedName }
            classesWorkingSet.retainAll { it.fullyQualifiedName.getOrNull() in typesToRetain }
        } finally {
            executor.shutdown()
            check(executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS))

            context.loadedCompilationUnits
                .parallelStream()
                .forEach { it.lockInclusionReasonsData() }
        }
    }

    override val taggingReductionPipeline: PhasedPipeline
        get() = PhasedPipeline {
            phase {
                TagImportUsage(context) andThen
                        TagClassFieldInitializers(context) andThen
                        TagThrownCheckedExceptions(context)
            }
            phase { TagStaticAnalysisClassUnusedDecls(context) }
            phase { TagUnusedImportsByResolution(context) }
        }

    override val mutatingReductionPipeline: PhasedPipeline
        get() = PhasedPipeline {
            phase { RemoveUnusedNodes(context, enableAssertions) }
        }

    companion object {

        private val LOGGER = Logger<StaticAnalysisClassReducer>()
    }
}