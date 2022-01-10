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
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots/") {
        name = "sonatype-oss-snapshots"
    }
    maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.18-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.6.0")
    implementation("com.github.HoshiKurama:KyoriComponentDSL:1.1.0")
    implementation("net.kyori:adventure-extra-kotlin:4.9.3")
    implementation("net.kyori:adventure-platform-bukkit:4.0.1")
    implementation("joda-time:joda-time:2.10.13")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-api:1.5.0")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-bukkit-core:1.5.0")
    implementation(project(":common"))
    implementation(files("KyoriAdventureBukkitAddition.jar"))
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-Spigot")

        from(project.files("KyoriAdventureBukkitAddition.jar"))

        dependencies {
            include(dependency("com.github.HoshiKurama:KyoriComponentDSL:1.1.0"))
            include(project(":common"))

            relocate("com.github.hoshikurama.componentDSL", "com.github.hoshikurama.ticketmanager.componentDSL")
        }
    }
}