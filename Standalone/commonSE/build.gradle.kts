plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.github.ben-manes.versions") version "0.47.0" // https://github.com/ben-manes/gradle-versions-plugin
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
    implementation("com.github.HoshiKurama.TicketManager_API:Common:10.0.0-RC26")
    // Not included but shaded later
    implementation("com.github.seratch:kotliquery:1.9.0")
    implementation("net.kyori:adventure-text-minimessage:4.14.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22")

    // Not included and not shaded later
    compileOnly("net.luckperms:api:5.4")
    implementation("net.kyori:adventure-api:4.14.0")
    implementation("net.kyori:adventure-extra-kotlin:4.14.0")

    implementation("org.yaml:snakeyaml:2.0")
    implementation("joda-time:joda-time:2.12.5")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.guava:guava:32.0.1-jre")
    implementation( "com.h2database:h2:2.1.214")
}