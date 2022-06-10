import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
   kotlin("jvm") version "1.7.0"
   java
}

repositories {
   mavenCentral()
}

dependencies {
   implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.0")
}

subprojects {
   group = "com.github.hoshikurama"
   version = "8.2.2"

   tasks.withType<KotlinCompile> {
      kotlinOptions.jvmTarget = "17"
   }

   tasks.withType<KotlinCompile>().configureEach {
      kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
   }
}