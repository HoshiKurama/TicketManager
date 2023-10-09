package com.github.hoshikurama.ticketmanager.commonse.registrydefaults.locale

import com.github.hoshikurama.ticketmanager.api.registry.config.Config
import com.github.hoshikurama.ticketmanager.api.registry.locale.Locale
import com.github.hoshikurama.ticketmanager.api.registry.locale.LocaleExtension
import com.github.hoshikurama.ticketmanager.common.supportedLocales
import org.yaml.snakeyaml.Yaml
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.notExists

class DefaultLocaleExtension : LocaleExtension {

    override suspend fun load(tmDirectory: Path, config: Config): Locale {
        val selectedLocale = config.visualOptions.requestedLocale.lowercase()
            .takeIf(supportedLocales::contains) ?: "en_ca"

        val internalVersion = buildLocaleFromInternal(
            colour = config.visualOptions.consistentColourCode,
            localeID = selectedLocale,
        )

        // AVC version or internal version~
        return if (config.visualOptions.enableAVC) {
            val localesDirectory = tmDirectory.resolve("locales")

            if (localesDirectory.notExists())
                localesDirectory.toFile().mkdir()

            // Generates any missing AVC files~
            supportedLocales
                .filterNot { localesDirectory.resolve("$it.yml").exists() }
                .forEach {
                    this::class.java.classLoader
                        .getResourceAsStream("locales/visual/$it.yml")!!
                        .use { input -> Files.copy(input, localesDirectory.resolve("$it.yml")) }
                }

            buildLocaleFromExternal(
                colour = config.visualOptions.consistentColourCode,
                localesFolderPath = localesDirectory,
                internalVersion = internalVersion,
                localeID = selectedLocale,
            )
        } else internalVersion
    }

    private fun loadYMLFrom(location: String): Map<String, String> =
        this::class.java.classLoader
            .getResourceAsStream(location)
            .let { Yaml().load(it) }

    private fun inlinePlaceholders(str: String?, tmHeader: String, cc: String) = str
        ?.replace("%TMHeader%", tmHeader)
        ?.replace("%nl%", "\n")
        ?.replace("%CC%", convertToMiniMessage(cc))

    private fun convertToMiniMessage(str: String): String {
        // Return if already kyrori insertable...
        if (str.startsWith("<") && str.endsWith(">"))
            return str.slice(1 until str.length - 1)

        val isHexCode = "^#([a-fA-F\\d]{6})\$".toRegex()::matches

        if (isHexCode(str)) return str
        str.toIntOrNull()?.let(Integer::toHexString)?.let { "#$it" }?.let { if (isHexCode(it)) return it }

        return when (str) {
            "dark_red", "&4" -> "#AA0000"
            "red", "&c" -> "#FF5555"
            "gold", "&6" -> "#FFAA00"
            "yellow", "&e" -> "#FFFF55"
            "dark_green", "&2" -> "#00AA00"
            "green", "&a" -> "#55FF55"
            "aqua", "&b" -> "#55FFFF"
            "dark_aqua", "&3" -> "#00AAAA"
            "dark_blue", "&1" -> "#0000AA"
            "blue", "&9" -> "#5555FF"
            "light_purple", "&d" -> "#FF55FF"
            "dark_purple", "&5" -> "#AA00AA"
            "white", "&f" -> "#FFFFFF"
            "gray", "&7" -> "#AAAAAA"
            "dark_gray", "&8" -> "#555555"
            "black", "&0" -> "#000000"
            else -> "#FFFFFF"
        }
    }

