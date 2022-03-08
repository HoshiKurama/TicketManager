plugins {
    kotlin("plugin.serialization") version "1.6.10"
    kotlin("jvm")
    java
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.10")
    implementation("mysql:mysql-connector-java:8.0.25")
    implementation("org.xerial:sqlite-jdbc:3.36.0.3")
    implementation("com.github.jasync-sql:jasync-mysql:2.0.6")
    implementation("com.github.seratch:kotliquery:1.6.3")
    implementation("net.kyori:adventure-api:4.10.0")
    implementation("net.kyori:adventure-extra-kotlin:4.10.0")
    implementation("net.kyori:adventure-text-minimessage:4.10.0")
    implementation("org.yaml:snakeyaml:1.29")
    implementation("joda-time:joda-time:2.10.13")
    implementation("com.discord4j:discord4j-core:3.2.2")
    implementation("com.google.code.gson:gson:2.9.0")
}
