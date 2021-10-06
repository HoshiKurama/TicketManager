plugins {
    id("com.github.johnrengelman.shadow") version "7.0.0"
    kotlin("jvm")
    java
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.spigot.TicketManagerPlugin")
}

repositories {
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/") {
        name = "sonatype-oss-snapshots"
    }
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.17-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.5.2-native-mt")
    implementation("com.github.HoshiKurama:KyoriComponentDSL:1.1.0")
    implementation("net.kyori:adventure-extra-kotlin:4.9.0")
    implementation("net.kyori:adventure-platform-bukkit:4.0.0")
    implementation("joda-time:joda-time:2.10.11")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:1.5.0")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:1.5.0")
    implementation(project(":common"))
    implementation(files("KyoriAdventureBukkitAddition.jar"))
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveBaseName.set("TicketManager-Spigot")

        from(project.files("KyoriAdventureBukkitAddition.jar"))

        dependencies {
            include(dependency("com.github.HoshiKurama:KyoriComponentDSL:1.1.0"))
            include(dependency("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.3.0"))
            include(project(":common"))

            relocate("com.github.hoshikurama.componentDSL", "com.github.hoshikurama.ticketmanager.componentDSL")
            relocate("kotlinx.serialization.json", "com.github.hoshikurama.ticketmanager.shaded.kotlinx.serialization.json")
        }
    }
}