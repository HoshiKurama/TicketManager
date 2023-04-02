plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.github.ben-manes.versions") version "0.45.0" // https://github.com/ben-manes/gradle-versions-plugin
    kotlin("jvm")
    java
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.spigot.SpigotPlugin")
}

repositories {
    mavenCentral()
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.19-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.20")
    implementation("net.kyori:adventure-extra-kotlin:4.13.0")
    implementation("net.kyori:adventure-platform-bukkit:4.3.0")
    implementation("joda-time:joda-time:2.12.2")
    implementation("org.bstats:bstats-bukkit:3.0.1")
    implementation(project(":Standalone:commonSE"))
    implementation(project(":common"))
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-Spigot")

        dependencies {
            include(project(":Standalone:commonSE"))
            include(project(":common"))
            include(dependency("org.bstats:bstats-bukkit:3.0.1"))
            include(dependency("org.bstats:bstats-base:3.0.1"))

            relocate("org.bstats", "com.github.hoshikurama.ticketmanager.bstats")
        }
    }
}