plugins {
    kotlin("jvm") version "2.2.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.2.0")
}

subprojects {
    group = "com.github.hoshikurama"
    version = "11.2.1"
}