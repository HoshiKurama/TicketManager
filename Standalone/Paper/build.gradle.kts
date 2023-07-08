plugins {
    id("com.github.ben-manes.versions") version "0.47.0" // https://github.com/ben-manes/gradle-versions-plugin
    id("com.github.johnrengelman.shadow") version "8.1.1"
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
    maven { url = uri("https://repo.codemc.org/repository/maven-public/") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://libraries.minecraft.net") }
}

dependencies {
    compileOnly("net.luckperms:api:5.4")
    compileOnly("io.papermc.paper:paper-api:1.20-R0.1-SNAPSHOT")
    compileOnly("org.yaml:snakeyaml:2.0")
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("com.google.guava:guava:32.1.1-jre")
    compileOnly("net.kyori:adventure-api:4.14.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.14.0")
    //compileOnly("dev.folia:folia-api:1.19.4-R0.1-SNAPSHOT")

    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")
    implementation("com.github.HoshiKurama.TicketManager_API:Paper:10.0.0-RC30")
    implementation("com.github.HoshiKurama.TicketManager_API:Common:10.0.0-RC30")
    implementation("com.mojang:brigadier:1.0.500")
    implementation("com.github.seratch:kotliquery:1.9.0")
    implementation("com.h2database:h2:2.1.214")
    implementation("dev.jorel:commandapi-bukkit-core:9.0.3")
    implementation("dev.jorel:commandapi-bukkit-shade:9.0.3")
    implementation("dev.jorel:commandapi-bukkit-kotlin:9.0.3")
    implementation("joda-time:joda-time:2.12.5")
    implementation("net.kyori:adventure-extra-kotlin:4.14.0")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.21")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")

    implementation(project(":Standalone:commonSE"))
    implementation(project(":common"))
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-Paper")

        dependencies {
            // Provided by Paper
            exclude { it.moduleGroup.startsWith("com.google") }
            exclude { it.moduleGroup.startsWith("org.apache") }
            exclude(dependency("org.checkerframework:.*:.*"))
            exclude(dependency("org.yaml:.*:.*"))
            exclude(dependency("it.unimi.dsi:.*:.*"))
            exclude(dependency("org.slf4j:.*:.*"))
            exclude(dependency("org.codehaus.plexus:.*:.*"))
            exclude(dependency("javax.inject:.*:.*"))
            exclude(dependency("org.eclipse.sisu:.*:.*"))
            exclude(dependency("com.mojang.brigadier:.*"))

            // Excludes for Adventure
            exclude { it.moduleGroup == "net.kyori" && it.moduleName != "adventure-extra-kotlin"}
        }

        // Relocators
        relocate("dev.jorel", "com.github.hoshikurama.ticketmanager.shaded.commandapi")
        relocate("org.bstats", "com.github.hoshikurama.ticketmanager.shaded.bStats")
        relocate("kotliquery", "com.github.hoshikurama.ticketmanager.shaded.kotliquery")
        relocate("org.h2", "com.github.hoshikurama.ticketmanager.shaded.org.h2")
        relocate("org.joda.time", "com.github.hoshikurama.ticketmanager.shaded.jodatime")
        relocate("kotlin", "com.github.hoshikurama.ticketmanager.shaded.kotlin")
        relocate("kotlinx", "com.github.hoshikurama.ticketmanager.shaded.kotlinx")
        relocate("net.kyori.adventure.extra.kotlin", "com.github.hoshikurama.ticketmanager.shaded.adventureextrakotlin")
        relocate("com.zaxxer.hikari", "com.github.hoshikurama.ticketmanager.shaded.hikari")
    }
}