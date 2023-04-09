package com.github.hoshikurama.ticketmanager.commonse.old.platform

import com.github.hoshikurama.ticketmanager.commonse.database.AsyncDatabase
import com.github.hoshikurama.ticketmanager.commonse.old.TMPlugin

abstract class TabComplete(private val platform: PlatformFunctions) {
    fun getReturnedTabs(sender: Sender, args: List<String>): List<String> {
        if (!sender.has("ticketmanager.commandArg.autotab") && sender is OnlinePlayer) return listOf("")
        val perms = LazyPermissions(sender)

        return sender.locale.run {
            if (args.size <= 1) return@run perms.getPermissiveCommands()
                .filter { it.startsWith(args[0]) }

            when (args[0]) {
                commandWordAssign, commandWordSilentAssign -> when { // /ticket assign <ID> <Assignment...>
                    !perms.hasAssignVariation -> listOf("")
                    args.size == 2 -> listOf("<$parameterID>")
                    args.size == 3 -> {
                        val groups = TMPlugin.lpGroupNames.map { "::$it" }
                        (listOf("<$parameterAssignment...>") + platform.getOnlineSeenPlayerNames(sender) + groups + listOf(consoleName))
                            .filter { it.startsWith(args[2]) }
                    }
                    else -> listOf("")
                }

                commandWordClaim, commandWordSilentClaim, commandWordUnassign, commandWordSilentUnassign -> when { // /ticket claim/unassign <ID>
                    !perms.hasAssignVariation -> listOf("")
                    args.size == 2 -> listOf("<$parameterID>").filter { it.startsWith(args[1]) }
                    else -> listOf("")
                }

                commandWordClose, commandWordSilentClose -> when { // /ticket close <ID> [Comment...]
                    !perms.hasClose -> listOf("")
                    args.size == 2 -> listOf("<$parameterID>").filter { it.startsWith(args[1]) }
                    else -> (listOf("[$parameterComment...]") + platform.getOnlineSeenPlayerNames(sender))
                        .filter { it.startsWith(args[args.lastIndex]) }
                }

                commandWordCloseAll, commandWordSilentCloseAll -> when { // /ticket closeall <Lower ID> <Upper ID>
                    !perms.hasMassClose -> listOf("")
                    args.size == 2 -> listOf("<$parameterLowerID>").filter { it.startsWith(args[1]) }
                    args.size == 3 -> listOf("<$parameterUpperID>").filter { it.startsWith(args[2]) }
                    else -> listOf("")
                }

                commandWordComment, commandWordSilentComment -> when { // /ticket comment <ID> <Comment…>
                    !perms.hasComment -> listOf("")
                    args.size == 2 -> listOf("<$parameterID>").filter { it.startsWith(args[1]) }
                    else -> (listOf("<$parameterComment...>") + platform.getOnlineSeenPlayerNames(sender))
                        .filter { it.startsWith(args[args.lastIndex]) }
                }

                commandWordCreate -> when { // /ticket create <Message…>
                    !perms.hasCreate -> listOf("")
                    else -> (listOf("<$parameterComment...>") + platform.getOnlineSeenPlayerNames(sender))
                        .filter { it.startsWith(args[args.lastIndex]) }
                }

                commandWordHelp -> listOf("")

                commandWordHistory -> when { // /ticket history [User] [Page]
                    !perms.hasHistory -> listOf("")
                    args.size == 2 -> (listOf("[$parameterUser]", consoleName) + platform.getOnlineSeenPlayerNames(sender))
                        .filter { it.startsWith(args[1]) }
                    args.size == 3 -> listOf("[$parameterPage]").filter { it.startsWith(args[2]) }
                    else -> listOf("")
                }

                commandWordList, commandWordListAssigned, commandWordListUnassigned -> when { // /ticket ListVariation [Page]
                    !perms.hasListVariation -> listOf("")
                    args.size == 2 -> listOf("[$parameterPage]").filter { it.startsWith(args[1]) }
                    else -> listOf("")
                }

                commandWordReopen, commandWordSilentReopen -> when { // /ticket reopen <ID>
                    !perms.hasReopen -> listOf("")
                    args.size == 2 -> listOf("<$parameterID>").filter { it.startsWith(args[1]) }
                    else -> listOf("")
                }

                commandWordSetPriority, commandWordSilentSetPriority -> when { // /ticket setpriority <ID> <Level>
                    !perms.hasPriority -> listOf("")
                    args.size == 2 -> listOf("<$parameterID>").filter { it.startsWith(args[1]) }
                    args.size == 3 -> listOf("<$parameterLevel>", "1", "2", "3", "4", "5").filter {
                        it.startsWith(
                            args[2]
                        )
                    }
                    else -> listOf("")
                }

                commandWordTeleport -> when { // /ticket teleport <ID>
                    !perms.hasTeleport -> listOf("")
                    args.size == 2 -> listOf("<$parameterID>").filter { it.startsWith(args[1]) }
                    else -> listOf("")
                }

                commandWordVersion -> listOf("")

                commandWordView -> when { // /ticket view <ID>
                    !perms.hasView -> listOf("")
                    args.size == 2 -> listOf("<$parameterID>").filter { it.startsWith(args[1]) }
                    else -> listOf("")
                }

                commandWordDeepView -> when {
                    !perms.hasDeepView -> listOf("")
                    args.size == 2 -> listOf("<$parameterID>").filter { it.startsWith(args[1]) }
                    else -> listOf("")
                }

                commandWordReload -> listOf("")

                commandWordSearch -> { //ticket search keywords:separated,by,commas status:OPEN/CLOSED time:5w creator:creator priority:value assignedto:player world:world
                    if (!perms.hasSearch) return@run listOf("")

                    val curArgument = args[args.lastIndex]
                    val splitArgs = curArgument.split(":", limit = 2)

                    if (splitArgs.size < 2)
                        return@run run {
                            listOf(
                                "$searchAssigned:",
                                "$searchCreator:",
                                "$searchKeywords:",
                                "$searchPriority:",
                                "$searchStatus:",
                                "$searchWorld:",
                                "$searchClosedBy:",
                                "$searchLastClosedBy:",
                                "$searchTime:",
                            )
                        }
                            .filter { it.startsWith(curArgument) }

                    // String now has form "constraint:"
                    return@run when (splitArgs[0]) {
                        searchAssigned -> {
                            val groups = TMPlugin.lpGroupNames.map { "::$it" }
                            (platform.getOnlineSeenPlayerNames(sender) + groups)
                                .filter { it.startsWith(splitArgs[1]) }
                                .map { "${splitArgs[0]}:$it" }
                        }

                        searchCreator, searchLastClosedBy, searchClosedBy -> {
                            (platform.getOnlineSeenPlayerNames(sender) + listOf(consoleName))
                                .filter { it.startsWith(splitArgs[1]) }
                                .map { "${splitArgs[0]}:$it" }
                        }

                        searchPriority -> {
                            listOf("1", "2", "3", "4", "5")
                                .filter { it.startsWith(splitArgs[1]) }
                                .map { "${splitArgs[0]}:$it" }
                        }

                        searchStatus -> {
                            listOf(statusOpen, statusClosed)
                                .filter { it.startsWith(splitArgs[1]) }
                                .map { "${splitArgs[0]}:$it" }
                        }

                        searchWorld -> {
                            platform.getWorldNames()
                                .filter { it.startsWith(splitArgs[1]) }
                                .map { "${splitArgs[0]}:$it" }
                        }

                        searchTime -> {
                            listOf(
                                searchTimeSecond,
                                searchTimeMinute,
                                searchTimeHour,
                                searchTimeDay,
                                searchTimeWeek,
                                searchTimeYear
                            )
                                .filter { curArgument.last().digitToIntOrNull() != null }
                                .map { "${splitArgs[0]}:${splitArgs[1]}$it" }
                        }

                        searchKeywords -> listOf(curArgument)

                        else -> listOf("")
                    }
                }

                commandWordConvertDB -> when { // /ticket convertDatabase <Target Database>
                    !perms.hasConvertDB -> listOf("")
                    args.size == 2 ->
                        AsyncDatabase.Type.values()
                            .map(AsyncDatabase.Type::name)
                            .filter { it.startsWith(args[1]) }
                    else -> listOf("")
                }

                else -> listOf("")
            }
        }
    }

