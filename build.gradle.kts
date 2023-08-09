import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.21"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.derppening.researchprojecttoolkit.dependencies")
}

group = "com.derppening.researchprojecttoolkit"

allprojects {
    repositories {
        mavenCentral {
            content {
                excludeGroup("com.github.javaparser")
            }
        }
    }
}

subprojects {
    tasks {
        // Enable reproducible builds
        withType<AbstractArchiveTask> {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
        }

        withType<KotlinCompile> {
            compilerOptions {
                freeCompilerArgs.addAll(
                    "-opt-in=kotlin.ExperimentalStdlibApi",
                    "-Xcontext-receivers"
                )
            }
        }
    }

    afterEvaluate {
        extensions.findByType<KotlinJvmProjectExtension>()?.apply {
            jvmToolchain {
                languageVersion.set(JavaLanguageVersion.of(8))
            }
        }
    }
}

tasks {
    shadowJar {
        isEnabled = false
    }

    wrapper {
        gradleVersion = "8.2.1"
    }

    create<Exec>("deploySccpu7") {
        dependsOn(":entrypoint:shadowJar")

        workingDir(projectDir)
        commandLine("./scripts/deploy-sccpu7.sh")
    }
    create<Exec>("deployWorkstation") {
        dependsOn(":entrypoint:shadowJar")

        workingDir(projectDir)
        commandLine("./scripts/deploy-workstation.sh")
    }
}