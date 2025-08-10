plugins {
    id("com.github.ben-manes.versions") version "0.52.0" // https://github.com/ben-manes/gradle-versions-plugin
    kotlin("jvm")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {

    compileOnly(project(":common"))
    compileOnly("com.github.HoshiKurama.TicketManager_API:Common:11.2.0")
    compileOnly("com.github.HoshiKurama.TicketManager_API:TMCoroutine:11.2.0")
    // Not included but shaded later
    compileOnly("com.github.seratch:kotliquery:1.9.1")
    compileOnly("net.kyori:adventure-text-minimessage:4.21.0")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:2.2.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")

    // Not included and not shaded later
    compileOnly("net.luckperms:api:5.5")
    compileOnly("net.kyori:adventure-api:4.24.0")
    compileOnly("net.kyori:adventure-extra-kotlin:4.24.0")

    compileOnly("org.yaml:snakeyaml:2.4")
    compileOnly("joda-time:joda-time:2.14.0")
    compileOnly("com.google.code.gson:gson:2.13.1")
    compileOnly("com.google.guava:guava:33.4.8-jre")
    compileOnly("com.h2database:h2:2.3.232")
}

kotlin {
    jvmToolchain(21)
}