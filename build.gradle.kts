import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
   kotlin("jvm") version "1.6.0"
   java
}

repositories {
   mavenCentral()
}

dependencies {
   implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.0")
}

subprojects {
   group = "com.github.hoshikurama"
   version = "6.1.3"

   tasks.withType<KotlinCompile> {
      kotlinOptions.jvmTarget = "17"
   }

   tasks.withType<KotlinCompile>().configureEach {
      kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
      kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
   }
}