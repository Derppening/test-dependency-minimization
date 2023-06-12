plugins {
    kotlin("jvm")
}

group = "edu.washington.cs.mut"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.apache.ant:ant-junit:1.10.13")
}
