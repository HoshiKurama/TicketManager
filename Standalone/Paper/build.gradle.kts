plugins {
    id("com.github.ben-manes.versions") version "0.46.0" // https://github.com/ben-manes/gradle-versions-plugin
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("jvm")
    java
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.paper.PaperPlugin")
}

repositories {
    mavenCentral()
    maven { url = uri("https://papermc.io/repo/repository/maven-public/") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.19-R0.1-SNAPSHOT")
    //compileOnly("dev.folia:folia-api:1.19.4-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.20")
    implementation("net.kyori:adventure-extra-kotlin:4.13.0")
    implementation("joda-time:joda-time:2.12.5")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation(project(":common"))
    implementation(project(":Standalone:commonSE"))
    compileOnly("net.luckperms:api:5.4")
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-Paper")

        dependencies {
            include(project(":common"))
            include(project(":Standalone:commonSE"))
            include(dependency("org.bstats:bstats-bukkit:3.0.2"))
            include(dependency("org.bstats:bstats-base:3.0.2"))

            relocate("org.bstats", "com.github.hoshikurama.ticketmanager.bstats")
        }
    }
}