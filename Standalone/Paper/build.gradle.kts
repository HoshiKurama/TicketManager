plugins {
    id("com.github.ben-manes.versions") version "0.51.0" // https://github.com/ben-manes/gradle-versions-plugin
    id("com.gradleup.shadow") version "8.3.5"
    kotlin("jvm")
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.paper.PaperPlugin")
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    maven { url = uri("https://repo.codemc.org/repository/maven-public/") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://libraries.minecraft.net") }
}

dependencies {
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.mojang:brigadier:1.0.500")
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:2.0.21")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-datetime-jvm:0.6.1")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.7.3")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")
    compileOnly("com.github.HoshiKurama.TicketManager_API:Common:11.1.1")
    compileOnly("com.github.HoshiKurama.TicketManager_API:TMCoroutine:11.1.1")

    implementation("org.yaml:snakeyaml:2.3")
    implementation("com.github.seratch:kotliquery:1.9.0")
    implementation("com.h2database:h2:2.3.232")
    implementation("dev.jorel:commandapi-bukkit-core:10.1.2")
    implementation("dev.jorel:commandapi-bukkit-shade-mojang-mapped:10.1.2")
    implementation("dev.jorel:commandapi-bukkit-kotlin:10.1.2")
    implementation("joda-time:joda-time:2.13.0")
    implementation("net.kyori:adventure-extra-kotlin:4.17.0")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation(project(":Standalone:commonSE"))
    implementation(project(":common"))
}

kotlin {
    jvmToolchain(21)
}

tasks {
    shadowJar {
        configurations = listOf(project.configurations.runtimeClasspath.get())
        archiveBaseName.set("TMSE-Paper")

        dependencies {
            exclude { it.moduleGroup == "net.kyori" && it.moduleName != "adventure-extra-kotlin"}
            exclude { it.moduleGroup == "org.jetbrains.kotlin" }
        }

        relocate("dev.jorel.commandapi", "com.github.hoshikurama.ticketmanager.shaded.commandapi")
        relocate("org.bstats", "com.github.hoshikurama.ticketmanager.shaded.bStats")
        relocate("kotlin", "com.github.hoshikurama.ticketmanager.shaded.kotlin")
        relocate("kotlinx", "com.github.hoshikurama.ticketmanager.shaded.kotlinx")
    }
}