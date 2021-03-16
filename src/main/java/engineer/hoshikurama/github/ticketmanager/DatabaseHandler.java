package engineer.hoshikurama.github.ticketmanager;

import com.google.gson.Gson;

import java.nio.ByteBuffer;
import java.sql.*;
import java.util.*;

class DatabaseHandler {

    // TicketManagerTickets methods

    static Optional<Ticket> getTicket(int ID) throws SQLException {
        try (Connection connection = Hikari.getConnection()) {

            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT JSON FROM TicketManagerTicketsV1 WHERE ID = " + ID);

            if (rs.next()) return Optional.of(new Gson().fromJson(rs.getString(1), Ticket.class));
            else return Optional.empty();
        }
    }

    static void updateTicket(Ticket ticket) throws SQLException {
        try (Connection connection = Hikari.getConnection()) {
            String JSONString = new Gson().toJson(ticket);
            PreparedStatement stmt = connection.prepareStatement("UPDATE TicketManagerTicketsV1 SET JSON = ? WHERE ID = ?");
            stmt.setString(1, JSONString);
            stmt.setInt(2, ticket.getId());
            stmt.executeUpdate();
        }
    }

    static int getNextOpenTicketNumber() throws SQLException {
        try (Connection connection = Hikari.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT MAX(ID) FROM TicketManagerTicketsV1");

            if (rs.next()) return (rs.getInt(1) + 1);
            else return 1;
        }
    }

    static Set<Ticket> getAllTicketsWithUUID(UUID uuid) throws SQLException {
        String encodedString = uuidToBase64(uuid);

        try (Connection connection = Hikari.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT JSON FROM TicketManagerTicketsV1 WHERE ENCODEDUUID = '" + encodedString + "'");

            HashSet<Ticket> tickets = new HashSet<>();
            while (rs.next()) tickets.add(new Gson().fromJson(rs.getString(1), Ticket.class));
            return tickets;
        }
    }

    static void addTicket(Ticket ticket) throws SQLException {
        String JSON = new Gson().toJson(ticket);
        String encodedUUID = uuidToBase64(ticket.getUUID().isPresent() ? ticket.getUUID().get() : null);
        try (Connection connection = Hikari.getConnection()) {
            PreparedStatement stmt = connection.prepareStatement("INSERT INTO TicketManagerTicketsV1 VALUES (?,?,?)");
            stmt.setInt(1,ticket.getId());
            stmt.setString(2,JSON);
            stmt.setString(3, encodedUUID);
            stmt.executeUpdate();
        }
    }


    // TicketManagerOpenTickets methods

    static Optional<Set<Ticket>> getOpenTickets() throws SQLException{
        try (Connection connection = Hikari.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT ID FROM TicketManagerOpenTicketsV1");

            HashSet<Integer> openTicketIDs = new HashSet<>();
            while (rs.next()) openTicketIDs.add(rs.getInt(1));
            if (openTicketIDs.size() == 0) return Optional.empty();

            HashSet<Ticket> openTickets = new HashSet<>(openTicketIDs.size());
            for (int id : openTicketIDs) getTicket(id).ifPresent(openTickets::add);
            return Optional.of(openTickets);
        }
    }

    static void addToOpenTickets(int id) throws SQLException {
        executeSQLUpdate("INSERT INTO TicketManagerOpenTicketsV1 VALUES (" + id + ")");
    }

    static void removeFromOpenTickets(int id) throws SQLException {
        executeSQLUpdate("DELETE FROM TicketManagerOpenTicketsV1 WHERE ID = " + id);
    }


    // TicketManagerUpdatedTickets methods

    static Optional<Set<Ticket>> getUnreadUpdatedTickets() throws SQLException {
        try (Connection connection = Hikari.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT ID FROM TicketManagerUpdatedTicketsV1");

            HashSet<Ticket> unreadUpdatedTickets = new HashSet<>();
            while (rs.next()) getTicket(rs.getInt(1)).ifPresent(unreadUpdatedTickets::add);

            if (unreadUpdatedTickets.size() == 0) return Optional.empty();
            else return Optional.of(unreadUpdatedTickets);
        }
    }

    static void addToUpdatedTickets(int ID) throws SQLException {
        executeSQLUpdate("INSERT IGNORE INTO TicketManagerUpdatedTicketsV1 VALUES (" + ID + ")");
    }

    static void removeFromUpdatedTickets(int ID) throws SQLException {
        executeSQLUpdate("DELETE FROM TicketManagerUpdatedTicketsV1 WHERE ID = " + ID);
    }


    // Other methods

    static void checkTables() throws SQLException {
        try (Connection connection = Hikari.getConnection()) {
            Statement stmt = connection.createStatement();

            if (tableDoesNotExist("TicketManagerTicketsV1", connection))
                stmt.execute("CREATE TABLE TicketManagerTicketsV1 (ID INT, JSON TEXT, ENCODEDUUID VARCHAR(22), PRIMARY KEY ( ID ))");
            if (tableDoesNotExist("TicketManagerOpenTicketsV1", connection))
                stmt.execute("CREATE TABLE TicketManagerOpenTicketsV1 (ID INT, PRIMARY KEY ( ID ))");
            if (tableDoesNotExist("TicketManagerUpdatedTicketsV1", connection))
                stmt.execute("CREATE TABLE TicketManagerUpdatedTicketsV1 (ID INT, PRIMARY KEY ( ID ))");
        }
    }

    private static boolean tableDoesNotExist(String tableName,Connection conn) throws SQLException {
        boolean tExists = false;
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
            while (rs.next()) {
                String tName = rs.getString("TABLE_NAME");
                if (tName != null && tName.equals(tableName)) {
                    tExists = true;
                    break;
                }
            }
        }
        return !tExists;
    }

    private static void executeSQLUpdate(String SQL) throws SQLException {
        try (Connection connection = Hikari.getConnection()) {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate(SQL);
        }
    }

    private static String uuidToBase64(UUID uuid) {
        if (uuid == null) return "Console";
        byte[] src = ByteBuffer.wrap(new byte[16])
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
        return Base64.getUrlEncoder().encodeToString(src).substring(0, 22);
    }
}