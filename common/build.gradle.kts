plugins {
    kotlin("jvm")
    id("com.github.ben-manes.versions") version "0.42.0" // https://github.com/ben-manes/gradle-versions-plugin
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.20")
    implementation("com.google.guava:guava:31.1-jre")
}