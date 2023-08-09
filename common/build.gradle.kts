import com.derppening.researchprojecttoolkit.dependency.`apache-commons`
import com.derppening.researchprojecttoolkit.dependency.slf4j

plugins {
    kotlin("jvm")
}

group = "com.derppening.researchprojecttoolkit"

dependencies {
    implementation("derplib-kt:util")

    implementation(kotlin("reflect"))
    implementation(`apache-commons`("math3", "3.6.1"))

    api(slf4j("api"))
}