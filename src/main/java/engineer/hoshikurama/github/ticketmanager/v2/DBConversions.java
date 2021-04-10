package engineer.hoshikurama.github.ticketmanager.v2;

import java.util.List;
import java.util.UUID;

class DBConversions {
    static boolean conversionInProgress;
    /*
    This is a placeholder class in the event a backend rewrite is performed
     */


    // Classes down below:

    public class TicketV1 {
        byte priority;              // 1-5
        String status;              // open or closed
        String assignment;          // null possible
        List<Comment> comments;     // Guaranteed
        Location location;          // null possible
        String creator;             // Guaranteed
        int id;                     // Guaranteed
        UUID uuid;                  // null possible

        Location createLocation(org.bukkit.Location bukkitLoc) {
            if (bukkitLoc != null && !bukkitLoc.getWorld().getName().equals("null")) return new Location(bukkitLoc);
            else return null;
        }



        class Comment {
            String user, comment;

            Comment(String user, String comment) {
                this.user = user;
                this.comment = comment;
            }
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
        }
    }

}
