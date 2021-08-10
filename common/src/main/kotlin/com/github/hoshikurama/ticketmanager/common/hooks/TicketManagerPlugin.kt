package com.github.hoshikurama.ticketmanager.common.hooks


import com.github.hoshikurama.componentDSL.formattedContent
import com.github.hoshikurama.ticketmanager.common.ConfigState
import com.github.hoshikurama.ticketmanager.common.MutexControlled
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.kyori.adventure.extra.kotlin.text

abstract class TicketManagerPlugin<T>(
    val mainPlugin: T,
    val mainDispatcher: CoroutineDispatcher,
    val asyncDispatcher: CoroutineDispatcher,

) {
    val jobCount = MutexControlled(0)
    val pluginLocked = MutexControlled(true)
    val ticketCountMetrics = MutexControlled(0)

    lateinit var configState: ConfigState private set
    lateinit var commandPipeline: CommandPipeline<T>

    val asyncScope: CoroutineScope
        get() = CoroutineScope(asyncDispatcher)
    val mainScope: CoroutineScope
        get() = CoroutineScope(mainDispatcher)


    suspend fun disableAsync() {
        pluginLocked.set(true)
        configState.database.closeDatabase()
    }

    suspend fun enable() {
        asyncScope.launch { loadPlugin() }
    }

    suspend fun loadPlugin() {
        pluginLocked.set(true)

        configState = loadConfig()

        // Creates async update notification checker
        asyncScope.launch {
            val updateNeeded = configState.database.updateNeeded()

            if (updateNeeded) {
                configState.database.updateDatabase(
                    onBegin = {
                        commandPipeline.pushMassNotify("ticketmanager.notify.info") {
                            text { formattedContent(it.informationDBUpdate) }
                        }
                    },
                    onComplete = {
                        commandPipeline.pushMassNotify("ticketmanager.notify.info") {
                            text { formattedContent(it.informationDBUpdateComplete) }
                        }
                        pluginLocked.set(false)
                    },
                    offlinePlayerNameToUuidOrNull = commandPipeline::offlinePlayerNameToUUIDOrNull
                )
            } else pluginLocked.set(false)
        }

        performRegistration()
    }

    suspend fun performPeriodicTasks() {
        // Cleans up cooldown list (only does stuff if enabled)
        asyncScope.launch { configState.cooldowns.filterMapAsync() }

        asyncScope.launch {
            if (pluginLocked.get()) return@launch

            try {
                // Mass Unread Notify
                launch {
                    if (configState.allowUnreadTicketUpdates) {
                        commandPipeline.getOnlinePlayers()
                            .filter { it.has("ticketmanager.notify.unreadUpdates.scheduled") }
                            .collect {
                                val ticketIDs = configState.database.getIDsWithUpdatesFor(it.uniqueID).toList()
                                val tickets = ticketIDs.joinToString(", ")
                                if (ticketIDs.isEmpty()) return@collect

                                val template = if (ticketIDs.size > 1) it.locale.notifyUnreadUpdateMulti
                                else it.locale.notifyUnreadUpdateSingle

                                template.replace("%num%", tickets)
                                    .run(it::sendMessage)
                            }
                    }
                }

                // Open and Assigned Notify
                launch {
                    val openPriority = configState.database.getOpenIDPriorityPairs().map { it.first }.toList()
                    val openCount = openPriority.count()

                    // Gets associated tickets
                    val assignments =
                        if (openCount == 0) listOf()
                        else configState.database.getBasicTickets(openPriority).mapNotNull { it.assignedTo }.toList()

                    commandPipeline.getOnlinePlayers()
                        .filter { it.has("ticketmanager.notify.openTickets.scheduled") }
                        .collect { p ->
                            val groups = p.permissionGroups.map { "::$it" }
                            val assignedCount = assignments.count { it == p.name || it in groups }

                            p.locale.notifyOpenAssigned
                                .replace("%open%", "$openCount")
                                .replace("%assigned%", "$assignedCount")
                                .run(p::sendMessage)
                        }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                //postModifiedStacktrace(e)
            }
        }
    }

    fun isConfigStateInitialized() = ::configState.isInitialized


    abstract suspend fun loadConfig(): ConfigState
    abstract suspend fun performRegistration()

}