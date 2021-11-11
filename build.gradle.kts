import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
   kotlin("jvm") version "1.5.31"
   java
}

repositories {
   mavenCentral()
}

dependencies {
   implementation("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
}

subprojects {
   group = "com.github.hoshikurama"
   version = "6.1.0"

   tasks.withType<KotlinCompile> {
      kotlinOptions.jvmTarget = "16"
   }

   tasks.withType<KotlinCompile>().configureEach {
      kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
      kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
   }
}