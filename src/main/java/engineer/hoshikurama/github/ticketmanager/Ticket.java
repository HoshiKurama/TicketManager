package engineer.hoshikurama.github.ticketmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class Ticket {
    private byte priority;
    private String status;
    private String assignment;
    private final List<Comment> comments;
    private final Location location;
    private final String creator;
    private final int id;
    private final UUID uuid;

    Ticket(org.bukkit.Location bukkitLoc, String creator, UUID uuid, int id) {
        comments = new ArrayList<>();
        location = new Location(bukkitLoc);
        this.creator = creator;
        this.assignment = null;
        this.uuid = uuid;
        this.id = id;
        status = "OPEN";
        priority = 3;
    }

    public String getAssignment() {
        return assignment;
    }

    Optional<UUID> getUUID() {
        return Optional.ofNullable(uuid);
    }

    int getId() {
        return id;
    }

    void addComment(String user, String comment) {
        comments.add(new Comment(user, comment));
    }

    void setStatus(String status) {
        this.status = status;
    }

    void setPriority(byte priority) {
        this.priority = priority;
    }

    void setAssignment(String assignment) {
        this.assignment = assignment;
    }

    List<Comment> getComments() {
        return comments;
    }

    Optional<Location> getLocation() {
        return Optional.of(location);
    }

    String getStatus() {
        return status;
    }

    String getCreator() {
        return creator;
    }

    byte getPriority() {
        return priority;
    }

    class Comment {
        String user, comment;

        Comment(String user, String comment) {
            this.user = user;
            this.comment = comment;
        }
    }

    class Location {
        private int x, y, z;
        private String worldName;

        Location(org.bukkit.Location bukkitLoc) {
            if (bukkitLoc != null) {
                this.x = bukkitLoc.getBlockX();
                this.y = bukkitLoc.getBlockY();
                this.z = bukkitLoc.getBlockZ();
                this.worldName = bukkitLoc.getWorld().getName();
            }
        }

        int getX() {
            return x;
        }

        int getY() {
            return y;
        }

        int getZ() {
            return z;
        }

        String getWorldName() {
            return worldName;
        }
    }
}
