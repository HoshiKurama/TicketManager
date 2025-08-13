rootProject.name = "TicketManager"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        //maven("https://maven.fabricmc.net") { name = "Fabric" }
    }
}

include("common")
include("Standalone")
include("Standalone:Paper")
findProject(":Standalone:Paper")?.name = "Paper"
include("Standalone:commonSE")
findProject(":Standalone:commonSE")?.name = "commonSE"
include("Standalone:Spigot")
findProject(":Standalone:Spigot")?.name = "Spigot"
include("ProxyBridge")
include("ProxyBridge:Velocity")
findProject(":ProxyBridge:Velocity")?.name = "Velocity"
include("ProxyBridge:Bungeecord")
findProject(":ProxyBridge:Bungeecord")?.name = "Bungeecord"
include("ProxyBridge:commonPDE")
findProject(":ProxyBridge:commonPDE")?.name = "commonPDE"
include("Standalone:Fabric")
findProject(":Standalone:Fabric")?.name = "Fabric"