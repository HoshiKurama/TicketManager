plugins {
    id("com.github.ben-manes.versions") version "0.46.0" // https://github.com/ben-manes/gradle-versions-plugin
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
}

dependencies {
// NO SHADE
    compileOnly("net.luckperms:api:5.4")
    compileOnly("io.papermc.paper:paper-api:1.19.3-R0.1-SNAPSHOT")
    //compileOnly("dev.folia:folia-api:1.19.4-R0.1-SNAPSHOT")

// SHADE
    compileOnly("dev.jorel:commandapi-core:8.8.0")
    implementation("com.github.seratch:kotliquery:1.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.guava:guava:31.1-jre")
    implementation("com.h2database:h2:2.1.214")
    implementation("dev.jorel:commandapi-shade:8.8.0")
    implementation("joda-time:joda-time:2.12.5")
    implementation("net.kyori:adventure-api:4.13.1")
    implementation("net.kyori:adventure-extra-kotlin:4.13.1")
    implementation("net.kyori:adventure-text-minimessage:4.13.1")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.21")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.0-Beta")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-Beta")
    implementation("org.yaml:snakeyaml:2.0")
    implementation(project(":Standalone:commonSE"))
    implementation(project(":common"))
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-Paper")

        dependencies {

            // Provided by Paper
            exclude(dependency("io.papermc.paper:"))
            exclude(dependency("com.google.guava:"))
            exclude(dependency("com.google.code.findbugs:"))
            exclude(dependency("org.checkerframework:"))
            exclude(dependency("com.google.errorprone:"))
            exclude(dependency("com.google.j2objc:"))
            exclude(dependency("com.google.code.gson:"))
            exclude(dependency("net.md-5:"))
            exclude(dependency("org.yaml:"))
            exclude(dependency("com.googlecode.json-simple:"))
            exclude(dependency("it.unimi.dsi:"))
            exclude(dependency("org.apache.logging.log4j:"))
            exclude(dependency("org.slf4j:"))
            exclude(dependency("org.apache.maven:"))
            exclude(dependency("org.codehaus.plexus:"))
            exclude(dependency("javax.inject:"))
            exclude(dependency("org.apache.commons:"))
            exclude(dependency("org.eclipse.sisu:"))
            exclude(dependency("org.apache.maven.resolver:"))
            exclude(dependency("net.kyori:"))

            // Provided by LuckPerms
            exclude(dependency("net.luckperms:"))

            // Other
            include(dependency("net.kyori:adventure-extra-kotlin:")) // INCLUDE Extra Kotlin

            // Relocators
            relocate("com.github.seratch", "com.github.hoshikurama.ticketmanager.shaded.com.github.seratch")
            relocate("com.h2database", "com.github.hoshikurama.ticketmanager.shaded.com.h2database")
            relocate("dev.jorel", "com.github.hoshikurama.ticketmanager.shaded.dev.jorel")
            relocate("joda-time", "com.github.hoshikurama.ticketmanager.shaded.joda-time")
            relocate("org.bstats", "com.github.hoshikurama.ticketmanager.shaded.org.bstats")
            relocate("org.jetbrains.kotlin", "com.github.hoshikurama.ticketmanager.shaded.org.jetbrains.kotlin")
            relocate("org.jetbrains.kotlinx", "com.github.hoshikurama.ticketmanager.shaded.org.jetbrains.kotlinx")
        }
    }
}