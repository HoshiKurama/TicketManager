import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
   kotlin("jvm") version "1.5.21"
   java
}

repositories {
   mavenCentral()
}

dependencies {
   implementation(kotlin("stdlib", version = "1.5.21"))
}

subprojects {
   group = "com.hoshikurama.github"
   version = "6.0.1"

   tasks.withType<KotlinCompile> {
      kotlinOptions.jvmTarget = "16"
   }

   tasks.withType<KotlinCompile>().configureEach {
      kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
      kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
   }
}