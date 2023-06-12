package com.derppening.researchprojecttoolkit.util.posix

object PosixUtils {

    val hostName: String
        get() = ProcessBuilder("hostname")
            .start()
            .let { process ->
                val exitCode = process.waitFor()

                if (exitCode != 0) {
                    throw RuntimeException("Execution of `hostname` failed: $exitCode")
                }

                return process.inputStream
                    .use { String(it.readBytes()) }
                    .trim()
            }
    val canonicalHostName: String
        get() = ProcessBuilder("hostname", "-f")
            .start()
            .let { process ->
                val exitCode = process.waitFor()

                if (exitCode != 0) {
                    throw RuntimeException("Execution of `hostname` failed: $exitCode")
                }

                return process.inputStream
                    .use { String(it.readBytes()) }
                    .trim()
            }
    val ipAddresses: Set<String>
        get() = ProcessBuilder("hostname", "-i")
            .start()
            .let { process ->
                val exitCode = process.waitFor()

                if (exitCode != 0) {
                    throw RuntimeException("Execution of `hostname` failed: $exitCode")
                }

                return process.inputStream
                    .use { String(it.readBytes()) }
                    .trim()
                    .split(" ")
                    .toSet()
            }
}