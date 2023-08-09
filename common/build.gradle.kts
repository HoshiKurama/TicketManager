plugins {
    kotlin("jvm")
    id("com.github.ben-manes.versions") version "0.47.0" // https://github.com/ben-manes/gradle-versions-plugin
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.guava:guava:32.1.2-jre")
}