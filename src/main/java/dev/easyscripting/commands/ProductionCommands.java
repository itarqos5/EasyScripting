package dev.easyscripting.commands;

import dev.easyscripting.config.*;
import dev.easyscripting.core.*;
import dev.easyscripting.effects.EffectService;
import dev.easyscripting.integration.VoiceBridge;
import dev.easyscripting.items.ItemService;
import dev.easyscripting.utilities.*;
import dev.easyscripting.world.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;

public final class ProductionCommands {
  public static void register(
      CommandRouter router,
      Access access,
      Settings settings,
      Messages messages,
      WorldService worlds,
      RegionService regions,
      ItemService items,
      EffectService effects,
      ModerationService moderation,
      TeamService teams,
      VillagerService villagers,
      VoiceBridge voice) {
    router.add(
        "world",
        "world",
        "time|weather|border|top|clean|limit|lock|allow ...",
        (s, a) -> {
          settings.require("world");
          Player p = Args.player(s);
          switch (a.get(0)) {
            case "time" -> worlds.time(p.getWorld(), a.integer(1, 0, 24000));
            case "weather" -> worlds.weather(p.getWorld(), a.get(1));
            case "border" -> {
              if (a.get(1).equals("reset")) worlds.resetBorder(p);
              else worlds.border(p, a.decimal(1, 1, 59999968));
            }
            case "top" -> worlds.top(p);
            case "teleport" -> {
              World world = Bukkit.getWorld(a.get(1));
              if (world == null)
                throw new IllegalArgumentException("World '" + a.get(1) + "' is not loaded.");
              p.teleportAsync(world.getSpawnLocation());
            }
            case "clean" -> {
              access.require(s, "destructive");
              messages.ok(
                  s,
                  "Removed "
                      + worlds.cleanup(p, a.get(1), Checks.decimal(a.get(2, "32"), 1, 128))
                      + " entities.");
            }
            case "limit" -> worlds.limit(p, a.get(1), a.integer(2, 2, 32));
            case "lock" -> {
              access.require(s, "admin");
              moderation.dimension(a.get(1), Checks.bool(a.get(2)));
            }
            case "allow" -> {
              access.require(s, "admin");
              moderation.dimensionAllow(a.get(1), a.get(2), Checks.bool(a.get(3)));
            }
            default -> throw new IllegalArgumentException("Unknown world operation.");
          }
        },
        "time",
        "weather",
        "border",
        "top",
        "teleport",
        "clean",
        "limit",
        "lock",
        "allow");
    router.add(
        "region",
        "world.edit",
        "wand|chunk|pos1|pos2|save|restore|cancel|delete|list [id]",
        (s, a) -> {
          Player p = Args.player(s);
          switch (a.get(0)) {
            case "wand" -> items.deliver(p, items.tool("regionwand"));
            case "chunk" -> regions.chunk(p);
            case "pos1" -> regions.point(p, 0, p.getLocation());
            case "pos2" -> regions.point(p, 1, p.getLocation());
            case "save" -> regions.save(p, a.get(1));
            case "restore" -> regions.restore(p, a.get(1));
            case "cancel" -> regions.cancel(a.get(1));
            case "delete" -> regions.delete(a.get(1));
            case "list" -> messages.ok(s, String.join(", ", regions.ids()));
            default -> throw new IllegalArgumentException("Unknown region operation.");
          }
        },
        (s, a) ->
            a.size() == 1
                ? List.of(
                    "wand", "chunk", "pos1", "pos2", "save", "restore", "cancel", "delete", "list")
                : regions.ids());
    router.add(
        "effect",
        "effects",
        "lightning|explosion|orbital|arrows|snowballs|rod|railgun|wolves|totem|bossbar|stop"
            + " [amount|text]",
        (s, a) -> {
          if (a.get(0).equals("stop")) effects.stop();
          else if (a.get(0).equals("bossbar")) effects.bossbar(Args.player(s), a.rest(1));
          else effects.run(Args.player(s), a.get(0), Checks.integer(a.get(1, "1"), 1, 40));
        },
        "lightning",
        "explosion",
        "orbital",
        "arrows",
        "snowballs",
        "rod",
        "railgun",
        "wolves",
        "totem",
        "bossbar",
        "stop");
    router.add(
        "chat",
        "chat",
        "block [on|off] | clear [self] | broadcast <text> | join|leave|death <name>",
        (s, a) -> {
          settings.require("chat");
          switch (a.get(0)) {
            case "block" ->
                moderation.mute(a.size() == 1 ? !moderation.muted() : Checks.bool(a.get(1)));
            case "clear" ->
                moderation.clear(a.get(1, "all").equals("self") ? Args.player(s) : null);
            case "broadcast" -> moderation.announce(a.rest(1));
            case "join", "leave", "death" -> moderation.fake(a.get(0), a.get(1));
            default -> throw new IllegalArgumentException("Unknown chat operation.");
          }
        },
        "block",
        "clear",
        "broadcast",
        "join",
        "leave",
        "death");
    router.add(
        "server",
        "admin",
        "lock on|off | allow|deny <name> | build|break|pvp on|off",
        (s, a) -> {
          switch (a.get(0)) {
            case "lock" -> moderation.lock(Checks.bool(a.get(1)));
            case "allow", "deny" -> moderation.allow(a.get(1), a.get(0).equals("allow"));
            case "build", "break", "pvp" -> {
              settings.file("moderation").set(a.get(0), Checks.bool(a.get(1)));
              settings.persist("moderation");
            }
            default -> throw new IllegalArgumentException("Unknown server operation.");
          }
        },
        "lock",
        "allow",
        "deny",
        "build",
        "break",
        "pvp");
    router.add(
        "team",
        "team",
        "create|delete|join|leave|set|list <id> ...",
        (s, a) -> {
          settings.require("teams");
          switch (a.get(0, "list")) {
            case "create" -> teams.create(a.get(1));
            case "delete" -> teams.delete(a.get(1));
            case "join", "leave" ->
                teams.member(a.get(1), a.get(2, s.getName()), a.get(0).equals("join"));
            case "set" -> teams.set(a.get(1), a.get(2), a.rest(3));
            case "list" -> messages.ok(s, String.join(", ", teams.ids()));
            default -> throw new IllegalArgumentException("Unknown team operation.");
          }
        },
        (s, a) ->
            a.size() == 1
                ? List.of("create", "delete", "join", "leave", "set", "list")
                : a.size() == 2 ? teams.ids() : List.of());
    router.add(
        "villager",
        "villager",
        "create|spawn|set|trade|clear|delete|list <id> ...",
        (s, a) -> {
          settings.require("villagers");
          switch (a.get(0, "list")) {
            case "create" -> villagers.create(a.get(1), Args.player(s).getLocation());
            case "spawn" -> villagers.spawn(a.get(1), Args.player(s).getLocation());
            case "set" -> villagers.set(a.get(1), a.get(2), a.rest(3));
            case "trade" -> {
              Player p = Args.player(s);
              villagers.trade(
                  a.get(1),
                  p.getInventory().getItemInOffHand(),
                  p.getInventory().getItemInMainHand(),
                  Checks.bool(a.get(2, "off")));
            }
            case "clear" -> villagers.clear(a.get(1));
            case "delete" -> villagers.delete(a.get(1));
            case "list" -> messages.ok(s, String.join(", ", villagers.ids()));
            default -> throw new IllegalArgumentException("Unknown villager operation.");
          }
        },
        (s, a) ->
            a.size() == 1
                ? List.of("create", "spawn", "set", "trade", "clear", "delete", "list")
                : villagers.ids());
    router.add(
        "voice",
        "voice",
        "mute on|off | broadcast on|off",
        (s, a) -> {
          settings.require("voice");
          switch (a.get(0)) {
            case "mute" -> voice.mute(Checks.bool(a.get(1)));
            case "broadcast" -> voice.broadcast(Args.player(s), Checks.bool(a.get(1)));
            default -> throw new IllegalArgumentException("Use voice mute or broadcast.");
          }
        },
        "mute",
        "broadcast");
  }
}
