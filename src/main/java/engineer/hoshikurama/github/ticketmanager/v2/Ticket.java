package engineer.hoshikurama.github.ticketmanager.v2;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.*;

public class Ticket {
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
    Ticket(CommandSender sender, String comment) throws DatabaseException {
        creationTime = Instant.now().getEpochSecond();
        id = TicketManager.dbHandler().getNextTicketID();
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

    // Ticket created from database
    public Ticket(int id, String status, byte priority, String creator, UUID uuid, String assignment, Location location, long creationTime, boolean wasUpdatedByOtherUser) {
        this.id = id;
        this.status = status;
        this.priority = priority;
        this.creator = creator;
        this.assignment = assignment;
        this.creationTime = creationTime;
        this.location = location;
        this.comments = new ArrayList<>();
        this.updatedByNonCreator = wasUpdatedByOtherUser;
        this.uuid = uuid;
    }

    public void addComment(String commenter, String message) {
        comments.add(new Comment(commenter, message));
    }

    boolean UUIDMatches(UUID uuid) {
        if (uuid == null)
            return this.uuid == null;
        else if (this.uuid == null)
            return false;
        else return this.uuid.equals(uuid);
    }

     public int getId() {
        return id;
    }

    public byte getPriority() {
        return priority;
    }

    void setPriority(byte value) {
        this.priority = value;
    }

    public String getStatus() {
        return status;
    }

    void setStatus(String value) {
        this.status = value;
    }

    public String getAssignment() {
        return assignment;
    }

    void setAssignment(String value) {
        this.assignment = value;
    }

    public Optional<Location> getLocation() {
        return Optional.ofNullable(location);
    }

    public boolean wasUpdatedByOtherUser() {
        return updatedByNonCreator;
    }

    void setUpdatedByOtherUser(boolean value) {
        if (uuid != null) this.updatedByNonCreator = value;
    }

    public String getCreator() {
        return creator;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public List<Comment> getComments() {
        return comments;
    }

    void addComment(CommandSender sender, String comment) {
        if (sender instanceof Player) comments.add(new Comment(sender.getName(), comment));
        else comments.add(new Comment("Console", comment.replace("/MySQLSep/"," ").replace("/MySQLNewLine/"," ")));
    }

    public String getStringUUIDForMYSQL() {
        return uuid == null ? "CONSOLE" : uuid.toString();
    }

    public static class Location {
        public int x, y, z;
        public String worldName;

        Location(org.bukkit.Location bukkitLoc) {
            this.x = bukkitLoc.getBlockX();
            this.y = bukkitLoc.getBlockY();
            this.z = bukkitLoc.getBlockZ();
            this.worldName = bukkitLoc.getWorld().getName();
        }

        public Location(String world, String x, String y, String z) {
            this.worldName = world;
            this.x = Integer.parseInt(x);
            this.y = Integer.parseInt(y);
            this.z = Integer.parseInt(z);
        }
    }

    public class Comment {
        public String user, comment;

        Comment(String user, String comment) {
            this.user = user;
            this.comment = comment;
        }
    }
}
