import com.derppening.researchprojecttoolkit.dependency.*
import java.net.URI

plugins {
    application
    kotlin("jvm")
    id("com.github.johnrengelman.shadow")
    jacoco
}

group = "com.derppening.researchprojecttoolkit"

application {
    mainClass.set("com.derppening.researchprojecttoolkit.MainKt")
}

repositories {
    maven {
        url = URI("https://repo.gradle.org/gradle/libs-releases")
    }
    mavenLocal {
        content {
            includeGroup("com.github.javaparser")
        }
    }
}

dependencies {
    components {

        @CacheableRule
        class ClearDependencies : ComponentMetadataRule {
            override fun execute(context: ComponentMetadataContext) {
                context.details.allVariants {
                    withDependencies {
                        clear()
                    }
                }
            }
        }

        // Clears all dependencies of dom4j, since (1) those dependencies are optional, and (2) it interferes with
        // Java's default SAX parser
        withModule<ClearDependencies>("org.dom4j:dom4j")
    }

    implementation(project(":common"))

    implementation("research-jvm-toolkit:util")

    implementation(kotlin("reflect"))

    implementation(slf4j("api"))
    runtimeOnly(logback("classic"))

    implementation(clikt())

    implementation(`java-parser`("core", "3.25.4-SNAPSHOT"))
    implementation(`java-parser`("symbol-solver-core", "3.25.4-SNAPSHOT"))

    implementation(dom4j())
    implementation(`apache-commons`("compress", "1.23.0"))
    runtimeOnly("com.github.luben:zstd-jni:1.5.5-2")
    implementation(`apache-commons`("csv", "1.10.0"))

    implementation(`junit-platform`("console-standalone"))

    testImplementation(`junit-jupiter`())
    testImplementation(kotlin("test-junit5"))
}

tasks {
    jacocoTestReport {
        reports {
            html.required.set(true)
            xml.required.set(false)
            csv.required.set(false)
        }
    }

    shadowJar {
        archiveFileName.set("${rootProject.name}-all.jar")

        manifest {
            attributes.apply {
                this["Main-Class"] = "com.derppening.researchprojecttoolkit.MainKt"
            }
        }

        minimize {
            // Reason: kotlin-reflect depends on kotlin-stdlib
            exclude(dependency("org.jetbrains.kotlin:.*"))

            // Reason: SLF4J requires logging implementation
            exclude(dependency("ch.qos.logback:logback-classic:.*"))
        }
    }

    test {
        useJUnitPlatform()

        jvmArgs = jvmArgs.orEmpty() + "-Xmx8G" + "-XX:+UseG1GC" + "-XX:-UseBiasedLocking"
    }
}
