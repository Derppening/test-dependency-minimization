package com.derppening.gradle

/**
 * A dependency group.
 *
 * @property group Reverse domain name of the dependency publisher.
 */
data class Group(val group: String) {

    override fun toString(): String = group

    /**
     * Appends a package level to the end of this group.
     *
     * @param other Package level(s) to attach.
     * @return [Group] representing the new dependency group.
     */
    operator fun plus(other: String): Group = this.copy(group = "$group.$other")
}