    private class LazyPermissions(private val sender: Sender) {
        val hasAssignVariation by lazy { sender.has("ticketmanager.command.assign") }
        val hasCreate by lazy { sender.has("ticketmanager.command.create") }
        val hasListVariation by lazy { sender.has("ticketmanager.command.list") }
        val hasReopen by lazy { sender.has("ticketmanager.command.reopen") }
        val hasSearch by lazy { sender.has("ticketmanager.command.search") }
        val hasPriority by lazy { sender.has("ticketmanager.command.setPriority") }
        val hasTeleport by lazy { sender.has("ticketmanager.command.teleport") }
        val hasMassClose by lazy { sender.has("ticketmanager.command.closeAll") }
        val hasConvertDB by lazy { sender.has("ticketmanager.command.convertDatabase") }
        private val hasHelp by lazy { sender.has("ticketmanager.command.help") }
        private val hasReload by lazy { sender.has("ticketmanager.command.reload") }
        private val hasSilent by lazy { sender.has("ticketmanager.commandArg.silence") }
        val hasClose by lazy {
            sender.has("ticketmanager.command.close.all")
                    || sender.has("ticketmanager.command.close.own")
        }
        val hasComment by lazy {
            sender.has("ticketmanager.command.comment.all")
                    || sender.has("ticketmanager.command.comment.own")
        }
        val hasView by lazy {
            sender.has("ticketmanager.command.view.all")
                    || sender.has("ticketmanager.command.view.own")
        }
        val hasDeepView by lazy {
            sender.has("ticketmanager.command.viewdeep.all")
                    || sender.has("ticketmanager.command.viewdeep.own")
        }
        val hasHistory by lazy {
            sender.has("ticketmanager.command.history.all")
                    || sender.has("ticketmanager.command.history.own")
        }


