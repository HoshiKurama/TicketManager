import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.8.21"
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.21")
}

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