plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.github.ben-manes.versions") version "0.46.0" // https://github.com/ben-manes/gradle-versions-plugin
    kotlin("jvm")
    java
    application
}

application {
    mainClass.set("CommonSEShadedDummy")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.yaml:snakeyaml:2.0")
    implementation("joda-time:joda-time:2.12.5")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.h2database:h2:2.1.214")
    implementation("com.google.guava:guava:31.1-jre")
}

tasks {
    // https://github.com/johnrengelman/shadow/blob/d530cff65c086e1cdd666bf3b12663949ae7ffcc/src/docs/changes/README.md Note on ConfigureShadowRelocation

    //.https://github.com/johnrengelman/shadow/issues/44
    shadowJar {
        isEnableRelocation = true



        /*relocate("*","com.github.hoshikurama.ticketmanager.shaded") {
            include("%regex[^(?!.*com\\.github\\.hoshikurama\\.ticketmanager).*]")
        }

         */
        relocate("%regex[^(?!.*com\\.github\\.hoshikurama\\.ticketmanager).*]", "com.github.hoshikurama.ticketmanager.shaded")
        dependencies {}
    }
}