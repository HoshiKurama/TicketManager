package com.github.hoshikurama.ticketmanager.commonse

import com.github.hoshikurama.ticketmanager.commonse.misc.TypeSafeStream.Companion.asTypeSafeStream
import com.github.hoshikurama.ticketmanager.commonse.misc.supportedLocales
import org.yaml.snakeyaml.Yaml
import java.nio.file.Paths
import kotlin.io.path.inputStream

class TMLocale(
// Core locale file fields
    // Miscellaneous
    val consoleName: String,
    val miscNobody: String,
    val wikiLink: String,

    // Command Types
    val commandBase: String,
    val commandWordAssign: String,
    val commandWordSilentAssign: String,
    val commandWordClaim: String,
    val commandWordSilentClaim: String,
    val commandWordClose: String,
    val commandWordSilentClose: String,
    val commandWordCloseAll: String,
    val commandWordSilentCloseAll: String,
    val commandWordComment: String,
    val commandWordSilentComment: String,
    val commandWordCreate: String,
    val commandWordHelp: String,
    val commandWordHistory: String,
    val commandWordList: String,
    val commandWordListAssigned: String,
    val commandWordListUnassigned: String,
    val commandWordReopen: String,
    val commandWordSilentReopen: String,
    val commandWordSearch: String,
    val commandWordSetPriority: String,
    val commandWordSilentSetPriority: String,
    val commandWordTeleport: String,
    val commandWordVersion: String,
    val commandWordUnassign: String,
    val commandWordSilentUnassign: String,
    val commandWordView: String,
    val commandWordDeepView: String,
    val commandWordReload: String,
    val commandWordConvertDB: String,

    // Search words:
    val searchAssigned: String,
    val searchCreator: String,
    val searchKeywords: String,
    val searchPriority: String,
    val searchStatus: String,
    val searchTime: String,
    val searchWorld: String,
    val searchPage: String,
    val searchClosedBy: String,
    val searchLastClosedBy: String,
    val searchTimeSecond: String,
    val searchTimeMinute: String,
    val searchTimeHour: String,
    val searchTimeDay: String,
    val searchTimeWeek: String,
    val searchTimeYear: String,

    // Required or Optional
    val parameterID: String,
    val parameterAssignment: String,
    val parameterLowerID: String,
    val parameterUpperID: String,
    val parameterComment: String,
    val parameterPage: String,
    val parameterLevel: String,
    val parameterUser: String,
    val parameterTargetDB: String,
    val parameterConstraints: String,

    // Console Logging Messages
    val consoleErrorBadDiscord: String,
    val consoleInitializationComplete: String,
    val consoleErrorBadDatabase: String,
    val consoleWarningInvalidConfigNode: String,
    val consoleErrorScheduledNotifications: String,
    val consoleErrorCommandExecution: String,
    val consoleErrorDBConversion: String,

// Visual Player-Modifiable Values
    // Priority
    val priorityLowest: String,
    val priorityLow: String,
    val priorityNormal: String,
    val priorityHigh: String,
    val priorityHighest: String,
    val priorityColourLowestHex: String,
    val priorityColourLowHex: String,
    val priorityColourNormalHex: String,
    val priorityColourHighHex: String,
    val priorityColourHighestHex: String,

    // Status
    val statusOpen: String,
    val statusClosed: String,
    val statusColourOpenHex: String,
    val statusColourClosedHex: String,

    // Time
    val timeSeconds: String,
    val timeMinutes: String,
    val timeHours: String,
    val timeDays: String,
    val timeWeeks: String,
    val timeYears: String,

    // Click Events
    val clickTeleport: String,
    val clickViewTicket: String,
    val clickNextPage: String,
    val clickBackPage: String,
    val clickWiki: String,

    // Pages
    val pageActiveNext: String,
    val pageInactiveNext: String,
    val pageActiveBack: String,
    val pageInactiveBack: String,
    val pageFormat: String,

    // Warnings
    val warningsLocked: String,
    val warningsNoPermission: String,
    val warningsInvalidID: String,
    val warningsInvalidNumber: String,
    val warningsNoConfig: String,
    val warningsInvalidCommand: String,
    val warningsPriorityOutOfBounds: String,
    val warningsUnderCooldown: String,
    val warningsTicketAlreadyClosed: String,
    val warningsTicketAlreadyOpen: String,
    val warningsInvalidDBType: String,
    val warningsConvertToSameDBType: String,
    val warningsUnexpectedError: String,
    val warningsLongTaskDuringReload: String,
    val warningsInvalidConfig: String,
    val warningsInternalError: String,

    // Discord Notifications
    val discordOnAssign: String,
    val discordOnClose: String,
    val discordOnCloseAll: String,
    val discordOnComment: String,
    val discordOnCreate: String,
    val discordOnReopen: String,
    val discordOnPriorityChange: String,

    // View and Deep View
    val viewHeader: String,
    val viewSep1: String,
    val viewCreator: String,
    val viewAssignedTo: String,
    val viewPriority: String,
    val viewStatus: String,
    val viewLocation: String,
    val viewSep2: String,
    val viewComment: String,
    val viewDeepComment: String,
    val viewDeepSetPriority: String,
    val viewDeepAssigned: String,
    val viewDeepReopen: String,
    val viewDeepClose: String,
    val viewDeepMassClose: String,

    // List
    val listHeader: String,
    val listAssignedHeader: String,
    val listUnassignedHeader: String,
    val listEntry: String,
    val listFormattingSize: Int,
    val listMaxLineSize: Int,

    // Search Format:
    val searchHeader: String,
    val searchEntry: String,
    val searchQuerying: String,
    val searchFormattingSize: Int,
    val searchMaxLineSize: Int,

    // History Format:
    val historyHeader: String,
    val historyEntry: String,
    val historyFormattingSize: Int,
    val historyMaxLineSize: Int,

    // Help Format:
    val helpHeader: String,
    val helpLine1: String,
    val helpLine2: String,
    val helpLine3: String,
    val helpSep: String,
    val helpHasSilence: String,
    val helpLackSilence: String,
    val helpRequiredParam: String,
    val helpOptionalParam: String,
    val helpEntry: String,

    // Modified Stacktrace
    val stacktraceLine1: String,
    val stacktraceLine2: String,
    val stacktraceLine3: String,
    val stacktraceLine4: String,
    val stacktraceEntry: String,

    // Information:
    val informationReloadInitiated: String,
    val informationReloadSuccess: String,
    val informationReloadTasksDone: String,
    val informationDBConvertInit: String,
    val informationDBConvertSuccess: String,
    val informationReloadFailure: String,

    // Ticket Notifications
    val notifyUnreadUpdateSingle: String,
    val notifyUnreadUpdateMulti: String,
    val notifyOpenAssigned: String,
    val notifyTicketAssignEvent: String,
    val notifyTicketAssignSuccess: String,
    val notifyTicketCreationSuccess: String,
    val notifyTicketCreationEvent: String,
    val notifyTicketCommentEvent: String,
    val notifyTicketCommentSuccess: String,
    val notifyTicketModificationEvent: String,
    val notifyTicketMassCloseEvent: String,
    val notifyTicketMassCloseSuccess: String,
    val notifyTicketCloseSuccess: String,
    val notifyTicketCloseWCommentSuccess: String,
    val notifyTicketCloseEvent: String,
    val notifyTicketCloseWCommentEvent: String,
    val notifyTicketReopenSuccess: String,
    val notifyTicketReopenEvent: String,
    val notifyTicketSetPrioritySuccess: String,
    val notifyTicketSetPriorityEvent: String,
    val notifyPluginUpdate: String,
    val notifyProxyUpdate: String,
) {
    companion object {
        private fun loadYMLFrom(location: String): Map<String, String> =
            this::class.java.classLoader
            .getResourceAsStream(location)
            .let { Yaml().load(it) }

        private fun inlinePlaceholders(str: String?, tmHeader: String, cc: String) = str
            ?.replace("%TMHeader%", tmHeader)
            ?.replace("%nl%", "\n")
            ?.replace("%CC%", miniMessageToHex(cc))

        private fun miniMessageToHex(str: String): String {
            val isHexCode = "^#([a-fA-F\\d]{6})\$".toRegex()::matches

            if (isHexCode(str)) return str
            str.toIntOrNull()?.let(Integer::toHexString)?.let { "#$it" }?.let { if (isHexCode(it)) return it }

            return when (str) {
                "dark_red","&4" -> "#AA0000"
                "red","&c" -> "#FF5555"
                "gold","&6" -> "#FFAA00"
                "yellow","&e" -> "#FFFF55"
                "dark_green","&2" -> "#00AA00"
                "green","&a" -> "#55FF55"
                "aqua","&b" -> "#55FFFF"
                "dark_aqua","&3" -> "#00AAAA"
                "dark_blue","&1" -> "#0000AA"
                "blue","&9" -> "#5555FF"
                "light_purple","&d" -> "#FF55FF"
                "dark_purple","&5" -> "#AA00AA"
                "white","&f" -> "#FFFFFF"
                "gray","&7" -> "#AAAAAA"
                "dark_gray","&8" -> "#555555"
                "black","&0" -> "#000000"
                else -> "#FFFFFF"
            }
        }

        fun buildLocaleFromInternal(localeID: String, colour: String): TMLocale {
            val core = loadYMLFrom("locales/core/$localeID.yml")
            val visuals = loadYMLFrom("locales/visual/$localeID.yml")

            // Prepares Headers for inlining (Still has placeholders)
            val uniformHeader = visuals["Uniform_Header"]!!
            val warningHeader = visuals["Warning_Header"]!!

            fun readAndPrime(key: String) = inlinePlaceholders(visuals[key]!!, uniformHeader, colour)
            fun inlineWarningHeader(key: String) = inlinePlaceholders(visuals[key]!!.replace("%Header%", warningHeader), uniformHeader, colour)

            return TMLocale(
                // Core Aspects
                consoleName = core["Console_Name"]!!,
                miscNobody = core["Nobody"]!!,
                wikiLink = core["Localed_Wiki_Link"]!!,
                commandBase = core["Command_BaseCommand"]!!,
                commandWordAssign = core["Command_Assign"]!!,
                commandWordSilentAssign = core["Command_SilentAssign"]!!,
                commandWordClaim = core["Command_Claim"]!!,
                commandWordSilentClaim = core["Command_SilentClaim"]!!,
                commandWordClose = core["Command_Close"]!!,
                commandWordSilentClose = core["Command_SilentClose"]!!,
                commandWordCloseAll = core["Command_CloseAll"]!!,
                commandWordSilentCloseAll = core["Command_SilentCloseAll"]!!,
                commandWordComment = core["Command_Comment"]!!,
                commandWordSilentComment = core["Command_SilentComment"]!!,
                commandWordCreate = core["Command_Create"]!!,
                commandWordHelp = core["Command_Help"]!!,
                commandWordHistory = core["Command_History"]!!,
                commandWordList = core["Command_List"]!!,
                commandWordListAssigned = core["Command_ListAssigned"]!!,
                commandWordListUnassigned = core["Command_ListUnassigned"]!!,
                commandWordReopen = core["Command_Reopen"]!!,
                commandWordSilentReopen = core["Command_SilentReopen"]!!,
                commandWordSearch = core["Command_Search"]!!,
                commandWordSetPriority = core["Command_SetPriority"]!!,
                commandWordSilentSetPriority = core["Command_SilentSetPriority"]!!,
                commandWordTeleport = core["Command_Teleport"]!!,
                commandWordVersion = core["Command_Version"]!!,
                commandWordUnassign = core["Command_Unassign"]!!,
                commandWordSilentUnassign = core["Command_SilentUnassign"]!!,
                commandWordView = core["Command_View"]!!,
                commandWordDeepView = core["Command_DeepView"]!!,
                commandWordReload = core["Command_Reload"]!!,
                commandWordConvertDB = core["Command_ConvertDB"]!!,
                searchAssigned = core["Search_AssignedTo"]!!,
                searchCreator = core["Search_Creator"]!!,
                searchKeywords = core["Search_Keywords"]!!,
                searchPriority = core["Search_Priority"]!!,
                searchStatus = core["Search_Status"]!!,
                searchTime = core["Search_Time"]!!,
                searchWorld = core["Search_World"]!!,
                searchPage = core["Search_Page"]!!,
                searchClosedBy = core["Search_ClosedBy"]!!,
                searchLastClosedBy = core["Search_LastClosedBy"]!!,
                searchTimeSecond = core["Search_Time_Second"]!!,
                searchTimeMinute = core["Search_Time_Minute"]!!,
                searchTimeHour = core["Search_Time_Hour"]!!,
                searchTimeDay = core["Search_Time_Day"]!!,
                searchTimeWeek = core["Search_Time_Week"]!!,
                searchTimeYear = core["Search_Time_Year"]!!,
                parameterID = core["Parameters_ID"]!!,
                parameterAssignment = core["Parameters_Assignment"]!!,
                parameterLowerID = core["Parameters_LowerID"]!!,
                parameterUpperID = core["Parameters_UpperID"]!!,
                parameterComment = core["Parameters_Comment"]!!,
                parameterPage = core["Parameters_Page"]!!,
                parameterLevel = core["Parameters_Level"]!!,
                parameterUser = core["Parameters_User"]!!,
                parameterTargetDB = core["Parameters_Target_Database"]!!,
                parameterConstraints = core["Parameters_Constraints"]!!,
                consoleErrorBadDiscord = core["ConsoleError_DiscordInitialize"]!!,
                consoleInitializationComplete = core["Console_InitializationComplete"]!!,
                consoleErrorBadDatabase = core["ConsoleError_DatabaseInitialize"]!!,
                consoleWarningInvalidConfigNode = core["ConsoleWarning_InvalidConfigNode"]!!,
                consoleErrorScheduledNotifications = core["ConsoleError_ScheduledNotifications"]!!,
                consoleErrorCommandExecution = core["ConsoleError_CommandExecution"]!!,
                consoleErrorDBConversion = core["ConsoleError_DatabaseConversion"]!!,
                // Visual Aspects
                priorityLowest = visuals["Priority_Lowest"]!!,
                priorityLow = visuals["Priority_Low"]!!,
                priorityNormal = visuals["Priority_Normal"]!!,
                priorityHigh = visuals["Priority_High"]!!,
                priorityHighest = visuals["Priority_Highest"]!!,
                priorityColourLowestHex = miniMessageToHex(visuals["PriorityColour_Lowest"]!!),
                priorityColourLowHex = miniMessageToHex(visuals["PriorityColour_Low"]!!),
                priorityColourNormalHex = miniMessageToHex(visuals["PriorityColour_Normal"]!!),
                priorityColourHighHex = miniMessageToHex(visuals["PriorityColour_High"]!!),
                priorityColourHighestHex = miniMessageToHex(visuals["PriorityColour_Highest"]!!),
                statusOpen = visuals["Status_Open"]!!,
                statusClosed = visuals["Status_Closed"]!!,
                statusColourOpenHex = miniMessageToHex(visuals["StatusColour_Open"]!!),
                statusColourClosedHex = miniMessageToHex(visuals["StatusColour_Closed"]!!),
                timeSeconds = visuals["Time_Seconds"]!!,
                timeMinutes = visuals["Time_Minutes"]!!,
                timeHours = visuals["Time_Hours"]!!,
                timeDays = visuals["Time_Days"]!!,
                timeWeeks = visuals["Time_Weeks"]!!,
                timeYears = visuals["Time_Years"]!!,
                clickTeleport = visuals["Click_Teleport"]!!,
                clickViewTicket = visuals["Click_ViewTicket"]!!,
                clickNextPage = visuals["Click_NextPage"]!!,
                clickBackPage = visuals["Click_BackPage"]!!,
                clickWiki = visuals["Click_GitHub_Wiki"]!!,
                pageActiveNext = readAndPrime("Page_ActiveNext")!!,
                pageInactiveNext = readAndPrime("Page_InactiveNext")!!,
                pageActiveBack = readAndPrime("Page_ActiveBack")!!,
                pageInactiveBack = readAndPrime("Page_InactiveBack")!!,
                pageFormat = readAndPrime("Page_Format")!!,
                warningsLocked = inlineWarningHeader("Warning_Locked")!!,
                warningsNoPermission = inlineWarningHeader("Warning_NoPermission")!!,
                warningsInvalidID = inlineWarningHeader("Warning_InvalidID")!!,
                warningsInvalidNumber = inlineWarningHeader("Warning_NAN")!!,
                warningsNoConfig = inlineWarningHeader("Warning_NoConfig")!!,
                warningsInvalidCommand = inlineWarningHeader("Warning_InvalidCommand")!!,
                warningsPriorityOutOfBounds = inlineWarningHeader("Warning_PriorityOutOfBounds")!!,
                warningsUnderCooldown = inlineWarningHeader("Warning_Under_Cooldown")!!,
                warningsTicketAlreadyClosed = inlineWarningHeader("Warning_TicketAlreadyClosed")!!,
                warningsTicketAlreadyOpen = inlineWarningHeader("Warning_TicketAlreadyOpen")!!,
                warningsInvalidDBType = inlineWarningHeader("Warning_InvalidDBType")!!,
                warningsConvertToSameDBType = inlineWarningHeader("Warning_ConvertToSameDBType")!!,
                warningsUnexpectedError = inlineWarningHeader("Warning_UnexpectedError")!!,
                warningsLongTaskDuringReload = inlineWarningHeader("Warning_LongTaskDuringReload")!!,
                warningsInvalidConfig = inlineWarningHeader("Warning_InvalidConfig")!!,
                warningsInternalError = inlineWarningHeader("Warning_InternalError")!!,
                discordOnAssign = visuals["Discord_OnAssign"]!!,
                discordOnClose = visuals["Discord_OnClose"]!!,
                discordOnCloseAll = visuals["Discord_OnCloseAll"]!!,
                discordOnComment = visuals["Discord_OnComment"]!!,
                discordOnCreate = visuals["Discord_OnCreate"]!!,
                discordOnReopen = visuals["Discord_OnReopen"]!!,
                discordOnPriorityChange = visuals["Discord_OnPriorityChange"]!!,
                viewHeader = readAndPrime("ViewFormat_Header")!!,
                viewSep1 = readAndPrime("ViewFormat_Sep1")!!,
                viewCreator = readAndPrime("ViewFormat_InfoCreator")!!,
                viewAssignedTo = readAndPrime("ViewFormat_InfoAssignedTo")!!,
                viewPriority = readAndPrime("ViewFormat_InfoPriority")!!,
                viewStatus = readAndPrime("ViewFormat_InfoStatus")!!,
                viewLocation = readAndPrime("ViewFormat_InfoLocation")!!,
                viewSep2 = readAndPrime("ViewFormat_Sep2")!!,
                viewComment = readAndPrime("ViewFormat_Comment")!!,
                viewDeepComment = readAndPrime("ViewFormat_DeepComment")!!,
                viewDeepSetPriority = readAndPrime("ViewFormat_DeepSetPriority")!!,
                viewDeepAssigned = readAndPrime("ViewFormat_DeepAssigned")!!,
                viewDeepReopen = readAndPrime("ViewFormat_DeepReopen")!!,
                viewDeepClose = readAndPrime("ViewFormat_DeepClose")!!,
                viewDeepMassClose = readAndPrime("ViewFormat_DeepMassClose")!!,
                listHeader = readAndPrime("ListFormat_Header")!!,
                listAssignedHeader = readAndPrime("ListFormat_AssignedHeader")!!,
                listUnassignedHeader = readAndPrime("ListFormat_UnassignedHeader")!!,
                listEntry = readAndPrime("ListFormat_Entry")!!,
                listFormattingSize = visuals["ListFormat_FormattingSize"]!!.toInt(),
                listMaxLineSize = visuals["ListFormat_MaxLineSize"]!!.toInt(),
                searchHeader = readAndPrime("SearchFormat_Header")!!,
                searchEntry = readAndPrime("SearchFormat_Entry")!!,
                searchQuerying = readAndPrime("SearchFormat_Querying")!!,
                searchFormattingSize = visuals["SearchFormat_FormattingSize"]!!.toInt(),
                searchMaxLineSize = visuals["SearchFormat_MaxLineSize"]!!.toInt(),
                historyHeader = readAndPrime("History_Header")!!,
                historyEntry = readAndPrime("History_Entry")!!,
                historyFormattingSize = visuals["History_FormattingSize"]!!.toInt(),
                historyMaxLineSize = visuals["History_MaxLineSize"]!!.toInt(),
                helpHeader = readAndPrime("Help_Header")!!,
                helpLine1 = readAndPrime("Help_Line1")!!,
                helpLine2 = readAndPrime("Help_Line2")!!,
                helpLine3 = readAndPrime("Help_Line3")!!,
                helpSep = readAndPrime("Help_Sep")!!,
                helpHasSilence = readAndPrime("Help_HasSilence")!!,
                helpLackSilence = readAndPrime("Help_LackSilence")!!,
                helpRequiredParam = readAndPrime("Help_RequiredParam")!!,
                helpOptionalParam = readAndPrime("Help_OptionalParam")!!,
                helpEntry = readAndPrime("Help_Entry")!!,
                stacktraceLine1 = readAndPrime("Stacktrace_Line1")!!,
                stacktraceLine2 = readAndPrime("Stacktrace_Line2")!!,
                stacktraceLine3 = readAndPrime("Stacktrace_Line3")!!,
                stacktraceLine4 = readAndPrime("Stacktrace_Line4")!!,
                stacktraceEntry = readAndPrime("Stacktrace_Entry")!!,
                informationReloadInitiated = readAndPrime("Info_ReloadInitiated")!!,
                informationReloadSuccess = readAndPrime("Info_ReloadSuccess")!!,
                informationReloadTasksDone = readAndPrime("Info_Reload_TasksDone")!!,
                informationDBConvertInit = readAndPrime("Info_DBConversionInit")!!,
                informationDBConvertSuccess = readAndPrime("Info_DBConversionSuccess")!!,
                informationReloadFailure = readAndPrime("Info_ReloadFailure")!!,
                notifyUnreadUpdateSingle = readAndPrime("Notify_UnreadUpdate_Single")!!,
                notifyUnreadUpdateMulti = readAndPrime("Notify_UnreadUpdate_Multi")!!,
                notifyOpenAssigned = readAndPrime("Notify_OpenAssigned")!!,
                notifyTicketAssignEvent = readAndPrime("Notify_Event_TicketAssign")!!,
                notifyTicketAssignSuccess = readAndPrime("Notify_TicketAssignSuccess")!!,
                notifyTicketCreationSuccess = readAndPrime("Notify_TicketCreationSuccessful")!!,
                notifyTicketCreationEvent = readAndPrime("Notify_Event_TicketCreation")!!,
                notifyTicketCommentEvent = readAndPrime("Notify_Event_TicketComment")!!,
                notifyTicketCommentSuccess = readAndPrime("Notify_TicketCommentSuccessful")!!,
                notifyTicketModificationEvent = readAndPrime("Notify_Event_TicketModification")!!,
                notifyTicketMassCloseEvent = readAndPrime("Notify_Event_MassClose")!!,
                notifyTicketMassCloseSuccess = readAndPrime("Notify_MassCloseSuccess")!!,
                notifyTicketCloseSuccess = readAndPrime("Notify_TicketCloseSuccess")!!,
                notifyTicketCloseWCommentSuccess = readAndPrime("Notify_TicketCloseWithCommentSuccess")!!,
                notifyTicketCloseEvent = readAndPrime("Notify_Event_TicketClose")!!,
                notifyTicketCloseWCommentEvent = readAndPrime("Notify_Event_TicketCloseWithComment")!!,
                notifyTicketReopenSuccess = readAndPrime("Notify_TicketReopenSuccess")!!,
                notifyTicketReopenEvent = readAndPrime("Notify_Event_TicketReopen")!!,
                notifyTicketSetPrioritySuccess = readAndPrime("Notify_TicketSetPrioritySuccess")!!,
                notifyTicketSetPriorityEvent = readAndPrime("Notify_Event_SetPriority")!!,
                notifyPluginUpdate = readAndPrime("Notify_Event_PluginUpdate")!!,
                notifyProxyUpdate = readAndPrime("Notify_Event_ProxyUpdate")!!,
            )
        }

        fun buildLocaleFromExternal(localeID: String, rootFileLocation: String, colour: String, internalVersion: TMLocale): TMLocale {
            val visuals: Map<String, String> = try {
                Paths.get("$rootFileLocation/locales/$localeID.yml")
                    .inputStream()
                    .let { Yaml().load(it) }
            } catch (e: Exception) { mapOf() }

            // Prepares Headers for inlining (Still has placeholders)
            val uniformHeader = visuals["Uniform_Header"] ?: "<%CC%>[TicketManager] "
            val warningHeader = visuals["Warning_Header"] ?: "<red>[TicketManager] "

            fun readAndPrime(key: String) = inlinePlaceholders(visuals[key], uniformHeader, colour)
            fun inlineWarningHeader(key: String) = inlinePlaceholders(visuals[key]?.replace("%Header%", warningHeader), uniformHeader, colour)

            return TMLocale(
                // Core Aspects
                consoleName = internalVersion.consoleName,
                miscNobody = internalVersion.miscNobody,
                wikiLink = internalVersion.wikiLink,
                commandBase = internalVersion.commandBase,
                commandWordAssign = internalVersion.commandWordAssign,
                commandWordSilentAssign = internalVersion.commandWordSilentAssign,
                commandWordClaim = internalVersion.commandWordClaim,
                commandWordSilentClaim = internalVersion.commandWordSilentClaim,
                commandWordClose = internalVersion.commandWordClose,
                commandWordSilentClose = internalVersion.commandWordSilentClose,
                commandWordCloseAll = internalVersion.commandWordCloseAll,
                commandWordSilentCloseAll = internalVersion.commandWordSilentCloseAll,
                commandWordComment = internalVersion.commandWordComment,
                commandWordSilentComment = internalVersion.commandWordSilentComment,
                commandWordCreate = internalVersion.commandWordCreate,
                commandWordHelp = internalVersion.commandWordHelp,
                commandWordHistory = internalVersion.commandWordHistory,
                commandWordList = internalVersion.commandWordList,
                commandWordListAssigned = internalVersion.commandWordListAssigned,
                commandWordListUnassigned = internalVersion.commandWordListUnassigned,
                commandWordReopen = internalVersion.commandWordReopen,
                commandWordSilentReopen = internalVersion.commandWordSilentReopen,
                commandWordSearch = internalVersion.commandWordSearch,
                commandWordSetPriority = internalVersion.commandWordSetPriority,
                commandWordSilentSetPriority = internalVersion.commandWordSilentSetPriority,
                commandWordTeleport = internalVersion.commandWordTeleport,
                commandWordVersion = internalVersion.commandWordVersion,
                commandWordUnassign = internalVersion.commandWordUnassign,
                commandWordSilentUnassign = internalVersion.commandWordSilentUnassign,
                commandWordView = internalVersion.commandWordView,
                commandWordDeepView = internalVersion.commandWordDeepView,
                commandWordReload = internalVersion.commandWordReload,
                commandWordConvertDB = internalVersion.commandWordConvertDB,
                searchAssigned = internalVersion.searchAssigned,
                searchCreator = internalVersion.searchCreator,
                searchKeywords = internalVersion.searchKeywords,
                searchPriority = internalVersion.searchPriority,
                searchStatus = internalVersion.searchStatus,
                searchTime = internalVersion.searchTime,
                searchWorld = internalVersion.searchWorld,
                searchPage = internalVersion.searchPage,
                searchClosedBy = internalVersion.searchClosedBy,
                searchLastClosedBy = internalVersion.searchLastClosedBy,
                searchTimeSecond = internalVersion.searchTimeSecond,
                searchTimeMinute = internalVersion.searchTimeMinute,
                searchTimeHour = internalVersion.searchTimeHour,
                searchTimeDay = internalVersion.searchTimeDay,
                searchTimeWeek = internalVersion.searchTimeWeek,
                searchTimeYear = internalVersion.searchTimeYear,
                parameterID = internalVersion.parameterID,
                parameterAssignment = internalVersion.parameterAssignment,
                parameterLowerID = internalVersion.parameterLowerID,
                parameterUpperID = internalVersion.parameterUpperID,
                parameterComment = internalVersion.parameterComment,
                parameterPage = internalVersion.parameterPage,
                parameterLevel = internalVersion.parameterLevel,
                parameterUser = internalVersion.parameterUser,
                parameterTargetDB = internalVersion.parameterTargetDB,
                parameterConstraints = internalVersion.parameterConstraints,
                consoleErrorBadDiscord = internalVersion.consoleErrorBadDiscord,
                consoleInitializationComplete = internalVersion.consoleInitializationComplete,
                consoleErrorBadDatabase = internalVersion.consoleErrorBadDatabase,
                consoleWarningInvalidConfigNode = internalVersion.consoleWarningInvalidConfigNode,
                consoleErrorScheduledNotifications = internalVersion.consoleErrorScheduledNotifications,
                consoleErrorCommandExecution = internalVersion.consoleErrorCommandExecution,
                consoleErrorDBConversion = internalVersion.consoleErrorDBConversion,
                // Visual Aspects
                priorityLowest = visuals["Priority_Lowest"] ?: internalVersion.priorityLowest,
                priorityLow = visuals["Priority_Low"] ?: internalVersion.priorityLow,
                priorityNormal = visuals["Priority_Normal"] ?: internalVersion.priorityNormal,
                priorityHigh = visuals["Priority_High"] ?: internalVersion.priorityHigh,
                priorityHighest = visuals["Priority_Highest"] ?: internalVersion.priorityHighest,
                priorityColourLowestHex = miniMessageToHex(visuals["PriorityColour_Lowest"] ?: internalVersion.priorityColourLowestHex),
                priorityColourLowHex = miniMessageToHex(visuals["PriorityColour_Low"] ?: internalVersion.priorityColourLowHex),
                priorityColourNormalHex = miniMessageToHex(visuals["PriorityColour_Normal"] ?: internalVersion.priorityColourNormalHex),
                priorityColourHighHex = miniMessageToHex(visuals["PriorityColour_High"] ?: internalVersion.priorityColourHighHex),
                priorityColourHighestHex = miniMessageToHex(visuals["PriorityColour_Highest"] ?: internalVersion.priorityColourHighestHex),
                statusOpen = visuals["Status_Open"] ?: internalVersion.statusOpen,
                statusClosed = visuals["Status_Closed"] ?: internalVersion.statusClosed,
                statusColourOpenHex = miniMessageToHex(visuals["StatusColour_Open"] ?: internalVersion.statusColourOpenHex),
                statusColourClosedHex = miniMessageToHex(visuals["StatusColour_Closed"] ?: internalVersion.statusColourClosedHex),
                timeSeconds = visuals["Time_Seconds"] ?: internalVersion.timeSeconds,
                timeMinutes = visuals["Time_Minutes"] ?: internalVersion.timeMinutes,
                timeHours = visuals["Time_Hours"] ?: internalVersion.timeHours,
                timeDays = visuals["Time_Days"] ?: internalVersion.timeDays,
                timeWeeks = visuals["Time_Weeks"] ?: internalVersion.timeWeeks,
                timeYears = visuals["Time_Years"] ?: internalVersion.timeYears,
                clickTeleport = visuals["Click_Teleport"] ?: internalVersion.clickTeleport,
                clickViewTicket = visuals["Click_ViewTicket"] ?: internalVersion.clickViewTicket,
                clickNextPage = visuals["Click_NextPage"] ?: internalVersion.clickNextPage,
                clickBackPage = visuals["Click_BackPage"] ?: internalVersion.clickBackPage,
                clickWiki = visuals["Click_GitHub_Wiki"] ?: internalVersion.clickWiki,
                pageActiveNext = readAndPrime("Page_ActiveNext") ?: internalVersion.pageActiveNext,
                pageInactiveNext = readAndPrime("Page_InactiveNext") ?: internalVersion.pageInactiveNext,
                pageActiveBack = readAndPrime("Page_ActiveBack") ?: internalVersion.pageActiveBack,
                pageInactiveBack = readAndPrime("Page_InactiveBack") ?: internalVersion.pageInactiveBack,
                pageFormat = readAndPrime("Page_Format") ?: internalVersion.pageFormat,
                warningsLocked = inlineWarningHeader("Warning_Locked") ?: internalVersion.warningsLocked,
                warningsNoPermission = inlineWarningHeader("Warning_NoPermission") ?: internalVersion.warningsNoPermission,
                warningsInvalidID = inlineWarningHeader("Warning_InvalidID") ?: internalVersion.warningsInvalidID,
                warningsInvalidNumber = inlineWarningHeader("Warning_NAN") ?: internalVersion.warningsInvalidNumber,
                warningsNoConfig = inlineWarningHeader("Warning_NoConfig") ?: internalVersion.warningsNoConfig,
                warningsInvalidCommand = inlineWarningHeader("Warning_InvalidCommand") ?: internalVersion.warningsInvalidCommand,
                warningsPriorityOutOfBounds = inlineWarningHeader("Warning_PriorityOutOfBounds") ?: internalVersion.warningsPriorityOutOfBounds,
                warningsUnderCooldown = inlineWarningHeader("Warning_Under_Cooldown") ?: internalVersion.warningsUnderCooldown,
                warningsTicketAlreadyClosed = inlineWarningHeader("Warning_TicketAlreadyClosed") ?: internalVersion.warningsTicketAlreadyClosed,
                warningsTicketAlreadyOpen = inlineWarningHeader("Warning_TicketAlreadyOpen") ?: internalVersion.warningsTicketAlreadyOpen,
                warningsInvalidDBType = inlineWarningHeader("Warning_InvalidDBType") ?: internalVersion.warningsInvalidDBType,
                warningsConvertToSameDBType = inlineWarningHeader("Warning_ConvertToSameDBType") ?: internalVersion.warningsConvertToSameDBType,
                warningsUnexpectedError = inlineWarningHeader("Warning_UnexpectedError") ?: internalVersion.warningsUnexpectedError,
                warningsLongTaskDuringReload = inlineWarningHeader("Warning_LongTaskDuringReload") ?: internalVersion.warningsLongTaskDuringReload,
                warningsInvalidConfig = inlineWarningHeader("Warning_InvalidConfig") ?: internalVersion.warningsInvalidConfig,
                warningsInternalError = inlineWarningHeader("Warning_InternalError") ?: internalVersion.warningsInternalError,
                discordOnAssign = visuals["Discord_OnAssign"] ?: internalVersion.discordOnAssign,
                discordOnClose = visuals["Discord_OnClose"] ?: internalVersion.discordOnClose,
                discordOnCloseAll = visuals["Discord_OnCloseAll"] ?: internalVersion.discordOnCloseAll,
                discordOnComment = visuals["Discord_OnComment"] ?: internalVersion.discordOnComment,
                discordOnCreate = visuals["Discord_OnCreate"] ?: internalVersion.discordOnCreate,
                discordOnReopen = visuals["Discord_OnReopen"] ?: internalVersion.discordOnReopen,
                discordOnPriorityChange = visuals["Discord_OnPriorityChange"] ?: internalVersion.discordOnPriorityChange,
                viewHeader = readAndPrime("ViewFormat_Header") ?: internalVersion.viewHeader,
                viewSep1 = readAndPrime("ViewFormat_Sep1") ?: internalVersion.viewSep1,
                viewCreator = readAndPrime("ViewFormat_InfoCreator") ?: internalVersion.viewCreator,
                viewAssignedTo = readAndPrime("ViewFormat_InfoAssignedTo") ?: internalVersion.viewAssignedTo,
                viewPriority = readAndPrime("ViewFormat_InfoPriority") ?: internalVersion.viewPriority,
                viewStatus = readAndPrime("ViewFormat_InfoStatus") ?: internalVersion.viewStatus,
                viewLocation = readAndPrime("ViewFormat_InfoLocation") ?: internalVersion.viewLocation,
                viewSep2 = readAndPrime("ViewFormat_Sep2") ?: internalVersion.viewSep2,
                viewComment = readAndPrime("ViewFormat_Comment") ?: internalVersion.viewComment,
                viewDeepComment = readAndPrime("ViewFormat_DeepComment") ?: internalVersion.viewDeepComment,
                viewDeepSetPriority = readAndPrime("ViewFormat_DeepSetPriority") ?: internalVersion.viewDeepSetPriority,
                viewDeepAssigned = readAndPrime("ViewFormat_DeepAssigned") ?: internalVersion.viewDeepAssigned,
                viewDeepReopen = readAndPrime("ViewFormat_DeepReopen") ?: internalVersion.viewDeepReopen,
                viewDeepClose = readAndPrime("ViewFormat_DeepClose") ?: internalVersion.viewDeepClose,
                viewDeepMassClose = readAndPrime("ViewFormat_DeepMassClose") ?: internalVersion.viewDeepMassClose,
                listHeader = readAndPrime("ListFormat_Header") ?: internalVersion.listHeader,
                listAssignedHeader = readAndPrime("ListFormat_AssignedHeader") ?: internalVersion.listAssignedHeader,
                listUnassignedHeader = readAndPrime("ListFormat_UnassignedHeader") ?: internalVersion.listUnassignedHeader,
                listEntry = readAndPrime("ListFormat_Entry") ?: internalVersion.listEntry,
                listFormattingSize = visuals["ListFormat_FormattingSize"]?.toIntOrNull() ?: internalVersion.listFormattingSize,
                listMaxLineSize = visuals["ListFormat_MaxLineSize"]?.toIntOrNull() ?: internalVersion.listMaxLineSize,
                searchHeader = readAndPrime("SearchFormat_Header") ?: internalVersion.searchHeader,
                searchEntry = readAndPrime("SearchFormat_Entry") ?: internalVersion.searchEntry,
                searchQuerying = readAndPrime("SearchFormat_Querying") ?: internalVersion.searchQuerying,
                searchFormattingSize = visuals["SearchFormat_FormattingSize"]?.toIntOrNull() ?: internalVersion.searchFormattingSize,
                searchMaxLineSize = visuals["SearchFormat_MaxLineSize"]?.toIntOrNull() ?: internalVersion.searchMaxLineSize,
                historyHeader = readAndPrime("History_Header") ?: internalVersion.historyHeader,
                historyEntry = readAndPrime("History_Entry") ?: internalVersion.historyEntry,
                historyFormattingSize = visuals["History_FormattingSize"]?.toIntOrNull() ?: internalVersion.historyFormattingSize,
                historyMaxLineSize = visuals["History_MaxLineSize"]?.toIntOrNull() ?: internalVersion.historyMaxLineSize,
                helpHeader = readAndPrime("Help_Header") ?: internalVersion.helpHeader,
                helpLine1 = readAndPrime("Help_Line1") ?: internalVersion.helpLine1,
                helpLine2 = readAndPrime("Help_Line2") ?: internalVersion.helpLine2,
                helpLine3 = readAndPrime("Help_Line3") ?: internalVersion.helpLine3,
                helpSep = readAndPrime("Help_Sep") ?: internalVersion.helpSep,
                helpHasSilence = readAndPrime("Help_HasSilence") ?: internalVersion.helpHasSilence,
                helpLackSilence = readAndPrime("Help_LackSilence") ?: internalVersion.helpLackSilence,
                helpRequiredParam = readAndPrime("Help_RequiredParam") ?: internalVersion.helpRequiredParam,
                helpOptionalParam = readAndPrime("Help_OptionalParam") ?: internalVersion.helpOptionalParam,
                helpEntry = readAndPrime("Help_Entry") ?: internalVersion.helpEntry,
                stacktraceLine1 = readAndPrime("Stacktrace_Line1") ?: internalVersion.stacktraceLine1,
                stacktraceLine2 = readAndPrime("Stacktrace_Line2") ?: internalVersion.stacktraceLine2,
                stacktraceLine3 = readAndPrime("Stacktrace_Line3") ?: internalVersion.stacktraceLine3,
                stacktraceLine4 = readAndPrime("Stacktrace_Line4") ?: internalVersion.stacktraceLine4,
                stacktraceEntry = readAndPrime("Stacktrace_Entry") ?: internalVersion.stacktraceEntry,
                informationReloadInitiated = readAndPrime("Info_ReloadInitiated") ?: internalVersion.informationReloadInitiated,
                informationReloadSuccess = readAndPrime("Info_ReloadSuccess") ?: internalVersion.informationReloadSuccess,
                informationReloadTasksDone = readAndPrime("Info_Reload_TasksDone") ?: internalVersion.informationReloadTasksDone,
                informationDBConvertInit = readAndPrime("Info_DBConversionInit") ?: internalVersion.informationDBConvertInit,
                informationDBConvertSuccess = readAndPrime("Info_DBConversionSuccess") ?: internalVersion.informationDBConvertSuccess,
                informationReloadFailure = readAndPrime("Info_ReloadFailure") ?: internalVersion.informationReloadFailure,
                notifyUnreadUpdateSingle = readAndPrime("Notify_UnreadUpdate_Single") ?: internalVersion.notifyUnreadUpdateSingle,
                notifyUnreadUpdateMulti = readAndPrime("Notify_UnreadUpdate_Multi") ?: internalVersion.notifyUnreadUpdateMulti,
                notifyOpenAssigned = readAndPrime("Notify_OpenAssigned") ?: internalVersion.notifyOpenAssigned,
                notifyTicketAssignEvent = readAndPrime("Notify_Event_TicketAssign") ?: internalVersion.notifyTicketAssignEvent,
                notifyTicketAssignSuccess = readAndPrime("Notify_TicketAssignSuccess") ?: internalVersion.notifyTicketAssignSuccess,
                notifyTicketCreationSuccess = readAndPrime("Notify_TicketCreationSuccessful") ?: internalVersion.notifyTicketCreationSuccess,
                notifyTicketCreationEvent = readAndPrime("Notify_Event_TicketCreation") ?: internalVersion.notifyTicketCreationEvent,
                notifyTicketCommentEvent = readAndPrime("Notify_Event_TicketComment") ?: internalVersion.notifyTicketCommentEvent,
                notifyTicketCommentSuccess = readAndPrime("Notify_TicketCommentSuccessful") ?: internalVersion.notifyTicketCommentSuccess,
                notifyTicketModificationEvent = readAndPrime("Notify_Event_TicketModification") ?: internalVersion.notifyTicketModificationEvent,
                notifyTicketMassCloseEvent = readAndPrime("Notify_Event_MassClose") ?: internalVersion.notifyTicketMassCloseEvent,
                notifyTicketMassCloseSuccess = readAndPrime("Notify_MassCloseSuccess") ?: internalVersion.notifyTicketMassCloseSuccess,
                notifyTicketCloseSuccess = readAndPrime("Notify_TicketCloseSuccess") ?: internalVersion.notifyTicketCloseSuccess,
                notifyTicketCloseWCommentSuccess = readAndPrime("Notify_TicketCloseWithCommentSuccess") ?: internalVersion.notifyTicketCloseWCommentSuccess,
                notifyTicketCloseEvent = readAndPrime("Notify_Event_TicketClose") ?: internalVersion.notifyTicketCloseEvent,
                notifyTicketCloseWCommentEvent = readAndPrime("Notify_Event_TicketCloseWithComment") ?: internalVersion.notifyTicketCloseWCommentEvent,
                notifyTicketReopenSuccess = readAndPrime("Notify_TicketReopenSuccess") ?: internalVersion.notifyTicketReopenSuccess,
                notifyTicketReopenEvent = readAndPrime("Notify_Event_TicketReopen") ?: internalVersion.notifyTicketReopenEvent,
                notifyTicketSetPrioritySuccess = readAndPrime("Notify_TicketSetPrioritySuccess") ?: internalVersion.notifyTicketSetPrioritySuccess,
                notifyTicketSetPriorityEvent = readAndPrime("Notify_Event_SetPriority") ?: internalVersion.notifyTicketSetPriorityEvent,
                notifyPluginUpdate = readAndPrime("Notify_Event_PluginUpdate") ?: internalVersion.notifyPluginUpdate,
                notifyProxyUpdate = readAndPrime("Notify_Event_ProxyUpdate") ?: internalVersion.notifyProxyUpdate,
            )
        }
    }
}

