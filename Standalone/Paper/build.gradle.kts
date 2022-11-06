plugins {
    id("com.github.ben-manes.versions") version "0.42.0" // https://github.com/ben-manes/gradle-versions-plugin
    id("com.github.johnrengelman.shadow") version "7.1.2"
    kotlin("jvm")
    java
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.paper.PaperPlugin")
}

repositories {
    mavenCentral()
    maven { url = uri("https://papermc.io/repo/repository/maven-public/") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.19-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.20")
    implementation("net.kyori:adventure-extra-kotlin:4.11.0")
    implementation("joda-time:joda-time:2.12.1")
    implementation("org.bstats:bstats-bukkit:3.0.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    implementation(project(":common"))
    implementation(project(":Standalone:commonSE"))
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-Paper")

        dependencies {
            include(project(":common"))
            include(project(":Standalone:commonSE"))
            include(dependency("org.bstats:bstats-bukkit:3.0.0"))
            include(dependency("org.bstats:bstats-base:3.0.0"))

            relocate("org.bstats", "com.github.hoshikurama.ticketmanager.bstats")
        }
    }
}