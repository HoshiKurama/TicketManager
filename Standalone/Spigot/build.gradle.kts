plugins {
    id("com.gradleup.shadow") version "8.3.5"
    id("com.github.ben-manes.versions") version "0.51.0" // https://github.com/ben-manes/gradle-versions-plugin
    kotlin("jvm")
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.spigot.SpigotPlugin")
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://libraries.minecraft.net")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.mojang:brigadier:1.0.500")
    compileOnly("org.spigotmc:spigot-api:1.21.3-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:2.0.21")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.7.3")
    compileOnly("com.github.HoshiKurama.TicketManager_API:Common:11.1.1")
    compileOnly("com.github.HoshiKurama.TicketManager_API:TMCoroutine:11.1.1")

    implementation("org.yaml:snakeyaml:2.3")
    implementation("com.github.seratch:kotliquery:1.9.0")
    implementation("com.h2database:h2:2.3.232")
    implementation("dev.jorel:commandapi-bukkit-core:9.6.0")
    implementation("dev.jorel:commandapi-bukkit-shade:9.6.0")
    implementation("dev.jorel:commandapi-bukkit-kotlin:9.6.0")
    implementation("joda-time:joda-time:2.13.0")
    implementation("net.kyori:adventure-platform-bukkit:4.3.4")
    implementation("net.kyori:adventure-text-minimessage:4.17.0")
    implementation("net.kyori:adventure-extra-kotlin:4.17.0")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation(project(":Standalone:commonSE"))
    implementation(project(":common"))
}

kotlin {
    jvmToolchain(21)
}

tasks {
    shadowJar {
        configurations = listOf(project.configurations.runtimeClasspath.get())
        archiveBaseName.set("TMSE-Spigot")

        dependencies {
            exclude { it.moduleGroup == "org.jetbrains.kotlin" }
        }

        // Relocators
        relocate("dev.jorel", "com.github.hoshikurama.ticketmanager.shaded.commandapi")
        relocate("org.bstats", "com.github.hoshikurama.ticketmanager.shaded.bStats")
        relocate("kotlin", "com.github.hoshikurama.ticketmanager.shaded.kotlin")
        relocate("kotlinx", "com.github.hoshikurama.ticketmanager.shaded.kotlinx")
    }
}