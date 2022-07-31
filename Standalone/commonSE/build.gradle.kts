plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    kotlin("plugin.serialization") version "1.7.10"
    kotlin("jvm")
    java
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.commonse.TMPlugin")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation(project(":common"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.7.10")
    implementation("mysql:mysql-connector-java:8.0.29")
    implementation("com.github.jasync-sql:jasync-mysql:2.0.8")
    implementation("com.github.seratch:kotliquery:1.8.0")
    implementation("net.kyori:adventure-api:4.11.0")
    implementation("net.kyori:adventure-extra-kotlin:4.11.0")
    implementation("net.kyori:adventure-text-minimessage:4.11.0")
    implementation("org.yaml:snakeyaml:1.30")
    implementation("joda-time:joda-time:2.10.14")
    implementation("com.discord4j:discord4j-core:3.2.2")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("com.h2database:h2:2.1.212")
    implementation("com.google.guava:guava:31.1-jre")
}

tasks {
    shadowJar {
        dependencies {
            include(project(":common"))
        }
    }
}