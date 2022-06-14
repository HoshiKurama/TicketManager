plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    kotlin("jvm")
    java
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.velocity.TMPluginVelocityImpl")
}

repositories {
    mavenCentral()
    maven {
        name = "velocity"
        url = uri("https://nexus.velocitypowered.com/repository/maven-public/")

    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":common"))
    compileOnly("com.velocitypowered:velocity-api:3.0.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.0")
    annotationProcessor("com.velocitypowered:velocity-api:3.0.1")
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-VelocityBridge")

        dependencies {
            include(dependency("org.jetbrains.kotlin:kotlin-stdlib:1.7.0"))
            include(project(":common"))
        }
    }
}