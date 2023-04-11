plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.github.ben-manes.versions") version "0.46.0" // https://github.com/ben-manes/gradle-versions-plugin
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-Beta")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.0-Beta")
    implementation("com.mysql:mysql-connector-j:8.0.32") //TODO EXPORT
    implementation("com.github.jasync-sql:jasync-mysql:2.1.23") //TODO EXPORT
    implementation("com.github.seratch:kotliquery:1.9.0")
    implementation("net.kyori:adventure-api:4.13.0")
    implementation("net.kyori:adventure-extra-kotlin:4.13.0")
    implementation("net.kyori:adventure-text-minimessage:4.13.0")
    implementation("org.yaml:snakeyaml:2.0")
    implementation("joda-time:joda-time:2.12.5")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.h2database:h2:2.1.214")
    implementation("com.google.guava:guava:31.1-jre")
    compileOnly("net.luckperms:api:5.4")
}

tasks {
    // https://github.com/johnrengelman/shadow/blob/d530cff65c086e1cdd666bf3b12663949ae7ffcc/src/docs/changes/README.md Note on ConfigureShadowRelocation

    //.https://github.com/johnrengelman/shadow/issues/44
    shadowJar {
        isEnableRelocation = true
        relocationPrefix = "com.github.hoshikurama.ticketmanager.shaded"

        dependencies {
            include(project(":common"))

            exclude(dependency("net.luckperms:api"))

            // Exclude Kyori API
            exclude(dependency("net.kyori:adventure-api"))
            exclude(dependency("net.kyori:examination-api"))
            exclude(dependency("net.kyori:examination-string"))
        }
    }
}