class LocaleHandler(
    private val activeTypes: Map<String, TMLocale>,
    private val fallbackType: TMLocale,
    val consoleLocale: TMLocale,
) {
    companion object {
        fun buildLocales(
            mainColourCode: String,
            preferredLocale: String,
            console_Locale: String,
            forceLocale: Boolean,
            rootFolderLocation: String,
            enableAVC: Boolean,
        ) : LocaleHandler {
            val allLocales = supportedLocales
                .asTypeSafeStream()
                .parallel()
                .map { it to TMLocale.buildLocaleFromInternal(it, mainColourCode) }
                .map { (k,v) -> if (enableAVC) k to TMLocale.buildLocaleFromExternal(
                    k,
                    rootFolderLocation,
                    mainColourCode,
                    v
                ) else k to v }
                .toList()
                .toMap()
            val lowercasePreferred = preferredLocale.lowercase()

            val activeTypes = if (forceLocale) mapOf(lowercasePreferred to allLocales.getOrDefault(lowercasePreferred, allLocales["en_ca"]!!)) else allLocales
            val fallback = activeTypes.getOrDefault(lowercasePreferred, allLocales["en_ca"]!!)
            val consoleLocale = activeTypes.getOrDefault(console_Locale.lowercase(), fallback)

            return LocaleHandler(activeTypes, fallback, consoleLocale)
        }
    }

    fun getOrDefault(type: String) = activeTypes.getOrDefault(type.lowercase(), fallbackType)
    fun getCommandBases() = if (activeTypes.isEmpty()) setOf(fallbackType.commandBase) else activeTypes.map { it.value.commandBase }.toSet()
}