package com.derppening.researchprojecttoolkit.commands

import com.derppening.researchprojecttoolkit.util.Logger
import com.derppening.researchprojecttoolkit.util.astToString
import com.derppening.researchprojecttoolkit.util.createParserConfiguration
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.path
import com.github.javaparser.JavaParser

class JavaParserCmd : CliktCommand(name = "jp") {

    init {
        subcommands(SUBCOMMANDS)
    }

    override fun run() = Unit

    companion object {

        private val SUBCOMMANDS = listOf(
            JavaParserAST()
        )
    }
}

private class JavaParserAST : CliktCommand(name = "ast") {

    private val input by argument()
        .path(mustExist = true, canBeDir = false)

    override fun run() {
        val cu = JavaParser(createParserConfiguration())
            .parse(input)
            .result
            .get()

        cu.astToString()
            .split('\n')
            .forEach { LOGGER.info(it) }
    }

    companion object {

        private val LOGGER = Logger<JavaParserAST>()
    }
}