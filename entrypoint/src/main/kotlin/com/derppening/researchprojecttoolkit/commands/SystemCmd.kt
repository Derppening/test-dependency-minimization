package com.derppening.researchprojecttoolkit.commands

import com.derppening.researchprojecttoolkit.util.*
import com.derppening.researchprojecttoolkit.util.posix.*
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import hk.ust.cse.castle.toolkit.jvm.ByteUnit
import hk.ust.cse.castle.toolkit.jvm.jsl.jvmRuntime
import java.net.InetAddress
import kotlin.io.path.Path
import kotlin.io.path.isDirectory

class SystemCmd : CliktCommand(name = "system", help = HELP_TEXT, printHelpOnEmptyArgs = true) {

    init {
        subcommands(SUBCOMMANDS)
    }

    override fun run() = Unit

    companion object {

        private const val HELP_TEXT = "System-related commands."

        private val SUBCOMMANDS = listOf(
            SystemInfo(),
            SystemLocateJava()
        )
    }
}

private class SystemInfo : CliktCommand(name = "info", help = HELP_TEXT) {

    private val procDir by option()
        .path(mustExist = true, canBeFile = false, mustBeReadable = true)
        .default(Path("/proc"))

    override fun run() {
        if (!procDir.isDirectory()) {
            error("System information not supported on this platform")
        }

        val majorDivider = '=' * 80
        val minorDivider = '-' * 80

        val linuxVersion = LinuxVersion.getInstance(procDir)
        val cpuInfo = CpuInfo.getInstance(procDir)
        val memInfo = MemInfo.getInstance(procDir)
        val loadAvg = LoadAvg.getInstance(procDir)
        val systemProps = System.getProperties()

        LOGGER.info(majorDivider)
        LOGGER.info("System Information")
        LOGGER.info(minorDivider)

        LOGGER.info("OS Type            : ${linuxVersion.osType}")
        LOGGER.info("Kernel Version     : ${linuxVersion.osRelease}")
        LOGGER.info("Full Version String: ${linuxVersion.versionString}")

        LOGGER.info(majorDivider)
        LOGGER.info("CPU Information")
        LOGGER.info(minorDivider)
        cpuInfo.cores.groupBy { it.modelName }
            .forEach {
                LOGGER.info("CPU Model: ${it.value.size}x ${it.key}")
            }

        LOGGER.info(majorDivider)
        LOGGER.info("Memory Information")
        LOGGER.info(minorDivider)
        LOGGER.info("Installed Memory: ${memInfo.MemTotal} KB")
        memInfo.MemAvailable?.let {
            LOGGER.info("Available Memory: $it KB")
        }
        LOGGER.info("Free Memory     : ${memInfo.MemFree} KB")

        LOGGER.info(majorDivider)
        LOGGER.info("Network Information")
        LOGGER.info(minorDivider)

        val inetInstance = InetAddress.getLocalHost()
        val nativeHostName = runCatching { PosixUtils.hostName }.getOrNull()
        val nativeCanonicalHostName = runCatching { PosixUtils.canonicalHostName }.getOrNull()
        val nativeIpAddr = runCatching { PosixUtils.ipAddresses }.getOrNull()
        if (nativeHostName == inetInstance.hostName) {
            LOGGER.info("Hostname                           : $nativeHostName")
        } else {
            nativeHostName?.let {
                LOGGER.info("Hostname (`hostname`)              : $it")
            }
            LOGGER.info("Hostname (InetAddress)             : ${inetInstance.hostName}")
        }
        if (nativeCanonicalHostName == inetInstance.canonicalHostName) {
            if (nativeCanonicalHostName != nativeHostName) {
                LOGGER.info("Canonical Hostname                 : $nativeCanonicalHostName")
            }
        } else {
            if (nativeCanonicalHostName != nativeHostName) {
                LOGGER.info("Canonical Hostname (`hostname`)    : $nativeCanonicalHostName")
            }
            if (inetInstance.canonicalHostName != inetInstance.hostName) {
                LOGGER.info("Canonical Hostname (InetAddress) : $nativeCanonicalHostName")
            }
        }
        if (nativeIpAddr?.equals(setOf(inetInstance.hostAddress)) == true) {
            LOGGER.info("IP Addresses                       : ${nativeIpAddr.joinToString(",")}")
        } else {
            nativeIpAddr?.let {
                LOGGER.info("IP Addresses (`hostname`)          : ${it.joinToString(",")}")
            }
            LOGGER.info("IP Addresses (InetAddress)         : ${inetInstance.hostAddress}")
        }

        LOGGER.info(majorDivider)
        LOGGER.info("Load Average")
        LOGGER.info(minorDivider)
        LOGGER.info("1m : ${loadAvg.loadAvg1}")
        LOGGER.info("5m : ${loadAvg.loadAvg5}")
        LOGGER.info("15m: ${loadAvg.loadAvg15}")

        LOGGER.info(majorDivider)
        LOGGER.info("JVM Information")
        LOGGER.info(minorDivider)
        LOGGER.info("JVM Flavor                 : ${systemProps["java.runtime.name"]}")
        LOGGER.info("JVM Version                : ${systemProps["java.runtime.version"]} ($BOOT_JVM_VERSION)")
        LOGGER.info("Path to JVM                : $bootJvmHome")
        LOGGER.info("Available Processors       : ${jvmRuntime.availableProcessors()}")
        LOGGER.info("Allocatable Memory (-Xmx)  : ${ByteUnit.MEBIBYTE.convertIntegral(jvmRuntime.maxMemory())} MB")
        LOGGER.info(majorDivider)
    }

    companion object {

        private const val HELP_TEXT = "Displays the system information."

        private val LOGGER = Logger<SystemInfo>()
    }
}

private class SystemLocateJava : CliktCommand(name = "locate-java", help = HELP_TEXT) {

    private val searchPaths by option(help = "Additional search paths to look for Java installations.")
        .multiple()
    private val jdk by option(help = "Only search for JDKs.")
        .flag()
    private val followSymlinks by option(help = "Follow symlinks when searching for installation.")
        .flag()

    override fun run() {
        val searchPaths = DEFAULT_SEARCH_PATHS + searchPaths.map { Path(it) }

        LOGGER.info("Searching for Java installations in the following paths:")
        searchPaths.forEach {
            LOGGER.info("$it")
        }

        val foundJava = if (jdk) {
            findJdk(searchPaths, followSymlinks)
        } else {
            findJre(searchPaths, followSymlinks)
        }

        LOGGER.info("")
        LOGGER.info("Found the following Java installations:")
        foundJava.forEach {
            LOGGER.info("$it")
        }
    }

    companion object {

        private const val HELP_TEXT = "Locates all Java installations."

        private val LOGGER = Logger<SystemLocateJava>()

        private val DEFAULT_SEARCH_PATHS = listOf(
            "/usr/lib/jvm"
        ).map { Path(it) }
    }
}