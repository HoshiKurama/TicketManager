package engineer.hoshikurama.github.ticketmanager;

import com.google.gson.Gson;

import java.sql.*;
import java.util.*;

class DatabaseHandler {

    // TicketManagerTickets methods

    static Optional<Ticket> getTicket(int ID) throws SQLException {
        try (Connection connection = Hikari.getConnection()) {

            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT JSON FROM TicketManagerTickets WHERE ID = " + ID);

            if (rs.next()) return Optional.of(new Gson().fromJson(rs.getString(1), Ticket.class));
            else return Optional.empty();
        }
    }

    static void updateTicket(Ticket ticket) throws SQLException {
        try (Connection connection = Hikari.getConnection()) {
            String JSONString = new Gson().toJson(ticket);
            PreparedStatement stmt = connection.prepareStatement("UPDATE TicketManagerTickets SET JSON = ? WHERE ID = ?");
            stmt.setString(1, JSONString);
            stmt.setInt(2, ticket.getId());
            stmt.executeUpdate();
        }
    }

    static int getNextOpenTicketNumber() throws SQLException {
        try (Connection connection = Hikari.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT MAX(ID) FROM TicketManagerTickets");

            if (rs.next()) return (rs.getInt(1) + 1);
            else return 1;
        }
    }

    static void addTicket(Ticket ticket) throws SQLException {
        String JSON = new Gson().toJson(ticket);

        try (Connection connection = Hikari.getConnection()) {
            PreparedStatement stmt = connection.prepareStatement("INSERT INTO TicketManagerTickets VALUES (?,?)");
            stmt.setInt(1,ticket.getId());
            stmt.setString(2,JSON);
            stmt.executeUpdate();
        }
    }

    static Set<Ticket> getAllTickets() throws SQLException {
        try (Connection connection = Hikari.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT JSON FROM TicketManagerTickets");

            HashSet<Ticket> tickets = new HashSet<>();
            while (rs.next()) tickets.add(new Gson().fromJson(rs.getString(1), Ticket.class));
            return tickets;
        }
    }


    // TicketManagerOpenTickets methods

    static Optional<Set<Ticket>> getOpenTickets() throws SQLException{
        try (Connection connection = Hikari.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT ID FROM TicketManagerOpenTickets");

            HashSet<Integer> openTicketIDs = new HashSet<>();
            while (rs.next()) openTicketIDs.add(rs.getInt(1));
            if (openTicketIDs.size() == 0) return Optional.empty();

            HashSet<Ticket> openTickets = new HashSet<>(openTicketIDs.size());
            for (int id : openTicketIDs) getTicket(id).ifPresent(openTickets::add);
            return Optional.of(openTickets);
        }
    }

    static void addToOpenTickets(int id) throws SQLException {
        executeSQLUpdate("INSERT INTO TicketManagerOpenTickets VALUES (" + id + ")");
    }

    static void removeFromOpenTickets(int id) throws SQLException {
        executeSQLUpdate("DELETE FROM TicketManagerOpenTickets WHERE ID = " + id);
    }


    // TicketManagerUpdatedTickets methods

    static Optional<Set<Ticket>> getUnreadUpdatedTickets() throws SQLException {
        try (Connection connection = Hikari.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT ID FROM TicketManagerUpdatedTickets");

            HashSet<Ticket> unreadUpdatedTickets = new HashSet<>();
            while (rs.next()) getTicket(rs.getInt(1)).ifPresent(unreadUpdatedTickets::add);

            if (unreadUpdatedTickets.size() == 0) return Optional.empty();
            else return Optional.of(unreadUpdatedTickets);
        }
    }

    static void addToUpdatedTickets(int ID) throws SQLException {
        executeSQLUpdate("INSERT IGNORE INTO TicketManagerUpdatedTickets VALUES (" + ID + ")");
    }

    static void removeFromUpdatedTickets(int ID) throws SQLException {
        executeSQLUpdate("DELETE FROM TicketManagerUpdatedTickets WHERE ID = " + ID);
    }


    // Other methods

    static void checkTables() throws SQLException {
        try (Connection connection = Hikari.getConnection()) {
            Statement stmt = connection.createStatement();

            if (tableDoesNotExist("TicketManagerTickets", connection))
                stmt.execute("CREATE TABLE TicketManagerTickets (ID INT, JSON TEXT, PRIMARY KEY ( ID ))");
            if (tableDoesNotExist("TicketManagerOpenTickets", connection))
                stmt.execute("CREATE TABLE TicketManagerOpenTickets (ID INT, PRIMARY KEY ( ID ))");
            if (tableDoesNotExist("TicketManagerUpdatedTickets", connection))
                stmt.execute("CREATE TABLE TicketManagerUpdatedTickets (ID INT, PRIMARY KEY ( ID ))");
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
}