package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import com.derppening.researchprojecttoolkit.defects4j.SourceRootOutput
import com.derppening.researchprojecttoolkit.model.*
import com.derppening.researchprojecttoolkit.tool.ReducerContext
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.Range
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.type.Type
import com.github.javaparser.resolution.declarations.*
import com.github.javaparser.resolution.types.ResolvedType
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserAnonymousClassDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserEnumConstantDeclaration
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import java.nio.file.Path
import java.util.*
import kotlin.io.path.bufferedReader
import kotlin.jvm.optionals.getOrElse
import kotlin.jvm.optionals.getOrNull

/**
 * A reducer which reduces the set of methods based on an execution trace.
 */
abstract class AbstractBaselineReducer(
    classpath: String,
    sourcesClasspath: String,
    sourceRoots: List<Path>,
    entrypoints: List<EntrypointSpec>,
    enableAssertions: Boolean,
    threads: Int?,
    protected val projectRev: Defects4JWorkspace.ProjectRev,
    protected val testCase: TestCase
) : AbstractReducer(
    classpath,
    sourcesClasspath,
    sourceRoots,
    entrypoints,
    enableAssertions,
    Optimization.ALL,
    threads
) {

    protected val baselineDir: ToolOutputDirectory
        get() = ToolOutputDirectory(GlobalConfiguration.INSTANCE.cmdlineOpts.baselineDir, projectRev, testCase, true)

    protected val allSourceClasses by lazy {
        baselineDir.getAllClassesInSourceRootPath(SourceRootOutput.SOURCE)
            .bufferedReader()
            .use { it.readLines() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    protected val allTestClasses by lazy {
        baselineDir.getAllClassesInSourceRootPath(SourceRootOutput.TEST)
            .bufferedReader()
            .use { it.readLines() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    protected val coverage = baselineDir.readCoverageData()

    companion object {

        private val LOGGER = Logger<AbstractBaselineReducer>()

        /**
         * Checks whether this [Node] is unused based on the provided coverage data.
         *
         * @param coberturaLines The lines of the containing class or method, either by [CoberturaXML.Class.lines] or
         * [CoberturaXML.Method.lines].
         * @param defaultIfNoCov The default value to return if no coverage data is present for the line.
         * @return `true` if the node is unused based on coverage data. If the range of the node is not present in the
         * coverage data, returns [defaultIfNoCov].
         */
        fun Node.isUnusedInCoverage(
            coberturaLines: List<CoberturaXML.Line>?,
            jacocoLines: List<JacocoXML.Line>?,
            defaultIfNoCov: Boolean
        ): Boolean {
            check(range.isPresent)

            val lineRange = lineRange.get()

            val cobFiltLines = coberturaLines
                ?.filter { it.number in lineRange }
            val jacFiltLines = jacocoLines
                ?.filter { it.nr in lineRange }

            val isCoberturaUnused = cobFiltLines?.let {
                it.isNotEmpty() && it.all { it.hits == 0L }
            } ?: true
            val isJacocoUnused = jacFiltLines?.let {
                it.isNotEmpty() && it.all { it.ci == 0 }
            } ?: true

            return if (cobFiltLines != null || jacFiltLines != null) {
                isCoberturaUnused && isJacocoUnused
            } else defaultIfNoCov
        }

        fun ReferenceTypeLikeDeclaration<*>.isUsedInCoverage(defaultIfNoCov: Boolean): Boolean {
            val mergedCov = MergedCoverageData.from(this)
            check(mergedCov.isPartiallyPresent) {
                val qsig = nameWithScope
                val astStr = node.astToString(showChildren = false)

                "No coverage data associated with node `$qsig`\n$astStr"
            }

            return if (mergedCov.isDataSound) {
                mergedCov.isReachable
            } else defaultIfNoCov
        }

        fun CallableDeclaration<*>.isUsedInCoverageOrNull(
            defaultIfMissingCov: Boolean?,
            defaultIfUnsoundCov: Boolean?
        ): Boolean? {
            val mergedCov = MergedCoverageData.from(ExecutableDeclaration.create(this))
            if (!mergedCov.isPartiallyPresent) {
                return defaultIfMissingCov
            }

            return if (mergedCov.isDataSound) {
                mergedCov.isReachable
            } else defaultIfUnsoundCov
        }

        fun CallableDeclaration<*>.isUsedInCoverage(defaultIfUnsoundCov: Boolean): Boolean =
            checkNotNull(isUsedInCoverageOrNull(null, defaultIfUnsoundCov)) {

                val qsig = getQualifiedSignature(null)
                val astStr = astToString(showChildren = false)

                "No coverage data associated with node `$qsig`\n$astStr"
            }

        /**
         * Finds a class in [allBodyDecls] matching the name provided by [coverageClass].
         */
        internal fun findClassFromJacocoCoverage(
            allBodyDecls: List<ReferenceTypeLikeDeclaration<*>>,
            coverageClass: JacocoXML.Class
        ): ReferenceTypeLikeDeclaration<*>? {
            val cu = allBodyDecls
                .map { it.node }
                .map { it.findCompilationUnit().get() }
                .distinctBy { it.storage.map { it.path }.getOrNull() }
                .single()

            val coverageClassName = coverageClass.name.replace('/', '.')

            return if (coverageClassName.contains(ANON_CLASS_REGEX)) {
                if (coverageClass.methods.isEmpty()) {
                    return null
                }

                val methodLines = coverageClass.methods.map { it.line }

                val classStartPos = methodLines
                    .mapNotNull {
                        cu.runCatching { getRangeOfLine(it) }
                            .getOrNull()
                    }
                    .minByOrNull { it.begin.line }
                    ?.let { range ->
                        val line = range.begin.line.also { check(it == range.end.line) }
                        if (line == methodLines.min()) {
                            range.end
                        } else {
                            range.begin
                        }
                    }
                val classEndPos = methodLines
                    .mapNotNull {
                        cu.runCatching { getRangeOfLine(it) }
                            .getOrNull()
                    }
                    .maxByOrNull { it.end.line }
                    ?.let { range ->
                        val line = range.end.line.also { check(it == range.begin.line) }
                        if (line == methodLines.max()) {
                            range.begin
                        } else {
                            range.end
                        }
                    }

                if (classStartPos != null && classEndPos != null) {
                    val classRange = Range(classStartPos, classEndPos)

                    if (classStartPos.line == classEndPos.line) {
                        allBodyDecls
                            .filter {
                                it.node.range
                                    .map {
                                        val lineRange = it.begin.line..it.end.line
                                        classStartPos.line in lineRange
                                    }
                                    .getOrElse {
                                        LOGGER.warn("$it does not have range information")
                                        false
                                    }
                            }
                            .reduceOrNull { acc, it ->
                                when {
                                    acc.node.range.get().strictlyContains(it.node.range.get()) -> it
                                    it.node.range.get().strictlyContains(acc.node.range.get()) -> acc
                                    else -> error("Don't know how to choose between ${acc.node.rangeString} and ${it.node.rangeString}")
                                }
                            }
                            ?: error("Cannot find class $coverageClassName in ${coverageClass.sourceFileName}")
                    } else {
                        allBodyDecls
                            .filter {
                                it.node.range
                                    .map { it.contains(classRange) }
                                    .getOrElse {
                                        LOGGER.warn("$it does not have range information")
                                        false
                                    }
                            }
                            .reduceOrNull { acc, it ->
                                when {
                                    acc.node.range.get().strictlyContains(it.node.range.get()) -> it
                                    it.node.range.get().strictlyContains(acc.node.range.get()) -> acc
                                    else -> error("Don't know how to choose between ${acc.node.rangeString} and ${it.node.rangeString}")
                                }
                            }
                            ?: error("Cannot find class $coverageClassName in ${coverageClass.sourceFileName}")
                    }
                } else null
            } else {
                allBodyDecls.filterIsInstance<ReferenceTypeLikeDeclaration.TypeDecl>()
                    .filter { it.nameWithScope != null }
                    .singleOrNull { it.nameWithScope == coverageClassName.replace('$', '.') }
            }
        }

        /**
         * Wrapper for [ResolvedType.describeTypeAsBytecode], but additionally using [reducerContext] for symbol
         * solving.
         */
        private fun describeTypeAsBytecode(reducerContext: ReducerContext, type: Type): String {
            return type.let { reducerContext.mapNodeInLoadedSet(it) }
                .let { reducerContext.toResolvedType<ResolvedType>(it) }
                .describeTypeAsBytecode()
        }

        /**
         * Whether the types in [declParams] and [bytecodeParams] are the same.
         */
        private fun isTypesSame(
            declParams: List<ResolvedParameterDeclaration>,
            bytecodeParams: List<String>,
            context: ReducerContext
        ): Boolean {
            val declParamStr = declParams
                .map { it.toTypedAst<Parameter>(context) }
                .map { param ->
                    describeTypeAsBytecode(context, param.type)
                        .let { if (param.isVarArgs) "$it[]" else it }
                }
            val bytecodeParamStr = bytecodeParams.map { it.replace('$', '.') }

            return bytecodeParamStr.zip(declParamStr)
                .all { (expected, actual) -> expected == actual }
        }

        /**
         * Whether the parameter types of [resolvedMethodDecl] and [bytecodeMethod] are the same.
         */
        private fun isParamsSameType(
            resolvedMethodDecl: ResolvedMethodDeclaration,
            bytecodeMethod: BytecodeMethod,
            context: ReducerContext
        ): Boolean {
            return isTypesSame(
                resolvedMethodDecl.parameters,
                bytecodeMethod.paramTypes,
                context
            )
        }

        /**
         * Whether the return type of [resolvedMethodDecl] and [bytecodeMethod] are the same.
         */
        private fun isRetTypeSameType(
            resolvedMethodDecl: ResolvedMethodDeclaration,
            bytecodeMethod: BytecodeMethod,
            context: ReducerContext,
        ): Boolean {
            return resolvedMethodDecl.toTypedAst<MethodDeclaration>(context)
                .let { describeTypeAsBytecode(context, it.type) == bytecodeMethod.returnType.replace('$', '.') }
        }

        /**
         * Finds a single method in [methods] matching the signature of [bytecodeMethod].
         *
         * @param baseDecl The declaration which the [methods] are obtained from.
         */
        private fun findResolvedMethodMatchingBytecode(
            bytecodeMethod: BytecodeMethod,
            baseDecl: ResolvedDeclaration,
            context: ReducerContext,
            methods: Set<ResolvedMethodDeclaration>,
            resolvedAllTypeMethods: () -> Collection<ResolvedMethodDeclaration>
        ): ResolvedMethodDeclaration? {
            check(baseDecl.isEnumConstant || baseDecl.isType)

            val baseDeclAsType = baseDecl.takeIf { it.isType }?.asType()
            val qualifiedName = baseDecl.describeQName(showType = false)
            val allMethods by lazy(LazyThreadSafetyMode.NONE) { resolvedAllTypeMethods() }

            val methodsMatchingName = methods
                .filter { it.name == bytecodeMethod.methodName }
            when (methodsMatchingName.size) {
                0 -> {
                    return when {
                        bytecodeMethod.methodName == "class\$" -> {
                            // Synthetic method delegate for Class.forName
                            null
                        }

                        bytecodeMethod.methodName.startsWith("access\$") -> {
                            // Synthetic Accessors generated by inner classes
                            null
                        }

                        baseDeclAsType?.isEnum == true && bytecodeMethod.methodName == "values" && bytecodeMethod.paramTypes.isEmpty() -> {
                            // static Enum.values
                            null
                        }

                        allMethods.any { it.name == bytecodeMethod.methodName } -> {
                            // Generated method for invokevirtual dispatch
                            null
                        }

                        else -> {
                            error("Cannot find method with name `${bytecodeMethod.methodName}` in $qualifiedName")
                        }
                    }
                }

                1 -> {
                    return methodsMatchingName.single()
                        .takeIf {
                            isParamsSameType(it, bytecodeMethod, context) &&
                                    isRetTypeSameType(it, bytecodeMethod, context)
                        }
                }

                else -> { /* fallthrough */
                }
            }

            val methodsMatchingNumParams =
                methodsMatchingName.filter { it.numberOfParams == bytecodeMethod.paramTypes.size }
            when (methodsMatchingNumParams.size) {
                0 -> error("Cannot find method `${bytecodeMethod.methodName}` with ${bytecodeMethod.paramTypes.size} parameters in $qualifiedName")
                1 -> {
                    return methodsMatchingNumParams.single()
                        .takeIf {
                            isParamsSameType(it, bytecodeMethod, context) &&
                                    isRetTypeSameType(it, bytecodeMethod, context)
                        }
                }

                else -> { /* fallthrough */
                }
            }

            return methodsMatchingNumParams.singleOrNull {
                isParamsSameType(it, bytecodeMethod, context) && isRetTypeSameType(it, bytecodeMethod, context)
            } ?: error("Cannot find method `${bytecodeMethod.methodName}` with parameter types ${bytecodeMethod.paramTypes} in $qualifiedName")
        }

        /**
         * Finds a single method in [resolvedEnumConst] matching the signature of [bytecodeMethod].
         */
        private fun findResolvedMethodMatchingBytecode(
            bytecodeMethod: BytecodeMethod,
            resolvedEnumConst: ResolvedEnumConstantDeclaration,
            context: ReducerContext
        ): ResolvedMethodDeclaration? {
            val declaredMethods = when (resolvedEnumConst) {
                is JavaParserEnumConstantDeclaration -> {
                    resolvedEnumConst.toTypedAst<EnumConstantDeclaration>(context)
                        .classBody
                        .filter { it.isMethodDeclaration }
                        .map { context.resolveDeclaration<ResolvedMethodDeclaration>(it.asMethodDeclaration()) }
                }

                else -> TODO()
            }.toSet()

            return findResolvedMethodMatchingBytecode(
                bytecodeMethod,
                resolvedEnumConst,
                context,
                declaredMethods
            ) {
                val type = resolvedEnumConst.type
                    .asReferenceType()
                    .toResolvedTypeDeclaration()

                declaredMethods + context.symbolSolverCache.getAllMethods(type).map { it.declaration }
            }
        }

        /**
         * Finds a single method in [resolvedTypeDecl] matching the signature of [bytecodeMethod].
         */
        private fun findResolvedMethodMatchingBytecode(
            bytecodeMethod: BytecodeMethod,
            resolvedTypeDecl: ResolvedReferenceTypeDeclaration,
            context: ReducerContext
        ): ResolvedMethodDeclaration? {
            return findResolvedMethodMatchingBytecode(
                bytecodeMethod,
                resolvedTypeDecl,
                context,
                context.symbolSolverCache.getDeclaredMethods(resolvedTypeDecl)
            ) {
                context.symbolSolverCache.getAllMethods(resolvedTypeDecl).map { it.declaration }
            }
        }

        /**
         * Finds a single constructor in [resolvedTypeDecl] matching the signature of [bytecodeMethod].
         */
        private fun findResolvedCtorMatchingBytecode(
            bytecodeMethod: BytecodeMethod,
            resolvedTypeDecl: ResolvedReferenceTypeDeclaration,
            context: ReducerContext
        ): ResolvedConstructorDeclaration? {
            if (resolvedTypeDecl.isAnonymousClass) {
                return null
            }

            val astTypeDecl = resolvedTypeDecl.toTypedAst<TypeDeclaration<*>>(context)
            val isInnerClass = astTypeDecl.isNestedType && !astTypeDecl.isStatic &&
                    astTypeDecl.findAncestor(TypeDeclaration::class.java)
                        .map {
                            when (it) {
                                is ClassOrInterfaceDeclaration -> !it.isInterface
                                is AnnotationDeclaration -> false
                                else -> true
                            }
                        }
                        .get()
            val isEnum = resolvedTypeDecl.isEnum
            val isLocalClass = astTypeDecl.ancestorsAsSequence()
                .filter { it is TypeDeclaration<*> || it is MethodDeclaration }
                .firstOrNull() is MethodDeclaration

            val ctors = context.symbolSolverCache.getConstructors(resolvedTypeDecl)

            if (isLocalClass && ctors.size <= 1) {
                return when {
                    ctors.isEmpty() -> null
                    astTypeDecl.constructors.isEmpty() -> null
                    else -> ctors.single()
                }
            }

            val numAnonParamTypes = bytecodeMethod.paramTypes.filterNot { it.contains(ANON_CLASS_REGEX) }.size
            val ctorsMatchingNumParams = if (isLocalClass) {
                ctors.filter { it.numberOfParams >= numAnonParamTypes }
            } else if (isEnum) {
                ctors.filter { it.numberOfParams == numAnonParamTypes - 2 }
            } else if (isInnerClass) {
                ctors.filter { it.numberOfParams == numAnonParamTypes - 1 }
            } else {
                ctors.filter { it.numberOfParams == numAnonParamTypes }
            }
            when (ctorsMatchingNumParams.size) {
                0 -> {
                    LOGGER.warn("Cannot find constructor `$bytecodeMethod` in ${resolvedTypeDecl.qualifiedName}")
                    return null
                }

                1 -> return ctorsMatchingNumParams.single()
                else -> { /* fallthrough */
                }
            }

            return ctorsMatchingNumParams.singleOrNull {
                val declParams = it.parameters
                    .filterNot {
                        it.type.isReferenceType && it.type.asReferenceType().toResolvedTypeDeclaration().isAnonymousClass
                    }
                val bytecodeParams = bytecodeMethod.paramTypes
                    .let { params ->
                        if (isEnum) {
                            params.drop(2)
                        } else if (isInnerClass) {
                            params.drop(1)
                        } else {
                            params
                        }
                    }
                    .let { params ->
                        if (isInnerClass) {
                            params.takeLast(it.numberOfParams)
                        } else {
                            params
                        }
                    }

                isTypesSame(declParams, bytecodeParams, context)
            } ?: error("Cannot find constructor `${bytecodeMethod.methodName}` with parameter types ${bytecodeMethod.paramTypes} in ${resolvedTypeDecl.qualifiedName}")
        }

        /**
         * Finds all containers which houses all declarations in [allExecDecls].
         */
        private fun findCallableContainers(
            allExecDecls: List<ExecutableDeclaration<*>>
        ): SortedSet<ReferenceTypeLikeDeclaration<*>> {
            return allExecDecls
                .map { it.node }
                .mapNotNull {
                    it.findAncestor<Node> { ReferenceTypeLikeDeclaration.createOrNull(it) != null }
                }
                .mapTo(CachedSortedSet({ it.node.astBasedId }, AstIdComparator)) {
                    ReferenceTypeLikeDeclaration.create(it)
                }
        }

        /**
         * Finds a method in [allExecDecls] matching the class name, method name and signature as provided by
         * [coverageClass] and [coverageMethod].
         */
        internal fun findMethodFromJacocoCoverage(
            allExecDecls: List<ExecutableDeclaration<*>>,
            coverageClass: JacocoXML.Class,
            coverageMethod: JacocoXML.Method,
            context: ReducerContext
        ): ExecutableDeclaration<*>? {
            val filtExecDecls = allExecDecls.filterNot { it is ExecutableDeclaration.FieldVariable }
            if (filtExecDecls.isEmpty()) {
                return null
            }

            val bytecodeMethod = BytecodeMethod(
                coverageClass.name.replace('/', '.'),
                coverageMethod.name,
                coverageMethod.desc,
                coverageMethod.counters.getCounterForType(JacocoXML.CounterType.INSTRUCTION).covered > 0
            )

            val resolvedContainerDecl = findClassFromJacocoCoverage(
                findCallableContainers(allExecDecls).toList(),
                coverageClass
            )?.let {
                when (it) {
                    is ReferenceTypeLikeDeclaration.AnonClassDecl -> {
                        JavaParserAnonymousClassDeclaration(it.node, context.typeSolver)
                    }

                    is ReferenceTypeLikeDeclaration.EnumConstDecl -> {
                        context.resolveDeclaration<ResolvedEnumConstantDeclaration>(it.node)
                    }

                    else -> {
                        context.resolveDeclaration<ResolvedReferenceTypeDeclaration>(it.node)
                    }
                }
            } ?: error("Cannot find type declaration matching ${coverageClass.name}")

            val mappedMethod = when (bytecodeMethod.methodName) {
                "<init>" -> {
                    when (resolvedContainerDecl) {
                        is ResolvedReferenceTypeDeclaration -> {
                            findResolvedCtorMatchingBytecode(bytecodeMethod, resolvedContainerDecl, context)
                                ?.toTypedAstOrNull<ConstructorDeclaration>(context)
                                ?.let { ExecutableDeclaration.create(it) }
                        }

                        is ResolvedEnumConstantDeclaration -> null

                        else -> unreachable()
                    }
                }

                "<clinit>" -> null

                else -> {
                    when (resolvedContainerDecl) {
                        is ResolvedReferenceTypeDeclaration -> {
                            findResolvedMethodMatchingBytecode(bytecodeMethod, resolvedContainerDecl, context)
                                ?.toTypedAstOrNull<MethodDeclaration>(context)
                                ?.let { ExecutableDeclaration.create(it) }
                        }

                        is ResolvedEnumConstantDeclaration -> {
                            findResolvedMethodMatchingBytecode(bytecodeMethod, resolvedContainerDecl, context)
                                ?.toTypedAstOrNull<MethodDeclaration>(context)
                                ?.let { ExecutableDeclaration.create(it) }
                        }

                        else -> unreachable()
                    }
                }
            }

            return mappedMethod
        }
    }
}