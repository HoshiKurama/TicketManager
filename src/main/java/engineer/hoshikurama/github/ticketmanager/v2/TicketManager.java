package engineer.hoshikurama.github.ticketmanager.v2;

import engineer.hoshikurama.github.ticketmanager.v2.sideClasses.Metrics;
import engineer.hoshikurama.github.ticketmanager.v2.sideClasses.UpdateChecker;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class TicketManager extends JavaPlugin implements Listener {
    FileConfiguration config;
    private static Permission perms;
    private static TicketManager instance;
    static AtomicBoolean conversionInProgress;
    private static AtomicBoolean updateAvailable;

    @Override
    public void onEnable() {
        try {
            // Assign fields and register plugin parts
            BukkitScheduler scheduler = Bukkit.getScheduler();
            Objects.requireNonNull(this.getCommand("ticket")).setExecutor(new TMCommands());
            Objects.requireNonNull(this.getCommand("ticket")).setTabCompleter(new TabCompletion());
            getServer().getPluginManager().registerEvents(this, this);
            perms = Objects.requireNonNull(getServer().getServicesManager().getRegistration(Permission.class)).getProvider();
            conversionInProgress = new AtomicBoolean(false);
            updateAvailable = new AtomicBoolean(false);
            new Metrics(this, 11033);
            instance = this;

            // Generates new config if not found and sends alert
            if (!new File(this.getDataFolder(), "config.yml").exists()) {
                config = this.getConfig();

                String[][] configSetup = {{"Port", ""}, {"Host", ""}, {"DB_Name", ""}, {"Username", ""}, {"Password", ""},};
                for (String[] ss : configSetup) config.addDefault(ss[0], ss[1]);

                config.options().copyDefaults(true);
                saveConfig();
                saveDefaultConfig();

                Bukkit.getLogger().log(Level.WARNING, "[TicketManager] No config file has been detected! Generating config file! Please type \"/ticket reload\" to reload plugin!");
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> perms.has(p, "ticketmanager.notify.warning"))
                        .forEach(p -> p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                "&3[TicketManager]&c No config file has been detected! Please fill out config and reload plugin!")));
            } else {
                // Launches database connection
                config = this.getConfig();
                HikariCP.LaunchDatabase(config.getString("Host"),
                        config.getString("Port"),
                        config.getString("DB_Name"),
                        config.getString("Username"),
                        config.getString("Password"));
                Connection connection = HikariCP.getConnection();
                connection.close();

                // Checks for detected conversion and initiates
                if (DatabaseHandler.conversionIsRequired()) {
                    scheduler.runTaskAsynchronously(this, () -> {
                        conversionInProgress.set(true);
                        DatabaseHandler.initiateConversionProcess();
                        conversionInProgress.set(false);
                    });
                }
            }

            // Check for updates
            new UpdateChecker(this, 91178).getVersion( v -> {
                if (!this.getDescription().getVersion().equalsIgnoreCase(v)) {
                    updateAvailable.set(true);
                    Bukkit.getLogger().log(Level.INFO, "[TicketManager] A new update is available!");
                }
            });

            // Scheduled broadcast to players
            scheduler.runTaskTimerAsynchronously(this, () -> {
                try {
                    List<Ticket> openTickets = DatabaseHandler.getOpenTickets();
                    List<Ticket> unreadTickets = DatabaseHandler.getUnreadTickets();

                    // Handles sending unread messages
                    HashMap<String, List<String>> notifications = new HashMap<>();
                    unreadTickets.forEach(t -> {
                        if (notifications.containsKey(t.getStringUUIDForMYSQL()))
                            notifications.get(t.getStringUUIDForMYSQL()).add(String.valueOf(t.getId()));
                        else
                            notifications.put(t.getStringUUIDForMYSQL(), new ArrayList<>(List.of(String.valueOf(t.getId()))));
                    });

                    notifications.entrySet().stream()
                            .map(e -> new AbstractMap.SimpleEntry<>(Bukkit.getPlayer(UUID.fromString(e.getKey())), e.getValue()))
                            .filter(e -> e.getKey() != null)
                            .filter(e -> e.getKey().isOnline())
                            .filter(e -> perms.has(e.getKey(), "ticketmanager.notify.scheduledUpdatedTickets"))
                            .forEach(e -> e.getKey().sendMessage(ChatColor.translateAlternateColorCodes('&',
                                    "&3[TicketManager] Ticket &7" + String.join("&3,&7 ", e.getValue()) + "&3 have pending notifications! " +
                                            "Type &7/ticket view <ID> &3to dismiss this notification")));

                    // Handles sending staff updates
                    int openTicketCount = openTickets.size();
                    Bukkit.getOnlinePlayers().stream()
                            .filter(p -> perms.has(p, "ticketmanager.notify.scheduledOpenTickets"))
                            .forEach(p -> {
                                long assignedTickets = openTickets.stream().filter(t -> t.getAssignment().equalsIgnoreCase(p.getName())).count();
                                p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                        "&3[TicketManager] &7" + openTicketCount + "&3 tickets open (&7" + assignedTickets + "&3 assigned to you)"));
                            });
                } catch (Exception e) {
                    TMCommands.pushWarningNotification(e);
                }
            }, 12000, 12000);
        } catch (Exception e) {
            e.printStackTrace();
            TMCommands.pushWarningNotification(e);
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean playerCanSeeUpdatedTicket = perms.has(player, "ticketmanager.notify.playerJoinUpdatedTickets");
        boolean playerCanSeeOpenTickets = perms.has(player, "ticketmanager.notify.playerJoinOpenTickets");
        boolean playerCanSeePluginUpdatesAndOneIsAvailable = updateAvailable.get() && perms.has(player, "ticketmanager.notify.update");

        if (playerCanSeeUpdatedTicket || playerCanSeeOpenTickets)
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try (Connection connection = HikariCP.getConnection()) {
                    PreparedStatement stmt;

                    if (playerCanSeeOpenTickets) {
                        stmt = connection.prepareStatement("SELECT * FROM TicketManagerTicketsV2 WHERE STATUS = ?");
                        stmt.setString(1, "OPEN");
                        List<Ticket> tickets = DatabaseHandler.getTicketsFromRS(stmt.executeQuery());
                        long assignedToPlayer = tickets.stream()
                                .filter(t -> t.getAssignment().equalsIgnoreCase(player.getName()))
                                .count();
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                "&3[TicketManager] &7" + tickets.size() + "&3 tickets open (&7" + assignedToPlayer + "&3 assigned to you)"));
                    }

                    if (playerCanSeeUpdatedTicket) {
                        stmt = connection.prepareStatement("SELECT * FROM TicketManagerTicketsV2 WHERE UPDATEDBYOTHERUSER = ? AND UUID = ?");
                        stmt.setBoolean(1, true);
                        stmt.setString(2, player.getUniqueId().toString());
                        List<Ticket> tickets = DatabaseHandler.getTicketsFromRS(stmt.executeQuery());
                        if (tickets.size() > 0) {
                            String verbForm = tickets.size() == 1 ? "has" : "have";
                            String nounForm = tickets.size() == 1 ? "Ticket" : "Tickets";
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                    "&3[TicketManager] " + nounForm + " &7" + tickets.stream().map(Ticket::getId).map(String::valueOf).collect(Collectors.joining("&3,&7 "))
                                            + "&3 " + verbForm + " pending notifications! " + "Type &7/ticket view <ID> &3to dismiss this notification"));
                        }
                    }

                    if (playerCanSeePluginUpdatesAndOneIsAvailable) {
                        ComponentBuilder msg = new ComponentBuilder("[TicketManager] A new update is available! Click on this message to visit the Spigot page!")
                                .color(ChatColor.DARK_AQUA)
                                .event(new ClickEvent(ClickEvent.Action.OPEN_URL,"https://www.spigotmc.org/resources/ticketmanager.91178/"));
                        player.sendMessage(msg.create());
                    }
                } catch (Exception e) {
                    TMCommands.pushWarningNotification(e);
                    e.printStackTrace();
                }
            });
    }

    static Permission getPermissions() {
        return perms;
    }

    static TicketManager getInstance() {
        return instance;
    }
}
