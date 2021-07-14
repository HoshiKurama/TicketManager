plugins {
    id("com.github.johnrengelman.shadow") version "7.0.0"
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
    compileOnly("io.papermc.paper:paper-api:1.17.1-R0.1-SNAPSHOT")
    implementation(kotlin("stdlib", version = "1.5.21"))
    implementation("com.github.HoshiKurama:KyoriComponentDSL:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.5.1")
    implementation("net.kyori:adventure-extra-kotlin:4.8.1")
    implementation("joda-time:joda-time:2.10.10")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:1.5.0")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:1.5.0")
    implementation(project(":common"))
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveBaseName.set("TicketManager-Paper")

        dependencies {
            include(dependency("com.github.HoshiKurama:KyoriComponentDSL:1.1.0"))
            include(dependency("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.2.2"))
            include(project(":common"))

            relocate("com.github.hoshikurama.componentDSL", "com.github.hoshikurama.ticketmanager.componentDSL")
            relocate("kotlinx.serialization.json", "com.github.hoshikurama.ticketmanager.shaded.kotlinx.serialization.json")
        }
    }
}