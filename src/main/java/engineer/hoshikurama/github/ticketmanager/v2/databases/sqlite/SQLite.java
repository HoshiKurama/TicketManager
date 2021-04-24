package engineer.hoshikurama.github.ticketmanager.v2.databases.sqlite;

import engineer.hoshikurama.github.ticketmanager.v2.DatabaseException;
import engineer.hoshikurama.github.ticketmanager.v2.TMInvalidDataException;
import engineer.hoshikurama.github.ticketmanager.v2.Ticket;
import engineer.hoshikurama.github.ticketmanager.v2.TicketManager;
import engineer.hoshikurama.github.ticketmanager.v2.databases.Database;
import engineer.hoshikurama.github.ticketmanager.v2.databases.mysql.MySQL;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static engineer.hoshikurama.github.ticketmanager.v2.TMCommands.convertRelTimeToEpochSecond;
import static engineer.hoshikurama.github.ticketmanager.v2.TMCommands.withColourCode;

public class SQLite implements Database {
    private final String url;

    public SQLite(TicketManager instance) throws DatabaseException {
        try {
            url = "jdbc:sqlite:" + instance.getDataFolder().getAbsolutePath() + "/TicketManager.db";
            Connection connection = connection();
            connection.close();

            createTableIfMissing();
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e.getCause());
        }

    }

    @Override
    public Ticket getTicket(int ID) throws DatabaseException, TMInvalidDataException {
        try (Connection connection = connection()) {
            PreparedStatement stmt = connection.prepareStatement("SELECT * FROM TicketManagerTicketsV2 WHERE ID = ?");
            stmt.setInt(1, ID);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) return parseTicket(rs.getInt(1), rs.getString(2), rs.getByte(3), rs.getString(4),
                    rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8), rs.getString(9),
                    rs.getBoolean(10));
            else throw new TMInvalidDataException("This is an invalid Ticket ID!");
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public void createTicket(Ticket ticket) throws DatabaseException {
        try (Connection connection = connection()) {
            PreparedStatement stmt = connection.prepareStatement("INSERT INTO TicketManagerTicketsV2 VALUES (?,?,?,?,?,?,?,?,?,?)");
            stmt.setInt(1, ticket.getId());
            stmt.setString(2, ticket.getStatus());
            stmt.setByte(3, ticket.getPriority());
            stmt.setString(4, ticket.getCreator());
            stmt.setString(5,ticket.getStringUUIDForMYSQL());
            stmt.setString(6, ticket.getAssignment());
            stmt.setLong(8,ticket.getCreationTime());
            stmt.setBoolean(10, ticket.wasUpdatedByOtherUser());

            // Handles Comment storage
            StringBuilder stringBuilder = new StringBuilder();
            for (Ticket.Comment c : ticket.getComments()) stringBuilder.append(c.user).append("/MySQLSep/").append(c.comment).append("/MySQLNewLine/");
            stmt.setString(9, stringBuilder.toString());

            // Handles location storage
            stmt.setString(7,ticket.getLocation().isPresent() ? ticket.getLocation().get().worldName + " " +
                    ticket.getLocation().get().x + " " + ticket.getLocation().get().y + " " + ticket.getLocation().get().z : "NoLocation");
            stmt.execute();
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public void updateTicket(Ticket ticket) throws DatabaseException {
        try (Connection connection = connection()) {
            PreparedStatement stmt = connection.prepareStatement("UPDATE TicketManagerTicketsV2 SET STATUS = ?, PRIORITY = ?, CREATOR = ?, UUID = ?, ASSIGNMENT = ?, LOCATION = ?, CREATIONTIME = ?, COMMENTS = ?, UPDATEDBYOTHERUSER = ? WHERE ID = ?");
            stmt.setString(1, ticket.getStatus());
            stmt.setByte(2, ticket.getPriority());
            stmt.setString(3, ticket.getCreator());
            stmt.setString(4, ticket.getStringUUIDForMYSQL());
            stmt.setString(5, ticket.getAssignment());
            stmt.setLong(7, ticket.getCreationTime());
            stmt.setBoolean(9, ticket.wasUpdatedByOtherUser());
            stmt.setInt(10, ticket.getId());

            // Handles location storage
            stmt.setString(6, ticket.getLocation().isPresent() ? ticket.getLocation().get().worldName + " " +
                    ticket.getLocation().get().x + " " + ticket.getLocation().get().y + " " + ticket.getLocation().get().z : "NoLocation");

            // Handles Comment storage
            StringBuilder stringBuilder = new StringBuilder();
            for (Ticket.Comment c : ticket.getComments()) stringBuilder.append(c.user).append("/MySQLSep/").append(c.comment).append("/MySQLNewLine/");
            stmt.setString(8, stringBuilder.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public int getNextTicketID() throws DatabaseException {
        try (Connection connection = connection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT MAX(ID) FROM TicketManagerTicketsV2");

            if (rs.next()) return (rs.getInt(1) + 1);
            else return 1;
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public List<Ticket> getOpenTickets() throws DatabaseException {
        try (Connection connection = connection()) {
            PreparedStatement stmt = connection.prepareStatement("SELECT * FROM TicketManagerTicketsV2 WHERE STATUS = ?");
            stmt.setString(1,"OPEN");
            return getTicketsFromRS(stmt.executeQuery());
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public List<Ticket> getTicketsWithUUID(UUID uuid) throws DatabaseException {
        String uuidString = uuid == null ? "CONSOLE" : uuid.toString();

        try (Connection connection = connection()) {
            PreparedStatement stmt = connection.prepareStatement("SELECT * FROM TicketManagerTicketsV2 WHERE UUID = ?");
            stmt.setString(1, uuidString);
            return getTicketsFromRS(stmt.executeQuery());
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public List<Ticket> getUnreadTickets() throws DatabaseException {
        try (Connection connection = connection()) {
            PreparedStatement stmt = connection.prepareStatement("SELECT * FROM TicketManagerTicketsV2 WHERE UPDATEDBYOTHERUSER = ?");
            stmt.setBoolean(1,true);
            return getTicketsFromRS(stmt.executeQuery());
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public void createTableIfMissing() throws DatabaseException {
        try (Connection connection = connection()) {
            Statement stmt = connection.createStatement();

            if (tableDoesNotExist(connection)) {
                stmt.execute("CREATE TABLE TicketManagerTicketsV2 (" +
                        "ID INT PRIMARY KEY," +
                        "STATUS VARCHAR(10) NOT NULL," +
                        "PRIORITY TINYINT NOT NULL," +
                        "CREATOR VARCHAR(50) NOT NULL," +
                        "UUID VARCHAR(36) NOT NULL," +
                        "ASSIGNMENT VARCHAR(255) NOT NULL," +
                        "LOCATION VARCHAR(100) NOT NULL," +
                        "CREATIONTIME BIGINT NOT NULL," +
                        "COMMENTS TEXT NOT NULL," +
                        "UPDATEDBYOTHERUSER BOOLEAN NOT NULL)");
                stmt.execute("CREATE INDEX STATUS ON TicketManagerTicketsV2 (STATUS)");
                stmt.execute("CREATE INDEX UPDATEDBYOTHERUSER ON TicketManagerTicketsV2 (UPDATEDBYOTHERUSER)");
            }
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public void massCloseTickets(int lowerID, int upperID) throws DatabaseException {
        try (Connection connection = connection()) {
            PreparedStatement stmt = connection.prepareStatement("UPDATE TicketManagerTicketsV2 SET STATUS = ? WHERE ID BETWEEN ? AND ?");
            stmt.setString(1, "CLOSED");
            stmt.setInt(2, lowerID);
            stmt.setInt(3, upperID);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public List<Ticket> dbSearchCommand(CommandSender sender, List<String> searches, AtomicInteger atomicPage) throws DatabaseException {
        try (Connection connection = connection()) {
            StringBuilder sqlStatement = new StringBuilder("SELECT * FROM TicketManagerTicketsV2 WHERE ");
            List<String> arguments = new ArrayList<>();
            searches.remove(0);

            //Adds page value if not present
            searches.stream().filter(str -> str.contains("-page:")).findFirst().ifPresentOrElse(String::length, () -> searches.add("-page:1"));
            sender.sendMessage(withColourCode("&3[TicketManager] Processing query..."));

            // Fills argtypes, arguments, and creates PreparedStatement String
            List<String> argTypes = searches.stream()
                    .map(str -> str.split(":"))
                    .map(arg -> {
                        switch (arg[0]) {
                            case "status":      //status:OPEN/CLOSED
                                sqlStatement.append("STATUS = ? AND ");
                                arguments.add(arg[1]);
                                return arg[0];
                            case "time":        //time:1y1w1d1h1m1s
                                sqlStatement.append("CREATIONTIME >= ? AND ");
                                arguments.add(arg[1]);
                                return arg[0];
                            case "creator":     //creator:username
                                sqlStatement.append("CREATOR = ? AND ");
                                arguments.add(arg[1]);
                                return arg[0];
                            case "priority":    //priority:1-5
                                sqlStatement.append("PRIORITY = ? AND ");
                                arguments.add(arg[1]);
                                return arg[0];
                            case "assignedto":  //assignedto:name
                                sqlStatement.append("ASSIGNMENT = ? AND ");
                                arguments.add(arg[1].equals("null") ? " " : arg[1]);
                                return arg[0];
                            case "world":       //world:world
                                sqlStatement.append("LOCATION LIKE ? AND ");
                                arguments.add(arg[1]);
                                return arg[0];
                            case "keywords":    //keywords:This,is,how,you,do,it
                                StringBuilder keywordsBuilder = new StringBuilder();
                                for (String ignored : arg[1].split(","))
                                    keywordsBuilder.append("COMMENTS LIKE ? AND ");
                                keywordsBuilder.delete(keywordsBuilder.length() - 5, keywordsBuilder.length());
                                sqlStatement.append(keywordsBuilder.toString()).append(" AND ");
                                arguments.add(arg[1] + " ");
                                return arg[0];
                            case "-page":
                                atomicPage.set(Integer.parseInt(arg[1]));
                                return null;
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // Creates final SQL Statement
            sqlStatement.delete(sqlStatement.length() - 5, sqlStatement.length()); //Gets rid of trailing AND
            PreparedStatement stmt = connection.prepareStatement(sqlStatement.toString());

            // Sets correct statement values
            int curIndex = 1;
            for (int listIndex = 0; listIndex < argTypes.size(); listIndex++) {
                switch (argTypes.get(listIndex)) {
                    case "status":
                    case "creator":
                    case "assignedto":
                        stmt.setString(curIndex, arguments.get(listIndex));
                        curIndex++;
                        break;
                    case "world":
                        stmt.setString(curIndex, arguments.get(listIndex) + "%");
                        curIndex++;
                        break;
                    case "time":
                        stmt.setLong(curIndex, convertRelTimeToEpochSecond(arguments.get(listIndex)));
                        curIndex++;
                        break;
                    case "priority":
                        stmt.setByte(curIndex, Byte.parseByte(arguments.get(listIndex)));
                        curIndex++;
                        break;
                    case "keywords":
                        for (String term : arguments.get(listIndex).split(",")) {
                            stmt.setString(curIndex, "%" + term + "%");
                            curIndex++;
                        }
                        break;
                }
            }
            // Grabs search results and collects to list
            return getTicketsFromRS(stmt.executeQuery());
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public List<Ticket> getUnreadTicketsForPlayer(Player player) throws DatabaseException {
        try (Connection connection = connection()) {
            PreparedStatement stmt = connection.prepareStatement("SELECT * FROM TicketManagerTicketsV2 WHERE UPDATEDBYOTHERUSER = ? AND UUID = ?");
            stmt.setBoolean(1, true);
            stmt.setString(2, player.getUniqueId().toString());
            return getTicketsFromRS(stmt.executeQuery());
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e.getCause());
        }
    }

    @Override
    public boolean conversionIsRequired() {
        return false;
    }

    @Override
    public void initiateConversionProcess() {

    }

    private Connection connection() throws DatabaseException {
        try {
            return DriverManager.getConnection(url);
        } catch (Exception e) {
            throw new DatabaseException(e.getMessage(), e.getCause());
        }
    }

    private boolean tableDoesNotExist(Connection connection) throws SQLException {
        boolean tExists = false;
        try (ResultSet rs = connection.getMetaData().getTables(null, null, "TicketManagerTicketsV2", null)) {
            while (rs.next()) {
                String tName = rs.getString("TABLE_NAME");
                if (tName != null && tName.equals("TicketManagerTicketsV2")) {
                    tExists = true;
                    break;
                }
            }
        }
        return !tExists;
    }

    private Ticket parseTicket(int id, String status, byte priority, String creator, String uuidString, String assignment,
                               String rawLocation, long creationTime, String rawComments, boolean wasUpdatedByOtherUser) {
        // Processes location data
        Ticket.Location location;
        if (!rawLocation.equals("NoLocation")) {
            String[] split = rawLocation.split(" ");
            location = new Ticket.Location(split[0], split[1], split[2], split[3]);
        } else location = null;

        // Processes UUID
        UUID uuid;
        if (uuidString.equals("CONSOLE")) uuid = null;
        else uuid = UUID.fromString(uuidString);

        // Creates ticket
        Ticket ticket = new Ticket(id, status, priority, creator, uuid, assignment, location, creationTime, wasUpdatedByOtherUser);

        // Adds comment data to ticket object
        for (String commentLines : rawComments.split("/MySQLNewLine/")) {
            String[] components = commentLines.split("/MySQLSep/");
            ticket.addComment(components[0], components[1]);
        }
        return ticket;
    }

    private List<Ticket> getTicketsFromRS(ResultSet rs) throws SQLException {
        List<Ticket> tickets = new ArrayList<>();
        while (rs.next()) tickets.add(parseTicket(rs.getInt(1), rs.getString(2), rs.getByte(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8), rs.getString(9),
                rs.getBoolean(10)));
        return tickets;
    }

    public void convertToMySQL(MySQL mySQL) throws DatabaseException {
        try (Connection connection = connection()) {
            //Wipe destination
            mySQL.wipeTable();
            // Write to destination
            PreparedStatement stmt = connection.prepareStatement("SELECT * FROM TicketManagerTicketsV2");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                mySQL.createTicket(parseTicket(rs.getInt(1), rs.getString(2), rs.getByte(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8), rs.getString(9),
                        rs.getBoolean(10)));
            }
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e.getCause());
        }
    }

    public void wipeTable() throws DatabaseException {
        try (Connection connection = connection()) {
            connection.prepareStatement("DELETE FROM TicketManagerTicketsV2").execute();
        } catch (SQLException e) {
            throw new DatabaseException(e.getMessage(), e.getCause());
        }
    }
}
