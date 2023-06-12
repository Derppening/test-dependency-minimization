package com.derppening.researchprojecttoolkit.commands

import com.derppening.researchprojecttoolkit.util.FileCompressionUtils
import com.github.ajalt.clikt.core.CliktCommand
import kotlin.io.path.Path

class TestCmd : CliktCommand(name = "test", help = HELP_TEXT) {

    override fun run() {
        FileCompressionUtils.compressDir(Path("/home/david/IdeaSnapshots"), Path("/home/david/IdeaSnapshots.tar.zst"))
        FileCompressionUtils.decompressDir(Path("/home/david/IdeaSnapshots.tar.zst"), Path("/home/david/IdeaSnapshots.extracted"))
    }

    companion object {

        private const val HELP_TEXT = "Test command."
    }
}