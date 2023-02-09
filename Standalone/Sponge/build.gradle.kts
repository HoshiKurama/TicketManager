import org.spongepowered.gradle.plugin.config.PluginLoaders

plugins {
    id("com.github.ben-manes.versions") version "0.45.0" // https://github.com/ben-manes/gradle-versions-plugin
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.spongepowered.gradle.plugin") version "2.1.1"
    kotlin("jvm")
    java
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.8.10")
    implementation(project(":common"))
    implementation(project(":Standalone:commonSE"))
    compileOnly("net.luckperms:api:5.4")
}

sponge {
    apiVersion("8.1.0")
    loader {
        name(PluginLoaders.JAVA_PLAIN)
        version("1.0")
    }
    license("AGPL-3.0")
    plugin("ticketmanager") {
        displayName("TicketManager")
        version("9.0.0") // Update each time
        entrypoint("com.github.hoshikurama.ticketmanager.sponge.") //TODO
        description("Advanced support-ticket plugin")
        links {
            source("https://github.com/HoshiKurama/TicketManager")
        }
        contributor("HoshiKurama") {
            description("Developer")
        }

        dependency("spongeapi") {
            loadOrder(org.spongepowered.plugin.metadata.model.PluginDependency.LoadOrder.AFTER)
            optional(false)
        }
        dependency("luckperms") {
            loadOrder(org.spongepowered.plugin.metadata.model.PluginDependency.LoadOrder.AFTER)
            optional(false)
        }
    }
}

/*
tasks {
    shadowJar {
        archiveBaseName.set("TicketManager-Paper")

        dependencies {
            include(project(":common"))
            include(project(":Standalone:commonSE"))
            include(dependency("org.bstats:bstats-bukkit:3.0.0"))
            include(dependency("org.bstats:bstats-base:3.0.0"))

            relocate("org.bstats", "com.github.hoshikurama.ticketmanager.bstats")
        }
    }
}
 */