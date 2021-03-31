package engineer.hoshikurama.github.ticketmanager;

import com.google.common.collect.Lists;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class TicketCommands implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length <= 0) {
            Bukkit.getScheduler().runTaskAsynchronously(TicketManager.getInstance, () -> sendHelpMessage(sender, args));
            return true;
        }

        // Grabs all sync vars before async
        final Map<UUID, Player> onlinePlayerMap = Bukkit.getOnlinePlayers().stream()
                .collect(Collectors.toMap(Entity::getUniqueId, p -> p));


        Bukkit.getScheduler().runTaskAsynchronously(TicketManager.getInstance, () -> {
            synchronized (this) {
                try {
                    switch (args[0]) {
                        case "help": sendHelpMessage(sender, args);
                        case "create": createTicketCommand(sender, args, onlinePlayerMap); break;
                        case "view": viewTicketCommand(sender, args); break;
                        case "comment": commentTicketCommand(sender, args, onlinePlayerMap, true); break;
                        case "close": closeTicketCommand(sender, args, onlinePlayerMap, true); break;
                        case "history": viewHistoryCommand(sender, args); break;
                        case "assign": assignTicketCommand(sender, args, onlinePlayerMap, String.join(" ", Arrays.copyOfRange(args, 2, args.length))); break;
                        case "claim": assignTicketCommand(sender, args, onlinePlayerMap, sender.getName()); break;
                        case "unassign": assignTicketCommand(sender, args, onlinePlayerMap, null); break;
                        case "setpriority": setPriorityTicketCommand(sender, args, onlinePlayerMap); break;
                        case "closeall": closeAllTicketsCommand(sender, command, label, args, onlinePlayerMap); break;
                        case "teleport": teleportTicketCommand(sender, args); break;
                        case "list": listOpenTicketsCommand(sender, args); break;
                        case "reopen": reopenTicketCommand(sender, args, onlinePlayerMap);
                        case "reload": reloadConfig(sender);
                    }
                } catch (SQLException e) {
                    sender.sendMessage(withColourCode("&cAn error occurred in trying to contact the database!"));
                } catch (NumberFormatException e) {
                    sender.sendMessage(withColourCode("&cPlease use a valid number for a ticket ID!"));
                }
            }
        });
        return true;
    }
    void reloadConfig(CommandSender sender) throws SQLException {
        // Filters permissions
        if (sender instanceof Player && !TicketManager.getPermissions().has(sender, "ticketmanager.reload")) {
            sender.sendMessage(withColourCode("&cYou do not have permission to reload this plugin!"));
            return;
        }

        // Re-reads config
        FileConfiguration config = TicketManager.getInstance.config;
        Hikari.LaunchDatabase(config.getString("Host"),
                config.getString("Port"),
                config.getString("DB_Name"),
                config.getString("Username"),
                config.getString("Password"));
        sender.sendMessage(withColourCode("&3[TicketManager] Config reloaded successfully!"));
    }

    void sendHelpMessage(CommandSender sender, String[] args) {
        // Filters permissions
        if (sender instanceof Player) {
            if (!TicketManager.getPermissions().has(sender, "ticketmanager.help.all") && !TicketManager.getPermissions().has(sender, "ticketmanager.help.basic")) {
                sender.sendMessage(withColourCode("&cYou do not have permission to view ticket commands!"));
                return;
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append("&3[TicketManager] Ticket Commands:  &7<Required> | [Optional]")
                .append("\n&3/ticket &7create <Message...>")
                .append("\n&3/ticket &7comment <Ticket ID> <Message...>")
                .append("\n&3/ticket &7view <Ticket ID>")
                .append("\n&3/ticket &7history [Username]")
                .append("\n&3/ticket &7close <Ticket ID> [Message...]");

        if (TicketManager.getPermissions().has(sender, "ticketmanager.help.all"))
            builder.append("\n&3/ticket &7assign <Ticket ID> <User/Assignment>")
                    .append("\n&3/ticket &7unassign <Ticket ID>")
                    .append("\n&3/ticket &7claim <Ticket ID>")
                    .append("\n&3/ticket &7reopen <Ticket ID>")
                    .append("\n&3/ticket &7setpriority <Ticket ID> <Priority (1-5)>")
                    .append("\n&3/ticket &7list")
                    .append("\n&3/ticket &7closeAll <Lower Bound> <Upper Bound>")
                    .append("\n&3/ticket &7teleport <Ticket ID>")
                    .append("\n&3/ticket reload");

        sender.sendMessage(withColourCode(builder.toString()));
    }

    void createTicketCommand(CommandSender sender, String[] args, Map<UUID, Player> onlinePlayerMap) throws SQLException {
        // Filter out blank tickets and people without permission
        if (args.length <= 1) {
            sender.sendMessage(withColourCode("&cYou cannot make a blank ticket!"));
            return;
        }

        // Filters out permissions
        if (sender instanceof Player) {
            if (!TicketManager.getPermissions().has(sender, "ticketmanager.create")) {
                sender.sendMessage(withColourCode("&cYou do not have permission to create tickets!"));
                return;
            }
        }

        // Creates ticket with distinction between player and Console
        Ticket ticket;
        int ticketID = DatabaseHandler.getNextOpenTicketNumber();
        String ticketMessage = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        if (sender instanceof Player) {
            Player player = (Player) sender;
            ticket = new Ticket(player.getLocation(), player.getName(), player.getUniqueId(), ticketID);
        }
        else ticket = new Ticket(null, sender.getName(), null, ticketID);

        // Adds comments and does database stuff
        ticket.addComment(sender.getName(), ticketMessage);
        DatabaseHandler.addTicket(ticket);
        DatabaseHandler.addToOpenTickets(ticketID);

        // Notifies users with notification permissions
        pushNotification("ticketmanager.notify.onCreate", null, ticket, sender, onlinePlayerMap,
                withColourCode("&3[TicketManager] &7" + ticket.getCreator() + "&3 has created ticket &7#" + ticketID + "&3:\n&7" + ticketMessage), null);

        if (!TicketManager.getPermissions().has(sender, "ticketmanager.notify.onCreate"))
            sender.sendMessage(withColourCode("&3Ticket #" + ticketID + " has been successfully created!"));
    }

    void viewTicketCommand(CommandSender sender, String[] args) throws SQLException,NumberFormatException {
        // Filters incorrect command
        if (args.length <= 1) {
            sender.sendMessage(withColourCode("&cPlease enter a ticket number to view!"));
            return;
        }
        // Filters invalid tickets
        Optional<Ticket> ticketOptional = DatabaseHandler.getTicket(Integer.parseInt(args[1]));
        if (!(ticketOptional.isPresent())) {
            sender.sendMessage(withColourCode("&cThis is not a valid ticket!"));
            return;
        }
        // Filters people without permission
        Ticket ticket = ticketOptional.get();
        if (sender instanceof Player) {
            if (senderCannotPerformSelfOtherAction(sender, ticket, "ticketmanager.view.others", "ticketmanager.view.self")) {
                sender.sendMessage("&cYou do not have permission to view this ticket!");
                return;
            }
        }
        sender.sendMessage(formatTicketForViewCommand(ticket));

        // Removes player from updatedTickets table if player views own updated ticket
        if (sender instanceof Player && ticket.getUUID().isPresent()) {
            Player player = (Player) sender;
            if (player.getUniqueId().equals(ticket.getUUID().get())) {
                Optional<Set<Ticket>> optionalUnreads = DatabaseHandler.getUnreadUpdatedTickets();
                if (optionalUnreads.isPresent())
                    if (optionalUnreads.get().stream().map(Ticket::getId).anyMatch(t -> t == ticket.getId()))
                        DatabaseHandler.removeFromUpdatedTickets(ticket.getId());
            }
        }
    }

    void commentTicketCommand(CommandSender sender, String[] args, Map<UUID, Player> onlinePlayerMap, boolean notPiggybacked) throws SQLException{
        // Filters out comments not long enough and invalid tickets
        if (args.length <= 2) {
            sender.sendMessage(withColourCode("&cPlease enter a ticket number and/or comment!"));
            return;
        }
        Optional<Ticket> ticketOptional = DatabaseHandler.getTicket(Integer.parseInt(args[1]));
        if (!(ticketOptional.isPresent())) {
            sender.sendMessage(withColourCode("&cThis is not a valid ticket!"));
            return;
        }

        // Filters out all players who lack permission to comment on ticket
        Ticket ticket = ticketOptional.get();
        if (sender instanceof Player) {
            if (senderCannotPerformSelfOtherAction(sender, ticket, "ticketmanager.comment.others", "ticketmanager.comment.self")) {
                sender.sendMessage(withColourCode("&cYou do not have permission to perform this command!"));
                return;
            }
        }

        // Filter out closed tickets
        if (ticket.getStatus().equals("CLOSED")) {
            sender.sendMessage(withColourCode("&cYou cannot comment on closed tickets!"));
            return;
        }

        // Adds and updates tickets
        String ticketMessage = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        ticket.addComment(sender.getName(), ticketMessage);
        DatabaseHandler.updateTicket(ticket);
        if (!ticket.getUUID().isPresent() || !(sender instanceof Player) || !ticket.getUUID().get().equals(((Player) sender).getUniqueId()))
            DatabaseHandler.addToUpdatedTickets(ticket.getId());

        // Send notifications
        pushNotification("ticketmanager.notify.onUpdate.others", "ticketmanager.notify.onUpdate.self", ticket, sender, onlinePlayerMap,
                withColourCode("&3[TicketManager] &7" + sender.getName() + " &3has commented on ticket &7#" + ticket.getId() + "&3:\n  &7" + ticketMessage),
                withColourCode("&3[TicketManager] Ticket #" + ticket.getId() + " has been updated! Please type &7/ticket view " +
                        ticket.getId() + " &3 to view this ticket."));
        if (notPiggybacked || !TicketManager.getPermissions().has(sender, "ticketmanager.notify.onUpdate.others")) sender.sendMessage(withColourCode("&3[TicketManager] Ticket comment successful!"));
    }

    void closeTicketCommand(CommandSender sender, String[] args, Map<UUID, Player> onlinePlayerMap, boolean sendMessage) throws SQLException {
        // If closing command with comment, run comment creator first
        if (args.length > 2) commentTicketCommand(sender, args, onlinePlayerMap, false);

        // Filters out comments not long enough and invalid tickets
        if (args.length <= 1) {
            sender.sendMessage(withColourCode("&cPlease enter a ticket number and/or comment!"));
            return;
        }
        Optional<Ticket> ticketOptional = DatabaseHandler.getTicket(Integer.parseInt(args[1]));
        if (!(ticketOptional.isPresent())) {
            sender.sendMessage(withColourCode("&cThis is not a valid ticket!"));
            return;
        }

        // Filters out people without permission
        Ticket ticket = ticketOptional.get();
        if (sender instanceof Player) {
            if (senderCannotPerformSelfOtherAction(sender, ticket, "ticketmanager.close.others", "ticketmanager.close.self")) {
                sender.sendMessage(withColourCode("&cYou do not have permission to perform this command!"));
                return;
            }
        }

        // Filter out closed tickets
        if (ticket.getStatus().equals("CLOSED")) {
            sender.sendMessage(withColourCode("&cYou cannot close tickets already closed!"));
            return;
        }

        // Set ticket status and perform database stuff
        ticket.setStatus("CLOSED");
        DatabaseHandler.updateTicket(ticket);
        DatabaseHandler.removeFromOpenTickets(ticket.getId());
        if (!ticket.getUUID().isPresent() || !(sender instanceof Player) || !ticket.getUUID().get().equals(((Player) sender).getUniqueId()))
            DatabaseHandler.addToUpdatedTickets(ticket.getId());

        // Pushes Notifications
        if (sendMessage) {
            pushNotification("ticketmanager.notify.onClose.others", "ticketmanager.notify.onClose.self", ticket, sender, onlinePlayerMap,
                    withColourCode("&3[TicketManager] Ticket &7#" + ticket.getId() + " &3has been closed by &7" + sender.getName()),
                    withColourCode("&3[TicketManager] Ticket #" + ticket.getId() + " has been closed! Please type &7/ticket view " +
                            ticket.getId() + " &3 to view this ticket.")
            );
            if (args.length <= 2 || !TicketManager.getPermissions().has(sender, "ticketmanager.notify.onClose.others")) sender.sendMessage(withColourCode("&3[TicketManager] Ticket closed!"));
        }
    }

    void assignTicketCommand(CommandSender sender, String[] args, Map<UUID, Player> onlinePlayerMap, String assignee) throws SQLException {
        // /ticket assign <ID> <User/Name>

        // Filters invalid command and tickets
        if (args.length <= 1) {
            sender.sendMessage(withColourCode("&cPlease at least have a ticket ID!"));
            return;
        }
        Optional<Ticket> ticketOptional = DatabaseHandler.getTicket(Integer.parseInt(args[1]));
        if (!(ticketOptional.isPresent())) {
            sender.sendMessage(withColourCode("&cThis is not a valid ticket!"));
            return;
        }

        // Filters people without permission
        Ticket ticket = ticketOptional.get();
        if (sender instanceof Player) {
            if (!(TicketManager.getPermissions().has(sender, "ticketmanager.assign"))) {
                sender.sendMessage(withColourCode("&cYou do not have permission to perform this command!"));
                return;
            }
        }

        // Perform database stuff
        ticket.setAssignment(assignee);
        DatabaseHandler.updateTicket(ticket);

        pushNotification("ticketmanager.notify.onUpdate.others", null, ticket, sender, onlinePlayerMap,
                withColourCode("&3[TicketManager] Ticket &7#" + ticket.getId() + "&3 has been assigned to &7" + ticket.getAssignment()), null);
        if (!TicketManager.getPermissions().has(sender, "ticketmanager.notify.onUpdate.Others"))
            sender.sendMessage(withColourCode("&3Ticket has been successfully assigned!"));
    }

    void teleportTicketCommand(CommandSender sender, String[] args) throws SQLException {
        // Filters incorrect command
        if (args.length <= 1) {
            sender.sendMessage(withColourCode("&cPlease enter a ticket number to view!"));
            return;
        }
        // Filters invalid tickets
        Optional<Ticket> ticketOptional = DatabaseHandler.getTicket(Integer.parseInt(args[1]));
        if (!(ticketOptional.isPresent())) {
            sender.sendMessage(withColourCode("&cThis is not a valid ticket!"));
            return;
        }
        // Filters people without permission
        Ticket ticket = ticketOptional.get();
        if (sender instanceof Player) {
            if (!TicketManager.getPermissions().has(sender, "ticketmanager.teleport")) {
                sender.sendMessage(withColourCode("7cYou do not have permission to perform this command!"));
                return;
            }
        }

        // Teleports to location
        ticket.getLocation().ifPresent(loc ->
                Bukkit.getScheduler().runTask(TicketManager.getInstance, () -> {
                    if (!(sender instanceof Player)) return;
                    Player player = (Player) sender;
                    player.teleport(new Location(Bukkit.getWorld(loc.getWorldName()), loc.getX(), loc.getY(), loc.getZ()));
                }));
    }

    void reopenTicketCommand(CommandSender sender, String[] args, Map<UUID, Player> onlinePlayerMap) throws SQLException {
        // Filters out comments not long enough and invalid tickets
        if (args.length <= 1) {
            sender.sendMessage(withColourCode("&cPlease enter a ticket number!"));
            return;
        }
        Optional<Ticket> ticketOptional = DatabaseHandler.getTicket(Integer.parseInt(args[1]));
        if (!(ticketOptional.isPresent())) {
            sender.sendMessage(withColourCode("&cThis is not a valid ticket!"));
            return;
        }
        // Filters out people without permission
        Ticket ticket = ticketOptional.get();
        if (sender instanceof Player) {
            if (!TicketManager.getPermissions().has(sender, "ticketmanager.reopen")) {
                sender.sendMessage(withColourCode("&cYou do not have permission to perform this command!"));
                return;
            }
        }
        // Filters out open tickets
        if (ticket.getStatus().equals("OPEN")) {
            sender.sendMessage(withColourCode("&cThis ticket is already open!"));
            return;
        }

        //Does database and ticket stuff
        ticket.setStatus("OPEN");
        DatabaseHandler.updateTicket(ticket);
        DatabaseHandler.addToOpenTickets(ticket.getId());

        pushNotification("ticket.notify.onUpdate.others", "ticket.notify.onUpdate.self", ticket, sender, onlinePlayerMap,
                withColourCode("&3[TicketManager] &7" + sender.getName() + " &3 has reopened ticket &7" + ticket.getId() + "&3."),
                withColourCode("&3Ticket # " + ticket.getId() + " has been reopened!"));
        if (!TicketManager.getPermissions().has(sender, "ticketmanager.notify.onUpdate.others"))
            sender.sendMessage(withColourCode("&3Ticket re-opened successfully!"));

    }

    void listOpenTicketsCommand(CommandSender sender, String[] args) throws SQLException, NumberFormatException {
        if (sender instanceof Player) {
            if (!TicketManager.getPermissions().has(sender, "ticketmanager.list")) {
                sender.sendMessage(withColourCode("&cYou do not have permission to perform this command!"));
                return;
            }
        }

        // Creates beginning of message
        ComponentBuilder components = new ComponentBuilder(withColourCode("&3[TicketManager] &7Viewing all open tickets:"));

        // Filters out situation with no open tickets
        Optional<Set<Ticket>> ticketsOptional = DatabaseHandler.getOpenTickets();
        if (!ticketsOptional.isPresent()) {
            sender.sendMessage(components.create());
            return;
        }

        // Sorts tickets by ID/Priority and appends to components
        ticketsOptional.get().stream()
                .sorted(Comparator.comparing(Ticket::getPriority).reversed().thenComparing(Comparator.comparing(Ticket::getId).reversed()))
                .map(this::formatTicketForListCommand)
                .forEach(components::append);

        // Creates Page info
        int page = 1;
        if (args.length == 2) page = Integer.parseInt(args[1]);
        List<List<BaseComponent>> partitionedBaseComponents = getPartitionedComponents(0, components.getParts());

        int maxPages = partitionedBaseComponents.size();
        if (maxPages == 0) maxPages = 1;

        // Fixes invalid numbers
        if (page <= 0) page = 1;
        if (page > maxPages) page = maxPages;

        // Gets requested page
        List<BaseComponent> partitionedPage = partitionedBaseComponents.get(page-1);

        // If there is only one page, add navigation row
        if (maxPages > 1) {
            ComponentBuilder arrowNode = new ComponentBuilder("\n[Back]");

            if (page == 1) arrowNode.color(ChatColor.DARK_GRAY);
            else arrowNode.color(ChatColor.WHITE)
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Move to previous page")))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ticket list " + (page - 1)));

            arrowNode.append(withColourCode("&7.......................&3(" + page + " of " + maxPages + ")&7.......................")).append("[Next]");

            if (page == maxPages) arrowNode.color(ChatColor.DARK_GRAY);
            else arrowNode.color(ChatColor.WHITE)
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Move to next page")))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ticket list " + (page + 1)));

            partitionedPage.addAll(arrowNode.getParts());
        }
        sender.sendMessage(partitionedPage.toArray(new BaseComponent[0]));
    }

    void viewHistoryCommand(CommandSender sender, String[] args) throws SQLException {
        UUID targetID = null;
        String[] correctedArgs = new String[3];
        correctedArgs[0] = "history";

        //Fills in correctedArgs [history,NAME,PAGE]
        correctedArgs[1] = args.length >= 2 ? args[1] : sender.getName();
        if (args.length >= 3) correctedArgs[2] = args[2];
        else correctedArgs[2] = "1";

        // Assigns targetUUID based on command usage (filters for permissions)
        if (sender instanceof Player) {
            if (!TicketManager.getPermissions().has(sender, "ticketmanager.history.others")) {
               if (TicketManager.getPermissions().has(sender, "ticketmanager.history.self")) {
                   UUID senderID = Bukkit.getPlayerUniqueId(sender.getName());
                   targetID = Bukkit.getPlayerUniqueId(correctedArgs[1]);
                   if (senderID == null || !senderID.equals(targetID)) {
                       sender.sendMessage(withColourCode("&cYou do not have permission to view this person's ticket history!"));
                       return;
                   }
               }
            } else targetID = Bukkit.getPlayerUniqueId(sender.getName()); //Could be null if Console
        }

        //Confirms targetUUID is valid or is Console
        if (correctedArgs[1].equalsIgnoreCase("console")) targetID = null;
        else if (targetID == null) {
            sender.sendMessage(withColourCode("&cThis is not a valid user!"));
            return;
        }

        Set<Ticket> playertickets = DatabaseHandler.getAllTicketsWithUUID(targetID);

        // Builds and creates initial data
        ComponentBuilder component = new ComponentBuilder(withColourCode("&3[TicketManager] &7This user has " + playertickets.size() + " tickets!"));
        playertickets.stream()
                .sorted(Comparator.comparing(Ticket::getId).reversed())
                .forEach(t -> component.append(formatTicketForHistoryCommand(t)));

        // Creates Page info
        int page = Integer.parseInt(correctedArgs[2]);
        List<List<BaseComponent>> partitionedBaseComponents = getPartitionedComponents(0, component.getParts());

        int maxPages = partitionedBaseComponents.size();
        if (maxPages == 0) maxPages = 1;

        // Fixes invalid numbers
        if (page <= 0) page = 1;
        if (page > maxPages) page = maxPages;

        // Gets requested page
        List<BaseComponent> partitionedPage = partitionedBaseComponents.get(page - 1);

        // If there is only one page, add navigation row
        if (maxPages > 1) {
            ComponentBuilder arrowNode = new ComponentBuilder("\n[Back]");

            if (page == 1) arrowNode.color(ChatColor.DARK_GRAY);
            else arrowNode.color(ChatColor.WHITE)
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Move to previous page")))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ticket history " + correctedArgs[1] + " " + (page - 1)));

            arrowNode.append(withColourCode("&7.......................&3(" + page + " of " + maxPages + ")&7.......................")).append("[Next]");

            if (page == maxPages) arrowNode.color(ChatColor.DARK_GRAY);
            else arrowNode.color(ChatColor.WHITE)
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Move to next page")))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ticket history " + correctedArgs[1] + " " + (page + 1)));

            partitionedPage.addAll(arrowNode.getParts());
        }
        sender.sendMessage(partitionedPage.toArray(new BaseComponent[0]));
    }

    void setPriorityTicketCommand(CommandSender sender, String[] args, Map<UUID, Player> onlinePlayerMap) throws SQLException, NumberFormatException {
        // Filters out permission-less people
        if (sender instanceof Player) {
            if (!TicketManager.getPermissions().has(sender, "ticketmanager.setpriority")) {
                sender.sendMessage("&cYou do not have permission to set ticket priorities!");
                return;
            }
        }

        // Filters commands of wrong size
        if (args.length != 3) {
            sender.sendMessage(withColourCode("&cPlease use the correct format"));
            return;
        }

        // Fixes incorrect priority values
        int priority = Integer.parseInt(args[2]);
        if (priority < 1) priority = 1;
        if (priority > 5) priority = 5;

        // Filters invalid tickets
        Optional<Ticket> optionalTicket = DatabaseHandler.getTicket(Integer.parseInt(args[1]));
        if (!optionalTicket.isPresent()) {
            sender.sendMessage(withColourCode("&cPlease use a valid ticket ID!"));
            return;
        }
        Ticket ticket = optionalTicket.get();

        // Filter out closed tickets
        if (ticket.getStatus().equals("CLOSED")) {
            sender.sendMessage(withColourCode("&cWhy would you try to change the priority of a closed ticket?"));
            return;
        }

        // Sets priority and database stuff
        ticket.setPriority((byte) priority);
        DatabaseHandler.updateTicket(ticket);

        // Notify others
        onlinePlayerMap.values().stream()
                .filter(p -> TicketManager.getPermissions().has(p, "ticketmanager.notify.onUpdate.others"))
                .forEach(p -> p.sendMessage(withColourCode("&3[TicketManager] " + sender.getName() + " &7has set ticket &3#" + ticket.getId() +
                        " &7's priority to " + priorityToColorCode(ticket) + priorityToString(ticket))));
        if (!TicketManager.getPermissions().has(sender, "ticketmanager.notify.onUpdate.others"))
            sender.sendMessage(withColourCode("&3Ticket priority changed successfully!"));
    }

    private List<List<BaseComponent>> getPartitionedComponents(int headerEndIndex, List<BaseComponent> components) {
        if (components.size() <  9) return Collections.singletonList(components);

        List<BaseComponent> headerComponents = components.subList(0, headerEndIndex + 1);
        List<BaseComponent> nonHeaderComponents = components.subList(headerEndIndex + 1, components.size());

        return Lists.partition(nonHeaderComponents, 8 - headerEndIndex).stream()
                .map(e -> {
                    List<BaseComponent> nonConcurrent =  new ArrayList<>(e);
                    nonConcurrent.addAll(0, headerComponents);
                    return nonConcurrent;
                })
                .collect(Collectors.toList());
    }


    void closeAllTicketsCommand(CommandSender sender, Command command, String label, String[] args, Map<UUID, Player> onlinePlayerMap) throws SQLException, NumberFormatException {
        // Filter out players without permission
        if (sender instanceof Player) {
            if (!TicketManager.getPermissions().has(sender, "ticketmanager.closeall")) {
                sender.sendMessage(withColourCode("&cYou do not have permission to perform this command!"));
                return;
            }
        }

        // Filters incorrect command
        if (args.length != 3) {
            sender.sendMessage(withColourCode("&cPlease use the correct syntax!"));
            return;
        }

        int lower = Integer.parseInt(args[1]);
        int upper = Integer.parseInt(args[2]);


        sender.sendMessage(withColourCode("&3Mass ticket close has begun! This might take a while depending on the number of tickets"));
        for (int i = lower; i <= upper; i++ ) {
            int finalI = i;

            Bukkit.getScheduler().runTaskAsynchronously(TicketManager.getInstance, () -> {
                try {
                    closeTicketCommand(sender, new String[]{args[0], String.valueOf(finalI)}, onlinePlayerMap, false);
                } catch (SQLException e) {
                    sender.sendMessage(withColourCode("&cSQL error in attempting to close ticket " + finalI + "!"));
                }
            });
        }

        // Notify others
        onlinePlayerMap.values().stream()
                .filter(p -> TicketManager.getPermissions().has(p, "ticketmanager.notify.onUpdate.others"))
                .forEach(p -> p.sendMessage(withColourCode("&3[TicketManager] " + sender.getName() + " &7has mass closed tickets &3#" + lower + "&7 to &3#" + upper)));
        if (!TicketManager.getPermissions().has(sender, "ticketmanager.notify.onClose.others"))
            sender.sendMessage(withColourCode("&3Ticket mass close successful!"));
    }


    private void pushNotification(String otherPermission, String selfPermission, Ticket ticket, CommandSender sender,
                                  Map<UUID,Player> onlinePlayerMap, String otherPermissionMSG, String selfPermissionMessage) throws SQLException {
        // Pushes update to all online users with otherPermission
        onlinePlayerMap.values().stream()
                .filter(p -> TicketManager.getPermissions().has(p, otherPermission))
                .forEach(p -> p.sendMessage(otherPermissionMSG));

        // Pushes update to relevant notify.self user
        if (selfPermission == null) return;
        if (!(sender instanceof Player) || !ticket.getUUID().isPresent() || !ticket.getUUID().get().equals(((Player) sender).getUniqueId())) {// Notify ticket creator if player is online and has notify permission
            UUID creatorUUID = ticket.getUUID().get();
            onlinePlayerMap.entrySet().stream()
                    .filter(e -> e.getKey().equals(creatorUUID))
                    .map(Map.Entry::getValue)
                    .filter(p -> TicketManager.getPermissions().has(p, selfPermission))
                    .forEach(p -> p.sendMessage(selfPermissionMessage));
        }
    }

    private BaseComponent[] formatTicketForHistoryCommand(Ticket t) {
        String comment = t.getComments().get(0).comment;
        int idLength = Integer.toString(t.getId()).length();

        // Shortens comments
        if (6 + idLength + t.getStatus().length() + comment.length() > 58)
            comment = comment.substring(0, 49 - idLength - t.getStatus().length()) + "...";

        return new ComponentBuilder(withColourCode("\n&3[" + t.getId() + "] " + statusToColorCode(t) + "[" + t.getStatus() + "] &7" + comment))
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ticket view " + t.getId()))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to view ticket #" + t.getId())))
                .create();
    }

    private BaseComponent[] formatTicketForListCommand(Ticket t) {
        String comment = t.getComments().get(0).comment;
        String assignment = t.getAssignment() == null ? "" : t.getAssignment();
        int idLength = String.valueOf(t.getId()).length();

        // Shortens comment preview to fit on one line
        if (13 + idLength + t.getCreator().length() + assignment.length() + comment.length() > 58)
            comment = comment.substring(0, 43 - idLength - assignment.length() - t.getCreator().length()) + "...";

        return new ComponentBuilder(withColourCode("\n" + priorityToColorCode(t) + "[" + t.getId() + "] &8[&3" + t.getCreator() + "&8 —> &3" + assignment + "&8] &7" + comment))
                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ticket view " + t.getId()))
                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to view ticket #" + t.getId())))
                .create();
    }

    private BaseComponent[] formatTicketForViewCommand(Ticket ticket) {
        // Determines what to do with location
        BaseComponent[] locationComponent;
        if (ticket.getLocation().isPresent()) {
            Ticket.Location loc = ticket.getLocation().get();
            locationComponent = new ComponentBuilder(withColourCode("&7&n" + loc.getWorldName() + "   " + loc.getX() + " " + loc.getY() + " " + loc.getZ()))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ticket teleport " + ticket.getId()))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to teleport to ticket #" + ticket.getId())))
                    .create();
        } else locationComponent = new ComponentBuilder("").create();

        String assignment = ticket.getAssignment() == null ? "" : ticket.getAssignment();

        // Builds base message
        ComponentBuilder message = new ComponentBuilder(withColourCode("&3&l[TicketManager] Ticket #" + ticket.getId()))
                .append(withColourCode("\n&r&8**************************"))
                .append(withColourCode("\n&3&lCreator: &7" + ticket.getCreator() + "  &f&l &3&lAssigned To: &7" + assignment + ""))
                .append(withColourCode("\n&3&lPriority: &7" + priorityToColorCode(ticket) + priorityToString(ticket) + "  &f&l  &3&lStatus: &7" + statusToColorCode(ticket) + ticket.getStatus()))
                .append(withColourCode("\n&3&lLocation: ")).append(locationComponent)
                .append(withColourCode("\n&8*********Comments*********")).reset();
        ticket.getComments().forEach(c -> message.append("\n[" + c.user + "]: ").color(ChatColor.DARK_AQUA).bold(true).append(c.comment).color(ChatColor.GRAY).bold(false));
        return message.create();
    }

    private boolean senderCannotPerformSelfOtherAction(CommandSender sender, Ticket ticket, String otherPermission, String selfPermission) {
        return !TicketManager.getPermissions().has(sender, otherPermission) &&
                (!TicketManager.getPermissions().has(sender, selfPermission) ||
                        !ticket.getUUID().isPresent() ||
                        !(sender instanceof Player) ||
                        !((Player) sender).getUniqueId().equals(ticket.getUUID().get()));
    }

    private String priorityToColorCode(Ticket ticket) {
        switch (ticket.getPriority()) {
            case 1: return "&1";
            case 2: return "&9";
            case 4: return "&c";
            case 5: return "&4";
            default: return "&e";
        }
    }

    private String priorityToString(Ticket ticket) {
        switch (ticket.getPriority()) {
            case 1: return "LOWEST";
            case 2: return "LOW";
            case 4: return "HIGH";
            case 5: return "HIGHEST";
            default: return "NORMAL";
        }
    }

    private String statusToColorCode(Ticket ticket) {
        return ticket.getStatus().equals("CLOSED") ? "&c" : "&a";
    }

    String withColourCode(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}