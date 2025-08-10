plugins {
    id("com.gradleup.shadow") version "9.0.1"
    id("com.github.ben-manes.versions") version "0.52.0" // https://github.com/ben-manes/gradle-versions-plugin
    kotlin("jvm")
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.waterfall.WaterfallBridge")
}

repositories {
    mavenCentral()
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    compileOnly("net.md-5:bungeecord-api:1.21-R0.3")

    implementation(project(":common"))
    implementation(project(":ProxyBridge:commonPDE"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
    implementation("org.bstats:bstats-bungeecord:3.1.0")
    implementation("org.yaml:snakeyaml:2.4")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-WaterfallBridge")

        relocate("org.bstats", "com.github.hoshikurama.ticketmanager.bstats")
    }
}