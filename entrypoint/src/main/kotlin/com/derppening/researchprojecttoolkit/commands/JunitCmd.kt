package com.derppening.researchprojecttoolkit.commands

import com.derppening.researchprojecttoolkit.tool.compilation.JUnitRunnerProxy
import com.derppening.researchprojecttoolkit.util.Defects4JWorkspace
import com.derppening.researchprojecttoolkit.util.TestCase
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.path
import kotlin.io.path.Path
import kotlin.system.exitProcess

class JunitCmd : CliktCommand(name = "junit", help = HELP_TEXT) {

    private val d4jDir by option("-dD", help = "The directory to Defects4J.")
        .path(mustExist = true, canBeFile = false)
        .defaultLazy { Path("/defects4j") }
    private val workspaceDir by option("-dW", help = "The directory to the repository workspace.")
        .path()
        .defaultLazy { Path("/workspace") }

    private val projectId by option("-p", help = "Defects4J project ID.")
        .convert { Defects4JWorkspace.ProjectID.fromString(it) }
        .required()
    private val versionId by option("-v", help = "Defects4J bug ID.")
        .convert { Defects4JWorkspace.VersionID.fromString(it) }
        .required()
    private val testCases by option("-t")
        .convert { TestCase.fromD4JQualifiedName(it) }
        .multiple()
    private val env by option("-E")
        .convert { it.split('=').let { it[0] to it.drop(0).joinToString("=") } }
        .multiple()
        .toMap()
    private val jvmArgs by option("-J")
        .multiple()
    private val junitArgs by option("-T")
        .multiple()

    override fun run() {
        val projectRev = Defects4JWorkspace.ProjectRev(projectId, versionId)
        val d4jWorkspace = Defects4JWorkspace(workspaceDir, d4jDir, null, Defects4JWorkspace.JavacVersionOverride.UNCHANGED)

        d4jWorkspace.initAndCheckout(projectRev)
        d4jWorkspace.compile()

        val classpath = d4jWorkspace.getTestClasspath(projectRev)

        val junitArgs = buildList {
            add("-cp")
            add(classpath)
            if (testCases.isNotEmpty()) {
                testCases.forEach {
                    add("-m")
                    add(it.toJUnitMethod())
                }
            } else {
                add("--scan-classpath")
            }

            addAll(junitArgs)
        }

        val junitRunner = JUnitRunnerProxy(junitArgs, workspaceDir, jvmArgs, env, inheritIO = true).apply { run() }

        if (junitRunner.exitCode.isFailure) {
            exitProcess(junitRunner.exitCode.code)
        }
    }

    companion object {

        private const val HELP_TEXT = ""
    }
}