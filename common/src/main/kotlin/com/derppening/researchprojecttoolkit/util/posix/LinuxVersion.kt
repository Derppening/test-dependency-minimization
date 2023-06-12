package com.derppening.researchprojecttoolkit.util.posix

import java.nio.file.Path
import kotlin.io.path.*

class LinuxVersion private constructor(
    procVersionFile: Path,
    procSysKernelDir: Path
) {

    init {
        check(procVersionFile.isRegularFile() && procVersionFile.isReadable())
        check(procSysKernelDir.isDirectory())
        check(procSysKernelDir.resolve("ostype").let { it.isRegularFile() && it.isReadable() })
        check(procSysKernelDir.resolve("osrelease").let { it.isRegularFile() && it.isReadable() })
    }

    val versionString = procVersionFile.readText().trim()
    val osType = procSysKernelDir.resolve("ostype").readText().trim()
    val osRelease = procSysKernelDir.resolve("osrelease").readText().trim()
    val kernelVersion = osRelease.takeWhile { it != '-' }

    override fun equals(other: Any?): Boolean = (other as? LinuxVersion)?.versionString == versionString
    override fun toString(): String = versionString
    override fun hashCode(): Int = versionString.hashCode()

    companion object {

        fun getInstance(procRoot: Path = Path("/proc")) =
            LinuxVersion(procRoot.resolve("version"), procRoot.resolve("sys/kernel"))
    }
}