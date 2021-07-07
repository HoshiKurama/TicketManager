plugins {
    kotlin("jvm")
    java
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation(kotlin("stdlib", version = "1.5.20"))
    implementation("mysql:mysql-connector-java:8.0.25")
    implementation("org.xerial:sqlite-jdbc:3.34.0")
    implementation("com.github.jasync-sql:jasync-mysql:1.1.6")
    implementation("com.github.seratch:kotliquery:1.3.1")
    implementation("com.github.HoshiKurama:KyoriComponentDSL:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.5.0")
    implementation("net.kyori:adventure-api:4.8.1")
    implementation("net.kyori:adventure-extra-kotlin:4.8.1")
    implementation("net.kyori:adventure-text-serializer-legacy:4.8.1")
    implementation("org.yaml:snakeyaml:1.29")
    implementation("joda-time:joda-time:2.10.10")
}