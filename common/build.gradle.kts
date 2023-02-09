plugins {
    kotlin("jvm")
    id("com.github.ben-manes.versions") version "0.45.0" // https://github.com/ben-manes/gradle-versions-plugin
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")
    implementation("com.google.guava:guava:31.1-jre")
    implementation("com.discord4j:discord4j-core:3.2.3")
}