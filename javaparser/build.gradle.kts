// All patches to this source tree should be saved somewhere, so that when a new version of JavaParser is released, we
// can directly apply the patches on top of the tree.
//
// Current Revision: 44ae12511dbb8995301c75637567a3e7d3beb324

tasks {
    create<Exec>("install") {
        workingDir(projectDir)
        commandLine("./mvnw", "-pl", ":javaparser-parent,:javaparser-core,:javaparser-symbol-solver-core", "-DskipTests", "-Dmaven.test.skip=true", "install")
    }

    create<Exec>("jar") {
        workingDir(projectDir)
        commandLine("./mvnw", "-pl", ":javaparser-parent,:javaparser-core,:javaparser-symbol-solver-core", "-DskipTests", "-Dmaven.test.skip=true", "package")
    }

    create<Exec>("test") {
        workingDir(projectDir)
        commandLine("./mvnw", "test")
    }

    create<Exec>("clean") {
        workingDir(projectDir)
        commandLine("./mvnw", "clean")
    }
}
