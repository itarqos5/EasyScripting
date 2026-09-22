package dev.easyscripting.commands;

import dev.easyscripting.actors.*;
import dev.easyscripting.config.Messages;
import dev.easyscripting.core.Checks;
import dev.easyscripting.gui.MenuService;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;

public final class GroupCommands {
  private GroupCommands() {}

  private static final List<String> OPERATIONS =
      List.of(
          "create",
          "delete",
          "list",
          "info",
          "gui",
          "add",
          "remove",
          "leader",
          "intelligence",
          "immortal",
          "kit",
          "identities",
          "tool",
          "follow",
          "hold",
          "stop",
          "move",
          "lineup",
          "purge",
          "attack",
          "fight");

  public static void register(
      CommandRouter router,
      ActorGroupService groups,
      ActorService actors,
      GroupActorTool tools,
      Messages messages,
      MenuService menus) {
    router.add(
        "group",
        "use",
        "[gui|list|create|delete|purge|info|add|remove|leader|intelligence|immortal|kit"
            + "|identities|tool|follow|hold|stop|move|lineup|attack|fight]"
            + " [group] [value]",
        (sender, args) -> {
          String operation = args.get(0, "gui").toLowerCase(Locale.ROOT);
          if (operation.equals("gui")) {
            if (args.size() > 2) throw new IllegalArgumentException("Use /es group gui [group].");
            if (args.size() == 2) menus.group(Args.player(sender), args.get(1));
            else menus.open(Args.player(sender), "groups", 0);
            return;
          }
          if (operation.equals("list")) {
            messages.ok(sender, "Your NPC groups: " + String.join(", ", groups.visible(sender)));
            return;
          }
          if (!OPERATIONS.contains(operation))
            throw new IllegalArgumentException("Use /es group gui or /es help.");
          String id = Checks.id(args.get(1));
          boolean manage =
              Set.of(
                      "create",
                      "delete",
                      "purge",
                      "add",
                      "remove",
                      "leader",
                      "intelligence",
                      "immortal",
                      "kit",
                      "identities",
                      "tool")
                  .contains(operation);
          if (manage) ActorGroupService.requireManager(sender);
          else groups.requireOrder(sender, id);
          int minimum =
              Set.of(
                      "add",
                      "remove",
                      "leader",
                      "intelligence",
                      "immortal",
                      "kit",
                      "attack",
                      "fight")
                      .contains(operation)
                  ? 3
                  : operation.equals("tool") ? 3 : 2;
          int maximum =
              operation.equals("tool") || operation.equals("lineup") ? 4 : minimum;
          if (args.size() < minimum || args.size() > maximum)
            throw new IllegalArgumentException(
                "Use /es group "
                    + operation
                    + " <group>"
                    + (minimum == 3 ? " <value>" : "")
                    + (operation.equals("tool") ? " [type]" : "")
                    + (operation.equals("lineup") ? " [front|behind] [columns]" : "")
                    + ".");
          switch (operation) {
            case "create" -> groups.create(id);
            case "delete" -> {
              int count = groups.delete(id);
              messages.ok(
                  sender,
                  "Deleted group " + id + " and permanently deleted " + count + " NPC(s).");
              return;
            }
            case "info" -> {
              messages.ok(sender, groups.info(id));
              return;
            }
            case "add" -> {
              int count = groups.add(id, args.get(2));
              messages.ok(
                  sender,
                  "Added "
                      + count
                      + " actor(s) to "
                      + id
                      + ". Configured shared kit and Immortal defaults were applied.");
              return;
            }
            case "remove" -> groups.remove(id, args.get(2));
            case "leader" -> {
              Player target =
                  args.get(2).equalsIgnoreCase("off") ? null : realPlayer(args.get(2), actors);
              groups.leader(id, target);
            }
            case "intelligence" -> groups.intelligence(id, Checks.bool(args.get(2)));
            case "immortal" -> {
              boolean value = Checks.bool(args.get(2));
              int count = groups.sharedImmortal(id, value);
              messages.ok(
                  sender,
                  "Set shared Immortal "
                      + (value ? "ON" : "OFF")
                      + " for "
                      + count
                      + " NPC(s) in "
                      + id
                      + ".");
              return;
            }
            case "kit" -> {
              int count = groups.sharedKit(id, args.get(2));
              messages.ok(
                  sender,
                  "Applied shared kit '"
                      + args.get(2)
                      + "' to "
                      + count
                      + " NPC(s) in "
                      + id
                      + ".");
              return;
            }
            case "identities" -> {
              int count = groups.sharedIdentities(id);
              messages.ok(
                  sender,
                  "Randomized the identities of " + count + " NPC(s) in " + id + ".");
              return;
            }
            case "tool" -> {
              tools.give(Args.player(sender), id, args.get(2), args.get(3, tools.defaultType()));
              return;
            }
            case "purge" -> {
              int count = groups.purge(id);
              messages.ok(
                  sender,
                  "Permanently deleted "
                      + count
                      + " NPC(s) from "
                      + id
                      + ". The group, its leader and its shared defaults are kept.");
              return;
            }
            case "lineup" -> {
              int count =
                  groups.lineUp(
                      id,
                      Args.player(sender),
                      args.get(2, "behind"),
                      args.size() > 3 ? Checks.integer(args.get(3), 1, 32) : 0);
              messages.ok(
                  sender,
                  "Lined up "
                      + count
                      + " NPC(s) of "
                      + id
                      + ", each on its own block. The group is now holding; use /es group follow "
                      + id
                      + " to resume following.");
              return;
            }
            case "follow" -> groups.order(id, ActorGroup.Order.FOLLOW, null);
            case "hold", "stop" -> groups.order(id, ActorGroup.Order.HOLD, null);
            case "move" ->
                groups.order(id, ActorGroup.Order.MOVE, Args.player(sender).getLocation());
            case "attack" -> {
              String value = args.get(2);
              LivingEntity target =
                  value.startsWith("actor:")
                      ? actors.get(value.substring(6)).requireEntity()
                      : realPlayer(value, actors);
              groups.attack(id, target);
            }
            case "fight" -> groups.fight(id, args.get(2));
            default -> throw new IllegalArgumentException("Unknown group operation.");
          }
          messages.ok(
              sender,
              "Group " + id + ": " + operation + " applied. " + groups.info(id));
        },
        (sender, args) -> {
          if (args.size() <= 1) return OPERATIONS;
          if (args.size() == 2)
            return args.get(0).equals("create") ? List.of() : groups.visible(sender);
          if (args.size() == 4 && args.get(0).equals("tool")) return tools.livingTypes();
          if (args.size() != 3) return List.of();
          return switch (args.get(0)) {
            case "intelligence", "immortal" -> List.of("on", "off");
            case "lineup" -> List.of("behind", "front");
            case "kit", "tool" -> tools.kitIds();
            case "fight" -> groups.ids();
            case "add" -> {
              List<String> ids = new ArrayList<>(actors.ids());
              actors.list().stream().map(a -> "tag:" + a.group()).distinct().forEach(ids::add);
              yield ids;
            }
            case "remove" ->
                groups.members(args.get(1)).stream().map(ActorService.ManagedActor::id).toList();
            // Attack suggests only what this group could actually be ordered to attack now.
            case "attack" -> groups.attackable(args.get(1));
            case "leader" -> {
              List<String> names =
                  new ArrayList<>(
                      Bukkit.getOnlinePlayers().stream()
                          .filter(
                              p ->
                                  !p.hasMetadata("NPC")
                                      && actors.byEntity(p.getUniqueId()).isEmpty())
                          .map(Player::getName)
                          .toList());
              names.add("off");
              yield names;
            }
            default -> List.of();
          };
        });
  }

  private static Player realPlayer(String name, ActorService actors) {
    Player player = Bukkit.getPlayerExact(name);
    if (player == null
        || player.hasMetadata("NPC")
        || actors.byEntity(player.getUniqueId()).isPresent())
      throw new IllegalArgumentException("Choose a real online player by their account name.");
    return player;
  }
}
