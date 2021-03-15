package engineer.hoshikurama.github.ticketmanager;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TabCompleter implements org.bukkit.command.TabCompleter {

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1)
            return Stream.of("assign", "claim", "close", "closeall", "comment", "create", "help", "history", "list", "reopen", "setpriority", "teleport", "unassign", "view")
                    .filter(e -> e.startsWith(args[0]))
                    .collect(Collectors.toList());
        else if (args.length > 1) {
            switch (args[0]) {

                case "assign":  //ticket assign <ID> <Name/User>
                    if (args.length == 2) return Collections.singletonList("<ID>");
                    else if (args.length == 3) return Stream.concat(Stream.of("<Name/User>"),
                            Bukkit.getOnlinePlayers().stream()
                                    .map(HumanEntity::getName)
                                    .filter(e -> e.startsWith(args[2])))
                            .collect(Collectors.toList());
                    else break;

                case "claim":       //ticket claim <ID>
                case "reopen":      //ticket reopen <ID>
                case "teleport":    //ticket teleport <ID>
                case "view":        //ticket view <ID>
                case "unassign":    //ticket unassign <ID>
                    if (args.length == 2) return Collections.singletonList("<ID>");
                    else break;

                case "close":   //ticket close <ID> [Comment]
                    if (args.length == 2) return Collections.singletonList("<ID>");
                    else return Stream.concat(Stream.of("[Comment...]"),
                            Bukkit.getOnlinePlayers().stream()
                                .map(HumanEntity::getName)
                                .filter(e -> e.startsWith(args[args.length-1])))
                        .collect(Collectors.toList());

                case "closeall":    //ticket closeall <LowerBounds (Inclusive)> <UpperBounds (Inclusive)>
                    if (args.length == 2) return Collections.singletonList("<LowerBounds (Inclusive)>");
                    else if (args.length == 3) return Collections.singletonList("<UpperBounds (Inclusive)>");
                    else break;

                case "comment":    //ticket comment <ID> <Comment>
                    if (args.length == 2) return Collections.singletonList("<ID>");
                    else return Stream.concat(Stream.of("<Comment...>"),
                            Bukkit.getOnlinePlayers().stream()
                                    .map(HumanEntity::getName)
                                    .filter(e -> e.startsWith(args[args.length-1])))
                            .collect(Collectors.toList());

                case "create":     //ticket create <Message>
                    return Stream.concat(Stream.of("<Message...>"),
                        Bukkit.getOnlinePlayers().stream()
                                .map(HumanEntity::getName)
                                .filter(e -> e.startsWith(args[args.length-1])))
                        .collect(Collectors.toList());

                case "history":    //ticket history <User>
                    if (args.length == 2) return Stream.concat(Stream.of("<Message...>"),
                            Bukkit.getOnlinePlayers().stream()
                                    .map(HumanEntity::getName)
                                    .filter(e -> e.startsWith(args[args.length-1])))
                            .collect(Collectors.toList());

                case "list":    //ticket list [Page]
                    if (args.length == 2) return Collections.singletonList("[Page]");
                    else break;

                case "setpriority":    //ticket setpriority <ID> <Priority (1-5)>
                    if (args.length == 2) return Collections.singletonList("<ID>");
                    else if (args.length == 3) return Stream.of("<Priority>","1","2","3","4","5")
                            .filter(e -> e.startsWith(args[2]))
                            .collect(Collectors.toList());
                    else break;

                default: break;
            }
            return Bukkit.getOnlinePlayers().stream().map(HumanEntity::getName).collect(Collectors.toList());
        }
        return null;
    }
}
/*
    NOTE: GIVE ticketmanager.* to OP by default

    Player commands:
 1       /ticket create <problem>
            ticketmanager.create
 1       /ticket view <ID>
            ticketmanager.view.self
 1       /ticket comment <ID> <message>
            ticketmanager.comment.self
 1       /ticket close <ID> [Comment]
            ticketmanager.close.self
 1       /ticket history <ID>
            ticketmanager.history.self
 1       /ticket help
            ticketmanager.help.basic
            ticketmanager.help.all

    Staff Commands:
 1       /ticket view <ID>
            ticketmanager.view.others
 1       /ticket comment <ID> <Message>
            ticketmanager.comment.others
 1       /ticket close <ID> [Message]
            ticketmanager.close
 1       /ticket assign <ID> <Player/Name>
            ticketmanager.assign
 1       /ticket claim <ID>
            ticketmanager.assign
 1       /ticket unassign <ID>
            ticketmanager.assign
 1       /ticket setpriority <ID> <Priority>
            ticketmanager.setpriority
 1       /ticket history <User>
            ticketmanager.history.others
 1       /ticket closeall <ID1> <ID2>
            ticketmanager.closeall
 1       /ticket teleport <ID>
            ticketmanager.teleport
 1       /ticket list [page]
            ticketmanager.list
 1       /ticket reopen <ID>
            ticketmanager.reopen

    Other nodes:
        ticketmanager.basic
        ticketmanager.manage
        ticketmanager.*
        ticketmanager.notify.*
        ticketmanager.notify.onCreate
        ticketmanager.notify.onUpdate.self
        ticketmanager.notify.onUpdate.others
        ticketmanager.notify.onClose
     */
