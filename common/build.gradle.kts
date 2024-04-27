plugins {
    kotlin("jvm")
    id("com.github.ben-manes.versions") version "0.51.0" // https://github.com/ben-manes/gradle-versions-plugin
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("com.google.guava:guava:33.1.0-jre")
}