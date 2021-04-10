package engineer.hoshikurama.github.ticketmanager.v2;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TabCompletion implements TabCompleter {
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        try {
            if (args.length <= 1) {
                return Stream.of("assign", "s.assign", "claim", "s.claim", "close", "s.close", "closeall", "s.closeall", "comment", "s.comment", "create", "help",
                        "history", "list", "reopen", "s.reopen", "search", "setpriority", "s.setpriority", "teleport", "unassign", "s.unassign", "view", "reload")
                        .filter(e -> e.startsWith(args[0]))
                        .collect(Collectors.toList());
            }

            switch (args[0]) {
                case "claim":       //ticket claim <ID>
                case "s.claim":
                case "reopen":      //ticket reopen <ID>
                case "s.reopen":
                case "teleport":    //ticket teleport <ID>
                case "view":        //ticket view <ID>
                case "unassign":    //ticket unassign <ID>
                case "s.unassign":
                    if (args.length == 2) return Stream.of("<ID>")
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                    else return Collections.emptyList();

                case "assign":  //ticket assign <ID> <Name/User>
                case "s.assign":
                    if (args.length == 2) return Stream.of("<ID>")
                            .filter(e -> e.startsWith(args[1]))
                            .collect(Collectors.toList());
                    else if (args.length == 3)
                        return Stream.concat(Stream.of("<Name/User>"), onlinePlayerStream(sender))
                                .filter(s -> s.startsWith(args[2]))
                                .collect(Collectors.toList());
                    else return Collections.emptyList();

                case "close":       //ticket close <ID> [Comment]
                case "s.close":
                    if (args.length == 2) return Stream.of("<ID>")
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                    else if (args.length == 3)
                        return Stream.concat(Stream.of("[Comment..]"), onlinePlayerStream(sender))
                                .filter(s -> s.startsWith(args[2]))
                                .collect(Collectors.toList());
                    else
                        return onlinePlayerStream(sender).filter(s -> s.startsWith(args[args.length - 1])).collect(Collectors.toList());

                case "closeall":    //ticket closeall <LowerBounds (Inclusive)> <UpperBounds (Inclusive)>
                case "s.closeall":
                    if (args.length == 2) return Stream.of("<Lower-Bounds (Inclusive)>")
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                    else if (args.length == 3) return Stream.of("<Upper-Bounds (Inclusive)>")
                            .filter(s -> s.startsWith(args[2]))
                            .collect(Collectors.toList());
                    else return Collections.emptyList();

                case "comment":    //ticket comment <ID> <Comment...>
                case "s.comment":
                    if (args.length == 2) return Stream.of("<ID>")
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                    else if (args.length == 3)
                        return Stream.concat(Stream.of("<Comment...>"), onlinePlayerStream(sender))
                                .filter(s -> s.startsWith(args[2]))
                                .collect(Collectors.toList());
                    else
                        return onlinePlayerStream(sender).filter(s -> s.startsWith(args[args.length - 1])).collect(Collectors.toList());

                case "create":      //ticket create <Message>
                    if (args.length == 2)
                        return Stream.concat(Stream.of("<Message...>"), onlinePlayerStream(sender))
                                .filter(s -> s.startsWith(args[1]))
                                .collect(Collectors.toList());
                    else
                        return onlinePlayerStream(sender).filter(s -> s.startsWith(args[args.length - 1])).collect(Collectors.toList());

                case "history":     //ticket history <User>
                    if (args.length == 2) return Stream.concat(Stream.of("<User>"), onlinePlayerStream(sender))
                            .filter(s -> s.startsWith(args[1]))
                            .collect(Collectors.toList());
                    else return Collections.emptyList();

                case "list":        //ticket list [Page]
                    if (args.length == 2)
                        return Stream.of("[Page]").filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
                    else return Collections.singletonList("");

                case "setpriority":    //ticket setpriority <ID> <Priority (1-5)>
                case "s.setpriority":
                    if (args.length == 2)
                        return Stream.of("<ID>").filter(s -> s.startsWith(args[1])).collect(Collectors.toList());
                    else if (args.length == 3) return Stream.of("<Priority>", "1", "2", "3", "4", "5")
                            .filter(s -> s.startsWith(args[2]))
                            .collect(Collectors.toList());
                    else return Collections.emptyList();

                case "search":      //ticket search keywords:separated,by,commas status:OPEN/CLOSED time:5w creator:creator priority:value assignedto:player world:world
                    String curArgument = args[args.length - 1];
                    String testString = curArgument + "TEST";
                    if (testString.split(":").length < 2)
                        return Stream.of("assignedto:", "creator:", "keywords:", "priority:", "status:", "time:", "world:")
                                .filter(s -> s.startsWith(curArgument))
                                .collect(Collectors.toList());

                    // String now has form "constraint:"
                    if (curArgument.startsWith("assignedto"))
                        return onlinePlayerStream(sender)
                                .map(s -> "assignedto:" + s)
                                .filter(s -> s.startsWith(curArgument))
                                .collect(Collectors.toList());

                    else if (curArgument.startsWith("creator"))
                        return onlinePlayerStream(sender)
                                .map(s -> "creator:" + s)
                                .filter(s -> s.startsWith(curArgument))
                                .collect(Collectors.toList());
                    else if (curArgument.startsWith("priority"))
                        return Stream.of("priority:1", "priority:2", "priority:3", "priority:4", "priority:5")
                                .filter(s -> s.startsWith(curArgument))
                                .collect(Collectors.toList());
                    else if (curArgument.startsWith("status"))
                        return Stream.of("status:OPEN", "status:CLOSED")
                            .filter(s -> s.startsWith(curArgument))
                            .collect(Collectors.toList());
                    else if (curArgument.startsWith("world"))
                        return Bukkit.getWorlds().stream()
                                .map(World::getName)
                                .map(s -> "world:" + s)
                                .filter(s -> s.startsWith(curArgument))
                                .collect(Collectors.toList());
                    else if (curArgument.startsWith("time"))
                        return Stream.of("y", "w", "d", "h", "m", "s")
                                .filter(s -> lastCharIsNumber(curArgument.charAt(curArgument.length() - 1)))
                                .map(s -> curArgument + s)
                                .collect(Collectors.toList());
                    else if (curArgument.startsWith("sender"))
                        return onlinePlayerStream(sender)
                                .map(s -> {
                                    String[] keywords = curArgument.substring(9).split(",");
                                    if (s.startsWith(keywords[keywords.length - 1])) return "keywords:" +
                                            String.join(",", Arrays.copyOfRange(keywords, 0, keywords.length - 1)) + "," + s;
                                    else return null;
                                })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            TMCommands.pushWarningNotification(e);
            return Collections.emptyList();
        }
    }

    private boolean lastCharIsNumber(char c) {
        return c == '1' || c == '2'|| c == '3'|| c == '4'|| c == '5'|| c == '6'|| c == '7'|| c == '8'|| c == '9' || c == '0';
    }

    private Stream<String> onlinePlayerStream(CommandSender sender) {
        if (sender instanceof Player) {
            Player senderPlayer = (Player) sender;
            return Bukkit.getOnlinePlayers().stream()
                    .filter(senderPlayer::canSee)
                    .map(HumanEntity::getName);
        } else return Bukkit.getOnlinePlayers().stream()
                .map(HumanEntity::getName);
    }
}
