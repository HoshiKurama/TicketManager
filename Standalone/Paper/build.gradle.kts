plugins {
    id("com.github.ben-manes.versions") version "0.47.0" // https://github.com/ben-manes/gradle-versions-plugin
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
    compileOnly("io.papermc.paper:paper-api:1.20-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:1.9.10")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.10")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    compileOnly("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.4.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.6.0")
    compileOnly("net.kyori:adventure-api:4.14.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.14.0")
    compileOnly("com.mojang:brigadier:1.0.500")
    compileOnly("com.github.HoshiKurama.TicketManager_API:Common:11.0.0-RC1")
    compileOnly("com.github.HoshiKurama.TicketManager_API:TMCoroutine:11.0.0-RC1")

    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.guava:guava:32.1.2-jre")
    implementation("com.github.seratch:kotliquery:1.9.0")
    implementation("com.h2database:h2:2.2.220")
    implementation("dev.jorel:commandapi-bukkit-core:9.2.0")
    implementation("dev.jorel:commandapi-bukkit-shade:9.2.0")
    implementation("dev.jorel:commandapi-bukkit-kotlin:9.2.0")
    implementation("joda-time:joda-time:2.12.5")
    implementation("net.kyori:adventure-extra-kotlin:4.14.0")
    implementation("org.bstats:bstats-bukkit:3.0.2")

    implementation(project(":Standalone:commonSE"))
    implementation(project(":common"))
}

tasks {
    shadowJar {
        configurations = listOf(project.configurations.runtimeClasspath.get())
        archiveBaseName.set("TicketManager-Paper")

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