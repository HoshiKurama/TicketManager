plugins {
    id("com.gradleup.shadow") version "9.0.1"
    id("com.github.ben-manes.versions") version "0.52.0" // https://github.com/ben-manes/gradle-versions-plugin
    kotlin("jvm")
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.spigot.SpigotPlugin")
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://libraries.minecraft.net")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("net.luckperms:api:5.5")
    compileOnly("com.mojang:brigadier:1.0.500")
    compileOnly("org.spigotmc:spigot-api:1.21.3-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:2.2.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    compileOnly("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.7.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0")
    compileOnly("com.github.HoshiKurama.TicketManager_API:Common:12.1.0")
    compileOnly("com.github.HoshiKurama.TicketManager_API:TMCoroutine:12.1.0")

    implementation("org.yaml:snakeyaml:2.4")
    implementation("com.github.seratch:kotliquery:1.9.1")
    implementation("com.h2database:h2:2.3.232")
    implementation("dev.jorel:commandapi-spigot-core:11.0.0")
    implementation("dev.jorel:commandapi-spigot-shade:11.0.0")
    implementation("dev.jorel:commandapi-kotlin-spigot:11.0.0")
    implementation("joda-time:joda-time:2.14.0")
    implementation("net.kyori:adventure-platform-bukkit:4.4.1")
    implementation("net.kyori:adventure-text-minimessage:4.24.0")
    implementation("net.kyori:adventure-extra-kotlin:4.24.0")
    implementation("net.kyori:adventure-text-serializer-bungeecord:4.4.1")
    implementation("org.bstats:bstats-bukkit:3.1.0")
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
    }
}