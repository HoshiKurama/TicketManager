plugins {
    kotlin("jvm")
    id("com.github.ben-manes.versions") version "0.47.0" // https://github.com/ben-manes/gradle-versions-plugin
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("org.yaml:snakeyaml:2.2")
}