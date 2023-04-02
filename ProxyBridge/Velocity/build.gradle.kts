plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.github.ben-manes.versions") version "0.45.0" // https://github.com/ben-manes/gradle-versions-plugin
    kotlin("jvm")
    java
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.velocity.TMPluginImpl")
}

repositories {
    mavenCentral()
    maven {
        name = "velocity"
        url = uri("https://nexus.velocitypowered.com/repository/maven-public/")

    }
}

// Link to repo: https://nexus.velocitypowered.com/#browse/search=keyword%3Dvelocitypowered
dependencies {
    implementation(project(":common"))
    implementation(project(":ProxyBridge:commonPDE"))
    compileOnly("com.velocitypowered:velocity-api:3.1.0")
    annotationProcessor("com.velocitypowered:velocity-api:3.1.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.20")
    implementation("org.bstats:bstats-velocity:3.0.1")
    implementation("org.yaml:snakeyaml:2.0")
    implementation("com.discord4j:discord4j-core:3.2.3")
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-VelocityBridge")

        relocate("org.bstats", "com.github.hoshikurama.ticketmanager.bstats")
        relocate("io.netty", "com.discord4j.shaded.io.netty")
    }
}