plugins {
    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("org.spongepowered.plugin") version "0.9.0"
    kotlin("jvm")
    java
    application
}

sponge {

}

application {
    mainClass.set("com.github.hoshikurama.ticketmanager.sponge.SpongePlugin")
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("org.spongepowered:spongeapi:8.0.0")
    //implementation(project(":core"))
}

/*
sponge {
    apiVersion("8.0.0")
    license("All Rights Reserved")
    loader {
        name(PluginLoaders.JAVA_PLAIN)
        version("1.0")
    }
    plugin("test") {
        displayName("Test")
        entrypoint("test.test.Test")
        description("My plugin description")
        links {
            // homepage("https://spongepowered.org")
            // source("https://spongepowered.org/source")
            // issues("https://spongepowered.org/issues")
        }
        dependency("spongeapi") {
            loadOrder(PluginDependency.LoadOrder.AFTER)
            optional(false)
        }
    }
}
 */