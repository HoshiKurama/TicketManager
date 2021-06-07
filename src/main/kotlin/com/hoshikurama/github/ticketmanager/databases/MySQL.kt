package com.hoshikurama.github.ticketmanager.databases

import com.hoshikurama.github.ticketmanager.*
import com.hoshikurama.github.ticketmanager.ticket.Ticket
import com.hoshikurama.github.ticketmanager.ticket.toBukkitLocationOrNull
import kotliquery.*
import org.bukkit.Bukkit
import java.sql.Statement
import java.time.Instant
import java.util.*

internal class MySQL(
    host: String,
    port: String,
    dbName: String,
    username: String,
    password: String,
) : Database {
    override val type = Database.Types.MySQL
    private val dataSource = HikariCP.default("jdbc:mysql://$host:$port/$dbName?serverTimezone=UTC", username, password)
    private val toBukkitLocOrNull: String.() -> org.bukkit.Location? = {
        this.split(" ")
            .let { Ticket.Location(
                world = it[0],
                x = it[1].toInt(),
                y = it[2].toInt(),
                z = it[3].toInt())
            }
            .toBukkitLocationOrNull()
    }

    init {
        createDatabasesIfNeeded()
    }

    override fun getAssignment(ticketID: Int): String? {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT ASSIGNED_TO FROM TicketManager_V4_Tickets WHERE ID = $ticketID;")
                .map { it.stringOrNull(1) }
                .asSingle
            )
        }
    }

    override fun getCreatorUUID(ticketID: Int): UUID? {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT CREATOR_UUID FROM TicketManager_V4_Tickets WHERE ID = $ticketID;")
                .map { it.stringOrNull(1)?.run(UUID::fromString) }
                .asSingle
            )
        }
    }

    override fun getLocation(ticketID: Int): org.bukkit.Location? {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT LOCATION FROM TicketManager_V4_Tickets WHERE ID = $ticketID;")
                .map { it.stringOrNull(1)?.toBukkitLocOrNull() }
                .asSingle
            )
        }
    }

    override fun getPriority(ticketID: Int): Ticket.Priority {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT PRIORITY FROM TicketManager_V4_Tickets WHERE ID = $ticketID;")
                .map { byteToPriority(it.byte(1)) }
                .asSingle
            )!!
        }
    }

    override fun getStatus(ticketID: Int): Ticket.Status {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT STATUS FROM TicketManager_V4_Tickets WHERE ID = $ticketID;")
                .map { Ticket.Status.valueOf(it.string(1)) }
                .asSingle
            )!!
        }
    }

    override fun getStatusUpdateForCreator(ticketID: Int): Boolean {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT STATUS_UPDATE_FOR_CREATOR FROM TicketManager_V4_Tickets WHERE ID = $ticketID;")
                .map { it.boolean(1) }
                .asSingle
            )!!
        }
    }

    override fun setAssignment(ticketID: Int, assignment: String?) {
        using(sessionOf(dataSource)) {
            it.run(queryOf("UPDATE TicketManager_V4_Tickets SET ASSIGNED_TO = ? WHERE ID = $ticketID;", assignment).asUpdate)
        }
    }

    override fun setPriority(ticketID: Int, priority: Ticket.Priority) {
        using(sessionOf(dataSource)) {
            it.run(queryOf("UPDATE TicketManager_V4_Tickets SET PRIORITY = ? WHERE ID = $ticketID;", priority.level).asUpdate)
        }
    }

    override fun setStatus(ticketID: Int, status: Ticket.Status) {
        using(sessionOf(dataSource)) {
            it.run(queryOf("UPDATE TicketManager_V4_Tickets SET STATUS = ? WHERE ID = $ticketID;", status.name).asUpdate)
        }
    }

    override fun setStatusUpdateForCreator(ticketID: Int, status: Boolean) {
        using(sessionOf(dataSource)) {
            it.run(queryOf("UPDATE TicketManager_V4_Tickets SET STATUS_UPDATE_FOR_CREATOR = ? WHERE ID = $ticketID;", status).asUpdate)
        }
    }

    // More Specific Ticket Actions

    override fun addAction(ticketID: Int, action: Ticket.Action) {
        using(sessionOf(dataSource)) { writeAction(action, ticketID, it) }
    }

    override fun addTicket(ticket: Ticket, action: Ticket.Action): Int {
        return using(sessionOf(dataSource)) {
            val id = writeTicket(ticket, it)
            writeAction(action, id!!.toInt(), it)
            return@using id.toInt()
        }
    }

    override fun getOpen(): List<Ticket> {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT * FROM TicketManager_V4_Tickets WHERE STATUS = ?;", Ticket.Status.OPEN.toString())
                .map { it.toTicket(session) }
                .asList)
        }.sortedWith(sortForList)
    }

    override fun getOpenAssigned(assignment: String, groupAssignment: List<String>) =
        getOpen().filter { it.assignedTo == assignment || it.assignedTo in groupAssignment }

    override fun getTicket(ID: Int): Ticket? {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT * FROM TicketManager_V4_Tickets WHERE ID = $ID;")
                .map { row -> row.toTicket(session) }
                .asSingle)
        }
    }

    override fun getTicketIDsWithUpdates(): List<Pair<UUID, Int>> {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT CREATOR_UUID, ID FROM TicketManager_V4_Tickets WHERE STATUS_UPDATE_FOR_CREATOR = ?;", true)
                .map { Pair( first = UUID.fromString(it.string(1)), second = it.int(2)) }
                .asList)
        }
    }


    override fun getTicketIDsWithUpdates(uuid: UUID): List<Int> {
        return using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT ID FROM TicketManager_V4_Tickets WHERE STATUS_UPDATE_FOR_CREATOR = ? AND CREATOR_UUID = ?;", true, uuid)
                .map { it.int(1) }
                .asList
            )
        }
    }

    override fun isValidID(ticketID: Int): Boolean {
        val result = using(sessionOf(dataSource)) { session ->
            session.run(queryOf("SELECT ID FROM TicketManager_V4_Tickets WHERE ID = $ticketID;")
                .map { true }
                .asSingle
            )
        }
        return result ?: false
    }

    override fun massCloseTickets(lowerBound: Int, upperBound: Int, uuid: UUID?) {
        using(sessionOf(dataSource)) { session ->
            val idStatusPairs = session.run(queryOf("SELECT ID, STATUS FROM TicketManager_V4_Tickets WHERE ID BETWEEN $lowerBound AND $upperBound;")
                .map { it.int(1) to Ticket.Status.valueOf(it.string(2)) }
                .asList
            )

            idStatusPairs.asSequence()
                .filter { it.second == Ticket.Status.OPEN }
                .forEach {
                    writeAction(
                        Ticket.Action(
                            type = Ticket.Action.Type.MASS_CLOSE,
                            user = uuid,
                            message = null,
                            timestamp = Instant.now().epochSecond
                        ),
                        ticketID = it.first,
                        session = session
                    )
                }

            val idString = idStatusPairs.map { it.first }.joinToString(", ")
            session.run(queryOf("UPDATE TicketManager_V4_Tickets SET STATUS = ? WHERE ID IN ($idString);", Ticket.Status.CLOSED.name).asUpdate)
        }
    }

    override fun searchDB(params: Map<String, String>): List<Ticket> {
        if (params.isEmpty()) return emptyList()

        fun toNullable(s: String) = s.takeIf { it != "NULL" }

        // Builds map of functions to apply to ticket
        val functions = params.toList()
            .mapNotNull {
                when (it.first) {
                    "assignedto" -> { t: Ticket -> t.assignedTo == toNullable(it.second) }
                    "creator" -> { t: Ticket -> t.creatorUUID?.toString() == toNullable(it.second) }
                    "priority" -> { t: Ticket -> t.priority.level == it.second.toByte() }
                    "status" -> { t: Ticket -> t.status.name == it.second }
                    "time" -> { t: Ticket -> t.actions[0].timestamp >= it.second.toLong() }
                    "world" -> { t: Ticket -> t.location?.world?.equals(it.second) ?: false }
                    "keywords" -> { t: Ticket ->
                        it.second
                            .split(",")
                            .map { s ->
                                t.actions.asSequence()
                                    .filter { a ->  a.type == Ticket.Action.Type.OPEN || a.type == Ticket.Action.Type.COMMENT }
                                    .any { a -> a.message?.lowercase()?.contains(s.lowercase()) ?: false }
                            }
                            .all { results -> results }
                    }

                    else -> null
                }
            }

        val ticketMatchesSearch =  { ticket: Ticket -> functions.asSequence().map { it.invoke(ticket) }.all { it } }

        val tickets = mutableListOf<Ticket>()
        using(sessionOf(dataSource)) { session ->
            session.forEach(queryOf("SELECT * FROM TicketManager_V4_Tickets")) { row ->
                row.toTicket(session).apply { if (ticketMatchesSearch(this)) tickets.add(this) }
            }
        }

        return tickets
    }


    override fun closeDatabase() {
        dataSource.close()
    }

    override fun createDatabasesIfNeeded() {
        using(sessionOf(dataSource)) {
            if (!tableExists("TicketManager_V4_Tickets", it))
                it.run(queryOf("""
                    CREATE TABLE TicketManager_V4_Tickets (
                    ID INT NOT NULL AUTO_INCREMENT,
                    CREATOR_UUID VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_general_ci,
                    PRIORITY TINYINT NOT NULL,
                    STATUS VARCHAR(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
                    ASSIGNED_TO VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                    STATUS_UPDATE_FOR_CREATOR BOOLEAN NOT NULL,
                    LOCATION VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                    KEY STATUS_V4 (STATUS) USING BTREE,
                    KEY STATUS_UPDATE_FOR_CREATOR_V4 (STATUS_UPDATE_FOR_CREATOR) USING BTREE,
                    PRIMARY KEY (ID)
                    ) ENGINE=InnoDB;""".trimIndent()
                ).asExecute)
            if (!tableExists("TicketManager_V4_Actions", it))
                it.run(queryOf("""
                    CREATE TABLE TicketManager_V4_Actions (
                    ACTION_ID INT NOT NULL AUTO_INCREMENT,
                    TICKET_ID INT NOT NULL,
                    ACTION_TYPE VARCHAR(20) CHARACTER SET latin1 COLLATE latin1_general_ci NOT NULL,
                    CREATOR_UUID VARCHAR(36) CHARACTER SET latin1 COLLATE latin1_general_ci,
                    MESSAGE TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
                    TIMESTAMP BIGINT NOT NULL,
                    PRIMARY KEY (ACTION_ID)
                    ) ENGINE=InnoDB;""".trimIndent()
                ).asExecute)
        }
    }

    override fun updateNeeded(): Boolean {
        return using(sessionOf(dataSource)) {
            tableExists("TicketManagerTicketsV2", it)
        }
    }

    override fun updateDatabase() {
        pushMassNotify("ticketmanager.notify.info", { it.informationDBUpdate } )

        fun playerNameToUUIDOrNull(name: String) = Bukkit.getOfflinePlayers()
            .asSequence()
            .filter { it.name == name }
            .map { it.uniqueId }
            .firstOrNull()

        using(sessionOf(dataSource)) { session ->
            session.forEach(queryOf("SELECT * FROM TicketManagerTicketsV2;")) { row ->
                val ticket = Ticket(
                    id = row.int(1),
                    priority = byteToPriority(row.byte(3)),
                    statusUpdateForCreator = row.boolean(10),
                    status = Ticket.Status.valueOf(row.string(2)),
                    assignedTo = row.stringOrNull(6),

                    creatorUUID = row.string(5).let {
                        if (it.lowercase() == "console") null
                        else UUID.fromString(it)
                    },
                    location = row.string(7).run {
                        if (equals("NoLocation")) null
                        else Ticket.Location(split(" "))
                    },
                    actions = row.string(9)
                        .split("/MySQLNewLine/")
                        .filter { it.isNotBlank() }
                        .map { it.split("/MySQLSep/") }
                        .mapIndexed { index, action ->
                            Ticket.Action(
                                message = action[1],
                                type = if (index == 0) Ticket.Action.Type.OPEN else Ticket.Action.Type.COMMENT,
                                timestamp = row.long(8),
                                user =
                                if (action[0].lowercase() == "console") null
                                else playerNameToUUIDOrNull(action[0])
                            )
                        }
                )
                val id = writeTicket(ticket, session)
                ticket.actions.forEach { writeAction(it, id!!.toInt(), session) }
            }

            session.run(queryOf("DROP INDEX STATUS ON TicketManagerTicketsV2;").asExecute)
            session.run(queryOf("DROP INDEX UPDATEDBYOTHERUSER ON TicketManagerTicketsV2;").asExecute)
            session.run(queryOf("DROP TABLE TicketManagerTicketsV2;").asUpdate)

            pushMassNotify("ticketmanager.notify.info",  { it.informationDBUpdateComplete } )
        }
    }

    override fun migrateDatabase(targetType: Database.Types) {
        mainPlugin.pluginLocked = true

        when (targetType) {
            Database.Types.MySQL -> {} // MySQL -> MySQL not permitted!

            Database.Types.SQLite -> {
                pushMassNotify("ticketmanager.notify.info", {
                    it.informationDBConvertInit
                        .replace("%fromDB%", Database.Types.MySQL.name)
                        .replace("%toDB%", Database.Types.SQLite.name)
                })

                try {
                    val sqLite = SQLite()

                    // Writes to SQLite
                    using(sessionOf(dataSource)) { session ->
                        session.forEach(queryOf("SELECT * FROM TicketManager_V4_Tickets")) { row ->  //NOTE: During conversion, ticket ID is not guaranteed to be preserved
                            row.toTicket(session).apply {
                                val newID = sqLite.addTicket(this, actions[0])
                                if (actions.size > 1) actions.subList(1, actions.size)
                                    .forEach { sqLite.addAction(newID, it) }
                            }
                        }
                    }

                    pushMassNotify("ticketmanager.notify.info", { it.informationDBConvertSuccess } )

                } catch (e: Exception) {
                    e.printStackTrace()
                    postModifiedStacktrace(e)
                }
            }
        }

        mainPlugin.pluginLocked = false
    }

    private fun writeTicket(ticket: Ticket, session: Session): Long? {
        val stmt = session.connection.underlying.prepareStatement(
            "INSERT INTO TicketManager_V4_Tickets (CREATOR_UUID, PRIORITY, STATUS, ASSIGNED_TO, STATUS_UPDATE_FOR_CREATOR, LOCATION) VALUES (?,?,?,?,?,?);",
            Statement.RETURN_GENERATED_KEYS)
        stmt.setString(1, ticket.creatorUUID?.toString())
        stmt.setByte(2, ticket.priority.level)
        stmt.setString(3, ticket.status.name)
        stmt.setString(4, ticket.assignedTo)
        stmt.setBoolean(5, ticket.statusUpdateForCreator)
        stmt.setString(6, ticket.location?.toString())
        stmt.executeUpdate()

        val rs = stmt.generatedKeys
        rs.next()
        return rs.getLong(1)


/*
        return session.run(queryOf("INSERT INTO TicketManager_V4_Tickets (CREATOR_UUID, PRIORITY, STATUS, ASSIGNED_TO, STATUS_UPDATE_FOR_CREATOR, LOCATION) VALUES (?,?,?,?,?,?);",
            ticket.creatorUUID,
            ticket.priority.level,
            ticket.status.name,
            ticket.assignedTo,
            ticket.statusUpdateForCreator,
            ticket.location?.toString()
        ).asUpdateAndReturnGeneratedKey)

 */
    }

    private fun writeAction(action: Ticket.Action, ticketID: Int, session: Session) {
        session.run(queryOf("INSERT INTO TicketManager_V4_Actions (TICKET_ID,ACTION_TYPE,CREATOR_UUID,MESSAGE,TIMESTAMP) VALUES (?,?,?,?,?);",
            ticketID,
            action.type.name,
            action.user?.toString(),
            action.message,
            action.timestamp
        ).asExecute)
    }

    private fun Row.toTicket(session: Session): Ticket {
        val id = int(1)
        return Ticket(
            id = id,
            creatorUUID = stringOrNull(2)?.let(UUID::fromString),
            priority = byteToPriority(byte(3)),
            status = Ticket.Status.valueOf(string(4)),
            assignedTo = stringOrNull(5),
            statusUpdateForCreator = boolean(6),
            location = stringOrNull(7)?.split(" ")?.let {
                Ticket.Location(
                    world = it[0],
                    x = it[1].toInt(),
                    y = it[2].toInt(),
                    z = it[3].toInt()
                )
            },
            actions = session.run(queryOf("SELECT ACTION_ID, ACTION_TYPE, CREATOR_UUID, MESSAGE, TIMESTAMP FROM TicketManager_V4_Actions WHERE TICKET_ID = $id;")
                .map { row ->
                    row.int(1) to Ticket.Action(
                        type = Ticket.Action.Type.valueOf(row.string(2)),
                        user = row.stringOrNull(3)?.let { UUID.fromString(it) },
                        message = row.stringOrNull(4),
                        timestamp = row.long(5)
                    )
                }.asList
            )
                .sortedBy { it.first }
                .map { it.second }
        )
    }

    private fun tableExists(table: String, session: Session): Boolean {
        return using(session.connection.underlying.metaData.getTables(null, null, table, null)) {
            while (it.next())
                if (it.getString("TABLE_NAME")?.equals(table) == true) return@using true
            return@using false
        }
    }
}