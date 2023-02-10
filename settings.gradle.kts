rootProject.name = "TicketManager"

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
include("ProxyBridge:Waterfall")
findProject(":ProxyBridge:Waterfall")?.name = "Waterfall"
include("ProxyBridge:commonPDE")
findProject(":ProxyBridge:commonPDE")?.name = "commonPDE"

/*
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net") { name = "Fabric" }
    }
}

pluginManagement {
	repositories {
		gradlePluginPortal()
                // this below is what appears to be missing in your build
		maven {
			name = 'Fabric'
			url = 'https://maven.fabricmc.net/'
		}
	}
}
 */