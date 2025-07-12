plugins {
    kotlin("jvm") version "2.1.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.21")
}

subprojects {
    group = "com.github.hoshikurama"
    version = "11.2.0"
}