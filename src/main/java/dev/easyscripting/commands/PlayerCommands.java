package dev.easyscripting.commands;

import dev.easyscripting.config.*;
import dev.easyscripting.core.*;
import dev.easyscripting.gui.MenuService;
import dev.easyscripting.items.*;
import dev.easyscripting.players.*;
import dev.easyscripting.utilities.ModerationService;
import dev.easyscripting.world.WarpService;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;

public final class PlayerCommands {
  public static void register(
      CommandRouter router,
      Access access,
      Settings settings,
      Messages messages,
      PlayerService players,
      IdentityService identities,
      KitService kits,
      ItemService items,
      WarpService warps,
      DeathService deaths,
      ModerationService moderation,
      MenuService menus) {
    router.add(
        "player",
        "player",
        "<operation> [value] [player]",
        (s, a) -> {
          if (a.get(0).equals("otp")) {
            settings.require("players");
            Args.player(s).teleportAsync(players.lastLocation(a.get(1)));
            return;
          }
          Player target = a.size() > 2 ? players.player(a.get(2)) : Args.player(s);
          if (!target.equals(s)) access.require(s, "player.others");
          String operation = a.get(0);
          if (operation.equals("potion")) players.potion(target, a.get(1));
          else players.control(target, operation, a.get(1, "on"));
          messages.ok(s, "Updated " + operation + " for " + target.getName() + ".");
        },
        (s, a) ->
            a.size() == 1
                ? combine(
                    PlayerService.FLAGS,
                    List.of(
                        "otp",
                        "health",
                        "heal",
                        "feed",
                        "hunger",
                        "gamemode",
                        "flight",
                        "invulnerable",
                        "invisible",
                        "glow",
                        "speed",
                        "fire",
                        "potion",
                        "clear-effects"))
                : a.size() == 2
                    ? List.of("on", "off")
                    : Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    router.add(
        "take",
        "record",
        "snapshot|reset|discard | start <id> [self|all] | stop [reset|keep]",
        (s, a) -> {
          settings.require("recording");
          switch (a.get(0)) {
            case "snapshot" -> players.snapshot(Args.player(s));
            case "reset" -> players.reset(Args.player(s));
            case "discard" -> players.discard(Args.player(s));
            case "start" -> {
              String scope = a.get(2, "self");
              if (!List.of("self", "all").contains(scope))
                throw new IllegalArgumentException("Take scope must be self or all.");
              if (scope.equals("all")) access.require(s, "player.others");
              moderation.recordingStart(
                  a.get(1),
                  scope.equals("all") ? Bukkit.getOnlinePlayers() : List.of(Args.player(s)));
            }
            case "stop" -> moderation.recordingStop(!a.get(1, "reset").equals("keep"));
            default -> throw new IllegalArgumentException("Unknown take operation.");
          }
          messages.ok(s, "Take " + a.get(0) + " complete.");
        },
        "snapshot",
        "reset",
        "discard",
        "start",
        "stop");
    router.add(
        "kit",
        "kit",
        "create|save|apply|delete|edit <id> | list",
        (s, a) -> {
          settings.require("kits");
          switch (a.get(0, "list")) {
            case "create" -> {
              access.require(s, "kit.edit");
              kits.create(a.get(1));
              messages.ok(
                  s, "Created kit '" + a.get(1) + "'. Open /es kits to edit or import inventory.");
            }
            case "save" -> {
              access.require(s, "kit.edit");
              kits.save(a.get(1), Args.player(s));
            }
            case "apply" -> {
              players.available(Args.player(s).getUniqueId());
              kits.apply(a.get(1), Args.player(s));
            }
            case "delete" -> {
              access.require(s, "kit.edit");
              kits.delete(a.get(1));
            }
            case "edit" -> {
              access.require(s, "kit.edit");
              menus.kitEditor(Args.player(s), a.get(1));
            }
            case "list" -> messages.ok(s, String.join(", ", kits.ids()));
            default -> throw new IllegalArgumentException("Unknown kit operation.");
          }
        },
        (s, a) ->
            a.size() == 1
                ? List.of("create", "save", "apply", "delete", "edit", "list")
                : kits.ids());
    router.add(
        "warp",
        "warp",
        "save|go|delete|permission <id> ... | list",
        (s, a) -> {
          settings.require("warps");
          switch (a.get(0, "list")) {
            case "save" -> {
              access.require(s, "warp.edit");
              warps.save(a.get(1), Args.player(s).getLocation());
            }
            case "go" -> {
              Player target = a.size() > 2 ? players.player(a.get(2)) : Args.player(s);
              if (!target.equals(s)) access.require(s, "player.others");
              warps.teleport(a.get(1), s, target);
            }
            case "delete" -> {
              access.require(s, "warp.edit");
              warps.delete(a.get(1));
            }
            case "permission" -> {
              access.require(s, "warp.edit");
              warps.permission(a.get(1), a.get(2));
            }
            case "list" -> messages.ok(s, String.join(", ", warps.ids(s)));
            default -> throw new IllegalArgumentException("Unknown warp operation.");
          }
        },
        (s, a) ->
            a.size() == 1
                ? List.of("save", "go", "delete", "permission", "list")
                : a.size() == 2 ? warps.ids(s) : List.of());
    router.add(
        "spawn",
        "warp",
        "[set]",
        (s, a) -> {
          if (a.get(0, "go").equals("set")) {
            access.require(s, "warp.edit");
            warps.save("spawn", Args.player(s).getLocation());
          } else warps.teleport("spawn", s, Args.player(s));
        },
        "set");
    router.add(
        "item",
        "items",
        "name|lore|repair|durability|unbreakable|amount|enchant|give|head|tool|stasis ...",
        (s, a) -> {
          settings.require("items");
          Player p = Args.player(s);
          switch (a.get(0)) {
            case "give" -> {
              Material material = Material.matchMaterial(a.get(1));
              if (material == null) throw new IllegalArgumentException("Unknown material.");
              items.give(p, material, Checks.integer(a.get(2, "1"), 1, 64));
            }
            case "enchant" -> items.enchant(p, a.get(1), a.integer(2, 0, 255));
            case "attribute" ->
                items.attribute(
                    p,
                    a.get(1),
                    a.decimal(2, -1000, 1000),
                    a.get(3, "ADD_NUMBER"),
                    a.get(4, "mainhand"));
            case "head" -> items.head(p, a.get(1));
            case "randomhead" -> items.head(p, identities.random(a.get(1, "default")));
            case "tool" -> items.deliver(p, items.tool(a.get(1)));
            case "stasis" ->
                items.stasis(p, a.integer(1, 1, 64), a.integer(2, 0, 72000), p.getLocation());
            default -> items.edit(p, a.get(0), a.size() > 1 ? a.rest(1) : "");
          }
        },
        "name",
        "lore",
        "repair",
        "durability",
        "unbreakable",
        "amount",
        "enchant",
        "give",
        "head",
        "randomhead",
        "tool",
        "stasis");
    router.add(
        "inventory",
        "inventory",
        "view|ender|save|history|restore|restock|fill [player] [snapshot]",
        (s, a) -> {
          settings.require("inventory");
          Player target = a.size() > 1 ? players.player(a.get(1)) : Args.player(s);
          switch (a.get(0)) {
            case "view" -> menus.viewInventory(Args.player(s), target, false);
            case "ender" -> menus.viewInventory(Args.player(s), target, true);
            case "save" -> players.rollbackSave(target);
            case "history" -> messages.ok(s, String.join(", ", players.history(target)));
            case "restore" -> players.rollback(target, a.get(2));
            case "restock" -> items.restock(target.getInventory());
            case "fill" -> {
              var block = Args.player(s).getTargetBlockExact(6);
              if (block == null || !(block.getState() instanceof Container container))
                throw new IllegalArgumentException("Look at a container within 6 blocks.");
              items.randomFill(container.getInventory());
            }
            default -> throw new IllegalArgumentException("Unknown inventory operation.");
          }
        },
        "view",
        "ender",
        "save",
        "history",
        "restore",
        "restock",
        "fill");
    router.add(
        "nick",
        "identity",
        "set <name> | random [profile] | reset | info <name> | blacklist add|remove|list [name] |"
            + " profile add|remove <profile> <name>",
        (s, a) -> {
          switch (a.get(0)) {
            case "set" -> identities.nick(Args.player(s), a.get(1));
            case "random" ->
                identities.nick(Args.player(s), identities.random(a.get(1, "default")));
            case "reset" -> identities.reset(Args.player(s));
            case "info" -> messages.ok(s, String.join(", ", identities.info(a.get(1))));
            case "blacklist" -> {
              access.require(s, "admin");
              if (a.get(1).equals("list"))
                messages.ok(s, String.join(", ", identities.blacklist()));
              else {
                if (!List.of("add", "remove").contains(a.get(1)))
                  throw new IllegalArgumentException("Use blacklist add, remove or list.");
                identities.blacklist(a.get(2), a.get(1).equals("add"));
              }
            }
            case "profile" -> {
              access.require(s, "admin");
              identities.profile(a.get(2), a.get(3), a.get(1).equals("add"));
            }
            default -> throw new IllegalArgumentException("Unknown nickname operation.");
          }
        },
        "set",
        "random",
        "reset",
        "info",
        "blacklist",
        "profile");
    router.add(
        "skin",
        "identity",
        "<player-name|Minecraft-texture-url> [auto|slim|classic]",
        (s, a) -> identities.skin(Args.player(s), a.get(0), a.get(1, "auto")));
    router.add(
        "death",
        "death",
        "normal|spectator|kick|respawn [player] | scene <id|off> [player]",
        (s, a) -> {
          settings.require("death");
          boolean scene = a.get(0).equals("scene");
          int targetIndex = scene ? 2 : 1;
          Player player =
              a.size() > targetIndex ? players.player(a.get(targetIndex)) : Args.player(s);
          if (!player.equals(s)) access.require(s, "player.others");
          if (scene) {
            access.require(s, "scene.edit");
            deaths.scene(player, a.get(1));
          } else deaths.mode(player, a.get(0));
        },
        "normal",
        "spectator",
        "kick",
        "respawn",
        "scene");
  }

  private static List<String> combine(List<String> a, List<String> b) {
    List<String> result = new ArrayList<>(a);
    result.addAll(b);
    return result;
  }
}
