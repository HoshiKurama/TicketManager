plugins {
    id("com.github.johnrengelman.shadow") version "7.1.0"
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
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.10")
    implementation("net.kyori:adventure-extra-kotlin:4.10.0")
    implementation("joda-time:joda-time:2.10.13")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    implementation(project(":common"))
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-Paper")

        dependencies {
            include(project(":common"))
        }
    }
}