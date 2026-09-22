package dev.easyscripting.commands;

import dev.easyscripting.config.Messages;
import dev.easyscripting.gui.MenuService;
import dev.easyscripting.players.DeadUserRegistry;
import java.util.*;

public final class DeadUserCommands {
  private DeadUserCommands() {}

  public static void register(
      CommandRouter router, DeadUserRegistry deadUsers, Messages messages, MenuService menus) {
    router.add(
        "deadusers",
        "identity",
        "[list] | search <text> | remove <username> | clear",
        (sender, args) -> {
          String operation = args.get(0, "list").toLowerCase(Locale.ROOT);
          switch (operation) {
            case "list" -> {
              if (args.size() > 1)
                throw new IllegalArgumentException("Use /deadusers list.");
              menus.deadUsers(Args.player(sender), "", 0);
            }
            case "search" -> {
              if (args.size() != 2
                  || !args.get(1).matches("[A-Za-z0-9_]{1,48}"))
                throw new IllegalArgumentException(
                    "Use /deadusers search <username-or-part>.");
              menus.deadUsers(Args.player(sender), args.get(1), 0);
            }
            case "clear" -> {
              if (args.size() != 1) throw new IllegalArgumentException("Use /deadusers clear.");
              if (!sender.isOp())
                throw new IllegalArgumentException(
                    "Only operators can release every dead username.");
              int released = deadUsers.clear();
              messages.ok(
                  sender,
                  released == 0
                      ? "No dead usernames were saved; nothing to release."
                      : "Released " + released + " dead username(s). They can be generated again.");
            }
            case "remove" -> {
              if (args.size() != 2)
                throw new IllegalArgumentException("Use /deadusers remove <username>.");
              var removed = deadUsers.remove(args.get(1));
              messages.ok(
                  sender,
                  "Removed dead username '"
                      + removed.name()
                      + "'. It can now be generated again.");
            }
            default ->
                throw new IllegalArgumentException(
                    "Use /deadusers list, search <text>, remove <username>, or clear.");
          }
        },
        (sender, args) -> {
          if (args.size() <= 1) return List.of("list", "search", "remove", "clear");
          if (args.size() == 2 && args.get(0).equalsIgnoreCase("remove"))
            return deadUsers.list("").stream().map(DeadUserRegistry.Entry::name).toList();
          return List.of();
        });
  }
}
