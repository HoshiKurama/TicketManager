import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
   kotlin("jvm") version "1.5.20"
   java
}

repositories {
   mavenCentral()
}

dependencies {
   implementation(kotlin("stdlib", version = "1.5.20"))
}

subprojects {
   group = "com.hoshikurama.github"
   version = "5.0.0"

   tasks.withType<KotlinCompile> {
      kotlinOptions.jvmTarget = "16"
   }

   tasks.withType<KotlinCompile>().configureEach {
      kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
   }
}