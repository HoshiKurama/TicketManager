package engineer.hoshikurama.github.ticketmanager.v2;

import com.google.common.collect.Lists;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

class TMCommands implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length <= 0) {
            Bukkit.getScheduler().runTaskAsynchronously(TicketManager.getInstance(), () -> sendHelpMessage(sender));
            return true;
        }

        if (TicketManager.conversionInProgress.get()) {
            sender.sendMessage(withColourCode("[TicketManager]&c Unable to process requests due to on-going database conversion!"));
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(TicketManager.getInstance(), () -> {
            try {

                // Sets perform silently value
                boolean performSilently;
                if (args[0].startsWith("s."))
                    if (senderHasPermission(sender, "ticketmanager.silentcommands")){
                        performSilently = true;
                        args[0] = args[0].replaceFirst("s.", "");
                    } else throw new TMInvalidDataException("You do not have permission to perform this command!");
                else performSilently = false;

                switch (args[0]) {
                    case "list": listOpenTicketsCommand(sender, args); break;
                    case "view": viewTicketCommand(sender, args); break;
                    case "create": createTicketCommand(sender, args); break;
                    case "comment": commentTicketCommand(sender, args, performSilently, false); break;
                    case "close": closeTicketCommand(sender, args, performSilently); break;
                    case "assign": assignTicketCommand(sender, args, performSilently); break;
                    case "claim": claimTicketCommand(sender, args,performSilently); break;
                    case "unassign": unassignTicketCommand(sender, args, performSilently); break;
                    case "setpriority": setPriorityTicketCommand(sender, args, performSilently); break;
                    case "teleport": teleportTicketCommand(sender, args); break;
                    case "reopen": reopenTicketCommand(sender, args, performSilently); break;
                    case "closeall": closeAllTicketsCommand(sender, args, performSilently); break;
                    case "reload": reloadConfig(sender); break;
                    case "history": viewHistoryCommand(sender, args);break;
                    case "search": searchCommand(sender, args);
                }
            } catch (TMInvalidDataException e) {
                sender.sendMessage(withColourCode("&c" + e.getMessage()));
            } catch (NumberFormatException e) {
                sender.sendMessage(withColourCode("&c Please input a valid number!"));
            } catch (Exception e) {
                pushWarningNotification(e);
            }
        });
        return false;
    }

    // TicketManager command methods
    void claimTicketCommand(CommandSender sender, String[] args, boolean performSilently) throws SQLException, TMInvalidDataException {
        if (args.length <= 1) throw new TMInvalidDataException("Please at least have a ticket ID!");
        assignTicketCommand(sender, new String[] {args[0], args[1], sender.getName()}, performSilently);
    }

    void unassignTicketCommand(CommandSender sender, String[] args, boolean performSilently) throws SQLException, TMInvalidDataException {
        if (args.length <= 1) throw new TMInvalidDataException("Please at least have a ticket ID!");
        assignTicketCommand(sender, new String[]{args[0], args[1], " "}, performSilently);
    }

    void listOpenTicketsCommand(CommandSender sender, String[] args) throws SQLException, TMInvalidDataException  {
        if (!senderHasPermission(sender, "ticketmanager.list"))
            throw new TMInvalidDataException("You do not have permission to perform this command!");

        int page;
        if (args.length > 1) page = Integer.parseInt(args[1]);
        else page = 1;

        // Converts all open tickets to associated components
        List<BaseComponent> allComponents =  DatabaseHandler.getOpenTickets().stream()
                .sorted(Comparator.comparing(Ticket::getPriority).reversed().thenComparing(Comparator.comparing(Ticket::getId).reversed()))
                .flatMap(t -> {
                    String comment = t.getComments().get(0).comment;
                    int idLength = String.valueOf(t.getId()).length();

                    // Shortens comment preview to fit on one line
                    if (13 + idLength + t.getCreator().length() + t.getAssignment().length() + comment.length() > 58)
                        comment = comment.substring(0, 43 - idLength - t.getAssignment().length() - t.getCreator().length()) + "...";

                    return Arrays.stream(new ComponentBuilder(withColourCode("\n" + priorityToColorCode(t) + "[" +
                            t.getId() + "] &8[&3" + t.getCreator() + "&8 —> &3" + t.getAssignment() + "&8] &f" + comment))
                            .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ticket view " + t.getId()))
                            .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to view ticket #" + t.getId())))
                            .create());
                })
                .collect(Collectors.toList());
        allComponents.add(0, new ComponentBuilder(withColourCode("&3[TicketManager] &fViewing all open tickets:")).getComponent(0));

        List<BaseComponent> finalComponents = implementPageAt(page, getPartitionedComponents(0, 10,allComponents), "/ticket list ");
        sender.sendMessage(finalComponents.toArray(new BaseComponent[]{}));
    }

    void viewTicketCommand(CommandSender sender, String[] args) throws SQLException, TMInvalidDataException {
        if (args.length <= 1) throw new TMInvalidDataException("Please enter a ticket number to view!");

        Ticket ticket = DatabaseHandler.getTicket(Integer.parseInt(args[1]));

        if (!senderHasPermission(sender, "ticketmanager.view.others") && playerLacksValidSelfPermission(sender, ticket, "ticketmanager.view.self"))
            throw new TMInvalidDataException("You do not have permission to view this ticket!");

        // Determines what to do with location
        BaseComponent[] locationComponent;
        if (ticket.getLocation().isPresent()) {
            Ticket.Location loc = ticket.getLocation().get();
            locationComponent = new ComponentBuilder(withColourCode("&f&n" + loc.worldName + "   " + loc.x + " " + loc.y + " " + loc.z))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ticket teleport " + ticket.getId()))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to teleport to ticket #" + ticket.getId())))
                    .create();
        } else locationComponent = new ComponentBuilder("").create();

        // Builds base message and sends
        ComponentBuilder message = new ComponentBuilder(withColourCode("&3&l[TicketManager] Ticket #" + ticket.getId()))
                .append(withColourCode("\n&r&8**************************"))
                .append(withColourCode("\n&3&lCreator: &f" + ticket.getCreator() + "  &f&l &3&lAssigned To: &f" + ticket.getAssignment() + ""))
                .append(withColourCode("\n&3&lPriority: &f" + priorityToColorCode(ticket) + priorityToString(ticket) + "  &f&l  &3&lStatus: &f" + statusToColorCode(ticket) + ticket.getStatus()))
                .append(withColourCode("\n&3&lLocation: ")).append(locationComponent).append("").reset()
                .append(withColourCode("\n&3&lCreated: &r&f" + getBiggestTime(ticket.getCreationTime()).replace(':',' ')))
                .append(withColourCode("\n&8*********Comments*********")).reset();
        ticket.getComments().forEach(c -> message.append(withColourCode("\n&3&l[" + c.user + "]: &r" + c.comment)));
        sender.sendMessage(message.create());

        // Removes player from updatedTickets table if player views own updated ticket
        if (!nonCreatorMadeChange(sender, ticket)) {
            ticket.setUpdatedByOtherUser(false);
            DatabaseHandler.updateTicket(ticket);
        }

    }

    void createTicketCommand(CommandSender sender, String[] args) throws SQLException, TMInvalidDataException {
        if (args.length <= 1)
            throw new TMInvalidDataException("You cannot make a blank ticket!");
        if (!senderHasPermission(sender, "ticketmanager.create"))
            throw new TMInvalidDataException("You do not have permission to create tickets!");

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Ticket ticket = new Ticket(sender, message);
        DatabaseHandler.createTicket(ticket);

        // Notifications
        if (!senderHasPermission(sender, "ticketmanager.notify.onCreate"))
            sender.sendMessage(withColourCode("&3Ticket #" + ticket.getId() + " has been successfully created!"));
        pushMassNotification("ticketmanager.notify.onCreate",
                withColourCode("&3[TicketManager] &7" + ticket.getCreator() + "&3 has created ticket &7#" + ticket.getId() + "&3:\n&7" + message));
    }

    void commentTicketCommand(CommandSender sender, String[] args, boolean silent, boolean passThrough) throws SQLException, TMInvalidDataException {
        if (args.length <= 2) throw new TMInvalidDataException("Please enter a ticket number and/or comment!");
        Ticket ticket = DatabaseHandler.getTicket(Integer.parseInt(args[1]));

        if (!senderHasPermission(sender, "ticketmanager.comment.others") && playerLacksValidSelfPermission(sender, ticket, "ticketmanager.comment.self"))
            throw new TMInvalidDataException("You do not have permission to perform this command!");

        if (ticket.getStatus().equals("CLOSED"))
            throw new TMInvalidDataException("You cannot comment on closed tickets!");

        // Adds and updates tickets
        boolean nonCreatorMadeChange = nonCreatorMadeChange(sender, ticket);
        String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        ticket.addComment(sender, message);
        ticket.setUpdatedByOtherUser(nonCreatorMadeChange);
        DatabaseHandler.updateTicket(ticket);

        // Sends notifications
        if ((!senderHasPermission(sender, "ticketmanager.notify.onUpdate.others") && !passThrough) ||
                (senderHasPermission(sender, "ticketmanager.notify.onUpdate.others") && !passThrough && silent))
            sender.sendMessage(withColourCode("&3[TicketManager] Ticket comment successful!"));

        if (!silent) {
            pushMassNotification("ticketmanager.notify.onUpdate.others",
                    withColourCode("&3[TicketManager] &7" + sender.getName() + " &3has commented on ticket &7#" + ticket.getId() + "&3:\n  &7" + message));
            if (nonCreatorMadeChange)
                pushUserNotification(ticket, "ticketmanager.notify.onUpdate.self",
                        withColourCode("&3[TicketManager] Ticket #" + ticket.getId() + " has been updated! Please type &7/ticket view " +
                                ticket.getId() + "&3 to view this ticket."));
        }
    }

    void closeTicketCommand(CommandSender sender, String[] args, boolean silent) throws SQLException, TMInvalidDataException {
        if (args.length <= 1) throw new TMInvalidDataException("Please enter a ticket number and/or comment!");

        // If closing command with comment, run comment creator first
        if (args.length > 2) commentTicketCommand(sender, args,false, true);

        Ticket ticket = DatabaseHandler.getTicket(Integer.parseInt(args[1]));

        // Does filter stuff
        if (!senderHasPermission(sender, "ticketmanager.close.others") && playerLacksValidSelfPermission(sender, ticket, "ticketmanager.close.self"))
            throw new TMInvalidDataException("You do not have permission to perform this command!");
        if (ticket.getStatus().equals("CLOSED")) {
            sender.sendMessage(withColourCode("&cYou cannot close tickets already closed!"));
            return;
        }

        // Set ticket status and perform database stuff
        ticket.setStatus("CLOSED");
        boolean nonCreatorChange = nonCreatorMadeChange(sender, ticket);
        ticket.setUpdatedByOtherUser(nonCreatorChange);
        DatabaseHandler.updateTicket(ticket);

        // Notifications
        if ((!senderHasPermission(sender, "ticketmanager.notify.onClose.others")) ||
                (senderHasPermission(sender, "ticketmanager.notify.onClose.others") && silent))
            sender.sendMessage(withColourCode("&3[TicketManager] Ticket closed!"));

        if (!silent) {
            pushMassNotification( "ticketmanager.notify.onClose.others",
                    withColourCode("&3[TicketManager] Ticket &7#" + ticket.getId() + " &3has been closed by &7" + sender.getName()));
            pushUserNotification(ticket, "ticketmanager.notify.onClose.self",
                    withColourCode("&3[TicketManager] Ticket #" + ticket.getId() + " has been closed! Please type &7/ticket view " +
                            ticket.getId() + " &3 to view this ticket."));
        }
    }

    void assignTicketCommand(CommandSender sender, String[] args, boolean silent) throws SQLException, TMInvalidDataException {
        if (args.length <= 1) throw new TMInvalidDataException("Please at least have a ticket ID!");

        Ticket ticket = DatabaseHandler.getTicket(Integer.parseInt(args[1]));
        if (!senderHasPermission(sender,"ticketmanager.assign"))
            throw new TMInvalidDataException("You do not have permission to perform this command!");

        ticket.setAssignment(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
        if (nonCreatorMadeChange(sender, ticket))
            ticket.setUpdatedByOtherUser(true);
        DatabaseHandler.updateTicket(ticket);

        //Notifications
        if (!senderHasPermission(sender,"ticketmanager.notify.onUpdate.others") || silent)
            sender.sendMessage(withColourCode("&3Ticket has been successfully assigned!"));
        if (!silent) pushMassNotification("ticketmanager.notify.onUpdate.others",
                withColourCode("&3[TicketManager] Ticket &7#" + ticket.getId() + "&3 has been assigned to &7" + ticket.getAssignment()));
    }

    void setPriorityTicketCommand(CommandSender sender, String[] args, boolean silent) throws SQLException, TMInvalidDataException {
        if (!senderHasPermission(sender, "ticketmanager.setpriority"))
            throw new TMInvalidDataException("You do not have permission to set ticket priorities!");
        if (args.length != 3) throw new TMInvalidDataException("Please use the correct format!");

        // Fixes incorrect priority values
        byte priority = Byte.parseByte(args[2]);
        if (priority < 1) priority = 1;
        else if (priority > 5) priority = 5;

        Ticket ticket = DatabaseHandler.getTicket(Integer.parseInt(args[1]));

        if (ticket.getStatus().equals("CLOSED"))
            throw new TMInvalidDataException("Why would you try to change the priority of a closed ticket?");

        ticket.setPriority(priority);
        if (nonCreatorMadeChange(sender, ticket))
            ticket.setUpdatedByOtherUser(true);
        DatabaseHandler.updateTicket(ticket);

        // Notifications
        if (!senderHasPermission(sender, "ticketmanager.notify.onUpdate.others") || silent)
            sender.sendMessage(withColourCode("&3Ticket priority changed successfully!"));

        if (!silent) {
            pushMassNotification("ticketmanager.notify.onUpdate.others",
                    withColourCode("&3[TicketManager] " + sender.getName() + " &7has set ticket &3#" + ticket.getId() +
                            "&7's priority to " + priorityToColorCode(ticket) + priorityToString(ticket)));

        }
    }

    void teleportTicketCommand(CommandSender sender, String[] args) throws SQLException, TMInvalidDataException {
        if (args.length <= 1) throw new TMInvalidDataException("Please enter a ticket number to view!");
        if (!senderHasPermission(sender, "ticketmanager.teleport"))
            throw new TMInvalidDataException("You do not have permission to perform this command!");

        Ticket ticket = DatabaseHandler.getTicket(Integer.parseInt(args[1]));
        ticket.getLocation().ifPresent(loc ->
                Bukkit.getScheduler().runTask(TicketManager.getInstance(), () -> {
                    if (!(sender instanceof Player)) return;
                    Player player = (Player) sender;
                    player.teleport(new Location(Bukkit.getWorld(loc.worldName), loc.x, loc.y, loc.z));
                }));
    }

    void reopenTicketCommand(CommandSender sender, String[] args, boolean silent) throws SQLException, TMInvalidDataException {
       if (args.length <= 1) throw new TMInvalidDataException("Please enter a ticket number!");
       if (!senderHasPermission(sender, "ticketmanager.reopen"))
           throw new TMInvalidDataException("You do not have permission to perform this command!");

       Ticket ticket = DatabaseHandler.getTicket(Integer.parseInt(args[1]));

       if (ticket.getStatus().equals("OPEN")) throw new TMInvalidDataException("This ticket is already open!");

       ticket.setStatus("OPEN");
       if (nonCreatorMadeChange(sender, ticket))
           ticket.setUpdatedByOtherUser(true);
       DatabaseHandler.updateTicket(ticket);

       // Notifications
        if (!senderHasPermission(sender, "ticketmanager.notify.onUpdate.others") || silent)
            sender.sendMessage(withColourCode("&3Ticket re-opened successfully!"));
       if (!silent) {
           pushMassNotification("ticket.notify.onUpdate.others",
                   withColourCode("&3[TicketManager] &7" + sender.getName() + "&3 has reopened ticket &7" + ticket.getId() + "&3."));
           pushUserNotification(ticket, "ticket.notify.onUpdate.self",
                   withColourCode("&3Ticket # " + ticket.getId() + " has been reopened!"));
       }
    }

    void closeAllTicketsCommand(CommandSender sender, String[] args, boolean silent) throws SQLException, TMInvalidDataException {
        if (!senderHasPermission(sender, "ticketmanager.closeall"))
            throw new TMInvalidDataException("You do not have permission to perform this command!");
        if (args.length != 3) throw new TMInvalidDataException("Please use the correct syntax!");

        int lower = Integer.parseInt(args[1]);
        int upper = Integer.parseInt(args[2]);

        try (Connection connection = HikariCP.getConnection()) {
            PreparedStatement stmt = connection.prepareStatement("UPDATE TicketManagerTicketsV2 SET STATUS = ? WHERE ID BETWEEN ? AND ?");
            stmt.setString(1, "CLOSED");
            stmt.setInt(2, lower);
            stmt.setInt(3, upper);
            stmt.executeUpdate();
        }

        if (!senderHasPermission(sender, "ticketmanager.notify.onUpdate.others") || silent)
            sender.sendMessage(withColourCode("&3Ticket mass close successful!"));
        if (!silent) {
            pushMassNotification("ticketmanager.notify.onUpdate.others",
                    withColourCode("&3[TicketManager] " + sender.getName() + " &7has mass closed tickets &3#" + lower + "&7 to &3#" + upper));
        }
    }

    void reloadConfig(CommandSender sender) throws SQLException, TMInvalidDataException {
        if (!senderHasPermission(sender, "ticketmanager.reload"))
            throw new TMInvalidDataException("You do not have permission to reload this plugin!");

        FileConfiguration config = TicketManager.getInstance().config;
        HikariCP.LaunchDatabase(config.getString("Host"),
                config.getString("Port"),
                config.getString("DB_Name"),
                config.getString("Username"),
                config.getString("Password"));
        sender.sendMessage(withColourCode("&3[TicketManager] Config reloaded successfully. Testing database connection..."));
        Connection connection = HikariCP.getConnection();
        connection.close();
        sender.sendMessage(withColourCode("&3[TicketManager] Database connection established!"));

        // Checks for detected conversion and initiates
        if (DatabaseHandler.conversionIsRequired()) {
            TicketManager.conversionInProgress.set(true);
            DatabaseHandler.initiateConversionProcess();
            TicketManager.conversionInProgress.set(false);
        }
    }

    void sendHelpMessage(CommandSender sender) {
        // Filters permissions
        if (sender instanceof Player) {
            if (!TicketManager.getPermissions().has(sender, "ticketmanager.help.all") && !TicketManager.getPermissions().has(sender, "ticketmanager.help.basic")) {
                sender.sendMessage(withColourCode("&cYou do not have permission to view ticket commands!"));
                return;
            }
        }

        StringBuilder builder = new StringBuilder();
        builder.append("&3[TicketManager] Ticket Commands:  &f<Required> | [Optional]")
                .append("\n&3/ticket &fcreate <Message...>")
                .append("\n&3/ticket &fcomment <Ticket ID> <Message...>")
                .append("\n&3/ticket &fview <Ticket ID>")
                .append("\n&3/ticket &fhistory [Username]")
                .append("\n&3/ticket &fclose <Ticket ID> [Message...]");

        if (TicketManager.getPermissions().has(sender, "ticketmanager.help.all"))
            builder.append("\n&3/ticket &fassign <Ticket ID> <User/Assignment>")
                    .append("\n&3/ticket &funassign <Ticket ID>")
                    .append("\n&3/ticket &fclaim <Ticket ID>")
                    .append("\n&3/ticket &freopen <Ticket ID>")
                    .append("\n&3/ticket &fsetpriority <Ticket ID> <Priority (1-5)>")
                    .append("\n&3/ticket &flist")
                    .append("\n&3/ticket &fcloseall <Lower Bound> <Upper Bound>")
                    .append("\n&3/ticket &fteleport <Ticket ID>")
                    .append("\n&3/ticket &freload")
                    .append("\n&3/ticket search <Constraints...>")
                    .append("\n&3Silent versions: s.<command>");

        sender.sendMessage(withColourCode(builder.toString()));
    }

    void viewHistoryCommand(CommandSender sender, String[] args) throws SQLException, TMInvalidDataException {
        UUID targetID;
        String[] correctedArgs = new String[3];
        correctedArgs[0] = "history";

        //Fills in correctedArgs [history,NAME,PAGE]
        correctedArgs[1] = args.length >= 2 ? args[1] : sender.getName();
        if (args.length >= 3) correctedArgs[2] = args[2];
        else correctedArgs[2] = "1";

        // Assigns targetUUID based on permissions
        if (!senderHasPermission(sender, "ticketmanager.history.others")) {
            if (TicketManager.getPermissions().has(sender, "ticketmanager.history.self")) targetID = ((Player) sender).getUniqueId();
            else throw new TMInvalidDataException("You do not have permission to view this person's ticket history!");
        } else targetID = Bukkit.getPlayerUniqueId(correctedArgs[1]); //Could be null if Console

        //Confirms targetUUID is valid or is Console
        if (correctedArgs[1].equalsIgnoreCase("console")) targetID = null;
        else if (targetID == null) throw new TMInvalidDataException("This is not a valid user!");

        List<Ticket> playerTickets = DatabaseHandler.getTicketsWithUUID(targetID);
        String name = targetID == null ? "Console" : Bukkit.getOfflinePlayer(targetID).getName();

        if (playerTickets.size() == 0) sender.sendMessage(withColourCode("&3[TicketManager] &f" + correctedArgs[1] + " has 0 tickets."));
        else {
            sender.sendMessage(withColourCode("&3[TicketManager] Querying results..."));
            // Builds and creates initial data
            ComponentBuilder components = new ComponentBuilder(withColourCode("&3[TicketManager] &f" + name + " has " + playerTickets.size() + " tickets:"));
            playerTickets.stream()
                    .sorted(Comparator.comparing(Ticket::getId).reversed())
                    .map(t -> {
                        String comment = t.getComments().get(0).comment;
                        int idLength = Integer.toString(t.getId()).length();

                        // Shortens comments
                        if (6 + idLength + t.getStatus().length() + comment.length() > 58)
                            comment = comment.substring(0, 49 - idLength - t.getStatus().length()) + "...";

                        return new ComponentBuilder(withColourCode("\n&3[" + t.getId() + "] " + statusToColorCode(t) + "[" + t.getStatus() + "] &f" + comment))
                                .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ticket view " + t.getId()))
                                .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to view ticket #" + t.getId())))
                                .create();
                    })
                    .forEach(components::append);

            List<BaseComponent> correctPage = implementPageAt(Integer.parseInt(correctedArgs[2]),
                    getPartitionedComponents(0, 10, Arrays.asList(components.create())),
                    "/ticket history " + correctedArgs[1] + " ");
            sender.sendMessage(correctPage.toArray(new BaseComponent[0]));
        }
    }

    void searchCommand(CommandSender sender, String[] args) throws SQLException, TMInvalidDataException {
        // /ticket search keywords:separated,by,commas status:OPEN/CLOSED time:5w creator:creator priority:value assignedto:player world:world
        if (!senderHasPermission(sender, "ticketmanager.search"))
            throw new TMInvalidDataException("You do not have permission to perform this command!");
        if (args.length < 2) throw new TMInvalidDataException("You must have at least one search term!");

        try (Connection connection = HikariCP.getConnection()) {
            StringBuilder sqlStatement = new StringBuilder("SELECT * FROM TicketManagerTicketsV2 WHERE ");
            List<String> searches = new ArrayList<>(Arrays.asList(args));
            List<String> arguments = new ArrayList<>();
            searches.remove(0);
            AtomicInteger atomicPage = new AtomicInteger(1);

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
            List<Ticket> tickets = DatabaseHandler.getTicketsFromRS(stmt.executeQuery());
            if (tickets.size() == 0) sender.sendMessage(withColourCode("&3[TicketManager]&f Your search query returned 0 results"));
            else {
                List<BaseComponent> ticketComponents = tickets.stream()
                        .sorted(Comparator.comparing(Ticket::getCreationTime).reversed())
                        .flatMap(t -> {
                            String world = t.getLocation().isPresent() ? t.getLocation().get().worldName : "null";
                            String comment = t.getComments().get(0).comment;
                            String biggestTime = getBiggestTime(t.getCreationTime());

                            // Shortens comment preview to fit on one line
                            if (12 + comment.length() + biggestTime.length() > 60)
                                comment = comment.substring(0, 46 - biggestTime.length()) + "...";

                            return Arrays.stream(new ComponentBuilder(withColourCode("\n" + priorityToColorCode(t) + "[" + t.getId() + "] " + statusToColorCode(t) + "[" +
                                    t.getStatus() + "] &8[&3" + t.getCreator() + "&8 -> &3" + t.getAssignment() + "&8]&3 [World " + world + "]"))
                                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ticket view " + t.getId()))
                                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Click to view ticket #" + t.getId())))
                                    .append(withColourCode("\n          &3" + biggestTime + "&f" + comment))
                                    .create());
                        })
                        .collect(Collectors.toList());
                ticketComponents.add(0, new ComponentBuilder(withColourCode("&3[TicketManager]&f Search query returned " + tickets.size() + " results:")).getComponent(0));

                // It just does stuff, okay? Internal page count
                searches.remove(searches.size() - 1); // Removes internal page
                String params = String.join(" ", searches);

                List<BaseComponent> sentMSG = implementPageAt(atomicPage.get(), getPartitionedComponents(0, 20, ticketComponents),
                        "/ticket search " + params + " -page:");
                sender.sendMessage(sentMSG.toArray(new BaseComponent[0]));
            }
        }
    }

    // Helper methods

    private boolean senderHasPermission(CommandSender sender, String permission) {
        if (sender instanceof Player) return TicketManager.getPermissions().has(sender, permission);
        else return true;
    }

    private boolean playerLacksValidSelfPermission(CommandSender sender, Ticket ticket, String permission) {
        Player player = (Player) sender;
        if (TicketManager.getPermissions().has(player, permission))
            return !ticket.UUIDMatches(player.getUniqueId());
        else return true;
    }

    private List<List<BaseComponent>> getPartitionedComponents(int headerEndIndex,int maxLength, List<BaseComponent> components) {
        if (components.size() <  maxLength - 1) return Collections.singletonList(components);

        List<BaseComponent> headerComponents = components.subList(0, headerEndIndex + 1);
        List<BaseComponent> nonHeaderComponents = components.subList(headerEndIndex + 1, components.size());

        return Lists.partition(nonHeaderComponents, maxLength - 2 - headerEndIndex).stream()
                .map(e -> {
                    List<BaseComponent> nonConcurrent =  new ArrayList<>(e);
                    nonConcurrent.addAll(0, headerComponents);
                    return nonConcurrent;
                })
                .collect(Collectors.toList());
    }

    private List<BaseComponent> implementPageAt(int page, List<List<BaseComponent>> partitionedComponents, String buttonCommand) {

        // Establishes max pages and fixes out-of-bounds page values
        int maxPages = partitionedComponents.size();
        if (maxPages == 0) maxPages = 1;
        if (page <= 0) page = 1;
        if (page > maxPages) page = maxPages;

        // Gets requested page
        List<BaseComponent> partitionedPage = partitionedComponents.get(page - 1);

        // If there is only one page, add navigation row
        if (maxPages > 1) {
            ComponentBuilder arrowNode = new ComponentBuilder("\n[Back]");

            if (page == 1) arrowNode.color(ChatColor.DARK_GRAY);
            else arrowNode.color(ChatColor.WHITE)
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Move to previous page")))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, buttonCommand + (page - 1)));

            arrowNode.append("").reset().append(withColourCode("&f.......................&3(" + page + " of " + maxPages + ")&f.......................")).append("[Next]");

            if (page == maxPages) arrowNode.color(ChatColor.DARK_GRAY);
            else arrowNode.color(ChatColor.WHITE)
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("Move to next page")))
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, buttonCommand + (page + 1)))
                    .append("").reset();

            partitionedPage.addAll(arrowNode.getParts());
        }

        return partitionedPage;
    }

    void pushMassNotification(String permissionNode, String message) {
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> TicketManager.getPermissions().has(p, permissionNode))
                .forEach(p -> p.sendMessage(withColourCode(message)));
    }

    boolean nonCreatorMadeChange(CommandSender sender, Ticket ticket) {
        if (sender instanceof Player) return !ticket.UUIDMatches((((Player) sender).getUniqueId()));
        else return !ticket.UUIDMatches(null);
    }

    void pushUserNotification(Ticket ticket, String permissionNode, String message) {
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> ticket.UUIDMatches(p.getUniqueId()))
                .findFirst()
                .ifPresent(p -> {
                    if (TicketManager.getPermissions().has(p, permissionNode)) p.sendMessage(withColourCode(message));
                });
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

    private long convertRelTimeToEpochSecond(String relTime) {
       ArrayList<StringBuilder> values = new ArrayList<>();
        StringBuilder builder = new StringBuilder(relTime);
        values.add(new StringBuilder());
        int curIndex = 0;

        // Creates list of the time strings
        while (!builder.toString().equals("")) {
            String value = String.valueOf(builder.toString().charAt(0));
            try {
                Byte.parseByte(value);
                values.get(curIndex).append(value);
            } catch (NumberFormatException ignored) {
                values.get(curIndex).append(value);
                values.add(new StringBuilder());
                curIndex++;
            }
            builder.deleteCharAt(0);
        }
        values.remove(values.size() - 1); //removes extra stringBuilder

        // Converts to epoch seconds and subtracts from current time
        return Instant.now().getEpochSecond() - values.stream()
                .map(StringBuilder::toString)
                .mapToLong(str -> {
                    if (str.contains("y")) return Long.parseLong(str.split("y")[0]) * 31556952L;
                    else if (str.contains("w")) return Long.parseLong(str.split("w")[0]) * 604800L;
                    else if (str.contains("d")) return Long.parseLong(str.split("d")[0]) * 86400L;
                    else if (str.contains("h")) return Long.parseLong(str.split("h")[0]) * 3600L;
                    else if (str.contains("m")) return Long.parseLong(str.split("m")[0]) * 60L;
                    else if (str.contains("s")) return Long.parseLong(str.split("s")[0]);
                    else return 0L;
                })
                .sum();
    }

    private String getBiggestTime(long epochTime) {
        long timeAgo = Instant.now().getEpochSecond() - epochTime;

        if (timeAgo >= 31556952L) return (timeAgo / 31556952L) + " years ago: ";
        else if (timeAgo >= 604800L) return (timeAgo / 604800L) + " weeks ago: ";
        else if (timeAgo >= 86400L) return (timeAgo / 86400L) + " days ago: ";
        else if (timeAgo >= 3600L) return (timeAgo / 3600L) + " hours ago: ";
        else if (timeAgo >= 60L) return (timeAgo / 60L) + " minutes ago: ";
        else return (timeAgo) + " seconds ago: ";
    }

    static <T extends Exception> void pushWarningNotification(T e) {
        ComponentBuilder builder = new ComponentBuilder("\n\n\n[TicketManager] Warning! An error has occurred!").color(ChatColor.DARK_RED)
                .append("\n     Exception Type:  " + e.getClass().getSimpleName()).color(ChatColor.RED)
                .append("\n     Information:  " + e.getMessage()).color(ChatColor.RED)
                .append("\n=-=-=-=-=-=-=Modified Stacktrace:=-=-=-=-=-=-=").color(ChatColor.DARK_RED);

        // Adds stacktrace entries
        Arrays.stream(e.getStackTrace())
                .filter(f -> f.getClassName().startsWith("engineer.hoshikurama.github.ticketmanager"))
                .map(f -> new ComponentBuilder("\n[WARNING] " + f.getMethodName() + " (" + f.getFileName() + ":" + f.getLineNumber() + ")").color(ChatColor.RED))
                .map(ComponentBuilder::create)
                .forEach(builder::append);
        BaseComponent[] message = builder.create();

        //Pushes notification
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> TicketManager.getPermissions().has(p, "ticketmanager.notify.warning"))
                .forEach(p -> p.sendMessage(message));
        e.printStackTrace();
    }
}
