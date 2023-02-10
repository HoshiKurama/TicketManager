plugins {
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.github.ben-manes.versions") version "0.45.0" // https://github.com/ben-manes/gradle-versions-plugin
    kotlin("jvm")
    java
    application
}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.waterfall.WaterfallBridge")
}

repositories {
    mavenCentral()
    maven(url = uri("https://papermc.io/repo/repository/maven-public/"))
}

dependencies {
    implementation(project(":common"))
    implementation(project(":ProxyBridge:commonPDE"))
    compileOnly("io.github.waterfallmc:waterfall-api:1.19-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")
    implementation("org.bstats:bstats-bungeecord:3.0.0")
    implementation("org.yaml:snakeyaml:1.33")
    implementation("com.discord4j:discord4j-core:3.2.3")
}

tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-WaterfallBridge")

        dependencies {
            include(dependency("org.jetbrains.kotlin:kotlin-stdlib:1.8.10"))
            include(project(":common"))
            include(project(":ProxyBridge:commonPDE"))
            include(dependency("org.bstats:bstats-bungeecord:3.0.0"))
            include(dependency("org.bstats:bstats-base:3.0.0"))
            include(dependency("org.yaml:snakeyaml:1.33"))
            include(dependency("com.discord4j:discord4j-core:3.2.3")) // Remember to keep updating this
        }

        relocate("org.bstats", "com.github.hoshikurama.ticketmanager.bstats")
    }
}