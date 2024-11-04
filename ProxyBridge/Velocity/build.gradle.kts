plugins {
    id("com.github.ben-manes.versions") version "0.51.0" // https://github.com/ben-manes/gradle-versions-plugin
    id("com.gradleup.shadow") version "8.3.5"
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.21")
    implementation("org.bstats:bstats-velocity:3.0.2")
    implementation("org.yaml:snakeyaml:2.3")
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