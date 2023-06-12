package com.derppening.gradle

/**
 * A module group of dependencies.
 *
 * A dependency module group is defined by its package group, and optionally its artifact prefix and version of all
 * dependencies within the group.
 *
 * @param group Group name of the dependencies.
 * @property modulePrefix The name prefixing all dependencies within the group. `null` can be used to mean a lack of
 * a common prefix.
 * @param version Version of all dependencies within the group. `null` can be used to mean a lack of common version
 * within the dependency group.
 */
abstract class DependencyModule(group: Group, private val modulePrefix: String?, version: Version? = null) :
    DependencyGroup(group, version) {

    /**
     * The delimiter of the module.
     *
     * When generating dependency notation, this string is used to join the [modulePrefix] with the `module` name.
     *
     * Defaults to `-`.
     */
    protected open val moduleDelimiter = "-"

    constructor(group: String, modulePrefix: String?, version: String? = null) :
            this(Group(group), modulePrefix, version?.let { Version(it) })

    /**
     * Resolves a dependency name.
     *
     * @param module Name of the module.
     * @return The full name of the dependency.
     * @throws IllegalArgumentException if both the module name and the [modulePrefix] is not defined.
     */
    private fun resolveName(module: String?): String = when {
        !module.isNullOrBlank() && modulePrefix != null -> "$modulePrefix$moduleDelimiter$module"
        !module.isNullOrBlank() && modulePrefix == null -> module
        module.isNullOrBlank() && modulePrefix != null -> modulePrefix
        else -> throw IllegalArgumentException("Module name and prefix cannot both be undefined")
    }

    /**
     * Converts this module into dependency notation.
     *
     * @param module Name of the module.
     * @param overrideVersion If specified, uses this version instead of the default version specified in the class.
     * @return The dependency notation of the module.
     * @throws IllegalArgumentException if neither the class or [overrideVersion] defines the version of the module.
     */
    fun asDependencyNotation(module: String?, overrideVersion: String? = null): Any {
        val name = resolveName(module)
        val version = try {
            overrideVersion ?: version
        } catch (e: IllegalStateException) {
            null
        }

        return "$group:$name${version?.let { ":$it" } ?: ""}"
    }
}