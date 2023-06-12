package com.derppening.researchprojecttoolkit.util

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import com.derppening.researchprojecttoolkit.defects4j.ClassFilter
import com.derppening.researchprojecttoolkit.model.LoadedClasses
import com.derppening.researchprojecttoolkit.tool.facade.FuzzySymbolSolver
import com.derppening.researchprojecttoolkit.tool.facade.typesolver.PartitionedTypeSolver
import com.derppening.researchprojecttoolkit.util.posix.ProcessOutput
import com.derppening.researchprojecttoolkit.util.posix.runProcess
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.utils.SourceRoot
import hk.ust.cse.castle.toolkit.jvm.jsl.PredicatedFileCollector
import org.dom4j.Document
import org.dom4j.io.SAXReader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Duration
import java.util.*
import java.util.stream.Collectors
import kotlin.io.path.*

/**
 * A Defects4J workspace.
 */
class Defects4JWorkspace(
    val workspace: Path,
    val d4jRoot: Path,
    var cachedRevision: ProjectRev?,
    private val javacVersionOverride: JavacVersionOverride
) {

    data class JavacVersionOverride(
        val sourceVersion: JavaRelease?,
        val targetVersion: JavaRelease?
    ) {

        constructor(releaseVersion: JavaRelease?) : this(releaseVersion, releaseVersion)

        init {
            if (sourceVersion != null && targetVersion != null) {
                check(targetVersion >= sourceVersion) {
                    "Source Level $sourceVersion must be greater or equal to Target Level $targetVersion"
                }
            }
        }

        companion object {

            /**
             * Unchanged from the version as declared in the build file.
             */
            val UNCHANGED = JavacVersionOverride(null)

            fun fromCmdline(release: JavaRelease?, source: JavaRelease?, target: JavaRelease?): JavacVersionOverride =
                if (release != null) JavacVersionOverride(release) else JavacVersionOverride(source, target)
        }
    }

    /**
     * ID of the project.
     */
    enum class ProjectID(private val validBugRange: Iterable<Int>) {
        CHART(1..26),
        CLI((1..5) + (7..40)),
        CLOSURE((1..62) + (64..92) + (94..176)),
        CODEC(1..18),
        COLLECTIONS(25..28),
        COMPRESS(1..47),
        CSV(1..16),
        GSON(1..18),
        JACKSON_CORE(1..26),
        JACKSON_DATABIND(1..112),
        JACKSON_XML(1..6),
        JSOUP(1..93),
        JX_PATH(1..22),
        LANG((1..1) + (3..65)),
        MATH(1..106),
        MOCKITO(1..38),
        TIME((1..20) + (22..27));

        /**
         * Returns a [Set] of numbers representing the valid bug IDs of the project.
         */
        val projectValidBugs: Set<Int>
            get() = validBugRange.toSet()

        /**
         * The string representation of the project as accepted by the defects4j command.
         */
        override fun toString(): String {
            return name
                .split("_")
                .joinToString("") {
                    it.lowercase(Locale.ENGLISH)
                        .replaceFirstChar { it.titlecase(Locale.ENGLISH) }
                }
        }

        companion object {

            fun fromString(str: String): ProjectID =
                checkNotNull(ProjectID.values().associateBy { it.toString() }[str]) {
                    "Unknown project ID $str"
                }
        }
    }

    enum class BugVersion {
        BUGGY, FIXED;

        /**
         * The string representation of the bug version as accepted by the defects4j command.
         */
        override fun toString(): String {
            return name[0].lowercase()
        }
    }

    data class VersionID(
        val id: Int,
        val version: BugVersion
    ) {

        /**
         * The string representation of the version ID as accepted by the defects4j command.
         */
        override fun toString(): String =
            "$id$version"

        companion object {

            fun fromString(str: String): VersionID {
                val id = str.takeWhile { it.isDigit() }.toInt()
                val version = when (val versionChar = str.dropWhile { it.isDigit() }.first()) {
                    'b' -> BugVersion.BUGGY
                    'f' -> BugVersion.FIXED
                    else -> error("Unknown bug version $versionChar")
                }

                return VersionID(id, version)
            }
        }
    }

    /**
     * Convenience structure which bundles [ProjectID] and [VersionID], allowing a project revision to be uniquely
     * identified.
     */
    data class ProjectRev(
        val projectId: ProjectID,
        val versionId: VersionID
    ) {

        constructor(projectId: ProjectID, version: Int, type: BugVersion) :
                this(projectId, VersionID(version, type))

        /**
         * @return Bug-specific additional classpaths that are not outputted from Defects4J.
         */
        fun getAdditionalClasspaths(d4jWorkspace: Defects4JWorkspace): String {
            return buildList {
                if (projectId == ProjectID.MATH) {
                    add(d4jWorkspace.d4jRoot.resolve("framework/projects/Math/lib/commons-discovery-0.5.jar"))
                }
            }.joinToString(":")
        }

        fun getAdditionalSourceRoots(d4jWorkspace: Defects4JWorkspace): List<Path> {
            return buildList {
                if (projectId == ProjectID.CHART) {
                    add(Path("experimental"))
                }
            }.map { d4jWorkspace.workspace.resolve(it) }
        }

        /**
         * @return Bug-specific additional source-based classpaths.
         */
        fun getAdditionalSourceClasspaths(d4jWorkspace: Defects4JWorkspace): String {
            return buildList {
                if (projectId == ProjectID.CLOSURE) {
                    if (versionId.id != 106) {
                        add(d4jWorkspace.workspace.resolve("gen"))
                    }
                }
            }.joinToString(":")
        }

        /**
         * @return The type solver associated with this project revision.
         */
        fun getTypeSolver(
            d4jWorkspace: Defects4JWorkspace,
            includeJreSolverInLibrary: Boolean = false
        ): PartitionedTypeSolver {
            val sourceRoots = listOf(Property.DIR_SRC_CLASSES, Property.DIR_SRC_TESTS)
                .map { d4jWorkspace.export(it) }
                .map { d4jWorkspace.workspace.resolve(it) }
            val classpath = d4jWorkspace.getTestClasspath(this)
                .split(":")
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(":")
            val srcClasspath = getAdditionalSourceClasspaths(d4jWorkspace)

            val sourceRootTypeSolvers = getTypeSolversForSourceRoot(sourceRoots)
            val classpathTypeSolvers = getTypeSolversForClasspath(classpath)
            val sourcesClasspathTypeSolvers = getTypeSolversForSourceRoot(srcClasspath.split(':').map { Path(it) })

            val librarySolvers = (classpathTypeSolvers + sourcesClasspathTypeSolvers + jreTypeSolver.takeIf { includeJreSolverInLibrary })
                .filterNotNull()

            return PartitionedTypeSolver(
                sourceRootTypeSolvers,
                librarySolvers,
                true
            )
        }

        /**
         * @return The symbol solver associated with this project revision.
         */
        fun getSymbolSolver(
            d4jWorkspace: Defects4JWorkspace,
            includeJreSolverInLibrary: Boolean = false
        ): FuzzySymbolSolver = FuzzySymbolSolver(getTypeSolver(d4jWorkspace, includeJreSolverInLibrary))

        override fun toString(): String = "$projectId-$versionId"

        companion object {

            fun fromString(str: String): ProjectRev {
                val (projectId, versionId) = str.split('-')
                return ProjectRev(
                    ProjectID.fromString(projectId),
                    VersionID.fromString(versionId)
                )
            }
        }
    }

    /**
     * A bug-specific property which can be outputted via `defects4j export`.
     */
    enum class Property {
        CLASSES_MODIFIED,
        CLASSES_RELEVANT,
        CP_COMPILE,
        CP_TEST,
        DIR_SRC_CLASSES,
        DIR_BIN_CLASSES,
        DIR_SRC_TESTS,
        DIR_BIN_TESTS,
        TESTS_ALL,
        TESTS_RELEVANT,
        TESTS_TRIGGER;

        val propertyName: String
            get() = name.lowercase(Locale.ENGLISH).replace('_', '.')
    }

    val cachedCheckedOutRevision: ProjectRev?
        get() = cachedRevision

    //<editor-fold desc="Defects4J Compat Methods">

    /**
     * Port of `read_config_file` in `.../framework/core/Utils.pm`.
     */
    @Suppress("NAME_SHADOWING")
    private fun readConfigFile(file: Path, keySeparator: String? = null): Map<String, String> {
        val keySeparator = keySeparator ?: "="

        return file.bufferedReader().use { reader ->
            reader.lineSequence()
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .map {
                    it.split(keySeparator)
                        .let { it[0] to it[1] }
                        .mapBoth { it.trim() }
                }
                .associate { (k, v) -> k to v }
        }
    }

    /**
     * Port of `write_config_file` in `.../framework/core/Utils.pm`.
     */
    private fun writeConfigFile(file: Path, hash: Map<String, String>) {
        val newConf = hash.toMutableMap()
        if (file.isRegularFile()) {
            val oldConf = readConfigFile(file)
            oldConf.forEach { (k, v) ->
                newConf.putIfAbsent(k, v)
            }
        }
        file.bufferedWriter().use { writer ->
            writer.appendLine("#File automatically generated by Defects4J")
            for ((k, v) in newConf.toSortedMap()) {
                writer.appendLine("$k=$v")
            }
        }
    }

    private fun coverageInstrument(instrumentClasses: Path): ProcessOutput {
        val workDir = workspace

        if (instrumentClasses.notExists()) {
            error("Instrument classes file '$instrumentClasses' does not exist!")
        }

        val classes = instrumentClasses.useLines { it.toList() }.filter { it.isNotEmpty() }

        check(classes.isNotEmpty()) { "No classes to instrument found!" }
        val classesAndInners = classes
            .map { it.replace('.', '/') }
            .map { it.trim() }
            .flatMap { listOf("$it.class", "$it$*.class") }

        val list = classesAndInners.joinToString(",")
        val config = mapOf("d4j.classes.instrument" to list)
        writeConfigFile(workDir.resolve("defects4j.build.properties"), config)

        return antCallComp("coverage.instrument")
    }

    /**
     * Port of `_ant_call` in `.../framework/core/Project.pm`.
     */
    private fun antCall(
        target: String,
        options: List<String>,
        antCmd: String? = null,
        configure: ProcessBuilder.() -> Unit
    ): ProcessOutput {
        val cmd = buildList {
            add(antCmd ?: "ant")
            add("-f")
            add("${d4jRoot.resolve("framework/projects/defects4j.build.xml")}")
            add("-Dd4j.home=$d4jRoot")
            add("-Dd4j.dir.projects=${d4jRoot.resolve("framework/projects")}")
            add("-Dbasedir=$workspace")
            addAll(options)
            add(target)
        }

        val processOutput = runProcess(
            cmd,
            timeout = null
        ) {
            configure()

            directory(workspace.toFile())
            environment()["JAVA_TOOL_OPTIONS"] = "-Dfile.encoding=UTF-8"
            redirectErrorStream(true)
        }

        processOutput.checkNormalTermination(cmd)

        return processOutput
    }

    /**
     * Port of `_ant_call_comp` in `.../framework/core/Project.pm`.
     */
    @Suppress("NAME_SHADOWING")
    private fun antCallComp(
        target: String,
        options: List<String> = emptyList(),
        antCmd: String? = null,
        configure: ProcessBuilder.() -> Unit = {}
    ): ProcessOutput {
        val options = listOf("-Dbuild.compiler=javac1.7") + options
        val antCmd = antCmd ?: "/defects4j/major/bin/ant"

        return antCall(target, options, antCmd, configure)
    }

    //</editor-fold>

    private fun runCmd(
        args: List<String>,
        allowFailure: Boolean,
        timeout: Duration?,
        redirectErrorStream: Boolean = true,
        configure: ProcessBuilder.() -> Unit = {}
    ): ProcessOutput {
        val fullCmdline = listOf("$d4jRoot/framework/bin/defects4j") + args + listOf("-w", workspace.toString())

        val processOutput = runProcess(
            fullCmdline,
            timeout?.takeUnless { it.isZero || it.isNegative }
        ) {
            configure()

            environment()["JAVA_TOOL_OPTIONS"] = "-Dfile.encoding=UTF-8"
            redirectErrorStream(redirectErrorStream)
        }

        if (!allowFailure) {
            processOutput.checkNormalTermination(fullCmdline)
        }

        return processOutput
    }

    private fun ProcessOutput.checkNormalTermination(cmdline: List<String>) {
        if (exitCode.isFailure) {
            LOGGER.error("Failed to run \"${cmdline.joinToString(" ")}\"")
            if (stdout.isNotEmpty()) {
                LOGGER.error("Stdout:")
                stdout.forEach(LOGGER::error)
            }
            if (stderr.isNotEmpty()) {
                LOGGER.error("Stderr:")
                stderr.forEach(LOGGER::error)
            }

            throw RuntimeException("Failed to run command")
        }
    }

    /**
     * Checks out a bug revision in the defects4j repository.
     */
    fun checkout(projectId: ProjectID, versionId: VersionID, timeout: Duration?, configure: ProcessBuilder.() -> Unit = {}) {
        runCmd(listOf("checkout", "-p", projectId.toString(), "-v", versionId.toString()), false, timeout, configure = configure)

        cachedRevision = ProjectRev(projectId, versionId)
    }

    fun checkout(projectRev: ProjectRev, timeout: Duration?, configure: ProcessBuilder.() -> Unit = {}) =
        checkout(projectRev.projectId, projectRev.versionId, timeout, configure)

    /**
     * Generates a command for `defects4j export`.
     *
     * Note that the `defects4j` component will not be generated.
     */
    private fun generateExportCmd(property: Property, outputFile: Path?): List<String> =
        buildList {
            add("export")
            add("-p")
            add(property.propertyName)

            outputFile?.also {
                add("-o")
                add(outputFile.toString())
            }
        }

    /**
     * Reads the value of an exported property, or `null` if the `export` command failed to run.
     *
     * @param allowFailure If `true`, does not emit a stack trace when the command fails.
     */
    fun exportOrNull(property: Property, allowFailure: Boolean = false, timeout: Duration = Duration.ZERO, configure: ProcessBuilder.() -> Unit = {}): String? {
        return TemporaryPath.createFile().use { tempFile ->
            val p = runCmd(generateExportCmd(property, tempFile.path), allowFailure, timeout, configure = configure)

            if (p.exitCode.isFailure) {
                return null
            }
            tempFile.path.readText()
        }
    }

    /**
     * Reads the value of an exported property.
     *
     * If the `export` command failed to run, returns an empty string.
     *
     * @param allowFailure If `true`, does not emit a stack trace when the command fails.
     */
    fun export(property: Property, allowFailure: Boolean = false, timeout: Duration = Duration.ZERO, configure: ProcessBuilder.() -> Unit = {}): String {
        return TemporaryPath.createFile().use { tempFile ->
            runCmd(generateExportCmd(property, tempFile.path), allowFailure, timeout, configure = configure)

            tempFile.path.readText()
        }
    }

    /**
     * Runs compilation on the project.
     */
    fun compile(timeout: Duration = Duration.ZERO, configure: ProcessBuilder.() -> Unit = {}): ProcessOutput {
        return runCmd(listOf("compile"), true, timeout, configure = configure)
    }

    /**
     * Runs tests on the project.
     */
    fun test(runRelevantOnly: Boolean = true, testCases: Collection<TestCase>? = null, timeout: Duration = Duration.ZERO, configure: ProcessBuilder.() -> Unit = {}): ProcessOutput {
        val args = buildList {
            add("test")

            when {
                runRelevantOnly -> add("-r")
                testCases != null -> {
                    testCases.forEach {
                        add("-t")
                        add(it.toString())
                    }
                }
            }
        }

        return runCmd(args, true, timeout, configure = configure)
    }

    /**
     * Runs code coverage analysis.
     */
    fun coverage(
        runRelevantOnly: Boolean = false,
        testCases: Collection<TestCase>? = null,
        testSuite: Path? = null,
        instrumentClasses: Path? = null,
        timeout: Duration = Duration.ZERO,
        configure: ProcessBuilder.() -> Unit = {}
    ): ProcessOutput {
        val args = buildList {
            add("coverage")

            when {
                runRelevantOnly -> add("-r")
                testCases != null -> {
                    testCases.forEach {
                        add("-t")
                        add(it.toString())
                    }
                }
            }

            if (testSuite != null) {
                add("-s")
                add(testSuite.toString())
            }
            if (instrumentClasses != null) {
                add("-i")
                add(instrumentClasses.toString())
            }
        }

        return runCmd(args, false, timeout, configure = configure)
    }

    /**
     * Monitors the classloader while running a test class or test case.
     *
     * @param testUnits Names of test classes or test cases to execute.
     * @return [ProcessOutput] instance encapsulating the created process. The loaded class output will be stored in
     * [ProcessOutput.stdout].
     */
    fun monitorTest(
        testUnits: Collection<TestUnit>,
        timeout: Duration = Duration.ZERO,
        configure: ProcessBuilder.() -> Unit = {}
    ): ProcessOutput = monitorTest(testUnits, timeout, false, configure).second

    /**
     * Monitors the classloader while running a test class or test case.
     *
     * @param testUnits Names of test classes or test cases to execute.
     * @param outputAll Whether to output names of all classes. If `false`, only output names of top-level classes.
     * @return [ProcessOutput] instance encapsulating the created process. The loaded class output will be stored in
     * [ProcessOutput.stdout].
     */
    fun monitorTest(
        testUnits: Collection<TestUnit>,
        timeout: Duration = Duration.ZERO,
        outputAll: Boolean = false,
        configure: ProcessBuilder.() -> Unit = {}
    ): Pair<LoadedClasses?, ProcessOutput> {
        val processOut = runCmd(
            listOf("monitor.test") + testUnits.flatMap { listOf("-t", it.toString()) },
            true,
            timeout,
            false,
            configure = configure
        )

        val loadedClasses = if (outputAll) {
            val classes = workspace.resolve("classes.log")
            if (classes.isRegularFile()) {
                val dirBinClasses = export(Property.DIR_BIN_CLASSES)
                    .let { "${workspace.resolve(it).toUri().toURL()}" }
                val dirBinTests = export(Property.DIR_BIN_TESTS)
                    .let { "${workspace.resolve(it).toUri().toURL()}" }

                classes.bufferedReader()
                    .useLines { seq ->
                        seq.filter { it.startsWith("[Loaded") && it.endsWith("]") }
                            .map { it.removePrefix("[Loaded").removeSuffix("]") }
                            .filter { it.contains(" from ") }
                            .map { it.split(" from ") }
                            .onEach { check(it.size == 2) { "Expected 2 elements, got $it" } }
                            .map { it[0].trim() to it[1].trim() }
                            .groupBy { it.second }
                    }
                    .mapValues { (_, p) -> p.map { it.first } }
                    .filterKeys { it in arrayOf(dirBinClasses, dirBinTests) }
                    .let {
                        LoadedClasses(it[dirBinClasses].orEmpty(), it[dirBinTests].orEmpty())
                    }
            } else null
        } else {
            val stdout = processOut.stdout

            val sourceClasses = stdout
                .dropWhile { it.trim() == "Loaded source classes:" }
                .takeWhile { it.startsWith("  - ") }
                .map { it.removePrefix("  - ").trim() }
            val testClasses = stdout
                .dropWhileAndIncluding { it.trim() != "Loaded test classes:" }
                .takeWhile { it.startsWith("  - ") }
                .map { it.removePrefix("  - ").trim() }

            LoadedClasses(sourceClasses, testClasses)
        }

        return loadedClasses to processOut
    }

    /**
     * Rewrites the [xmlFile] using the [transform] function.
     */
    private fun rewriteXmlFile(xmlFile: Path, transform: Document.() -> Unit): Path {
        check(xmlFile.extension == "xml")

        val xmlDocument = xmlFile
            .also { check(it.isRegularFile()) }
            .bufferedReader()
            .use {
                SAXReader.createDefault().read(it)
            }

        xmlDocument.apply(transform)

        xmlFile
            .bufferedWriter()
            .use { writer ->
                xmlDocument.write(writer)
            }

        return xmlFile
    }

    /**
     * Patches `defects4j.build.xml` to enable coverage data to be collected for all classes.
     */
    fun patchD4JCoverage() {
        LOGGER.debug("Patching Defects4J to enable coverage information for all classes")

        val d4jBuildXml = d4jRoot.resolve("framework/projects/defects4j.build.xml")
            .also { check(it.isRegularFile()) }
            .bufferedReader()
            .use {
                SAXReader.createDefault().read(it)
            }

        val coverageInstrumentTask = d4jBuildXml.rootElement
            .also { check(it.name == "project" && it.attributeValue("name") == "Defects4J") }
            .elements("target")
            .single { it.attributeValue("name") == "coverage.instrument" }

        val coverageInstrumentTaskDeps = coverageInstrumentTask.attributeValue("depends")
        if ("compile.tests" !in coverageInstrumentTaskDeps.split(",")) {
            coverageInstrumentTask.attribute("depends")
                .value = "${coverageInstrumentTaskDeps},compile.tests"
        }

        val coberturaInstrumentSettings = coverageInstrumentTask.element("cobertura-instrument")

        val fileset = coberturaInstrumentSettings.elements("fileset")
        if ("\${test.classes.dir}" !in fileset.map { it.attributeValue("dir") }) {
            coberturaInstrumentSettings.addElement("fileset")
                .addAttribute("dir", "\${test.classes.dir}")
                .addAttribute("includes", "\${d4j.classes.instrument}")
        }

        val auxClasspath = coberturaInstrumentSettings.element("auxClasspath")
        if ("d4j.test.classpath" !in auxClasspath.elements("path").map { it.attributeValue("refid") }) {
            auxClasspath.addElement("path")
                .addAttribute("refid", "d4j.test.classpath")
        }

        d4jRoot.resolve("framework/projects/defects4j.build.xml")
            .bufferedWriter()
            .use { writer ->
                d4jBuildXml.write(writer)
            }
    }

    /**
     * Patches `defects4j.build.xml` to use a newer version of Cobertura.
     */
    private fun patchForNewCobertura() {
        if (d4jRoot.resolve("framework/projects/lib/cobertura-2.0.3.jar").exists()) {
            return
        }

        LOGGER.debug("Patching Defects4J to use newer version of Cobertura")

        val d4jBuildXmlFile = d4jRoot.resolve("framework/projects/defects4j.build.xml")

        rewriteXmlFile(d4jBuildXmlFile) {
            rootElement.also { check(it.name == "project" && it.attributeValue("name") == "Defects4J") }

            rootElement
                .elements("property")
                .singleOrNull { it.attributeValue("name") == "cobertura.jar" }
                ?.attribute("value")
                ?.let {
                    it.value = it.value.replace("cobertura-2.0.3.jar", "cobertura-2.1.1.jar")
                }

            val sl4fjPath = "\${d4j.home}/framework/projects/lib/cobertura-lib/slf4j-api-1.7.5.jar"
            val junitClasspath = rootElement
                .elements("target")
                .single { it.attributeValue("name") == "run.dev.tests" }
                .element("junit")
                .element("classpath")
            if (junitClasspath.elements("pathelement").none { it.attributeValue("path") == sl4fjPath }) {
                junitClasspath.addElement("pathelement")
                    .addAttribute("path", sl4fjPath)
            }

            rootElement
                .elements("path")
                .single { it.attributeValue("id") == "cobertura.classpath" }
                .element("fileset")
                .elements("include")
                .singleOrNull { it.attributeValue("name") == "cobertura-2.0.3.jar" }
                ?.attribute("name")
                ?.let { it.value = "cobertura-2.1.1.jar" }
        }
    }

    /**
     * Patches `defects4j.build.xml` to enable forking when executing test cases.
     */
    private fun enableD4jTestFork() {
        // https://github.com/rjust/defects4j/pull/485
        LOGGER.debug("Patching Defects4J to enable forking during test run")

        val d4jBuildXmlFile = d4jRoot.resolve("framework/projects/defects4j.build.xml")

        rewriteXmlFile(d4jBuildXmlFile) {
            val junit = rootElement
                .also { check(it.name == "project" && it.attributeValue("name") == "Defects4J") }
                .elements("target")
                .single { it.attributeValue("name") == "run.dev.tests" }
                .element("junit")

            if (junit.attributeValue("fork") == "no") {
                junit.attribute("fork").value = "on"
                junit.addAttribute("forkmode", "once")
                junit.addAttribute("timeout", Duration.ofMinutes(3).toMillis().toString())
            }
        }
    }

    /**
     * Resets any changes made in [d4jRoot], removes any existing files in [workspace], and checks out a fresh revision
     * of [projectRev] to [workspace].
     */
    fun initAndCheckout(projectRev: ProjectRev) {
        LOGGER.info("Resetting initial state of {} to {}", projectRev, workspace)

        LOGGER.debug("Resetting Defect4J working tree")
        runProcess("git", "reset", "--hard") {
            directory(d4jRoot.toFile())
        }
        runProcess("git", "clean", "-fd") {
            directory(d4jRoot.toFile())
        }

        LOGGER.debug("Enabling processing forking for test execution")
        enableD4jTestFork()

        LOGGER.debug("Patching Defects4J build script to use updated Cobertura")
        patchForNewCobertura()

        if (workspace.isDirectory()) {
            LOGGER.debug("Restoring {} working tree before checkout", workspace)
            runProcess("git", "restore", ".") {
                directory(workspace.toFile())
            }
        }

        LOGGER.debug("Checking out {} to {}", projectRev, workspace)
        checkout(projectRev, timeout = null)

        LOGGER.debug("Resetting {} working tree", workspace)
        runProcess("git", "reset", "--hard") {
            directory(workspace.toFile())
        }
        runProcess("git", "clean", "-fdX") {
            directory(workspace.toFile())
        }
    }

    /**
     * Convenience method which executes [initAndCheckout], [patchProjectWideFlags], [patchProjectSpecificFlags], and
     * [doBeforeAnalyze] if [forAnalysis] is `true`.
     */
    fun initAndPrepare(projectRev: ProjectRev, patchJavaVersion: Boolean = true, forAnalysis: Boolean = true) {
        initAndCheckout(projectRev)
        patchProjectWideFlags(patchJavaVersion)
        patchProjectSpecificFlags(projectRev, patchJavaVersion)
        if (forAnalysis) {
            doBeforeAnalyze(projectRev)
        }
    }

    /**
     * Returns the compile classpath for the current checked out project, including additional classpaths not exported by
     * `d4j export`.
     *
     * @param projectRev The project revision of this checked out project.
     */
    fun getCompileClasspath(projectRev: ProjectRev, ignoreBaseline: Boolean = false): String {
        val filePath = ToolOutputDirectory(GlobalConfiguration.INSTANCE.cmdlineOpts.baselineDir, projectRev, readOnly = true)
            .getCompileClasspathPath()
        val additionalCp = projectRev.getAdditionalClasspaths(this)

        val bundledCpRes = filePath.takeIf { !ignoreBaseline && it.isRegularFile() }
        if (bundledCpRes != null) {
            return bundledCpRes
                .bufferedReader()
                .use { it.readText().trim() }
                .split(":")
                .let { it + additionalCp.split(":") }
                .distinct()
                .filter { it.isNotBlank() }
                .joinToString(":")
        }

        val exportedCp = exportOrNull(Property.CP_COMPILE)
        if (exportedCp != null) {
            return exportedCp.trim()
                .split(":")
                .let { it + additionalCp.split(":") }
                .distinct()
                .filter { it.isNotBlank() }
                .joinToString(":")
        }

        error("Cannot find compile classpath in resources and unable export compile classpath from project")
    }

    /**
     * Returns the test classpath for the current checked out project, including additional classpaths not exported by
     * `d4j export`.
     *
     * @param projectRev The project revision of this checked out project.
     */
    fun getTestClasspath(projectRev: ProjectRev, ignoreBaseline: Boolean = false): String {
        val filePath = ToolOutputDirectory(GlobalConfiguration.INSTANCE.cmdlineOpts.baselineDir, projectRev, readOnly = true)
            .getTestClasspathPath()
        val additionalCp = projectRev.getAdditionalClasspaths(this)

        val bundledCpRes = filePath.takeIf { !ignoreBaseline && it.isRegularFile() }
        if (bundledCpRes != null) {
            return bundledCpRes
                .bufferedReader()
                .use { it.readText().trim() }
                .split(":")
                .let { it + additionalCp.split(":") }
                .distinct()
                .filter { it.isNotBlank() }
                .joinToString(":")
        }

        val exportedCp = exportOrNull(Property.CP_TEST)
        if (exportedCp != null) {
            return exportedCp.trim()
                .split(":")
                .let { it + additionalCp.split(":") }
                .distinct()
                .filter { it.isNotBlank() }
                .joinToString(":")
        }

        error("Cannot find test classpath in resources and unable export test classpath from project")
    }

    /**
     * [Set] of triggering [TestCase] for the current checked out project.
     */
    val triggeringTests: Set<TestCase>
        get() = export(Property.TESTS_TRIGGER)
            .split('\n')
            .filter { it.isNotBlank() }
            .map { TestCase.fromD4JQualifiedName(it) }
            .toSet()

    /**
     * Reads the content from [path], applies the [transform] function on each line, then writes back to the same file.
     */
    private fun readTransformWriteFile(path: Path, transform: (String) -> String) {
        return path.bufferedReader()
            .useLines { it.toList() }
            .map { transform(it) }
            .let { lines ->
                path.bufferedWriter().use { writer ->
                    lines.forEach(writer::appendLine)
                }
            }
    }

    /**
     * Applies project-specific flags to the project.
     */
    fun patchProjectSpecificFlags(projectRev: ProjectRev, patchJavaVersion: Boolean) {
        LOGGER.info("Patching project-specific source/target compatibility flags")

        val effectiveSourceVersion = javacVersionOverride.sourceVersion
        val effectiveTargetVersion = when {
            javacVersionOverride.targetVersion != null -> javacVersionOverride.targetVersion
            javacVersionOverride.sourceVersion != null -> javacVersionOverride.sourceVersion
            else -> null
        }

        when (projectRev.projectId) {
            Defects4JWorkspace.ProjectID.CHART -> {
                if (patchJavaVersion) {
                    val sourceRegex = Regex("source=\".+\"")
                    val targetRegex = Regex("target=\".+\"")

                    readTransformWriteFile(Path("/defects4j/framework/projects/Chart/Chart.build.xml")) { line ->
                        line.let {
                            if (effectiveTargetVersion != null) {
                                it.replace(targetRegex, "target=\"$effectiveTargetVersion\"")
                            } else it
                        }.let {
                            if (effectiveSourceVersion != null) {
                                it.replace(sourceRegex, "source=\"$effectiveSourceVersion\"")
                            } else it
                        }
                    }
                }
            }
            Defects4JWorkspace.ProjectID.CLOSURE -> {
                if (patchJavaVersion) {
                    PredicatedFileCollector(workspace)
                        .collect { it.fileName == Path("build.properties") }
                        .forEach { file ->
                            val sourceLevelRegex = Regex("source-level: .+")
                            val targetJvmRegex = Regex("target-jvm: .+")

                            readTransformWriteFile(file) { line ->
                                line.let {
                                    if (effectiveTargetVersion != null) {
                                        it.replace(targetJvmRegex, "target-jvm: $effectiveTargetVersion")
                                    } else it
                                }.let {
                                    if (effectiveSourceVersion != null) {
                                        it.replace(sourceLevelRegex, "source-level: $effectiveSourceVersion")
                                    } else it
                                }
                            }
                        }

                    PredicatedFileCollector(workspace)
                        .collect { it.fileName == Path("build.xml") }
                        .forEach { file ->
                            val javacSourceRegex = Regex("<property name=\"ant\\.build\\.javac\\.source\" +value=\".+\" />")
                            val javacTargetRegex = Regex("<property name=\"ant\\.build\\.javac\\.target\" +value=\".+\" />")

                            readTransformWriteFile(file) { line ->
                                line.let {
                                    if (effectiveSourceVersion != null) {
                                        it.replace(
                                            javacSourceRegex,
                                            "<property name=\"ant.build.javac.source\" value=\"$effectiveSourceVersion\" />"
                                        )
                                    } else it
                                }.let {
                                    if (effectiveTargetVersion != null) {
                                        it.replace(
                                            javacTargetRegex,
                                            "<property name=\"ant.build.javac.target\" value=\"$effectiveTargetVersion\" />"
                                        )
                                    } else it
                                }
                            }
                        }
                }

                val workspaceJarJar = workspace.resolve("lib/jarjar.jar")
                if (workspaceJarJar.isRegularFile()) {
                    val d4jClosureLib = d4jRoot.resolve("framework/projects/Closure/lib")
                    if (d4jClosureLib.isDirectory()) {
                        d4jClosureLib.resolve("jarjar-1.3.jar").copyTo(workspaceJarJar, overwrite = true)
                    }
                }
            }
            Defects4JWorkspace.ProjectID.MATH -> {
                if (patchJavaVersion) {
                    val compileSourceRegex = Regex("<property name=\"compile\\.source\" +value=\".+\"/>")
                    val compileTargetRegex = Regex("<property name=\"compile\\.target\" +value=\".+\"/>")

                    readTransformWriteFile(workspace.resolve("build.xml")) { line ->
                        line.let {
                            if (effectiveSourceVersion != null) {
                                it.replace(
                                    compileSourceRegex,
                                    "<property name=\"compile.source\" value=\"$effectiveSourceVersion\"/>"
                                )
                            } else it
                        }.let {
                            if (effectiveTargetVersion != null) {
                                it.replace(
                                    compileTargetRegex,
                                    "<property name=\"compile.target\" value=\"$effectiveTargetVersion\"/>"
                                )
                            } else it
                        }
                    }
                }

                // Update build.xml to download JARs from Maven central
                rewriteXmlFile(workspace.resolve("build.xml")) {
                    rootElement
                        .elements("target")
                        .singleOrNull { it.attributeValue("name") == "get-dep-commons-logging.jar" }
                        ?.element("get")
                        ?.attribute("src")
                        ?.apply {
                            if (value == "http://www.ibiblio.org/maven/commons-logging/jars/commons-logging-1.0.4.jar") {
                                value = "https://repo1.maven.org/maven2/commons-logging/commons-logging/1.0.4/commons-logging-1.0.4.jar"
                            }
                        }

                    rootElement
                        .elements("target")
                        .singleOrNull { it.attributeValue("name") == "get-dep-commons-discovery.jar" }
                        ?.element("get")
                        ?.attribute("src")
                        ?.apply {
                            if (value == "http://www.ibiblio.org/maven/commons-discovery/jars/commons-discovery-0.2.jar") {
                                value = "https://repo1.maven.org/maven2/commons-discovery/commons-discovery/0.4/commons-discovery-0.4.jar"
                            }
                        }
                }
            }
            else -> {}
        }
    }

    /**
     * Applies project-wide flags to the project.
     */
    fun patchProjectWideFlags(patchJavaVersion: Boolean) {
        LOGGER.info("Patching project-wide source/target compatibility flags")
        readTransformWriteFile(Path("/defects4j/framework/core/Project.pm")) {
            it.replace(Regex("javac\\d+(\\.\\d+)?"), "modern")
        }

        if (patchJavaVersion) {
            val effectiveSourceVersion = javacVersionOverride.sourceVersion
            val effectiveTargetVersion = when {
                javacVersionOverride.targetVersion != null -> javacVersionOverride.targetVersion
                javacVersionOverride.sourceVersion != null -> javacVersionOverride.sourceVersion
                else -> null
            }

            PredicatedFileCollector(workspace)
                .collect { it.fileName == Path("build.xml") || it.fileName == Path("maven-build.xml") }
                .forEach { file ->
                    val targetRegex = Regex("target=\"\\d+(\\.\\d+)?\"")
                    val sourceRegex = Regex("source=\"\\d+(\\.\\d+)?\"")

                    readTransformWriteFile(file) { line ->
                        line.let {
                            if (effectiveTargetVersion != null) {
                                it.replace(targetRegex, "target=\"$effectiveTargetVersion\"")
                            } else it
                        }.let {
                            if (effectiveSourceVersion != null) {
                                it.replace(sourceRegex, "source=\"$effectiveSourceVersion\"")
                            } else it
                        }
                    }
                }
            PredicatedFileCollector(workspace)
                .collect { it.fileName == Path("maven-build.properties") }
                .forEach { file ->
                    val javacSrcRegex = Regex("javac\\.src\\.version=\\d+(\\.\\d+)?")
                    val javacTargetRegex = Regex("javac\\.target\\.version=\\d+(\\.\\d+)?")

                    readTransformWriteFile(file) { line ->
                        line.let {
                            if (effectiveSourceVersion != null) {
                                it.replace(javacSrcRegex, "javac.src.version=$effectiveSourceVersion")
                            } else it
                        }.let {
                            if (effectiveTargetVersion != null) {
                                it.replace(javacTargetRegex, "javac.target.version=$effectiveTargetVersion")
                            } else it
                        }
                    }
                }
            PredicatedFileCollector(workspace)
                .collect { it.fileName == Path("pom.xml") }
                .forEach { file ->
                    val compileSourceRegex = Regex("<maven\\.compile\\.source>.+</maven\\.compile\\.source>")
                    val compileTargetRegex = Regex("<maven\\.compile\\.target>.+</maven\\.compile\\.target>")

                    readTransformWriteFile(file) { line ->
                        line.let {
                            if (effectiveSourceVersion != null) {
                                it.replace(
                                    compileSourceRegex,
                                    "<maven.compile.source>$effectiveSourceVersion</maven.compile.source>"
                                )
                            } else it
                        }.let {
                            if (effectiveTargetVersion != null) {
                                it.replace(
                                    compileTargetRegex,
                                    "<maven.compile.target>$effectiveTargetVersion</maven.compile.target>"
                                )
                            } else it
                        }
                    }
                }
            PredicatedFileCollector(workspace)
                .collect { it.fileName == Path("default.properties") }
                .forEach { file ->
                    val compileSourceRegex = Regex("compile\\.source = \\d+(\\.\\d+)?")
                    val compileTargetRegex = Regex("compile\\.target = \\d+(\\.\\d+)?")

                    readTransformWriteFile(file) { line ->
                        line.let {
                            if (effectiveSourceVersion != null) {
                                it.replace(compileSourceRegex, "compile.source = $effectiveSourceVersion")
                            } else it
                        }.let {
                            if (effectiveTargetVersion != null) {
                                it.replace(compileTargetRegex, "compile.target = $effectiveTargetVersion")
                            } else it
                        }
                    }
                }
        }
    }

    /**
     * Runs additional commands before analyzing the project.
     */
    fun doBeforeAnalyze(projectRev: ProjectRev) {
        when (projectRev.projectId) {
            Defects4JWorkspace.ProjectID.CLI -> {
                LOGGER.info("Applying Cli-Specific Workaround: Explicitly invoke `ant get-deps`")

                antCallComp("get-deps")
            }
            Defects4JWorkspace.ProjectID.CLOSURE -> {
                when (projectRev.versionId.id) {
                    in 1..62,
                    in 107..133,
                    in 161..176 -> {
                        LOGGER.info("Applying Closure-Specific Workaround: Explicitly invoke `ant rhino-jarjar`")

                        antCallComp("rhino-jarjar")
                    }
                }
            }
            Defects4JWorkspace.ProjectID.JX_PATH -> {
                LOGGER.info("Applying JxPath-Specific Workaround: Explicitly invoke `ant get-deps`")

                antCallComp("get-deps")
            }
            Defects4JWorkspace.ProjectID.MOCKITO -> {
                when (projectRev.versionId.id) {
                    in arrayOf(1, 3, 18, 19, 20) -> {
                        LOGGER.info("Applying Mockito-Specific Workaround: Explicitly invoke `defects4j compile`")

                        compile().also { it.checkNormalTermination(listOf("defects4j", "compile")) }
                        antCallComp("clean")
                    }
                }
            }

            else -> {}
        }
    }

    /**
     * Runs coverage using Jacoco.
     *
     * The Jacoco XML will be outputted to `jacoco.xml` in [workspace].
     */
    fun coverageJacoco(
        testCases: Collection<TestCase>,
        includes: List<String>? = null,
        timeout: Duration = Duration.ZERO
    ) {
        val cp = getTestClasspath(checkNotNull(cachedCheckedOutRevision))
        val sourceDirPaths = listOf(Property.DIR_SRC_CLASSES, Property.DIR_SRC_TESTS)
            .flatMap { export(it).split("\n") }
            .filter { it.isNotBlank() }
            .map { workspace.resolve(it) }
        val classDirPaths = listOf(Property.DIR_BIN_CLASSES, Property.DIR_BIN_TESTS)
            .flatMap { export(it).split("\n") }
            .filter { it.isNotBlank() }
            .map { workspace.resolve(it) }

        LOGGER.info("Compiling project for Jacoco coverage")
        compile()

        TemporaryPath.createFile(prefix = "jacoco.", suffix = ".exec").use { execFile ->
            val jacocoJavaAgentFlag = buildString {
                append("-javaagent:")
                append(JACOCO_AGENT_JAR)
                append("=destfile=")
                append(execFile.path.toString())

                includes?.let {
                    append(",includes=")
                    append(includes.joinToString(":"))
                }
            }

            val runCmd = buildList {
                add(bootJvmExecutable.toString())

                addAll(getDefaultGcArgs())
                add(jacocoJavaAgentFlag)
                add("-cp")
                add(JUNIT_STANDALONE_JAR.toString())

                add("org.junit.platform.console.ConsoleLauncher")

                add("-cp")
                add(cp)

                testCases.forEach {
                    add("-m")
                    add(it.toJUnitMethod())
                }
            }

            LOGGER.info("Running coverage over test case(s)")
            runProcess(runCmd, timeout.takeUnless { it.isZero || it.isNegative })

            // Workaround: Clear all non-classfiles from directories passed into `--classfiles`, as Jacoco may read
            // files whose filenames contain a compression extension, such as `SHRUNK.ZIP@TEST1.XML`.
            classDirPaths.forEach { classDir ->
                PredicatedFileCollector(classDir)
                    .collect { it.isRegularFile() && it.extension != "class" }
                    .forEach { it.deleteExisting() }
            }

            val reportCmd = buildList {
                add(bootJvmExecutable.toString())

                addAll(getDefaultGcArgs())

                add("-jar")
                add(JACOCO_CLI_JAR.toString())

                add("report")
                add(execFile.path.toString())

                add("--encoding")
                add(StandardCharsets.UTF_8.toString())

                classDirPaths.forEach {
                    add("--classfiles")
                    add(it.toString())
                }
                sourceDirPaths.forEach {
                    add("--sourcefiles")
                    add(it.toString())
                }

                add("--xml")
                add(workspace.resolve("jacoco.xml").toString())

                add("--html")
                add(workspace.resolve("jacoco.html").toString())
            }

            LOGGER.info("Exporting Jacoco report")
            runProcess(reportCmd, timeout.takeUnless { it.isZero || it.isNegative })
                .also { it.checkNormalTermination(reportCmd) }
        }
    }

    fun dumpCoberturaInstrumented() {
        val instrumentedClassList = dumpInstrumentClassList(
            this,
            ClassFilter.ALL
        )

        LOGGER.info("Dumping Cobertura-instrumented classes")
        coverageInstrument(instrumentedClassList)
    }

    fun dumpJacocoInstrumented(outDir: Path, timeout: Duration = Duration.ZERO) {
        val classDirPaths = listOf(Property.DIR_BIN_CLASSES, Property.DIR_BIN_TESTS)
            .flatMap { export(it).split("\n") }
            .filter { it.isNotBlank() }
            .map { workspace.resolve(it) }

        val instrumentCmd = listOf(
            bootJvmExecutable.toString(),
            *getDefaultGcArgs().toTypedArray(),
            "-jar",
            JACOCO_CLI_JAR.toString(),
            "instrument",
            *classDirPaths.map { it.toString() }.toTypedArray(),
            "--dest",
            outDir.toString()
        )

        LOGGER.info("Dumping Jacoco-instrumented classes")
        runProcess(instrumentCmd, timeout.takeUnless { it.isZero || it.isNegative })
            .also { it.checkNormalTermination(instrumentCmd) }
    }

    companion object {

        private val LOGGER = Logger<Defects4JWorkspace>()

        private val JACOCO_LIB_JAR = Path("/jacoco")
        private val JACOCO_AGENT_JAR = JACOCO_LIB_JAR / "jacocoagent.jar"
        private val JACOCO_CLI_JAR = JACOCO_LIB_JAR / "jacococli.jar"

        private val JUNIT_LIB_DIR = Path("/junit")
        private val JUNIT_STANDALONE_JAR = JUNIT_LIB_DIR / "junit-platform-console-standalone.jar"

        private val INSTRUMENT_CLASSES = Path("instrument_classes.txt")

        private fun listInstrumentClassList(d4jWorkspace: Defects4JWorkspace, classFilter: ClassFilter): Set<String> {
            val sourceRoots = listOfNotNull(
                d4jWorkspace.export(Property.DIR_SRC_CLASSES)
                    .takeUnless { classFilter == ClassFilter.TESTS },
                d4jWorkspace.export(Property.DIR_SRC_TESTS)
                    .takeUnless { classFilter == ClassFilter.CLASSES },
            ).map {
                d4jWorkspace.workspace.resolve(it)
            }

            return sourceRoots.stream()
                .map { SourceRoot(it, createParserConfiguration()) }
                .flatMap { it.tryToParseParallelized().stream() }
                .parallel()
                .map { it.result.get() }
                .flatMap { it.findAll<TypeDeclaration<*>> { it.canBeReferencedByName }.stream() }
                .map { it.fullyQualifiedName.get() }
                .collect(Collectors.toSet())
        }

        fun dumpInstrumentClassList(d4jWorkspace: Defects4JWorkspace, classFilter: ClassFilter): Path {
            LOGGER.info("Discovering and exporting class list")

            val allClasses = listInstrumentClassList(d4jWorkspace, classFilter)

            val instrumentClassesFile = d4jWorkspace.workspace.resolve(INSTRUMENT_CLASSES)
            instrumentClassesFile
                .bufferedWriter()
                .use { writer ->
                    allClasses.forEach {
                        writer.appendLine(it)
                    }
                }

            return instrumentClassesFile
        }
    }
}