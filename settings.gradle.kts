rootProject.name = "test-dependency-minimization"

require(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_1_8)) { "This project requires Java 8 or above." }

includeBuild("buildDeps")
includeBuild("derplib-kt")

include(
    "common",
    "entrypoint",

    // Patched JavaParser
    "javaparser",
)
