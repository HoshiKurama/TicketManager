plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.github.ben-manes.versions") version "0.46.0" // https://github.com/ben-manes/gradle-versions-plugin
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
    implementation(project(":common"))
    implementation("org.yaml:snakeyaml:2.0")
    implementation("joda-time:joda-time:2.12.5")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.google.guava:guava:31.1-jre")
    implementation("com.h2database:h2:2.1.214")
}

tasks {
    // https://github.com/johnrengelman/shadow/blob/d530cff65c086e1cdd666bf3b12663949ae7ffcc/src/docs/changes/README.md Note on ConfigureShadowRelocation

    //.https://github.com/johnrengelman/shadow/issues/44
    shadowJar {
        //isEnableRelocation = true

        relocate("com", "com.github.hoshikurama.ticketmanager.shaded.com") {
            exclude("%regex[^(?!.*com\\.github\\.hoshikurama\\.ticketmanager).*]")
        }

        /*
        relocate("a", "com.github.hoshikurama.ticketmanager.shaded.a")
        relocate("b", "com.github.hoshikurama.ticketmanager.shaded.b")
        relocate("d", "com.github.hoshikurama.ticketmanager.shaded.d")
        relocate("e", "com.github.hoshikurama.ticketmanager.shaded.e")
        relocate("f", "com.github.hoshikurama.ticketmanager.shaded.f")
        relocate("g", "com.github.hoshikurama.ticketmanager.shaded.g")
        relocate("h", "com.github.hoshikurama.ticketmanager.shaded.h")
        relocate("i", "com.github.hoshikurama.ticketmanager.shaded.i")
        relocate("j", "com.github.hoshikurama.ticketmanager.shaded.j")
        relocate("k", "com.github.hoshikurama.ticketmanager.shaded.k")
        relocate("l", "com.github.hoshikurama.ticketmanager.shaded.l")
        relocate("m", "com.github.hoshikurama.ticketmanager.shaded.m")
        relocate("n", "com.github.hoshikurama.ticketmanager.shaded.n")
        relocate("o", "com.github.hoshikurama.ticketmanager.shaded.o")
        relocate("p", "com.github.hoshikurama.ticketmanager.shaded.p")
        relocate("q", "com.github.hoshikurama.ticketmanager.shaded.q")
        relocate("r", "com.github.hoshikurama.ticketmanager.shaded.r")
        relocate("s", "com.github.hoshikurama.ticketmanager.shaded.s")
        relocate("t", "com.github.hoshikurama.ticketmanager.shaded.t")
        relocate("u", "com.github.hoshikurama.ticketmanager.shaded.u")
        relocate("v", "com.github.hoshikurama.ticketmanager.shaded.v")
        relocate("w", "com.github.hoshikurama.ticketmanager.shaded.w")
        relocate("x", "com.github.hoshikurama.ticketmanager.shaded.x")
        relocate("y", "com.github.hoshikurama.ticketmanager.shaded.y")
        relocate("z", "com.github.hoshikurama.ticketmanager.shaded.z")
         */
        relocate("javax", "com.github.hoshikurama.ticketmanager.shaded.javax")
        relocate("com.google", "com.github.hoshikurama.ticketmanager.shaded.com.google")
        relocate("org", "com.github.hoshikurama.ticketmanager.shaded.org")

        dependencies {
            exclude(project(":common"))
            exclude(dependency("org.jetbrains.kotlin:"))
        }
    }
}