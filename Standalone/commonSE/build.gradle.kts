plugins {
    id("com.github.ben-manes.versions") version "0.51.0" // https://github.com/ben-manes/gradle-versions-plugin
    kotlin("jvm")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {

    compileOnly(project(":common"))
    compileOnly("com.github.HoshiKurama.TicketManager_API:Common:11.1.1")
    compileOnly("com.github.HoshiKurama.TicketManager_API:TMCoroutine:11.1.1")
    // Not included but shaded later
    compileOnly("com.github.seratch:kotliquery:1.9.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:2.0.21")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")

    // Not included and not shaded later
    compileOnly("net.luckperms:api:5.4")
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-extra-kotlin:4.17.0")

    compileOnly("org.yaml:snakeyaml:2.3")
    compileOnly("joda-time:joda-time:2.13.0")
    compileOnly("com.google.code.gson:gson:2.11.0")
    compileOnly("com.google.guava:guava:33.3.1-jre")
    compileOnly("com.h2database:h2:2.3.232")
}

kotlin {
    jvmToolchain(21)
}