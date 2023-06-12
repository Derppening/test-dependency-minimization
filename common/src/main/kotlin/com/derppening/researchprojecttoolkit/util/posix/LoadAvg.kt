package com.derppening.researchprojecttoolkit.util.posix

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readText

class LoadAvg private constructor(loadavgFile: Path) {

    val loadAvg1: Float
    val loadAvg5: Float
    val loadAvg15: Float

    val numRunnableThreads: Long
    val numTotalThreads: Long

    val mostRecentPID: Long

    init {
        val contents = loadavgFile.readText().trim().split(" ")

        loadAvg1 = contents[0].toFloat()
        loadAvg5 = contents[1].toFloat()
        loadAvg15 = contents[2].toFloat()

        val threads = contents[3].split("/")

        numRunnableThreads = threads[0].toLong()
        numTotalThreads = threads[1].toLong()

        mostRecentPID = contents[4].toLong()
    }

    override fun toString(): String =
        "$loadAvg1 $loadAvg5 $loadAvg15 $numRunnableThreads/$numTotalThreads $mostRecentPID"

    companion object {

        fun getInstance(procRoot: Path = Path("/proc")) = LoadAvg(procRoot.resolve("loadavg"))
    }
}