plugins {
    id("com.github.ben-manes.versions") version "0.52.0" // https://github.com/ben-manes/gradle-versions-plugin
    id("com.gradleup.shadow") version "9.0.1"
    kotlin("jvm")
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.velocity.TMPluginImpl")
}

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")

    implementation(project(":common"))
    implementation(project(":ProxyBridge:commonPDE"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
    implementation("org.bstats:bstats-velocity:3.1.0")
    implementation("org.yaml:snakeyaml:2.4")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-VelocityBridge")

        relocate("org.bstats", "com.github.hoshikurama.ticketmanager.bstats")
    }
}