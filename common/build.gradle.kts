plugins {
    kotlin("jvm")
    id("com.github.ben-manes.versions") version "0.52.0" // https://github.com/ben-manes/gradle-versions-plugin
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("com.google.guava:guava:33.4.8-jre")
}