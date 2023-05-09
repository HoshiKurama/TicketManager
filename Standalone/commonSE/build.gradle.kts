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
    // Not included but shaded later only on certain platforms
    implementation("com.github.seratch:kotliquery:1.9.0")
    implementation("net.kyori:adventure-text-minimessage:4.13.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.21")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-Beta")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.0-Beta")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.20")

    // Not included and not shaded later
    compileOnly("net.luckperms:api:5.4")
    implementation("net.kyori:adventure-api:4.13.1")
    implementation("net.kyori:adventure-extra-kotlin:4.13.1")

    implementation("org.yaml:snakeyaml:2.0")
    implementation("joda-time:joda-time:2.12.5")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.guava:guava:31.1-jre")
    implementation("com.h2database:h2:2.1.214")
}
/*
tasks {
    // https://github.com/johnrengelman/shadow/blob/d530cff65c086e1cdd666bf3b12663949ae7ffcc/src/docs/changes/README.md Note on ConfigureShadowRelocation

    //.https://github.com/johnrengelman/shadow/issues/44
    /*
    shadowJar {
        dependencies {}
    }

     */
}
/*
// Exclude Kyori API
    exclude(dependency("net.kyori:adventure-api"))
    exclude(dependency("net.kyori:examination-api"))
    exclude(dependency("net.kyori:examination-string"))
 */