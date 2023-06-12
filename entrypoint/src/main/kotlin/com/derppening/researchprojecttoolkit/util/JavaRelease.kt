package com.derppening.researchprojecttoolkit.util

enum class JavaRelease {
    JAVA_1_0,
    JAVA_1_1,
    JAVA_1_2,
    JAVA_1_3,
    JAVA_1_4,
    JAVA_5,
    JAVA_6,
    JAVA_7,
    JAVA_8,
    JAVA_9,
    JAVA_10,
    JAVA_11,
    JAVA_12,
    JAVA_13,
    JAVA_14,
    JAVA_15,
    JAVA_16,
    JAVA_17,
    JAVA_18,
    JAVA_19,
    JAVA_20;

    val minSourceVersion: JavaRelease
        get() = when {
            this >= JAVA_17 -> JAVA_7
            this >= JAVA_9 -> JAVA_6
            this >= JAVA_6 -> JAVA_1_2
            else -> JAVA_1_0
        }
    val minTargetVersion: JavaRelease
        get() = minSourceVersion

    override fun toString(): String = if (this <= JAVA_8) "1.$ordinal" else "$ordinal"

    companion object {

        /**
         * Current JVM version as used by the [Boot JVM][BOOT_JVM_VERSION].
         */
        val CURRENT get() = fromString(BOOT_JVM_VERSION)

        /**
         * The minimum version accepted by the `-source` flag of the current JVM.
         */
        val CURRENT_MIN_SUPPORTED get() = CURRENT.minSourceVersion

        private fun fromStringOrNull(str: String): JavaRelease? {
            val majVersion = str
                .takeWhile { it.isDigit() || it == '.' }
                .split('.')
                .take(2)
                .joinToString(".")

            return when {
                majVersion.toDouble() >= 1.0 && majVersion.toDouble() < 2.0 -> {
                    val dotMajVersion = majVersion.split('.')[1].toInt()
                    if (dotMajVersion <= 8) {
                        values()[dotMajVersion]
                    } else null
                }
                majVersion.toInt() >= 5 -> {
                    values()[majVersion.toInt()]
                }
                else -> null
            }
        }

        private fun fromString(str: String): JavaRelease =
            requireNotNull(fromStringOrNull(str)) { "$str is not a valid Java version" }

        fun fromCmdlineOption(str: String): JavaRelease = when (str) {
            "min" -> CURRENT_MIN_SUPPORTED
            "current" -> CURRENT
            else -> fromString(str)
        }
    }
}