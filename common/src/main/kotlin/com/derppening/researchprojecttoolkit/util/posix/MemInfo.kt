package com.derppening.researchprojecttoolkit.util.posix

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.isReadable
import kotlin.io.path.isRegularFile

@Suppress("PropertyName")
class MemInfo private constructor(meminfoFile: Path) {

    init {
        check(meminfoFile.isRegularFile() && meminfoFile.isReadable())
    }

    private val rawFields = meminfoFile.bufferedReader().useLines { sequence ->
        sequence.fold(mutableMapOf<String, String>()) { acc, it ->
            val split = it.split(":").also { check(it.size == 2) }

            acc[split[0].trim()] = split[1].trim()
            acc
        }
    }.toMap()

    val MemTotal = checkNotNull(rawFieldToNumber("MemTotal"))
    val MemFree = checkNotNull(rawFieldToNumber("MemFree"))
    val MemAvailable = rawFieldToNumber("MemAvailable")
    val Buffers = checkNotNull(rawFieldToNumber("Buffers"))
    val Cached = checkNotNull(rawFieldToNumber("Cached"))
    val SwapCached = checkNotNull(rawFieldToNumber("SwapCached"))
    val Active = checkNotNull(rawFieldToNumber("Active"))
    val Inactive = checkNotNull(rawFieldToNumber("Inactive"))
    val Active_anon = rawFieldToNumber("Active(anon)")
    val Inactive_anon = rawFieldToNumber("Inactive(anon)")
    val Active_file = rawFieldToNumber("Active(file)")
    val Inactive_file = rawFieldToNumber("Inactive(file)")
    val Unevictable = rawFieldToNumber("Unevictable")
    val Mlocked = rawFieldToNumber("Mlocked")
    val HighTotal = rawFieldToNumber("HighTotal")
    val HighFree = rawFieldToNumber("HighFree")
    val LowTotal = rawFieldToNumber("LowTotal")
    val LowFree = rawFieldToNumber("LowFree")
    val MmapCopy = rawFieldToNumber("MmapCopy")
    val SwapTotal = checkNotNull(rawFieldToNumber("SwapTotal"))
    val SwapFree = checkNotNull(rawFieldToNumber("SwapFree"))
    val Dirty = checkNotNull(rawFieldToNumber("Dirty"))
    val Writeback = checkNotNull(rawFieldToNumber("Writeback"))
    val AnonPages = rawFieldToNumber("AnonPages")
    val Mapped = checkNotNull(rawFieldToNumber("Mapped"))
    val Shmem = rawFieldToNumber("Shmem")
    val KReclaimable = rawFieldToNumber("KReclaimable")
    val Slab = checkNotNull(rawFieldToNumber("Slab"))
    val SReclaimable = rawFieldToNumber("SReclaimable")
    val SUnreclaim = rawFieldToNumber("SUnreclaim")
    val KernelStack = rawFieldToNumber("KernelStack")
    val PageTables = rawFieldToNumber("PageTables")
    val NFS_Unstable = rawFieldToNumber("NFS_Unstable")
    val Bounce = rawFieldToNumber("Bounce")
    val WritebackTmp = rawFieldToNumber("WritebackTmp")
    val CommitLimit = rawFieldToNumber("CommitLimit")
    val Committed_AS = checkNotNull(rawFieldToNumber("Committed_AS"))
    val VmallocTotal = checkNotNull(rawFieldToNumber("VmallocTotal"))
    val VmallocUsed = checkNotNull(rawFieldToNumber("VmallocUsed"))
    val VmallocChunk = checkNotNull(rawFieldToNumber("VmallocChunk"))
    val HardwareCorrupted = rawFieldToNumber("HardwareCorrupted")
    val AnonHugePages = rawFieldToNumber("AnonHugePages")
    val ShmemHugePages = rawFieldToNumber("ShmemHugePages")
    val ShmemPmdMapped = rawFieldToNumber("ShmemPmdMapped")
    val CmaTotal = rawFieldToNumber("CmaTotal")
    val CmaFree = rawFieldToNumber("CmaFree")
    val HugePages_Total = rawFieldToNumber("HugePages_Total")
    val HugePages_Free = rawFieldToNumber("HugePages_Free")
    val HugePages_Rsvd = rawFieldToNumber("HugePages_Rsvd")
    val HugePages_Surp = rawFieldToNumber("HugePages_Surp")
    val Hugepagesize = rawFieldToNumber("Hugepagesize")
    val DirectMap4k = rawFieldToNumber("DirectMap4k")
    val DirectMap4M = rawFieldToNumber("DirectMap4M")
    val DirectMap2M = rawFieldToNumber("DirectMap2M")
    val DirectMap1G = rawFieldToNumber("DirectMap1G")

    private fun rawFieldToNumber(field: String): ULong? = rawFields[field]?.removeSuffix("kB")?.trim()?.toULong()

    companion object {

        fun getInstance(procRoot: Path = Path("/proc")): MemInfo = MemInfo(procRoot.resolve("meminfo"))
    }
}