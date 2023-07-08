import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.22"
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22")
}
/*
TODO: update to Kotlin 1.9.0 once maven dependency releases.
https://kotlinlang.org/docs/whatsnew19.html#install-kotlin-1-9-0
Do an update to all gradle files
 */

subprojects {
    group = "com.github.hoshikurama"
    version = "10.0.0"

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }

    tasks.withType<JavaCompile>() {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
}