plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.github.ben-manes.versions") version "0.47.0" // https://github.com/ben-manes/gradle-versions-plugin
    kotlin("jvm")
    java
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

    compileOnly("org.spigotmc:spigot-api:1.19-R0.1-SNAPSHOT")
    compileOnly("com.mojang:brigadier:1.0.500")
    compileOnly("net.luckperms:api:5.4")

    implementation("com.github.HoshiKurama.TicketManager_API:Paper:10.0.0")
    implementation("com.github.HoshiKurama.TicketManager_API:Common:10.0.0")
    implementation("com.github.seratch:kotliquery:1.9.0")
    implementation("com.h2database:h2:2.2.220")
    implementation("dev.jorel:commandapi-bukkit-core:9.0.3")
    implementation("dev.jorel:commandapi-bukkit-shade:9.0.3")
    implementation("dev.jorel:commandapi-bukkit-kotlin:9.0.3")
    implementation("net.kyori:adventure-platform-bukkit:4.3.0")
    implementation("net.kyori:adventure-text-minimessage:4.14.0")
    implementation("net.kyori:adventure-extra-kotlin:4.14.0")
    implementation("joda-time:joda-time:2.12.5")
    implementation("org.bstats:bstats-bukkit:3.0.2")

    // Kotlin Stuff
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.0-RC")
    // Projects
    implementation(project(":Standalone:commonSE"))
    implementation(project(":common"))
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-Spigot")

        dependencies {
            // Provided by Spigot
            exclude { it.moduleGroup.startsWith("com.google") }
            exclude { it.moduleGroup.startsWith("org.apache") }
            exclude(dependency("org.checkerframework:.*:.*"))
            exclude(dependency("org.yaml:.*:.*"))
            exclude(dependency("it.unimi.dsi:.*:.*"))
            exclude(dependency("org.slf4j:.*:.*"))
            exclude(dependency("org.codehaus.plexus:.*:.*"))
            exclude(dependency("javax.inject:.*:.*"))
            exclude(dependency("org.eclipse.sisu:.*:.*"))
        }

        // Relocators
        relocate("dev.jorel", "com.github.hoshikurama.ticketmanager.shaded.commandapi")
        relocate("org.bstats", "com.github.hoshikurama.ticketmanager.shaded.bStats")
        relocate("kotliquery", "com.github.hoshikurama.ticketmanager.shaded.kotliquery")
        relocate("org.h2", "com.github.hoshikurama.ticketmanager.shaded.h2")
        relocate("org.joda.time", "com.github.hoshikurama.ticketmanager.shaded.jodatime")
        relocate("kotlin", "com.github.hoshikurama.ticketmanager.shaded.kotlin")
        relocate("kotlinx", "com.github.hoshikurama.ticketmanager.shaded.kotlinx")
        relocate("net.kyori.adventure.extra.kotlin", "com.github.hoshikurama.ticketmanager.shaded.adventureextrakotlin")
        relocate("com.zaxxer.hikari", "com.github.hoshikurama.ticketmanager.shaded.hikari")
        relocate("org.intellij", "com.github.hoshikurama.ticketmanager.shaded.intellij")
        relocate("org.jetbrains.annotations", "com.github.hoshikurama.ticketmanager.shaded.jetbrains.annotations")
        relocate("net.kyori", "com.github.hoshikurama.ticketmanager.shaded.kyori")
    }
}