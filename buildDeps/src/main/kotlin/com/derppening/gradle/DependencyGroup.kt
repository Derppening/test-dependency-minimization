package com.derppening.gradle

/**
 * A group of dependencies.
 *
 * A dependency group is defined by its package group, and optionally the version of all dependencies within the group.
 *
 * @property group Group name of the dependencies.
 * @param _version Version of all dependencies within the group. `null` can be used to mean a lack of common versions in
 * the dependency group.
 */
abstract class DependencyGroup(val group: Group, private val _version: Version? = null) {

    constructor(group: String, version: String? = null) : this(Group(group), version?.let { Version(it) })

    /**
     * The version of this dependency group.
     *
     * @throws IllegalStateException if the dependency group does not have version information.
     */
    val version: Version get() = checkNotNull(_version) { "Cannot get the version of a dependency group without version information" }
}