    private fun buildLocaleFromInternal(localeID: String, colour: String): TMLocale {
        val core = loadYMLFrom("locales/core/$localeID.yml")
        val visuals = loadYMLFrom("locales/visual/$localeID.yml")

        // Prepares Headers for inlining (Still has placeholders)
        val uniformHeader = visuals["Uniform_Header"]!!
        val warningHeader = visuals["Warning_Header"]!!

        fun readAndPrime(key: String) = inlinePlaceholders(visuals[key]!!, uniformHeader, colour)
        fun inlineWarningHeader(key: String) =
            inlinePlaceholders(visuals[key]!!.replace("%Header%", warningHeader), uniformHeader, colour)

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
            parameterConstraints = core["Parameters_Constraints"]!!,
            consoleInitializationComplete = core["Console_InitializationComplete"]!!,
            consoleErrorBadDatabase = core["ConsoleError_DatabaseInitialize"]!!,
            consoleWarningInvalidConfigNode = core["ConsoleWarning_InvalidConfigNode"]!!,
            consoleErrorScheduledNotifications = core["ConsoleError_ScheduledNotifications"]!!,
            consoleErrorCommandExecution = core["ConsoleError_CommandExecution"]!!,
            parameterNewSearchIndicator = core["Parameter_NewSearch_Indicator"]!!,
            parameterLiteralPlayer = core["Parameter_Literal_Player"]!!,
            parameterLiteralGroup = core["Parameter_Literal_PermissionGroup"]!!,
            parameterLiteralPhrase = core["Parameter_Literal_Phrase"]!!,
            consoleDatabaseLoaded = core["Console_DatabaseLoaded"]!!,
            consoleDatabaseWaitStart = core["Console_WaitingForDatabase"]!!,
            // Visual Aspects
            priorityLowest = visuals["Priority_Lowest"]!!,
            priorityLow = visuals["Priority_Low"]!!,
            priorityNormal = visuals["Priority_Normal"]!!,
            priorityHigh = visuals["Priority_High"]!!,
            priorityHighest = visuals["Priority_Highest"]!!,
            priorityColourLowestHex = convertToMiniMessage(visuals["PriorityColour_Lowest"]!!),
            priorityColourLowHex = convertToMiniMessage(visuals["PriorityColour_Low"]!!),
            priorityColourNormalHex = convertToMiniMessage(visuals["PriorityColour_Normal"]!!),
            priorityColourHighHex = convertToMiniMessage(visuals["PriorityColour_High"]!!),
            priorityColourHighestHex = convertToMiniMessage(visuals["PriorityColour_Highest"]!!),
            statusOpen = visuals["Status_Open"]!!,
            statusClosed = visuals["Status_Closed"]!!,
            statusColourOpenHex = convertToMiniMessage(visuals["StatusColour_Open"]!!),
            statusColourClosedHex = convertToMiniMessage(visuals["StatusColour_Closed"]!!),
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
            warningsNoConfig = inlineWarningHeader("Warning_NoConfig")!!,
            warningsUnexpectedError = inlineWarningHeader("Warning_UnexpectedError")!!,
            warningsLongTaskDuringReload = inlineWarningHeader("Warning_LongTaskDuringReload")!!,
            warningsInvalidConfig = inlineWarningHeader("Warning_InvalidConfig")!!,
            warningsInternalError = inlineWarningHeader("Warning_InternalError")!!,
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
            helpExplanationAssign = readAndPrime("Help_Explanation_Assign")!!,
            helpExplanationClaim = readAndPrime("Help_Explanation_Claim")!!,
            helpExplanationClose = readAndPrime("Help_Explanation_Close")!!,
            helpExplanationCloseAll = readAndPrime("Help_Explanation_CloseAll")!!,
            helpExplanationComment = readAndPrime("Help_Explanation_Comment")!!,
            helpExplanationCreate = readAndPrime("Help_Explanation_Create")!!,
            helpExplanationHelp = readAndPrime("Help_Explanation_Help")!!,
            helpExplanationHistory = readAndPrime("Help_Explanation_History")!!,
            helpExplanationList = readAndPrime("Help_Explanation_List")!!,
            helpExplanationListAssigned = readAndPrime("Help_Explanation_ListAssigned")!!,
            helpExplanationListUnassigned = readAndPrime("Help_Explanation_ListUnassigned")!!,
            helpExplanationReload = readAndPrime("Help_Explanation_Reload")!!,
            helpExplanationReopen = readAndPrime("Help_Explanation_Reopen")!!,
            helpExplanationSearch = readAndPrime("Help_Explanation_Search")!!,
            helpExplanationSetPriority = readAndPrime("Help_Explanation_SetPriority")!!,
            helpExplanationTeleport = readAndPrime("Help_Explanation_Teleport")!!,
            helpExplanationUnassign = readAndPrime("Help_Explanation_Unassign")!!,
            helpExplanationView = readAndPrime("Help_Explanation_View")!!,
            helpExplanationDeepView = readAndPrime("Help_Explanation_DeepView")!!,
            stacktraceLine1 = readAndPrime("Stacktrace_Line1")!!,
            stacktraceLine2 = readAndPrime("Stacktrace_Line2")!!,
            stacktraceLine3 = readAndPrime("Stacktrace_Line3")!!,
            stacktraceLine4 = readAndPrime("Stacktrace_Line4")!!,
            stacktraceEntry = readAndPrime("Stacktrace_Entry")!!,
            informationReloadInitiated = readAndPrime("Info_ReloadInitiated")!!,
            informationReloadSuccess = readAndPrime("Info_ReloadSuccess")!!,
            informationReloadTasksDone = readAndPrime("Info_Reload_TasksDone")!!,
            informationReloadFailure = readAndPrime("Info_ReloadFailure")!!,
            informationUnderCooldown = readAndPrime("Info_UnderCooldown")!!,
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
            brigadierNotYourTicket = readAndPrime("Brigadier_NotYourTicket")!!,
            brigadierInvalidID = readAndPrime("Brigadier_InvalidID")!!,
            brigadierTicketAlreadyClosed = readAndPrime("Brigadier_TicketAlreadyClosed")!!,
            brigadierTicketAlreadyOpen = readAndPrime("Brigadier_TicketAlreadyOpen")!!,
            brigadierConsoleLocTP = readAndPrime("Brigadier_ConsoleLocationTeleport")!!,
            brigadierNoTPDiffServer = readAndPrime("Brigadier_NoTeleport_DifferentServer")!!,
            brigadierNoTPSameServer = readAndPrime("Brigadier_NoTeleport_SameServer")!!,
            brigadierNoTPProxyDisabled = readAndPrime("Brigadier_NoTeleport_ProxyDisabled")!!,
            brigadierOtherHistory = readAndPrime("Brigadier_OtherHistory")!!,
            brigadierSearchBadSymbol1 = readAndPrime("Brigadier_Search_BadSymbol_1")!!,
            brigadierSearchBadStatus = readAndPrime("Brigadier_Search_BadStatus")!!,
            brigadierSearchBadSymbol2 = readAndPrime("Brigadier_Search_BadSymbol_2")!!,
            brigadierSearchBadSymbol3 = readAndPrime("Brigadier_Search_BadSymbol_3")!!,
            brigadierBadPageNumber = readAndPrime("Brigadier_BadPageNumber")!!,
            brigadierBadSearchConstraint = readAndPrime("Brigadier_BadSearchConstraint")!!,
            brigadierInvalidAssignment = readAndPrime("Brigadier_InvalidAssignment")!!,
            brigadierInvalidTimeUnit = readAndPrime("Brigadier_InvalidTimeUnit")!!,
            brigadierInvalidPriority = readAndPrime("Brigadier_InvalidPriority")!!,

        )
    }

    private fun buildLocaleFromExternal(localeID: String, localesFolderPath: Path, colour: String, internalVersion: TMLocale): TMLocale {
        val visuals: Map<String, String> = try {
            localesFolderPath.resolve("$localeID.yml")
                .absolute()
                .inputStream()
                .let { Yaml().load(it) }
        } catch (e: Exception) { mapOf() }

        // Prepares Headers for inlining (Still has placeholders)
        val uniformHeader = visuals["Uniform_Header"] ?: "<%CC%>[TicketManager] "
        val warningHeader = visuals["Warning_Header"] ?: "<red>[TicketManager] "

        fun readAndPrime(key: String) = inlinePlaceholders(visuals[key], uniformHeader, colour)
        fun inlineWarningHeader(key: String) =
            inlinePlaceholders(visuals[key]?.replace("%Header%", warningHeader), uniformHeader, colour)

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
            parameterConstraints = internalVersion.parameterConstraints,
            consoleInitializationComplete = internalVersion.consoleInitializationComplete,
            consoleErrorBadDatabase = internalVersion.consoleErrorBadDatabase,
            consoleWarningInvalidConfigNode = internalVersion.consoleWarningInvalidConfigNode,
            consoleErrorScheduledNotifications = internalVersion.consoleErrorScheduledNotifications,
            consoleErrorCommandExecution = internalVersion.consoleErrorCommandExecution,
            parameterNewSearchIndicator = internalVersion.parameterNewSearchIndicator,
            parameterLiteralGroup = internalVersion.parameterLiteralGroup,
            parameterLiteralPhrase = internalVersion.parameterLiteralPhrase,
            parameterLiteralPlayer = internalVersion.parameterLiteralPlayer,
            consoleDatabaseLoaded = internalVersion.consoleDatabaseLoaded,
            consoleDatabaseWaitStart = internalVersion.consoleDatabaseWaitStart,
            // Visual Aspects
            priorityLowest = visuals["Priority_Lowest"] ?: internalVersion.priorityLowest,
            priorityLow = visuals["Priority_Low"] ?: internalVersion.priorityLow,
            priorityNormal = visuals["Priority_Normal"] ?: internalVersion.priorityNormal,
            priorityHigh = visuals["Priority_High"] ?: internalVersion.priorityHigh,
            priorityHighest = visuals["Priority_Highest"] ?: internalVersion.priorityHighest,
            priorityColourLowestHex = convertToMiniMessage(
                visuals["PriorityColour_Lowest"] ?: internalVersion.priorityColourLowestHex
            ),
            priorityColourLowHex = convertToMiniMessage(
                visuals["PriorityColour_Low"] ?: internalVersion.priorityColourLowHex
            ),
            priorityColourNormalHex = convertToMiniMessage(
                visuals["PriorityColour_Normal"] ?: internalVersion.priorityColourNormalHex
            ),
            priorityColourHighHex = convertToMiniMessage(
                visuals["PriorityColour_High"] ?: internalVersion.priorityColourHighHex
            ),
            priorityColourHighestHex = convertToMiniMessage(
                visuals["PriorityColour_Highest"] ?: internalVersion.priorityColourHighestHex
            ),
            statusOpen = visuals["Status_Open"] ?: internalVersion.statusOpen,
            statusClosed = visuals["Status_Closed"] ?: internalVersion.statusClosed,
            statusColourOpenHex = convertToMiniMessage(
                visuals["StatusColour_Open"] ?: internalVersion.statusColourOpenHex
            ),
            statusColourClosedHex = convertToMiniMessage(
                visuals["StatusColour_Closed"] ?: internalVersion.statusColourClosedHex
            ),
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
            warningsNoConfig = inlineWarningHeader("Warning_NoConfig") ?: internalVersion.warningsNoConfig,
            warningsUnexpectedError = inlineWarningHeader("Warning_UnexpectedError") ?: internalVersion.warningsUnexpectedError,
            warningsLongTaskDuringReload = inlineWarningHeader("Warning_LongTaskDuringReload") ?: internalVersion.warningsLongTaskDuringReload,
            warningsInvalidConfig = inlineWarningHeader("Warning_InvalidConfig") ?: internalVersion.warningsInvalidConfig,
            warningsInternalError = inlineWarningHeader("Warning_InternalError") ?: internalVersion.warningsInternalError,
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
            helpExplanationAssign = readAndPrime("Help_Explanation_Assign") ?: internalVersion.helpExplanationAssign,
            helpExplanationClaim = readAndPrime("Help_Explanation_Claim") ?: internalVersion.helpExplanationClaim,
            helpExplanationClose = readAndPrime("Help_Explanation_Close") ?: internalVersion.helpExplanationClose,
            helpExplanationCloseAll = readAndPrime("Help_Explanation_CloseAll") ?: internalVersion.helpExplanationCloseAll,
            helpExplanationComment = readAndPrime("Help_Explanation_Comment") ?: internalVersion.helpExplanationComment,
            helpExplanationCreate = readAndPrime("Help_Explanation_Create") ?: internalVersion.helpExplanationCreate,
            helpExplanationHelp = readAndPrime("Help_Explanation_Help") ?: internalVersion.helpExplanationHelp,
            helpExplanationHistory = readAndPrime("Help_Explanation_History") ?: internalVersion.helpExplanationHistory,
            helpExplanationList = readAndPrime("Help_Explanation_List") ?: internalVersion.helpExplanationList,
            helpExplanationListAssigned = readAndPrime("Help_Explanation_ListAssigned") ?: internalVersion.helpExplanationListAssigned,
            helpExplanationListUnassigned = readAndPrime("Help_Explanation_ListUnassigned") ?: internalVersion.helpExplanationListUnassigned,
            helpExplanationReload = readAndPrime("Help_Explanation_Reload") ?: internalVersion.helpExplanationReload,
            helpExplanationReopen = readAndPrime("Help_Explanation_Reopen") ?: internalVersion.helpExplanationReopen,
            helpExplanationSearch = readAndPrime("Help_Explanation_Search") ?: internalVersion.helpExplanationSearch,
            helpExplanationSetPriority = readAndPrime("Help_Explanation_SetPriority") ?: internalVersion.helpExplanationSetPriority,
            helpExplanationTeleport = readAndPrime("Help_Explanation_Teleport") ?: internalVersion.helpExplanationTeleport,
            helpExplanationUnassign = readAndPrime("Help_Explanation_Unassign") ?: internalVersion.helpExplanationUnassign,
            helpExplanationView = readAndPrime("Help_Explanation_View") ?: internalVersion.helpExplanationView,
            helpExplanationDeepView = readAndPrime("Help_Explanation_DeepView") ?: internalVersion.helpExplanationDeepView,
            stacktraceLine1 = readAndPrime("Stacktrace_Line1") ?: internalVersion.stacktraceLine1,
            stacktraceLine2 = readAndPrime("Stacktrace_Line2") ?: internalVersion.stacktraceLine2,
            stacktraceLine3 = readAndPrime("Stacktrace_Line3") ?: internalVersion.stacktraceLine3,
            stacktraceLine4 = readAndPrime("Stacktrace_Line4") ?: internalVersion.stacktraceLine4,
            stacktraceEntry = readAndPrime("Stacktrace_Entry") ?: internalVersion.stacktraceEntry,
            informationReloadInitiated = readAndPrime("Info_ReloadInitiated") ?: internalVersion.informationReloadInitiated,
            informationReloadSuccess = readAndPrime("Info_ReloadSuccess") ?: internalVersion.informationReloadSuccess,
            informationReloadTasksDone = readAndPrime("Info_Reload_TasksDone") ?: internalVersion.informationReloadTasksDone,
            informationReloadFailure = readAndPrime("Info_ReloadFailure") ?: internalVersion.informationReloadFailure,
            informationUnderCooldown = readAndPrime("Info_UnderCooldown") ?: internalVersion.informationUnderCooldown,
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
            brigadierNotYourTicket = readAndPrime("Brigadier_NotYourTicket") ?: internalVersion.brigadierNotYourTicket,
            brigadierInvalidID = readAndPrime("Brigadier_InvalidID") ?: internalVersion.brigadierInvalidID,
            brigadierTicketAlreadyClosed = readAndPrime("Brigadier_TicketAlreadyClosed") ?: internalVersion.brigadierTicketAlreadyClosed,
            brigadierTicketAlreadyOpen = readAndPrime("Brigadier_TicketAlreadyOpen") ?: internalVersion.brigadierTicketAlreadyOpen,
            brigadierConsoleLocTP = readAndPrime("Brigadier_ConsoleLocationTeleport") ?: internalVersion.brigadierConsoleLocTP,
            brigadierNoTPDiffServer = readAndPrime("Brigadier_NoTeleport_DifferentServer") ?: internalVersion.brigadierNoTPDiffServer,
            brigadierNoTPSameServer = readAndPrime("Brigadier_NoTeleport_SameServer") ?: internalVersion.brigadierNoTPSameServer,
            brigadierNoTPProxyDisabled = readAndPrime("Brigadier_NoTeleport_ProxyDisabled") ?: internalVersion.brigadierNoTPProxyDisabled,
            brigadierOtherHistory = readAndPrime("Brigadier_OtherHistory") ?: internalVersion.brigadierOtherHistory,
            brigadierSearchBadSymbol1 = readAndPrime("Brigadier_Search_BadSymbol_1") ?: internalVersion.brigadierSearchBadSymbol1,
            brigadierSearchBadStatus = readAndPrime("Brigadier_Search_BadStatus") ?: internalVersion.brigadierSearchBadStatus,
            brigadierSearchBadSymbol2 = readAndPrime("Brigadier_Search_BadSymbol_2") ?: internalVersion.brigadierSearchBadSymbol2,
            brigadierSearchBadSymbol3 = readAndPrime("Brigadier_Search_BadSymbol_3") ?: internalVersion.brigadierSearchBadSymbol3,
            brigadierBadPageNumber = readAndPrime("Brigadier_BadPageNumber") ?: internalVersion.brigadierBadPageNumber,
            brigadierBadSearchConstraint = readAndPrime("Brigadier_BadSearchConstraint") ?: internalVersion.brigadierBadSearchConstraint,
            brigadierInvalidAssignment = readAndPrime("Brigadier_InvalidAssignment") ?: internalVersion.brigadierInvalidAssignment,
            brigadierInvalidTimeUnit = readAndPrime("Brigadier_InvalidTimeUnit") ?: internalVersion.brigadierInvalidTimeUnit,
            brigadierInvalidPriority = readAndPrime("Brigadier_InvalidPriority") ?: internalVersion.brigadierInvalidPriority,
        )
    }
}