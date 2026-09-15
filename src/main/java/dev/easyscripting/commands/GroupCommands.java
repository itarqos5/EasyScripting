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
          "follow",
          "hold",
          "stop",
          "move",
          "attack",
          "fight");

  public static void register(
      CommandRouter router,
      ActorGroupService groups,
      ActorService actors,
      Messages messages,
      MenuService menus) {
    router.add(
        "group",
        "use",
        "[gui|list|create|delete|info|add|remove|leader|intelligence|follow|hold|stop|move|attack|fight]"
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
              Set.of("create", "delete", "add", "remove", "leader", "intelligence")
                  .contains(operation);
          if (manage) ActorGroupService.requireManager(sender);
          else groups.requireOrder(sender, id);
          int expected =
              Set.of("add", "remove", "leader", "intelligence", "attack", "fight")
                      .contains(operation)
                  ? 3
                  : 2;
          if (args.size() != expected)
            throw new IllegalArgumentException(
                "Use /es group "
                    + operation
                    + " <group>"
                    + (expected == 3 ? " <value>" : "")
                    + ".");
          switch (operation) {
            case "create" -> groups.create(id);
            case "delete" -> groups.delete(id);
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
                      + ". Individual equipment and health kept.");
              return;
            }
            case "remove" -> groups.remove(id, args.get(2));
            case "leader" -> {
              Player target =
                  args.get(2).equalsIgnoreCase("off") ? null : realPlayer(args.get(2), actors);
              groups.leader(id, target);
            }
            case "intelligence" -> groups.intelligence(id, Checks.bool(args.get(2)));
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
              operation.equals("delete")
                  ? "Deleted group " + id + "; its NPCs are now unassigned."
                  : "Group " + id + ": " + operation + " applied. " + groups.info(id));
        },
        (sender, args) -> {
          if (args.size() <= 1) return OPERATIONS;
          if (args.size() == 2)
            return args.get(0).equals("create") ? List.of() : groups.visible(sender);
          if (args.size() != 3) return List.of();
          return switch (args.get(0)) {
            case "intelligence" -> List.of("on", "off");
            case "fight" -> groups.ids();
            case "add" -> {
              List<String> ids = new ArrayList<>(actors.ids());
              actors.list().stream().map(a -> "tag:" + a.group()).distinct().forEach(ids::add);
              yield ids;
            }
            case "remove" ->
                groups.members(args.get(1)).stream().map(ActorService.ManagedActor::id).toList();
            case "leader", "attack" -> {
              List<String> names =
                  new ArrayList<>(
                      Bukkit.getOnlinePlayers().stream()
                          .filter(
                              p ->
                                  !p.hasMetadata("NPC")
                                      && actors.byEntity(p.getUniqueId()).isEmpty())
                          .map(Player::getName)
                          .toList());
              if (args.get(0).equals("leader")) names.add("off");
              else actors.ids().forEach(id -> names.add("actor:" + id));
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
