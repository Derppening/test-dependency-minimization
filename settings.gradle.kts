rootProject.name = "test-dependency-minimization"

require(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_1_8)) { "This project requires Java 8 or above." }

includeBuild("buildDeps")
includeBuild("research-jvm-toolkit")

include(
    "common",
    "entrypoint",

    // Patched JavaParser
    "javaparser",
)
