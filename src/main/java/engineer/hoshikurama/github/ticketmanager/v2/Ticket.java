package engineer.hoshikurama.github.ticketmanager.v2;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

class Ticket {
    private final int id;                       // Guaranteed Ticket ID
    private byte priority;                      // 1 - 5
    private String status;                      // OPEN or CLOSED
    private String assignment;                  // either assigned user or " "
    private final Location location;            // Valid location or null " "
    private final UUID uuid;                    // null or uuid (mysql 'CONSOLE')
    private final String creator;               // User or CONSOLE
    private final long creationTime;            // epoch time
    private final List<Comment> comments;       // Guaranteed
    private boolean updatedByNonCreator;

    // Initial Ticket. TAKES CARE OF HANDLING PLAYER VS CONSOLE
    Ticket(CommandSender sender, String comment) throws SQLException {
        creationTime = Instant.now().getEpochSecond();
        id = DatabaseHandler.getNextTicketID();
        priority = 3;
        status = "OPEN";
        assignment = " ";
        updatedByNonCreator = false;

        if (sender instanceof Player) {
            Player player = (Player) sender;
             uuid = player.getUniqueId();
            creator = player.getName();
            location = new Location(player.getLocation());
        } else {
             uuid = null;
            location = null;
            creator = "Console";
        }

        comments = new ArrayList<>();
        comments.add(new Comment(creator, comment.replace("/MySQLSep/"," ").replace("/MySQLNewLine/"," ")));
    }

    // Ticket created with MySQL data
    Ticket(int id, String status, byte priority, String creator, String uuidString, String assignment, String rawLocation, long creationTime, String rawComments, boolean wasUpdatedByOtherUser) {
        this.priority = priority;
        this.status = status;
        this.assignment = assignment;
        this.creationTime = creationTime;
        this.updatedByNonCreator = wasUpdatedByOtherUser;
        this.creator = creator;
        this.id = id;

        // Process comments
        this.comments = new ArrayList<>();
        for (String commentLines : rawComments.split("/MySQLNewLine/")) {
            String[] components = commentLines.split("/MySQLSep/");
            comments.add(new Comment(components[0], components[1]));
        }

        // Processes location
        if (!rawLocation.equals("NoLocation")) {
            String[] split = rawLocation.split(" ");
            location = new Location(split[0], split[1], split[2], split[3]);
        } else location = null;

        // Processes UUID
        if (uuidString.equals("CONSOLE")) uuid = null;
        else uuid = UUID.fromString(uuidString);
    }

    boolean UUIDMatches(UUID uuid) {
        if (uuid == null)
            return this.uuid == null;
        else if (this.uuid == null)
            return false;
        else return this.uuid.equals(uuid);
    }

     int getId() {
        return id;
    }

    byte getPriority() {
        return priority;
    }

    void setPriority(byte value) {
        this.priority = value;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String value) {
        this.status = value;
    }

    String getAssignment() {
        return assignment;
    }

    void setAssignment(String value) {
        this.assignment = value;
    }

    Optional<Location> getLocation() {
        return Optional.ofNullable(location);
    }

    boolean wasUpdatedByOtherUser() {
        return updatedByNonCreator;
    }

    void setUpdatedByOtherUser(boolean value) {
        if (uuid != null) this.updatedByNonCreator = value;
    }

    String getCreator() {
        return creator;
    }

    long getCreationTime() {
        return creationTime;
    }

    List<Comment> getComments() {
        return comments;
    }

    void addComment(CommandSender sender, String comment) {
        if (sender instanceof Player) comments.add(new Comment(sender.getName(), comment));
        else comments.add(new Comment("Console", comment.replace("/MySQLSep/"," ").replace("/MySQLNewLine/"," ")));
    }

    String getStringUUIDForMYSQL() {
        return uuid == null ? "CONSOLE" : uuid.toString();
    }

    class Location {
        int x, y, z;
        String worldName;

        Location(org.bukkit.Location bukkitLoc) {
            this.x = bukkitLoc.getBlockX();
            this.y = bukkitLoc.getBlockY();
            this.z = bukkitLoc.getBlockZ();
            this.worldName = bukkitLoc.getWorld().getName();
        }

        Location(String world, String x, String y, String z) {
            this.worldName = world;
            this.x = Integer.parseInt(x);
            this.y = Integer.parseInt(y);
            this.z = Integer.parseInt(z);
        }
    }

    class Comment {
        String user, comment;

        Comment(String user, String comment) {
            this.user = user;
            this.comment = comment;
        }
    }
}
