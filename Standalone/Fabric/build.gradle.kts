import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.serialization") version "1.9.20"
    id("fabric-loom") version "1.4-SNAPSHOT"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.github.hoshikurama"
version = "1.0-SNAPSHOT"

base {
    archivesName = "TicketManager-Fabric"
}

repositories {
    mavenCentral()

    // Add repositories to retrieve artifacts from in here.
    // You should only use this when depending on other mods because
    // Loom adds the essential maven repositories to download Minecraft and libraries from automatically.
    // See https://docs.gradle.org/current/userguide/declaring_repositories.html
    // for more information about repositories.
    maven("https://jitpack.io")
}

dependencies {
    // Required for Fabric
    minecraft("com.mojang:minecraft:1.20.2")
    mappings("net.fabricmc:yarn:1.20.2+build.4:v2")
    modImplementation("net.fabricmc:fabric-loader:0.14.24")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.90.7+1.20.2")
    modImplementation("net.fabricmc:fabric-language-kotlin:1.10.13+kotlin.1.9.20")

    modImplementation(include("net.kyori:adventure-platform-fabric:5.10.0")!!)

    // Other dependencies required
    compileOnly("net.luckperms:api:5.4")
    shadow("net.kyori:adventure-text-minimessage:4.14.0")
    shadow("com.github.HoshiKurama.TicketManager_API:Common:11.0.0-RC7")
    shadow("com.github.HoshiKurama.TicketManager_API:TMCoroutine:11.0.0-RC7")
    shadow("org.yaml:snakeyaml:2.2")
    shadow("com.google.code.gson:gson:2.10.1")
    shadow("com.google.guava:guava:32.1.3-jre")
    shadow("com.github.seratch:kotliquery:1.9.0")
    shadow("com.h2database:h2:2.2.220")
    shadow("joda-time:joda-time:2.12.5")
    shadow("net.kyori:adventure-extra-kotlin:4.14.0")
    shadow(project(":Standalone:commonSE"))
    shadow(project(":common"))
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 17
    }

    withType<KotlinCompile>().all {
        kotlinOptions { jvmTarget = "17" }
    }
}

java {
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_TicketManager-Fabric"}
    }
}

tasks.remapJar {
    dependsOn(tasks.shadowJar)
    inputFile.set(tasks.shadowJar.get().archiveFile)
}

tasks.shadowJar {
    configurations = listOf(project.configurations.shadow.get())
    archiveBaseName.set("TicketManager-Fabric")
    isZip64 = true

    // Any specific shadowJar stuff here...
    dependencies {

    }
}

/*
Help from the places below:
---------------------------
- Using Kotlin: https://fabricmc.net/wiki/tutorial:kotlin
- Helpful Template Generator: https://fabricmc.net/develop/template/
- Help with external libs: https://www.reddit.com/r/fabricmc/comments/mkumx8/need_help_compiling_external_libraries_into_mod/
                           https://github.com/Siphalor/spiceoffabric/blob/1.18/build.gradle
 */