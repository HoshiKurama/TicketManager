plugins {
    id("com.github.johnrengelman.shadow") version "7.0.0"
    kotlin("jvm")
    java
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.spigot.TicketManagerPlugin")
}

repositories {
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/") {
        name = "sonatype-oss-snapshots"
    }
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.17-R0.1-SNAPSHOT")
    implementation(kotlin("stdlib", version = "1.5.21"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.5.1")
    implementation("joda-time:joda-time:2.10.10")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:1.5.0")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:1.5.0")
    implementation(project(":common"))
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveBaseName.set("TicketManager-Spigot")

        dependencies {
            include(dependency("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.2.2"))
            include(project(":common"))

            relocate("kotlinx.serialization.json", "com.github.hoshikurama.ticketmanager.shaded.kotlinx.serialization.json")
        }
    }
}