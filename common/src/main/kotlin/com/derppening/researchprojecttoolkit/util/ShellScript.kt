package com.derppening.researchprojecttoolkit.util

import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.*

class ScriptBuilder(
    private val lines: MutableList<String>,
    private val useEnvForShebang: Boolean = true
) : MutableList<String> by lines {

    fun setShebang(interpreter: String, vararg options: String): ScriptBuilder {
        val interpreterCmdline = "$interpreter ${options.joinToString(" ")}"
        val shebangStmt = if (useEnvForShebang) {
            "#!/usr/bin/env -S $interpreterCmdline"
        } else {
            "#!$interpreterCmdline"
        }

        val shebangLineNum = lines.indexOfFirst { it.startsWith("#!") }
        if (shebangLineNum != -1) {
            if (shebangLineNum != 0) {
                LOGGER.warn("Shebang not located in the first line of the script; Considering fixing this")
            }

            lines[shebangLineNum] = shebangStmt
        } else {
            lines.add(0, shebangStmt)
        }

        return this
    }

    fun addStmt(stmt: String): ScriptBuilder {
        add(stmt)
        return this
    }

    fun addCmd(cmd: String, vararg options: String): ScriptBuilder {
        addStmt("$cmd ${options.joinToString(" ")}")
        return this
    }

    fun addBlankLine(): ScriptBuilder {
        add("")
        return this
    }

    fun addComment(comment: String): ScriptBuilder {
        addAll(comment.split(Regex("\\s")).map { "# $it" })
        return this
    }

    fun addFunction(funcName: String, functionBody: ScriptBuilder.() -> Unit): ScriptBuilder {
        add("function $funcName {")
        addAll(ScriptBuilder(block = functionBody).lines.map { "${' ' * 4}$it" })
        add("}")
        return this
    }

    fun addVar(varName: String, expr: String): ScriptBuilder {
        add("$varName=$expr")
        return this
    }

    constructor(useEnvForShebang: Boolean = true, block: ScriptBuilder.() -> Unit) :
            this(mutableListOf<String>(), useEnvForShebang) {
        this.block()
    }

    fun build(fileExtension: String, setExecutable: Boolean = true): TemporaryPath {
        val file = TemporaryPath.createFile(suffix = ".${fileExtension.removePrefix(".")}")

        file.path.bufferedWriter()
            .use { writer ->
                lines.forEach { writer.appendLine(it) }
            }

        if (setExecutable) {
            val permissions = file.path.getPosixFilePermissions() union EXECUTE_PERMISSION_BITMASK
            file.path.setPosixFilePermissions(permissions)
        }

        return file
    }

    override fun toString(): String = lines.joinToString("\n")
    override fun equals(other: Any?): Boolean = lines == (other as? ScriptBuilder)?.lines
    override fun hashCode(): Int = lines.hashCode()

    companion object {

        private val LOGGER = Logger<ScriptBuilder>()
        private val EXECUTE_PERMISSION_BITMASK = setOf(
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_EXECUTE
        )

        fun fromExisting(path: Path): ScriptBuilder {
            require(path.isRegularFile())

            return ScriptBuilder(path.bufferedReader().useLines { it.toMutableList() })
        }
    }
}