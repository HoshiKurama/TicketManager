plugins {
    kotlin("jvm")
    id("com.github.ben-manes.versions") version "0.49.0" // https://github.com/ben-manes/gradle-versions-plugin
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("com.google.guava:guava:32.1.3-jre")
}