        fun getPermissiveCommands(): List<String> {
            val locale = sender.locale
            return mapOf(
                locale.commandWordAssign to hasAssignVariation,
                locale.commandWordSilentAssign to (hasAssignVariation && hasSilent),
                locale.commandWordClaim to hasAssignVariation,
                locale.commandWordSilentClaim to (hasAssignVariation && hasSilent),
                locale.commandWordClose to hasClose,
                locale.commandWordSilentClose to (hasClose && hasSilent),
                locale.commandWordCloseAll to hasMassClose,
                locale.commandWordSilentCloseAll to (hasMassClose && hasSilent),
                locale.commandWordComment to hasComment,
                locale.commandWordSilentComment to (hasComment && hasSilent),
                locale.commandWordConvertDB to hasConvertDB,
                locale.commandWordCreate to hasCreate,
                locale.commandWordHelp to hasHelp,
                locale.commandWordHistory to hasHistory,
                locale.commandWordList to hasListVariation,
                locale.commandWordListAssigned to hasListVariation,
                locale.commandWordListUnassigned to hasListVariation,
                locale.commandWordReload to hasReload,
                locale.commandWordReopen to hasReopen,
                locale.commandWordSilentReopen to (hasReopen && hasSilent),
                locale.commandWordSearch to hasSearch,
                locale.commandWordSetPriority to hasPriority,
                locale.commandWordSilentSetPriority to (hasPriority && hasSilent),
                locale.commandWordTeleport to hasTeleport,
                locale.commandWordUnassign to hasAssignVariation,
                locale.commandWordSilentUnassign to (hasAssignVariation && hasSilent),
                locale.commandWordVersion to true,
                locale.commandWordView to hasView,
                locale.commandWordDeepView to hasDeepView
            )
                .filter { it.value }
                .map { it.key }
        }
    }
}
// NOTE: THIS WAS ONE POTENTIAL IDEA, BUT I MIGHT TRY TO IMPLEMENT SUPPORT
/*
fun selectOptimized(f: () -> List<String>): List<String> = Optimizer.selectOptimizedAndUpdate(f, sender, args.size, args.last().size)
 */
/*
private object Optimizer {
        private val map: ConcurrentHashMap<UUID, Data> = ConcurrentHashMap()
        private val randomConsoleUUID = UUID.randomUUID()
        //TODO TEST
        data class Data(
            @Volatile var deletingJob: Job,
            @Volatile var lastArgsCount: Int,
            @Volatile var lastArgSize: Int,
            @Volatile var options: List<String>
        )
        // Note: It's okay as only one person can modify their own data at once, so race conditions won't apply for internal Data
        // todo: uhhh, what if second+ event fires async and finishes before or at the same time as longer first query with db read

        val generateAsyncDelete: (UUID) -> Job = { uuid ->
            TMCoroutine.asyncNoSupervisor {
                delay(30_000)
                if (isActive) map.remove(uuid) // Does nothing if cancelled
            }
        }
        fun selectOptimizedAndUpdate(regenerateOptions: () -> List<String>, sender: Sender, curArgsCount: Int, curArgSize: Int): List<String> {
            val targetUuid = when (sender) {
                is Console -> randomConsoleUUID
                is OnlinePlayer -> sender.uniqueID
            }

            // Is person a new typer or still cached?
            if (!map.containsKey(targetUuid)) {
                val options = regenerateOptions()
                map[targetUuid] = Data(generateAsyncDelete(targetUuid), curArgsCount, curArgSize, options)
                return options
            }

            //Person is still cached... Cancel and Initiate new Job
            val dataRef = map[targetUuid]!!
            dataRef.deletingJob.cancel()
            dataRef.deletingJob = generateAsyncDelete(targetUuid)

            return if (dataRef.lastArgsCount == curArgsCount && dataRef.lastArgSize + 1 == curArgSize) {
                // Person only typed one extra character for the same argument. Previous results valid
                dataRef.lastArgSize += 1
                dataRef.options
            } else {
                // Person either moved onto something else or hit backspace. Regenerate...
                val options = regenerateOptions()
                map[targetUuid] = Data(generateAsyncDelete(targetUuid), curArgsCount, curArgSize, options)
                options
            }
        }
    }
 */
