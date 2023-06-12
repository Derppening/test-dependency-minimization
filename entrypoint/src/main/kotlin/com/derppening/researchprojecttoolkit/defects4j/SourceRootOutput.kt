package com.derppening.researchprojecttoolkit.defects4j

enum class SourceRootOutput {
    SOURCE, TEST, ALL;

    val fileComponent: String get() = name.lowercase()
}