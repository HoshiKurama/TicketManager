plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    kotlin("jvm")
    java
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.velocity.WaterfallBridge")
}

repositories {
    mavenCentral()
    maven(url = uri("https://papermc.io/repo/repository/maven-public/"))
}

dependencies {
    implementation(project(":common"))
    compileOnly("io.github.waterfallmc:waterfall-api:1.19-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.0")
    implementation("org.bstats:bstats-bungeecord:3.0.0")
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-WaterfallBridge")

        dependencies {
            include(dependency("org.jetbrains.kotlin:kotlin-stdlib:1.7.0"))
            include(project(":common"))
            include(dependency("org.bstats:bstats-bungeecord:3.0.0"))
            include(dependency("org.bstats:bstats-base:3.0.0"))
        }
    }
}