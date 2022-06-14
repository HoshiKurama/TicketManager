plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    kotlin("jvm")
    java
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.spigot.TicketManagerPlugin")
}

repositories {
    mavenCentral()
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.19-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.0")
    implementation("net.kyori:adventure-extra-kotlin:4.11.0")
    implementation("net.kyori:adventure-platform-bukkit:4.1.0")
    implementation("joda-time:joda-time:2.10.14")
    implementation("org.bstats:bstats-bukkit:3.0.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    implementation(project(":core"))
    implementation(project(":common"))
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-Spigot")

        dependencies {
            include(project(":core"))
            include(project(":common"))
            include(dependency("org.bstats:bstats-bukkit:3.0.0"))
            include(dependency("org.bstats:bstats-base:3.0.0"))

            relocate("org.bstats", "com.github.hoshikurama.ticketmanager.bstats")
        }
    }
}