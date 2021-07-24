
# Locale Guide
Thank you so much for helping to translate TicketManager! Before you begin, please read all of the information below.
NOTE: This is a markdown file! It is recommended to open this file in a markdown editor. If you do not have a markdown editor:
 * Ctrl + A to copy all text in the file.
 * Go to [stackedit.io](https://stackedit.io/app#) and enter the site.
 * Clear any text already on the site on the left side.
 * Ctrl + V to paste all text to the left side.
 * Use the left size to write text. Use the right side to read text.
You do not need a markdown editor however. You can use a normal text editor if you wish.

## Information:
* TicketManager expects high-quality and accurate translations.
* Users whose work is incorporated into TicketManager will be acknowledged as the rightful translator for that language everywhere TicketManager is posted.

## Requirements:
Translators must comply with the following:
* Have a basic understanding of English.
* Have a fluent understanding of the translated language.
* Language must be supported by the Minecraft client.
* Language must not yet be implemented by TicketManger.
* If your language has different versions *(US English vs UK English)*, additional requirements apply:
  - Language version must be supported by Minecraft client.
  - Language version must not yet be implemented by TicketManager.
  - Language version must have different spellings or  different words from the other language file *(color vs colour)*.
    - The change could be as small as one line or nearly all lines.
* Fulfill the extra eligibility criteria outlined for the specific type of translation. More information is provided in the instructions section.
* Translate for the latest version of TicketManager. Translations will not be accepted for previous versions.

## Steps:
 1. Contact me and express interest in translating TicketManager.
	  * If Hoshi sent you to download this file, you have completed this step.
	 * I can be reached in a few ways:
		 * Submit a feature request under issues on TicketManager's GitHub [here](https://github.com/HoshiKurama/TicketManager/issues).
		 * Send a message on Spigot [here](https://www.spigotmc.org/members/hoshikurama.278244/).
	* Initial contact should happen on one of the above sites, but alternative means can be discussed after.
	* Be clear about which translation you will provide and what sections you will translate. Current translatable sections are the following (pick at least 1):
		* Locale file
		* Config file
		* Wiki page
2. Fill out this form. Instructions are explained below.
3. Submit file(s) to Hoshi on the appropriate platform used or discussed previously.
	* Discussions may occur during this time regarding the translated document.
4. If your translation is accepted, congratulations!

# Translation Instructions
TicketManager has a few different types of translations needed, and each type has its own documentation.
Please follow instructions carefully.

## Locale File
Locale files are responsible for how users will interact with TicketManager.

### Instructions:
* Below the section "Input Data", you will see a table. This is where you write translations.
* "English" column contains the English word or sentence that needs to be translated.
* "Description" column has more information about the English word, such as how it is used.
* "Translation" column is where the translations are written.
	* To fill in, write the translation between the last two vertical bars.
		* Example: *| Don't touch | Don't touch | Don't touch | Write here! |*
		* Make sure to leave a space between the beginning and end of your translation.
* **DO NOT** write or change any field other than the translation column!
* Anything inside [] is **NOT** a part of the translation. It is a placeholder for something like another word or a number. **DO NOT** change the word inside!! Please copy and paste the placeholder where it is needed for the translation to be accurate. More information about the placeholder may be contained in the description column.
	* Words will always be singular or plural based on how they are spelled in the English column. If your language does not distinguish within the context, please use whatever makes sense in your language.
* Inside the Description column, a few words might be used:
	* "User" always refers to the user who executed the command
	* "Creator" always refers to the creator of the ticket
* If a direct translation does not exist or the direct translation would not be used by native speakers, use a word or phrase that native speakers would use instead.

### Input Data:
| YML_Key | Description | English | Translation |  
| -- | -- | -- | -- |  
| ViewFormat_Header | Tells user which ticket number they are viewing | Viewing Ticket [number] |  |  
| ViewFormat_Sep1 | Shorter way of saying "Information" | Info |  |  
| ViewFormat_Info1 | Person who created ticket | Creator |  |  
| ViewFormat_Info1 | The current assignment of ticket. Tickets can be assigned to people, groups, or phrases | Assigned To |  |  
| ViewFormat_Info2 | Current priority of ticket. Ranges from *LOWEST* to *HIGHEST* | Priority |  |  
| ViewFormat_Info2 | Current status of ticket. Either *OPEN* or *CLOSED* | Status |  |  
| ViewFormat_Info3 | Location ticket was created | Location |  |  
| ViewFormat_Sep2 | Action: Something you do. "Flipping a switch is an action" | Actions |  |  
| ViewFormat_DeepComment | User left a comment ~x hours ago | [user] \[comment] ~[number]\[time word] ago |  |  
| ViewFormat_DeepReopen | User reopened this ticket ~x hours ago | [user] Reopened ~[number]\[time word] ago |  |  
| ViewFormat_DeepAssigned | User assigned ticket previously | [user] Assignment [assignment] ~[number]\[time word] ago |  |  
| ViewFormat_DeepClose | User closed ticket previously | [user] Closed ~[number]\[time word] ago |  |  
| ViewFormat_DeepSetPriority | User changed ticket priority previously | [user] Priority [priority] ~[number]\[time word] ago |  |  
| ViewFormat_DeepMassClose | User mass-closed ticket previously | [user] Mass-Closed ~[number]\[time word] ago |  |  
| SearchFormat_Querying | User did a ticket search  | Querying Data... Please be patient! |  |  
| SearchFormat_Header | User's search results returned | Search Query Returned [number] Results |  |  
| History_Header | header for */ticket history* | [user] has [number] tickets |  |  
| Help_Header | Header for */ticket help* | [plugin name] Commands |  |  
| Help_Line1 | Types of arguments for */ticket help*  | Argument Types: Required... Optional |  |  
| Help_Line2 | N/A | Silent Format: /[ticket Command].Command |  |  
| Help_Line3 | Ability to be silenced  | Silenceable: [✕/✓] |  |  
| Time_Seconds | Plural second | seconds |  |  
| Time_Minutes | Plural minute | minutes |  |  
| Time_Hours | Plural hour  | hours |  |  
| Time_Days | Plural day | days |  |  
| Time_Weeks | Plural week | weeks |  |  
| Time_Years | Plural year | years |  |  
| Priority_Lowest | Lowest ticket priority | LOWEST |  |  
| Priority_Low | Low ticket priority | LOW |  |  
| Priority_Normal | Normal ticket priority | NORMAL |  |  
| Priority_High | High ticket priority | HIGH |  |  
| Priority_Highest | Highest ticket priority | HIGHEST |  |  
| Status_Open | Open ticket status | OPEN |  |  
| Status_Closed | Closed ticket status | CLOSED |  |  
| Warning_Locked | Commands are locked | Commands are currently locked! Please try again later |  |  
| Warning_NoPermission | user lacks permission | You do not have permission to perform this command! |  |  
| Warning_InvalidID | user inputted invalid ticket ID | Please enter a valid ticket ID! |  |  
| Warning_NAN | user entered something not a number | Please enter a valid number! |  |  
| Warning_VaultNotFound | Vault plugin was not found | Vault plugin not found! [plugin name] shutting down... |  |  
| Warning_NoConfig | Configuration file not found | Config file not found! New file generated. |  |  
| Warning_InvalidCommand | Command is not found | No such command exists or number of arguments is incorrect! |  |  
| Warning_PriorityOutOfBounds | User used a priority level not between 1 and 5 | Priority must be between 1 and 5! |  |  
| Warning_Under_Cooldown | user under cooldown | You need to wait a bit before trying to modify or create another ticket! |  |  
| Warning_TicketAlreadyClosed | Ticket already closed when user tried to close ticket | This ticket is already closed! |  |  
| Warning_TicketAlreadyOpen | Ticket already open when user tried to open ticket | This ticket is already open! |  |  
| Warning_InvalidDBType | Database type does not exist | Invalid database type! |  |  
| Warning_ConvertToSameDBType | User tried to convert database to current type | Unable to convert database to type already in use! |  |  
| Warning_UnexpectedError | Something unexpected happened | Unexpected error has occurred! |  |  
| Warning_LongTaskDuringReload | N/A | Long-standing task detected during reload attempt. Forcefully ending all other tasks! |  |  
| Stacktrace_Line1 | Header for when something goes wrong | WARNING! An unexpected error has occurred! |  |  
| Stacktrace_Line2 | Java Exception | Exception: [error Type] |  |  
| Stacktrace_Line3 | Information about the Exception | Information: [message] |  |  
| Stacktrace_Line4 | N/A | Modified Stacktrace |  |  
| Command_BaseCommand  | Base word for all commands | ticket |  |  
| Command_Assign | "Assign" in */ticket assign* | assign |  |  
| Command_SilentAssign | Silent command variation of */ticket assign* | s.assign |  |  
| Command_Claim | "Claim" in */ticket claim* | claim |  |  
| Command_SilentClaim | Silent command variation of */ticket claim* | s.claim |  |  
| Command_Close | "Close" in */ticket close* | close |  |  
| Command_SilentClose | Silent command variation of */ticket close* | s.close |  |	
| Command_CloseAll | "Close all" in */ticket closeall* | closeall |  |	
| Command_SilentCloseAll | Silent command variation of */ticket closeall* | s.closeall |  |  
| Command_Comment | "Comment" in */ticket comment* | comment |  |  
| Command_SilentComment | Silent command variation of */ticket comment* | s.comment |  |  
| Command_ConvertDB |"Convert database" in */ticket convertdatabase*  | convertdatabase |  |  
| Command_Create | "Create" in */ticket create* | create |  |  
| Command_Help | "Help " in */ticket help * | help |  |  
| Command_History | "History" in */ticket history* | history |  |  
| Command_List | "List" in */ticket list* | list |  |  
| Command_ListAssigned | "List assigned" in */ticket listassigned* | listassigned |  |  
| Command_ListUnassigned | "List unassigned" in */ticket listunassigned* | listunassigned |  |  
| Command_Reload | "Reload" in */ticket reload* | reload |  |  
| Command_Reopen | "Reopen" in */ticket reopen* | reopen |  |  
| Command_SilentReopen | Silent command variation of */ticket reopen* | s.reopen |  |  
| Command_Search | "Search" in */ticket search* | search |  |  
| Command_SetPriority | "Set priority" in */ticket setpriority* | setpriority |  |  
| Command_SilentSetPriority | Silent command variation of */ticket setpriority* | s.setpriority |  |  
| Command_Teleport | "Teleport" in */ticket teleport* | teleport |  |  
| Command_Unassign | "Unassign" in */ticket unassign* | unassign |  |  
| Command_SilentUnassign | Silent command variation of */ticket unassign* | s.unassign |  |  
| Command_Version | "Version" in */ticket version* | version |  |  
| Command_View | "View" in */ticket view* | view |  |  
| Command_DeepView | "View deep" in */ticket * | deepview |  |  
| ListFormat_Header: |  | Viewing All Open Tickets |  |  
| ListFormat_AssignedHeader |  | Viewing Open Tickets Assigned to You |  |  
| ListFormat_UnassignedHeader |  | Viewing Unassigned Open Tickets |  |  
| Click_ViewTicket |  | Click to view this ticket |  |  
| Click_Teleport |  | Click to teleport to this location |  |  
| Click_NextPage |  | Click to move forward a page |  |  
| Click_BackPage |  | Click to move back a page |  |  
| Click_GitHub_Wiki |  | Click to go to the wiki! |  |  
| Console_Name | Word for Console | Console |  |  
| Nobody | Word used when user un-assigns ticket from anyone. "This ticket is now assigned to "x"  | Nobody |  |  
| Page_Back | Back button to go back a page | Back |  |  
| Page_Of | Separates current page from total pages. "Page 4 of 8" | of |  |  
| Page_Next | Next button to go to the next page | Next |  |  
| Info_ReloadInitiated | user started plugin reload | [User] has initiated a plugin restart! Plugin locked and waiting for ongoing tasks to complete... |  |  
| Info_Reload_TasksDone |  | All other tasks complete! Reloading now... |  |  
| Info_ReloadSuccess | Plugin reload was successful | Reload successful |  |  
| Info_ReloadFailure | Plugin reload failed | Reload failure |  |  
| Info_DBUpdate | Database updating | Database updating to latest version! Plugin Locked! DO NOT TURN OFF SERVER! |  |  
| Info_DBUpdateComplete | Database update done |  Database update complete! Plugin unlocked! |  |  
| Info_DBConversionInit | Database conversion | Database conversion from [current type] to [target type]! Plugin locked! |  |  
| Info_DBConversionSuccess | Database conversion successful |  Database conversion complete! Plugin unlocked! Change config and reload plugin to use new database type. |  |  
| Search_AssignedTo | Searching for tickets assigned to this | assignedto |  |  
| Search_Creator | Searching for ticket with this creator | creator |  |  
| Search_Keywords | Searching for tickets with these keywords | keywords |  |  
| Search_Priority | Searching for tickets with this priority | priority |  |  
| Search_Status | Searching for tickets with this status | status |  |  
| Search_Time | Searching for tickets made before this time | time |  |  
| Search_World | Searching for tickets made in this world | world |  |  
| Search_Page | Searching for tickets with all other parameters, but on this page | page |  |  
| Search_ClosedBy | Searching for tickets closed by this user | closedby |  |  
| Search_LastClosedBy | Searching for tickets last closed by this user | lastclosedby |  |  
| Search_Time_Second | one-character used as a short replacement for "second" | s |  |  
| Search_Time_Minute | one-character used as a short replacement for "minute" | m |  |  
| Search_Time_Hour | one-character used as a short replacement for "hour" | h |  |  
| Search_Time_Day | one-character used as a short replacement for "day" | d |  |  
| Search_Time_Week | one-character used as a short replacement for "week" | w |  |  
| Search_Time_Week | one-character used as a short replacement for "month" | y |  |  
| Parameters_ID | Parameter for ticket ID | ID |  |  
| Parameters_Assignment | Parameter for who/what to assign the ticket to | Assignment |  |  
| Parameters_LowerID | Parameter for lower ticket ID in a mass-close | Lower ID |  |  
| Parameters_UpperID | Parameter for upper ticket ID in a mass-close | Upper ID |  |  
| Parameters_Comment | Parameter for a user's comment | Comment |  |  
| Parameters_Page | Parameter for page to go to | Page |  |  
| Parameters_Level | Parameter for priority level (1-5) | Level |  |  
| Parameters_User | Parameter for a username | User |  |  
| Parameters_Target_Database | Parameter for type of database to convert to | Target Database |  |  
| Parameters_Constraints | Parameter for "constraints" for */ticket search \<Constraints\>* | Constraints |  |  
| Notify_UnreadUpdate_Single | Singular ticket has an update for a user | Ticket [number] has an update! Type */ticket view \<ID\>* to clear notification. |  |  
| Notify_UnreadUpdate_Multi | Multiple tickets have an update for a user | Tickets [number] have updates! Type */ticket view \<ID\>* for all updated tickets to clear notifications.' |  |  
| Notify_OpenAssigned | Staff situation notification | [number] tickets open ([number] assigned to you) |  |  
| Notify_MassCloseSuccess | Mass close complete | Mass-close from #[number] to #[number] sent! |  |  
| Notify_TicketAssignSuccess |  |  Ticket #[number] assigned to: [assignment] |  |  
| Notify_TicketCloseSuccess |  | Ticket #[number] closed! |  |  
| Notify_TicketCloseWithCommentSuccess |  | Ticket #[number] closed with a comment! |  |  
| Notify_TicketCreationSuccessful |  | Ticket #[number] created! |  |  
| Notify_TicketCommentSuccessful |  | Ticket #[number] commented on! |  |  
| Notify_TicketReopenSuccess |  | Ticket #[number] re-opened! |  |  
| Notify_TicketSetPrioritySuccess |  | Ticket #[number] priority changed! |  |  
| Notify_Event_TicketAssign |  | [User] assigned ticket #[number] to [assignment]. |  |  
| Notify_Event_TicketClose |  | [User] closed ticket #[number]. |  |  
| Notify_Event_TicketCloseWithComment |  | [User] closed ticket #[number] with a comment: [message] |  |  
| Notify_Event_MassClose |  | [User] is mass-closing tickets from #[number] to #[number]/ |  |  
| Notify_Event_TicketComment |  | [User] commented on ticket #[number]: [comment] |  |  
| Notify_Event_TicketCreation |  | [User] created ticket #[number]: [message] |  |  
| Notify_Event_TicketModification |  | Ticket #[number] has been updated! Type */ticket view [number]* to view this ticket. |  |  
| Notify_Event_TicketReopen |  | [User] reopened ticket #[number]. |  |  
| Notify_Event_SetPriority |  | [User] set ticket #[number]. to priority [priority] |  |  
| Notify_Event_PluginUpdate |  | TicketManager has an update! Current version [version]  Latest Version: [version] |  |  
| Discord_OnAssign |  | [User] assigned ticket #[number] |  |  
| Discord_OnClose |  | [User] closed ticket #[number] |  |  
| Discord_OnCloseAll |  | [User] mass-closed tickets: [number] to [Number] |  |  
| Discord_OnComment |  | [User] commented on ticket #[number] |  |  
| Discord_OnCreate |  | [User] created ticket #[number] |  |  
| Discord_OnReopen |  | [User] reopened ticket #[number] |  |  
| Discord_OnPriorityChange |  | [User] changed priority of ticket #[number] |  |  

## Config File
Extra Requirements:
* Basic YML Knowledge
### Instructions:
* Grab a copy of the latest config file.
* Translate any comments to your native language.
* **DO NOT** translate the YML keys
* **DO NOT** translate the YML values
* When you find the pattern: "*# Values: ...*", provide a mapping from the native language to what the config file expects. Basically tell the user that when they want x, they should provide y. Config reads exact values.
## Wiki Page
Extra Requirements:
* Markdown knowledge
### Instructions:
* Grab the markdown code for the page you wish to translate.
* DO NOT translate permission nodes! These are not localized. You are welcome to provide a mapping for what they are looking for to what they need.
