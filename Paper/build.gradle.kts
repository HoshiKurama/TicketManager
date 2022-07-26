plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    kotlin("jvm")
    java
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.paper.TicketManagerPlugin")
}

repositories {
    mavenCentral()
    maven { url = uri("https://papermc.io/repo/repository/maven-public/") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.19-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.10")
    implementation("net.kyori:adventure-extra-kotlin:4.11.0")
    implementation("joda-time:joda-time:2.10.14")
    implementation("org.bstats:bstats-bukkit:3.0.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    implementation(project(":core"))
    implementation(project(":common"))
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-Paper")

        dependencies {
            include(project(":core"))
            include(project(":common"))
            include(dependency("org.bstats:bstats-bukkit:3.0.0"))
            include(dependency("org.bstats:bstats-base:3.0.0"))

            relocate("org.bstats", "com.github.hoshikurama.ticketmanager.bstats")
        }
    }
}