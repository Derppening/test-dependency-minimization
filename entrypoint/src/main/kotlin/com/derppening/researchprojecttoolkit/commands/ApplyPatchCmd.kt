package com.derppening.researchprojecttoolkit.commands

import com.derppening.researchprojecttoolkit.tool.patch.ApplyPatch
import com.derppening.researchprojecttoolkit.util.GitUnidiffPatchFile
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import kotlin.io.path.Path

class ApplyPatchCmd : CliktCommand(name = "apply-patch", help = HELP_TEXT) {

    private val dir by option().path().default(Path(""))
    private val patchFile by argument().path()

    override fun run() {
        val patchFile = GitUnidiffPatchFile.fromFile(patchFile)

        ApplyPatch.patchFiles(dir, patchFile)
    }

    companion object {

        private const val HELP_TEXT = "Applies a patch containing one or more test cases into a different revision."
    }
}