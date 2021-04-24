package engineer.hoshikurama.github.ticketmanager.v2;

import engineer.hoshikurama.github.ticketmanager.v2.databases.Database;
import engineer.hoshikurama.github.ticketmanager.v2.databases.mysql.MySQL;
import engineer.hoshikurama.github.ticketmanager.v2.databases.sqlite.SQLite;
import engineer.hoshikurama.github.ticketmanager.v2.sideClasses.Metrics;
import engineer.hoshikurama.github.ticketmanager.v2.sideClasses.UpdateChecker;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static engineer.hoshikurama.github.ticketmanager.v2.TMCommands.withColourCode;

public final class TicketManager extends JavaPlugin implements Listener {
    private volatile static Database database;
    FileConfiguration config;
    private static Permission perms;
    private static TicketManager instance;
     AtomicBoolean conversionInProgress;
    static AtomicBoolean updateAvailable;

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

            // Check console
            readConfig(null);

            if (database == null) return;

            // Scheduled broadcast to players
            scheduler.runTaskTimerAsynchronously(this, () -> {
                if (conversionInProgress.get() || database == null) return;

                try {
                    List<Ticket> openTickets = database.getOpenTickets();
                    List<Ticket> unreadTickets = database.getUnreadTickets();

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
        if (conversionInProgress.get() || database == null) return;

        Player player = event.getPlayer();
        boolean playerCanSeeUpdatedTicket = perms.has(player, "ticketmanager.notify.playerJoinUpdatedTickets");
        boolean playerCanSeeOpenTickets = perms.has(player, "ticketmanager.notify.playerJoinOpenTickets");
        boolean shouldShowPluginUpdate = updateAvailable.get() && perms.has(player, "ticketmanager.notify.update");

        if (playerCanSeeUpdatedTicket || playerCanSeeOpenTickets)
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    List<Ticket> tickets;

                    if (playerCanSeeOpenTickets) {
                        tickets = database.getOpenTickets();
                        long assignedToPlayer = tickets.stream()
                                .filter(t -> t.getAssignment().equalsIgnoreCase(player.getName()))
                                .count();
                        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                "&3[TicketManager] &7" + tickets.size() + "&3 tickets open (&7" + assignedToPlayer + "&3 assigned to you)"));
                    }

                    if (playerCanSeeUpdatedTicket) {
                        tickets = database.getUnreadTicketsForPlayer(player);
                        if (tickets.size() > 0) {
                            String verbForm = tickets.size() == 1 ? "has" : "have";
                            String nounForm = tickets.size() == 1 ? "Ticket" : "Tickets";
                            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                    "&3[TicketManager] " + nounForm + " &7" + tickets.stream().map(Ticket::getId).map(String::valueOf).collect(Collectors.joining("&3,&7 "))
                                            + "&3 " + verbForm + " pending notifications! " + "Type &7/ticket view <ID> &3to dismiss this notification"));
                        }
                    }

                    if (shouldShowPluginUpdate) {
                        ComponentBuilder msg = new ComponentBuilder("[TicketManager] A new update is available! Click on this message to visit the Spigot page!")
                                .color(ChatColor.DARK_AQUA)
                                .event(new ClickEvent(ClickEvent.Action.OPEN_URL,"https://www.spigotmc.org/resources/ticketmanager.91178/"));
                        player.sendMessage(msg.create());
                    }

                } catch (DatabaseException e) {
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

    static Database dbHandler() {
        return database;
    }

    static void readConfig(@Nullable CommandSender sender) throws DatabaseException {
        TicketManager.getInstance().reloadConfig();

        try {
            if (!(new File(TicketManager.getInstance().getDataFolder(), "config.yml").exists())) {
                // Config file not found
                TicketManager.getInstance().saveDefaultConfig();
                Bukkit.getLogger().log(Level.WARNING, "[TicketManager] No config file has been detected! Generating config file and entering! SQLite mode!");
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> TicketManager.getPermissions().has(p, "ticketmanager.notify.warning"))
                        .forEach(p -> p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                "&3[TicketManager]&c No config file has been detected! Creating config file and entering SQLite mode!")));
                database = new SQLite(TicketManager.getInstance());
            } else {
                // Config file found
                FileConfiguration config = TicketManager.getInstance().getConfig();
                switch (Objects.requireNonNull(config.getString("database_mode"))) {
                    case "MySQL": database = new MySQL(
                                config.getString("MySQL_Host"),
                                config.getString("MySQL_Port"),
                                config.getString("MySQL_DBName"),
                                config.getString("MySQL_Username"),
                                config.getString("MySQL_Password"));
                        break;
                    case "SQLite":
                    default: database = new SQLite(TicketManager.getInstance());
                }

                if (sender != null) sender.sendMessage(withColourCode("&3[TicketManager] Database connection established!"));

                if (database.conversionIsRequired()) {
                    TicketManager.getInstance().conversionInProgress.set(true);
                    dbHandler().initiateConversionProcess();
                    TicketManager.getInstance().conversionInProgress.set(false);
                }

                if (Objects.equals(config.getString("new_version_checker"), "true")) {
                    new UpdateChecker(TicketManager.getInstance(), 91178).getVersion(v -> {
                        if (TicketManager.getInstance().getDescription().getVersion().equalsIgnoreCase(v)) {
                            TicketManager.updateAvailable.set(true);
                            Bukkit.getLogger().log(Level.INFO, "[TicketManager] A new update is available!");
                        }
                    });
                }
            }
        } catch (DatabaseException e) {
            database = null;
            throw e;
        }
    }
}
