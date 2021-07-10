package com.github.hoshikurama.ticketmanager.common

import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.yaml.snakeyaml.Yaml
import kotlin.coroutines.CoroutineContext

const val translationNotFound = " TNF "

class LocaleHandler(
    val mainColourCode: String,
    private val activeTypes: Map<String, TMLocale>,
    private val defaultType: TMLocale,
    val consoleLocale: TMLocale,
) {

    companion object {
        suspend fun buildLocalesAsync(
            mainColourCode: String,
            preferredLocale: String,
            console_Locale: String,
            forceLocale: Boolean,
            context: CoroutineContext
        ): LocaleHandler = withContext(context) {
            val fallback = async { TMLocale(mainColourCode, "en_CA") }

            val activeTypes =
                if (forceLocale) mapOf()
                else mapOf(
                    "en_ca" to fallback,
                    "en_us" to async { TMLocale(mainColourCode, "en_CA") },
                    "en_uk" to async { TMLocale(mainColourCode, "en_CA") }
                )
                    .mapValues { it.value.await() }

            val defaultType = activeTypes.getOrDefault(preferredLocale.lowercase(), fallback.await())
            val consoleLocale = activeTypes.getOrDefault(console_Locale.lowercase(), defaultType)

            LocaleHandler(mainColourCode, activeTypes, defaultType, consoleLocale)
        }
    }

    fun getOrDefault(type: String) = activeTypes.getOrDefault(type.lowercase(), defaultType)
    fun getCommandBases() = if (activeTypes.isEmpty()) setOf(defaultType.commandBase) else activeTypes.map { it.value.commandBase }.toSet()
}


