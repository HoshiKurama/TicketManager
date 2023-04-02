plugins {
    kotlin("jvm")
    id("com.github.ben-manes.versions") version "0.45.0" // https://github.com/ben-manes/gradle-versions-plugin
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.20")
    implementation("org.yaml:snakeyaml:2.0")
}