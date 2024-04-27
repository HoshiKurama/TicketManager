plugins {
    id("com.github.ben-manes.versions") version "0.51.0" // https://github.com/ben-manes/gradle-versions-plugin
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("jvm")
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.paper.PaperPlugin")
}

repositories {
    mavenCentral()
    maven { url = uri("https://papermc.io/repo/repository/maven-public/") }
    maven { url = uri("https://repo.codemc.org/repository/maven-public/") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://libraries.minecraft.net") }
}

dependencies {
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.mojang:brigadier:1.0.500")
    compileOnly("io.papermc.paper:paper-api:1.20.2-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:1.9.23")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.5.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.3")
    compileOnly("net.kyori:adventure-text-minimessage:4.16.0")
    compileOnly("com.github.HoshiKurama.TicketManager_API:Common:11.1.0")
    compileOnly("com.github.HoshiKurama.TicketManager_API:TMCoroutine:11.1.0")

    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.github.seratch:kotliquery:1.9.0")
    implementation("com.h2database:h2:2.2.224")
    implementation("dev.jorel:commandapi-bukkit-core:9.3.0")
    implementation("dev.jorel:commandapi-bukkit-shade:9.3.0")
    implementation("dev.jorel:commandapi-bukkit-kotlin:9.3.0")
    implementation("joda-time:joda-time:2.12.7")
    implementation("net.kyori:adventure-extra-kotlin:4.16.0")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation(project(":Standalone:commonSE"))
    implementation(project(":common"))
}

tasks {
    shadowJar {
        configurations = listOf(project.configurations.runtimeClasspath.get())
        archiveBaseName.set("TMSE-Paper")

        dependencies {
            exclude { it.moduleGroup == "net.kyori" && it.moduleName != "adventure-extra-kotlin"}
            exclude { it.moduleGroup == "org.jetbrains.kotlin" }
        }

        relocate("dev.jorel", "com.github.hoshikurama.ticketmanager.shaded.commandapi")
        relocate("org.bstats", "com.github.hoshikurama.ticketmanager.shaded.bStats")
        relocate("kotlin", "com.github.hoshikurama.ticketmanager.shaded.kotlin")
        relocate("kotlinx", "com.github.hoshikurama.ticketmanager.shaded.kotlinx")
    }
}