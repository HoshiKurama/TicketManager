plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.github.ben-manes.versions") version "0.49.0" // https://github.com/ben-manes/gradle-versions-plugin
    kotlin("jvm")
    java
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.waterfall.WaterfallBridge")
}

repositories {
    mavenCentral()
    maven(url = uri("https://papermc.io/repo/repository/maven-public/"))
}

dependencies {
    implementation(project(":common"))
    implementation(project(":ProxyBridge:commonPDE"))
    compileOnly("io.github.waterfallmc:waterfall-api:1.20-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("org.bstats:bstats-bungeecord:3.0.2")
    implementation("org.yaml:snakeyaml:2.2")
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-WaterfallBridge")

        relocate("org.bstats", "com.github.hoshikurama.ticketmanager.bstats")
    }
}