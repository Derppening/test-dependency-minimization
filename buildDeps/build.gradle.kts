plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        register("dependencies") {
            id = "com.derppening.researchprojecttoolkit.dependencies"
            implementationClass = "com.derppening.researchprojecttoolkit.DependenciesPlugin"
        }
    }
}
