import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.10"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    application
    java
}

group = "com.hoshikurama.github"
version = "4.0.1"

apply {
    plugin("application")
}

application {
    mainClass.set("com.hoshikurama.github.ticketmanager.TicketManagerPlugin")
}

repositories {
    mavenCentral()
    maven { url = uri("https://papermc.io/repo/repository/maven-public/") }
    maven { url = uri("https://oss.sonatype.org/content/groups/public/") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://oss.sonatype.org/content/repositories/central") }
}

dependencies {
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    implementation("joda-time:joda-time:2.10.10")
    implementation(kotlin("stdlib", version = "1.5.10"))
    implementation("com.github.seratch:kotliquery:1.3.1")
    implementation("com.zaxxer:HikariCP:4.0.3")
    implementation("mysql:mysql-connector-java:8.0.25")
    implementation("org.xerial:sqlite-jdbc:3.34.0")
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        dependencies {
            include(dependency("com.zaxxer:HikariCP:4.0.3"))
            include(dependency("mysql:mysql-connector-java:8.0.25"))
            include(dependency("org.xerial:sqlite-jdbc:3.34.0"))
            include(dependency("org.jetbrains.kotlin:kotlin-stdlib:1.5.10"))
            include(dependency("com.github.seratch:kotliquery:1.3.1"))
            include(dependency("joda-time:joda-time:2.10.10"))
        }

        relocate("com.zaxxer.hikari","com.hoshikurama.github.ticketmanager.shaded.zaxxerHikari")
        relocate("com.mysql","com.hoshikurama.github.ticketmanager.shaded.mysqlDrivers")
        relocate("org.sqlite","com.hoshikurama.github.ticketmanager.shaded.sqliteDrivers")
        relocate("kotlin","com.hoshikurama.github.ticketmanager.shaded.kotlin-stdlib")
        relocate("kotliquery","com.hoshikurama.github.ticketmanager.shaded.kotliquery")
        relocate("org.joda","com.hoshikurama.github.ticketmanager.shaded.joda-time")
    }
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}