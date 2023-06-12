package com.derppening.researchprojecttoolkit.util

fun <E : Enum<E>> Enum<E>.asCmdlineOpt() = name.lowercase().replace('_', '-')