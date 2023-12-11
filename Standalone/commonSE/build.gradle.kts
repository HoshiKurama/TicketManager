plugins {
    id("com.github.ben-manes.versions") version "0.50.0" // https://github.com/ben-manes/gradle-versions-plugin
    kotlin("jvm")
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {

    compileOnly(project(":common"))
    compileOnly("com.github.HoshiKurama.TicketManager_API:Common:11.0.0")
    compileOnly("com.github.HoshiKurama.TicketManager_API:TMCoroutine:11.0.0")
    // Not included but shaded later
    compileOnly("com.github.seratch:kotliquery:1.9.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.14.0")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:1.9.21")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.3")

    // Not included and not shaded later
    compileOnly("net.luckperms:api:5.4")
    compileOnly("net.kyori:adventure-api:4.14.0")
    compileOnly("net.kyori:adventure-extra-kotlin:4.14.0")

    compileOnly("org.yaml:snakeyaml:2.2")
    compileOnly("joda-time:joda-time:2.12.5")
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("com.google.guava:guava:32.1.3-jre")
    compileOnly("com.h2database:h2:2.2.220")
}