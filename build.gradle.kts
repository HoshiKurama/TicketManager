import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
   kotlin("jvm") version "1.6.10"
   java
}

repositories {
   mavenCentral()
}

dependencies {
   implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.10")
}

subprojects {
   group = "com.github.hoshikurama"
   version = "7.0.1"

   tasks.withType<KotlinCompile> {
      kotlinOptions.jvmTarget = "17"
   }

   tasks.withType<KotlinCompile>().configureEach {
      kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
   }
}