plugins {
    id("com.github.johnrengelman.shadow") version "7.1.0"
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
    compileOnly("org.spigotmc:spigot-api:1.18-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.10")
    implementation("net.kyori:adventure-extra-kotlin:4.10.1")
    implementation("net.kyori:adventure-platform-bukkit:4.1.0")
    implementation("joda-time:joda-time:2.10.13")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    implementation(project(":common"))
    //implementation(files("KyoriAdventureBukkitAddition.jar"))
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-Spigot")

        //from(project.files("KyoriAdventureBukkitAddition.jar"))

        dependencies {
            include(project(":common"))
        }
    }
}