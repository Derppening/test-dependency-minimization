package com.derppening.researchprojecttoolkit.tool.javac

enum class JavacError(
    val category: JavacErrorCategory,
    private val msgRegexes: List<Regex>,
    private val msgPrefix: String
) {
    AMBIGUOUS_REFERENCE(
        JavacErrorCategory.LIB_CHANGE,
        Regex("reference to .+ is ambiguous"),
        "reference to"
    ),
    CANNOT_FIND_SYMBOL(
        JavacErrorCategory.LIB_CHANGE,
        Regex("cannot find symbol"),
        "cannot find symbol"
    ),
    INCOMPATIBLE_TYPES_UPPER_BOUND(
        JavacErrorCategory.LANG_CHANGE,
        Regex("incompatible types: inferred type does not conform to upper bound\\(s\\)"),
        "incompatible types: inferred type does not conform to upper bound"
    ),
    METHOD_DOES_NOT_OVERRIDE(
        JavacErrorCategory.LIB_CHANGE,
        Regex("method does not override or implement a method from a supertype"),
        "method does not override or implement a method from a supertype"
    ),
    NEW_KEYWORD(
        JavacErrorCategory.LANG_CHANGE,
        Regex("as of release \\d+, '.+' is a keyword, and may not be used as an identifier"),
        "as of release"
    ),
    PACKAGE_DOES_NOT_EXIST(
        JavacErrorCategory.LIB_CHANGE,
        Regex("package .+ does not exist"),
        "package"
    ),
    UNMAPPABLE_CHARACTER(
        JavacErrorCategory.ENCODING_ERROR,
        Regex("unmappable character (?:\\(.+\\) )?for encoding UTF-?8"),
        "unmappable character"
    );

    constructor(category: JavacErrorCategory, msgRegex: Regex, msgPrefix: String) : this(category, listOf(msgRegex), msgPrefix)

    companion object {
        fun parse(msg: String): JavacError = values()
            .filter { msg.startsWith(it.msgPrefix) }
            .singleOrNull { it.msgRegexes.any { it.matches(msg) } }
            ?: error("Cannot find matching JavacError for message \"$msg\"")
    }
}
