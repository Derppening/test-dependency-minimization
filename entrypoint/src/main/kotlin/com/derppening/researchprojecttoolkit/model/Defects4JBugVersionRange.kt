package com.derppening.researchprojecttoolkit.model

import com.derppening.researchprojecttoolkit.util.Defects4JWorkspace

data class Defects4JBugVersionRange(
    val projectId: Defects4JWorkspace.ProjectID,
    val versionRange: IntRange
) {

    companion object {

        fun fromString(str: String): Defects4JBugVersionRange {
            val splitByDelimiter = str.split(':')
            val (projectIdStr, versionRangeStr) = splitByDelimiter[0] to splitByDelimiter.getOrNull(1)
            val projectId = Defects4JWorkspace.ProjectID.fromString(projectIdStr)

            val versionRange = when {
                versionRangeStr.isNullOrBlank() -> {
                    projectId.projectValidBugs.let { it.min()..it.max() }
                }

                versionRangeStr.contains('-') -> {
                    versionRangeStr.split('-')
                        .map { it.takeIf { it.isNotBlank() }?.toInt() }
                        .let {
                            val firstIncl = it[0] ?: projectId.projectValidBugs.min()
                            val lastIncl = it[1] ?: projectId.projectValidBugs.max()

                            firstIncl..lastIncl
                        }
                }

                else -> {
                    versionRangeStr.toInt().let { it..it }
                }
            }

            return Defects4JBugVersionRange(projectId, versionRange)
        }
    }
}