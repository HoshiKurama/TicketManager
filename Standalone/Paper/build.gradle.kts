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
    maven { url = uri("https://repo.codemc.org/repository/maven-public/") }
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    //TODO DETERMINE WHAT TO SHADE AND NOT SHADE
    compileOnly("io.papermc.paper:paper-api:1.19.3-R0.1-SNAPSHOT") // DO NOT SHADE
    //compileOnly("dev.folia:folia-api:1.19.4-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.20")
    implementation("net.kyori:adventure-extra-kotlin:4.13.0")
    implementation("joda-time:joda-time:2.12.5")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation(project(":common"))
    implementation(project(":Standalone:commonSE"))
    compileOnly("net.luckperms:api:5.4")
    compileOnly("dev.jorel:commandapi-core:8.8.0") // Don't shade this as it should be in the one below
    implementation("dev.jorel:commandapi-shade:8.8.0") //https://commandapi.jorel.dev/8.8.0/shading.html

}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-Paper")
        //NOTE: Implement shaded dependencies in Paper:ShadedDependencies
        dependencies {
            include(project(":common"))
            include(project(":Standalone:commonSE"))
            include(dependency("org.bstats:bstats-bukkit:3.0.2"))
            include(dependency("org.bstats:bstats-base:3.0.2"))

            relocate("org.bstats", "com.github.hoshikurama.ticketmanager.bstats")
        }
    }
}