plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.github.ben-manes.versions") version "0.45.0" // https://github.com/ben-manes/gradle-versions-plugin
    //kotlin("plugin.serialization") version "1.8.10"
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-Beta") //TODO IMPLEMENT INTO PLUGIN
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.0-Beta")
    implementation("com.mysql:mysql-connector-j:8.0.32")
    implementation("com.github.jasync-sql:jasync-mysql:2.1.23")
    implementation("com.github.seratch:kotliquery:1.9.0")
    implementation("net.kyori:adventure-api:4.13.0")
    implementation("net.kyori:adventure-extra-kotlin:4.13.0")
    implementation("net.kyori:adventure-text-minimessage:4.13.0")
    implementation("org.yaml:snakeyaml:2.0")
    implementation("joda-time:joda-time:2.12.2")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.h2database:h2:2.1.214")
    implementation("com.google.guava:guava:31.1-jre")
    compileOnly("net.luckperms:api:5.4")
}

tasks {
    shadowJar {
        dependencies {
            include(project(":common"))
        }
    }
}