class TMLocale(
    colourCode: String,
    locale: String,
) {
    // View and Deep View Format
    val viewFormatHeader: String
    val viewFormatSep1: String
    val viewFormatInfo1: String
    val viewFormatInfo2: String
    val viewFormatInfo3: String
    val viewFormatSep2: String
    val viewFormatComment: String
    val viewFormatDeepComment: String
    val viewFormatDeepSetPriority: String
    val viewFormatDeepAssigned: String
    val viewFormatDeepReopen: String
    val viewFormatDeepClose: String
    val viewFormatDeepMassClose: String

    // List Format:
    val listFormatAssignedHeader: String

    // Search Format:
    val searchFormatHeader: String
    val searchFormatEntry: String
    val searchFormatQuerying: String

    // Time
    val timeSeconds: String
    val timeMinutes: String
    val timeHours: String
    val timeDays: String
    val timeWeeks: String
    val timeYears: String

    // Search time keywords
    val searchTimeSecond: String
    val searchTimeMinute: String
    val searchTimeHour: String
    val searchTimeDay: String
    val searchTimeWeek: String
    val searchTimeYear: String

    // Warnings
    val warningsLocked: String
    val warningsNoPermission: String
    val warningsInvalidID: String
    val warningsInvalidNumber: String
    val warningsVaultNotFound: String
    val warningsNoConfig: String
    val warningsInvalidCommand: String
    val warningsPriorityOutOfBounds: String
    val warningsUnderCooldown: String
    val warningsTicketAlreadyClosed: String
    val warningsTicketAlreadyOpen: String
    val warningsInvalidDBType: String
    val warningsConvertToSameDBType: String
    val warningsUnexpectedError: String

    // Command Types
    val commandBase: String
    val commandWordAssign: String
    val commandWordSilentAssign: String
    val commandWordClaim: String
    val commandWordSilentClaim: String
    val commandWordClose: String
    val commandWordSilentClose: String
    val commandWordCloseAll: String
    val commandWordSilentCloseAll: String
    val commandWordComment: String
    val commandWordSilentComment: String
    val commandWordCreate: String
    val commandWordHelp: String
    val commandWordHistory: String
    val commandWordList: String
    val commandWordListAssigned: String
    val commandWordReopen: String
    val commandWordSilentReopen: String
    val commandWordSearch: String
    val commandWordSetPriority: String
    val commandWordSilentSetPriority: String
    val commandWordTeleport: String
    val commandWordVersion: String
    val commandWordUnassign: String
    val commandWordSilentUnassign: String
    val commandWordView: String
    val commandWordDeepView: String
    val commandWordReload: String
    val commandWordConvertDB: String

    // Required or Optional
    val parameterID: String
    val parameterAssignment: String
    val parameterLowerID: String
    val parameterUpperID: String
    val parameterComment: String
    val parameterPage: String
    val parameterLevel: String
    val parameterUser: String
    val parameterTargetDB: String
    val parameterConstraints: String

    // Priority
    val priorityLowest: String
    val priorityLow: String
    val priorityNormal: String
    val priorityHigh: String
    val priorityHighest: String

    // Status
    val statusOpen: String
    val statusClosed: String

    // List Format
    val listFormatHeader: String
    val listFormatEntry: String

    // Click Events
    val clickTeleport: String
    val clickViewTicket: String
    val clickNextPage: String
    val clickBackPage: String
    val clickWiki: String

    // Miscellaneous
    val consoleName: String
    val miscNobody: String
    val wikiLink: String

    // Page Words:
    val pageBack: String
    val pageOf: String
    val pageNext: String

    // Search words:
    val searchAssigned: String
    val searchCreator: String
    val searchKeywords: String
    val searchPriority: String
    val searchStatus: String
    val searchTime: String
    val searchWorld: String
    val searchPage: String
    val searchClosedBy: String
    val searchLastClosedBy: String

    // Ticket Notifications
    val notifyUnreadUpdateSingle: String
    val notifyUnreadUpdateMulti: String
    val notifyOpenAssigned: String
    val notifyTicketAssignEvent: String
    val notifyTicketAssignSuccess: String
    val notifyTicketCreationSuccess: String
    val notifyTicketCreationEvent: String
    val notifyTicketCommentEvent: String
    val notifyTicketCommentSuccess: String
    val notifyTicketModificationEvent: String
    val notifyTicketMassCloseEvent: String
    val notifyTicketMassCloseSuccess: String
    val notifyTicketCloseSuccess: String
    val notifyTicketCloseWCommentSuccess: String
    val notifyTicketCloseEvent: String
    val notifyTicketCloseWCommentEvent: String
    val notifyTicketReopenSuccess: String
    val notifyTicketReopenEvent: String
    val notifyTicketSetPrioritySuccess: String
    val notifyTicketSetPriorityEvent: String
    val notifyPluginUpdate: String

    // Information:
    val informationReloadInitiated: String
    val informationReloadSuccess: String
    val informationReloadTasksDone: String
    val informationDBUpdate: String
    val informationDBUpdateComplete: String
    val informationDBConvertInit: String
    val informationDBConvertSuccess: String

    // Modified Stacktrace
    val stacktraceLine1: String
    val stacktraceLine2: String
    val stacktraceLine3: String
    val stacktraceLine4: String
    val stacktraceEntry: String

    // History Format:
    val historyHeader: String
    val historyEntry: String

    // Help Format:
    val helpHeader: String
    val helpLine1: String
    val helpLine2: String
    val helpLine3: String
    val helpSep: String

    init {
        val inputStream = this::class.java.classLoader.getResourceAsStream("locales/$locale.yml")
        val contents: Map<String, String> = Yaml().load(inputStream)
        fun matchOrDefault(key: String) = contents[key]
            ?.replace("%CC%", colourCode)
            ?.replace("%nl%", "\n")
            ?: translationNotFound

        viewFormatHeader = matchOrDefault("ViewFormat_Header")
        viewFormatSep1 = matchOrDefault("ViewFormat_Sep1")
        viewFormatInfo1 = matchOrDefault("ViewFormat_Info1")
        viewFormatInfo2 = matchOrDefault("ViewFormat_Info2")
        viewFormatInfo3 = matchOrDefault("ViewFormat_Info3")
        viewFormatSep2 = matchOrDefault("ViewFormat_Sep2")
        viewFormatComment = matchOrDefault("ViewFormat_Comment")
        viewFormatDeepComment = matchOrDefault("ViewFormat_DeepComment")
        timeSeconds = matchOrDefault("Time_Seconds")
        timeMinutes = matchOrDefault("Time_Minutes")
        timeHours = matchOrDefault("Time_Hours")
        timeDays = matchOrDefault("Time_Days")
        timeWeeks = matchOrDefault("Time_Weeks")
        timeYears = matchOrDefault("Time_Years")
        warningsLocked = matchOrDefault("Warning_Locked")
        warningsNoPermission = matchOrDefault("Warning_NoPermission")
        warningsInvalidID = matchOrDefault("Warning_InvalidID")
        warningsInvalidNumber = matchOrDefault("Warning_NAN")
        commandBase = matchOrDefault("Command_BaseCommand")
        commandWordAssign = matchOrDefault("Command_Assign")
        commandWordSilentAssign = matchOrDefault("Command_SilentAssign")
        commandWordClaim = matchOrDefault("Command_Claim")
        commandWordSilentClaim = matchOrDefault("Command_SilentClaim")
        commandWordClose = matchOrDefault("Command_Close")
        commandWordSilentClose = matchOrDefault("Command_SilentClose")
        commandWordCloseAll = matchOrDefault("Command_CloseAll")
        commandWordSilentCloseAll = matchOrDefault("Command_SilentCloseAll")
        commandWordHistory = matchOrDefault("Command_History")
        commandWordList = matchOrDefault("Command_List")
        commandWordListAssigned = matchOrDefault("Command_ListAssigned")
        commandWordReopen = matchOrDefault("Command_Reopen")
        commandWordSilentReopen = matchOrDefault("Command_SilentReopen")
        commandWordSearch = matchOrDefault("Command_Search")
        commandWordSetPriority = matchOrDefault("Command_SetPriority")
        commandWordSilentSetPriority = matchOrDefault("Command_SilentSetPriority")
        commandWordTeleport = matchOrDefault("Command_Teleport")
        commandWordUnassign = matchOrDefault("Command_Unassign")
        commandWordSilentUnassign = matchOrDefault("Command_SilentUnassign")
        commandWordView = matchOrDefault("Command_View")
        commandWordDeepView = matchOrDefault("Command_DeepView")
        listFormatHeader = matchOrDefault("ListFormat_Header")
        listFormatEntry = matchOrDefault("ListFormat_Entry")
        clickTeleport = matchOrDefault("Click_Teleport")
        clickViewTicket = matchOrDefault("Click_ViewTicket")
        consoleName = matchOrDefault("Console_Name")
        pageBack = matchOrDefault("Page_Back")
        pageOf = matchOrDefault("Page_Of")
        pageNext = matchOrDefault("Page_Next")
        notifyUnreadUpdateSingle = matchOrDefault("Notify_UnreadUpdate_Single")
        notifyUnreadUpdateMulti = matchOrDefault("Notify_UnreadUpdate_Multi")
        warningsVaultNotFound = matchOrDefault("Warning_VaultNotFound")
        warningsNoConfig = matchOrDefault("Warning_NoConfig")
        notifyOpenAssigned = matchOrDefault("Notify_OpenAssigned")
        commandWordComment = matchOrDefault("Command_Comment")
        commandWordSilentComment = matchOrDefault("Command_SilentComment")
        commandWordCreate = matchOrDefault("Command_Create")
        commandWordHelp = matchOrDefault("Command_Help")
        commandWordVersion = matchOrDefault("Command_Version")
        warningsInvalidCommand = matchOrDefault("Warning_InvalidCommand")
        warningsPriorityOutOfBounds = matchOrDefault("Warning_PriorityOutOfBounds")
        notifyTicketCreationSuccess = matchOrDefault("Notify_TicketCreationSuccessful")
        notifyTicketCreationEvent = matchOrDefault("Notify_Event_TicketCreation")
        warningsUnderCooldown = matchOrDefault("Warning_Under_Cooldown")
        notifyTicketCommentEvent = matchOrDefault("Notify_Event_TicketComment")
        notifyTicketCommentSuccess = matchOrDefault("Notify_TicketCommentSuccessful")
        notifyTicketModificationEvent = matchOrDefault("Notify_Event_TicketModification")
        notifyTicketMassCloseSuccess = matchOrDefault("Notify_MassCloseSuccess")
        notifyTicketMassCloseEvent = matchOrDefault("Notify_Event_MassClose")
        notifyTicketAssignSuccess = matchOrDefault("Notify_TicketAssignSuccess")
        miscNobody = matchOrDefault("Nobody")
        warningsTicketAlreadyClosed = matchOrDefault("Warning_TicketAlreadyClosed")
        warningsTicketAlreadyOpen = matchOrDefault("Warning_TicketAlreadyOpen")
        notifyTicketAssignEvent = matchOrDefault("Notify_Event_TicketAssign")
        notifyTicketCloseSuccess = matchOrDefault("Notify_TicketCloseSuccess")
        notifyTicketCloseWCommentSuccess = matchOrDefault("Notify_TicketCloseWithCommentSuccess")
        notifyTicketCloseEvent = matchOrDefault("Notify_Event_TicketClose")
        notifyTicketCloseWCommentEvent = matchOrDefault("Notify_Event_TicketCloseWithComment")
        searchTimeSecond = matchOrDefault("Search_Time_Second")
        searchTimeMinute = matchOrDefault("Search_Time_Minute")
        searchTimeHour = matchOrDefault("Search_Time_Hour")
        searchTimeDay = matchOrDefault("Search_Time_Day")
        searchTimeWeek = matchOrDefault("Search_Time_Week")
        searchTimeYear = matchOrDefault("Search_Time_Year")
        clickNextPage = matchOrDefault("Click_NextPage")
        clickBackPage = matchOrDefault("Click_BackPage")
        listFormatAssignedHeader = matchOrDefault("ListFormat_AssignedHeader")
        notifyTicketReopenSuccess = matchOrDefault("Notify_TicketReopenSuccess")
        notifyTicketReopenEvent = matchOrDefault("Notify_Event_TicketReopen")
        searchAssigned = matchOrDefault("Search_AssignedTo")
        searchCreator = matchOrDefault("Search_Creator")
        searchKeywords = matchOrDefault("Search_Keywords")
        searchPriority = matchOrDefault("Search_Priority")
        searchStatus = matchOrDefault("Search_Status")
        searchTime = matchOrDefault("Search_Time")
        searchPage = matchOrDefault("Search_Page")
        searchWorld = matchOrDefault("Search_World")
        searchFormatHeader = matchOrDefault("SearchFormat_Header")
        searchFormatEntry = matchOrDefault("SearchFormat_Entry")
        notifyTicketSetPrioritySuccess = matchOrDefault("Notify_TicketSetPrioritySuccess")
        notifyTicketSetPriorityEvent = matchOrDefault("Notify_Event_SetPriority")
        wikiLink = matchOrDefault("Localed_Wiki_Link")
        clickWiki = matchOrDefault("Click_GitHub_Wiki")
        viewFormatDeepSetPriority = matchOrDefault("ViewFormat_DeepSetPriority")
        viewFormatDeepAssigned = matchOrDefault("ViewFormat_DeepAssigned")
        viewFormatDeepReopen = matchOrDefault("ViewFormat_DeepReopen")
        viewFormatDeepClose = matchOrDefault("ViewFormat_DeepClose")
        viewFormatDeepMassClose = matchOrDefault("ViewFormat_DeepMassClose")
        priorityLowest = matchOrDefault("Priority_Lowest")
        priorityLow = matchOrDefault("Priority_Low")
        priorityNormal = matchOrDefault("Priority_Normal")
        priorityHigh = matchOrDefault("Priority_High")
        priorityHighest = matchOrDefault("Priority_Highest")
        statusOpen = matchOrDefault("Status_Open")
        statusClosed = matchOrDefault("Status_Closed")
        parameterID = matchOrDefault("Parameters_ID")
        parameterAssignment = matchOrDefault("Parameters_Assignment")
        parameterLowerID = matchOrDefault("Parameters_LowerID")
        parameterUpperID = matchOrDefault("Parameters_UpperID")
        parameterComment = matchOrDefault("Parameters_Comment")
        parameterPage = matchOrDefault("Parameters_Page")
        parameterLevel = matchOrDefault("Parameters_Level")
        commandWordReload = matchOrDefault("Command_Reload")
        informationReloadInitiated = matchOrDefault("Info_ReloadInitiated")
        informationReloadSuccess = matchOrDefault("Info_ReloadSuccess")
        informationReloadTasksDone = matchOrDefault("Info_Reload_TasksDone")
        searchFormatQuerying = matchOrDefault("SearchFormat_Querying")
        stacktraceLine1 = matchOrDefault("Stacktrace_Line1")
        stacktraceLine2 = matchOrDefault("Stacktrace_Line2")
        stacktraceLine3 = matchOrDefault("Stacktrace_Line3")
        stacktraceLine4 = matchOrDefault("Stacktrace_Line4")
        stacktraceEntry = matchOrDefault("Stacktrace_Entry")
        informationDBUpdate = matchOrDefault("Info_DBUpdate")
        informationDBUpdateComplete = matchOrDefault("Info_DBUpdateComplete")
        informationDBConvertInit = matchOrDefault("Info_DBConversionInit")
        informationDBConvertSuccess = matchOrDefault("Info_DBConversionSuccess")
        commandWordConvertDB = matchOrDefault("Command_ConvertDB")
        warningsInvalidDBType = matchOrDefault("Warning_InvalidDBType")
        warningsConvertToSameDBType = matchOrDefault("Warning_ConvertToSameDBType")
        parameterUser = matchOrDefault("Parameters_User")
        historyHeader = matchOrDefault("History_Header")
        historyEntry = matchOrDefault("History_Entry")
        helpHeader = matchOrDefault("Help_Header")
        helpLine1 = matchOrDefault("Help_Line1")
        helpLine2 = matchOrDefault("Help_Line2")
        helpLine3 = matchOrDefault("Help_Line3")
        helpSep = matchOrDefault("Help_Sep")
        parameterTargetDB = matchOrDefault("Parameters_Target_Database")
        parameterConstraints = matchOrDefault("Parameters_Constraints")
        warningsUnexpectedError = matchOrDefault("Warning_UnexpectedError")
        searchClosedBy = matchOrDefault("Search_ClosedBy")
        searchLastClosedBy = matchOrDefault("Search_LastClosedBy")
        notifyPluginUpdate = matchOrDefault("Notify_Event_PluginUpdate")
    }
}