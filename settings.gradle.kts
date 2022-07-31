rootProject.name = "TicketManager"

include("common")
include("Standalone")
include("Standalone:Velocity")
findProject(":Standalone:Velocity")?.name = "Velocity"
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
include("Standalone:Sponge")
findProject(":Standalone:Sponge")?.name = "Sponge"
