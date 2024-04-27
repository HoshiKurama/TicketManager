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
    compileOnly("com.github.HoshiKurama.TicketManager_API:Common:11.1.0")
    compileOnly("com.github.HoshiKurama.TicketManager_API:TMCoroutine:11.1.0")
    // Not included but shaded later
    compileOnly("com.github.seratch:kotliquery:1.9.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.16.0")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:1.9.23")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.8.0")

    // Not included and not shaded later
    compileOnly("net.luckperms:api:5.4")
    compileOnly("net.kyori:adventure-api:4.16.0")
    compileOnly("net.kyori:adventure-extra-kotlin:4.16.0")

    compileOnly("org.yaml:snakeyaml:2.2")
    compileOnly("joda-time:joda-time:2.12.7")
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("com.google.guava:guava:33.1.0-jre")
    compileOnly("com.h2database:h2:2.2.224")
}