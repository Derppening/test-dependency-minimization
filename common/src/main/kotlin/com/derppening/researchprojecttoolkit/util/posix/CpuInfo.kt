package com.derppening.researchprojecttoolkit.util.posix

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.isReadable
import kotlin.io.path.isRegularFile

sealed class CpuInfo private constructor(cpuinfoFile: Path) {

    abstract val cores: List<CoreInfo>

    init {
        check(cpuinfoFile.isRegularFile() && cpuinfoFile.isReadable())
    }

    protected val sections = cpuinfoFile.bufferedReader().use { it.readLines() }
        .fold(mutableListOf<MutableList<String>>()) { acc, it ->
            if (acc.isEmpty() && it.isNotBlank()) {
                acc.add(mutableListOf(it))
            } else if (it.isNotBlank()) {
                acc.last().add(it)
            } else if (it.isBlank() && acc.last().isNotEmpty()) {
                acc.add(mutableListOf())
            }

            acc
        }
        .filter { it.isNotEmpty() }
        .map { it.toList() }
        .toList()

    protected fun List<String>.findFieldOrNull(fieldName: String): String? {
        return this.firstOrNull { it.startsWith(fieldName) }?.split(":")?.get(1)?.trim()
    }

    protected fun List<String>.findField(fieldName: String): String = checkNotNull(findFieldOrNull(fieldName))

    interface CoreInfo {

        val processor: Int
        val modelName: String
        val bogoMIPS: Double
    }

    class X86(cpuinfoFile: Path) : CpuInfo(cpuinfoFile) {
        class CoreInfo(
            override val processor: Int,
            val vendor_id: String,
            val cpuFamily: Int,
            val model: Int,
            override val modelName: String,
            val stepping: Int,
            val microcode: Int,
            val cpuMHz: Double,
            val cacheSize: Long,
            val physicalId: Int,
            val siblings: Int,
            val coreId: Int,
            val cpuCores: Int,
            val apicid: Int,
            val initialApicid: Int,
            val fpu: Boolean,
            val fpu_exception: Boolean,
            val cpuidLevel: Int,
            val wp: Boolean,
            val flags: List<String>,
            val bugs: List<String>,
            override val bogoMIPS: Double,
            val clflushSize: Int,
            val cache_alignment: Int,
            val addressSizes: AddressSize,
            val powerManagement: List<String>
        ) : CpuInfo.CoreInfo {

            class AddressSize(
                val physicalBits: Int,
                val virtualBits: Int
            ) {

                companion object {

                    fun fromLine(str: String): AddressSize {
                        val components = str.split(",").map { it.trim() }

                        return AddressSize(
                            components.single { it.endsWith("physical") }.takeWhile { it != ' ' }.toInt(),
                            components.single { it.endsWith("virtual") }.takeWhile { it != ' ' }.toInt(),
                        )
                    }
                }
            }
        }

        override val cores = sections
            .map {
                with(it) {
                    CoreInfo(
                        findField("processor").toInt(),
                        findField("vendor_id"),
                        findField("cpu family").toInt(),
                        findField("model").toInt(),
                        findField("model name"),
                        findField("stepping").toInt(),
                        findField("microcode").removePrefix("0x").toInt(16),
                        findField("cpu MHz").toDouble(),
                        findField("cache size").removeSuffix(" KB").toLong() * 1024L,
                        findField("physical id").toInt(),
                        findField("siblings").toInt(),
                        findField("core id").toInt(),
                        findField("cpu cores").toInt(),
                        findField("apicid").toInt(),
                        findField("initial apicid").toInt(),
                        findField("fpu") == "yes",
                        findField("fpu_exception") == "yes",
                        findField("cpuid level").toInt(),
                        findField("wp") == "yes",
                        findField("flags").split(" "),
                        findField("bugs").split(" "),
                        findField("bogomips").toDouble(),
                        findField("clflush size").toInt(),
                        findField("cache_alignment").toInt(),
                        CoreInfo.AddressSize.fromLine(findField("address sizes")),
                        findField("power management").split(" ")
                    )
                }
            }

    }

    class Arm(cpuinfoFile: Path) : CpuInfo(cpuinfoFile) {

        class CoreInfo(
            override val processor: Int,
            override val modelName: String,
            override val bogoMIPS: Double,
            val features: List<String>,
            val cpuImplementer: Int,
            val cpuArchitecture: Int,
            val cpuVariant: Int,
            val cpuPart: Int,
            val cpuRevision: Int
        ) : CpuInfo.CoreInfo

        class Hardware(
            val hardware: String,
            val revision: String?,
            val serial: String?,
            val model: String?
        )

        override val cores: List<CoreInfo>
        val hardware: Hardware

        init {
            cores = sections
                .filter { it.first() == "processor" }
                .map {
                    with(it) {
                        CoreInfo(
                            findField("processor").toInt(),
                            findField("model name"),
                            findField("BogoMIPS").toDouble(),
                            findField("Features").split(" "),
                            findField("cpu implementer").removePrefix("0x").toInt(16),
                            findField("cpu architecture").toInt(),
                            findField("cpu variant").removePrefix("0x").toInt(16),
                            findField("cpu part").removePrefix("0x").toInt(16),
                            findField("cpu revision").toInt()
                        )
                    }
                }

            hardware = sections
                .single { it.first() == "Hardware" }
                .let {
                    with(it) {
                        Hardware(
                            findField("Hardware"),
                            findFieldOrNull("Revision"),
                            findFieldOrNull("Serial"),
                            findFieldOrNull("Model")
                        )
                    }
                }
        }
    }

    companion object {

        fun getInstance(procDir: Path = Path("/proc")): CpuInfo {
            val cpuinfoFile = procDir.resolve("cpuinfo")

            return runCatching {
                X86(cpuinfoFile)
            }.recoverCatching {
                Arm(cpuinfoFile)
            }.getOrThrow()
        }
    }
}