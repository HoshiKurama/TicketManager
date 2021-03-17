package engineer.hoshikurama.github.ticketmanager;

import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class TicketManager extends JavaPlugin {
    FileConfiguration config;
    private static Permission perms;
    static TicketManager getInstance;

    @Override
    public void onEnable() {
        BukkitScheduler scheduler = Bukkit.getScheduler();

        try {
            Objects.requireNonNull(this.getCommand("ticket")).setExecutor(new TicketCommands());
            Objects.requireNonNull(getCommand("ticket")).setTabCompleter(new TabCompleter());

            perms = Objects.requireNonNull(getServer().getServicesManager().getRegistration(Permission.class)).getProvider();
            getInstance = this;

            // Generates New Config if not found and stops plugin
            if (!new File(this.getDataFolder(), "config.yml").exists()) {
                config = this.getConfig();

                String[][] configSetup = {{"Port", ""}, {"Host", ""}, {"DB_Name", ""}, {"Username", ""}, {"Password", ""},};
                for (String[] ss : configSetup) config.addDefault(ss[0], ss[1]);

                config.options().copyDefaults(true);
                saveConfig();
                saveDefaultConfig();

                Bukkit.getLogger().log(Level.WARNING, "Plugin is being shut down as first time setup is detected. Please fill out config and restart server!");
                getServer().getPluginManager().disablePlugin(this);
            }

            // Launches database services with config credentials
            config = this.getConfig();
            Hikari.LaunchDatabase(config.getString("Host"),
                    config.getString("Port"),
                    config.getString("DB_Name"),
                    config.getString("Username"),
                    config.getString("Password"));

        } catch (SQLException e) {
            e.printStackTrace();
            Bukkit.getLogger().log(Level.SEVERE, "[TicketManager] Error occurred in connecting to database! Plugin shutting down!");
            getServer().getPluginManager().disablePlugin(this);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "[TicketManager] An unforeseen error has occurred! Printing stacktrace and shutting plugin down! \n");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }

        // Regularly scheduled tasks
        scheduler.runTaskTimerAsynchronously(this, () -> {
            try {
                Optional<Set<Ticket>> openTicketsOptional = DatabaseHandler.getOpenTickets();
                Optional<Set<Ticket>> updatedTicketsOptional = DatabaseHandler.getUnreadUpdatedTickets();

                // Filters out times when nothing needs to be done
                if (!openTicketsOptional.isPresent() && !updatedTicketsOptional.isPresent()) return;
                Future<Collection<? extends Player>> onlinePlayersFuture = scheduler.callSyncMethod(getInstance, Bukkit::getOnlinePlayers);

                // Notify online staff of ticket situation
                if (openTicketsOptional.isPresent()) {
                    Set<Ticket> openTickets = openTicketsOptional.get();
                    int length = openTickets.size();
                    // Notifies all online players of ticket situation
                    onlinePlayersFuture.get().stream()
                            .filter(p -> getPermissions().has(p, "ticketmanager.notify.scheduledOpenTickets"))
                            .forEach(p -> {
                                long assignedCount = openTickets.stream()
                                        .filter(t -> t.getAssignment() != null)
                                        .filter(t -> t.getAssignment().equals(p.getName())).count();
                                p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                        "&3[TicketManager] &7" + length + "&3 tickets open (&7" + assignedCount + "&3 assigned to you)"));
                            });
                }

                // Schedule players of their own ticket updates situation
                if (updatedTicketsOptional.isPresent()) {
                    Set<Ticket> updatedTickets = updatedTicketsOptional.get();

                    onlinePlayersFuture.get().parallelStream()
                            .filter(p -> getPermissions().has(p, "ticketmanager.notify.scheduledUpdatedTickets"))
                            .forEach(p -> {
                                List<Integer> ticketNumbers = updatedTickets.stream()
                                        .filter(t -> t.getUUID().isPresent())
                                        .filter(t -> t.getUUID().get().equals(p.getUniqueId()))
                                        .map(Ticket::getId)
                                        .collect(Collectors.toList());
                                if (ticketNumbers.size() > 0) {
                                    StringBuilder builder = new StringBuilder();
                                    ticketNumbers.forEach(n -> builder.append("&3#").append(n).append("&7, "));
                                    p.sendMessage(ChatColor.translateAlternateColorCodes('&',
                                            "&3[TicketManager]&7 Ticket(s) " + builder.toString() + "have been updated! " +
                                                    "Please type &3/ticket view <ID> &7to view and dismiss the notification(s) "));
                                }
                            });
                }
            } catch (Exception e) {
                Bukkit.getLogger().log(Level.SEVERE, "[TicketManager] An error has occurred in the scheduled tasks!");
                e.printStackTrace();
            }
        }, 12000, 12000);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static Permission getPermissions() {
        return perms;
    }
}