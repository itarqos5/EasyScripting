package dev.easyscripting.commands;

import dev.easyscripting.actors.ActorService;
import dev.easyscripting.api.Scene;
import dev.easyscripting.config.*;
import dev.easyscripting.core.*;
import dev.easyscripting.gui.MenuService;
import dev.easyscripting.items.KitService;
import dev.easyscripting.players.PlayerService;
import dev.easyscripting.recording.*;
import dev.easyscripting.scenes.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;

public final class SceneCommands {
  public static void register(
      CommandRouter router,
      Access access,
      Settings settings,
      Messages messages,
      SceneService scenes,
      ActionRegistry actions,
      ActorService actors,
      RecordingService recordings,
      ActingService acting,
      CameraService cameras,
      KitService kits,
      PlayerService players,
      MenuService menus) {
    router.add(
        "scene",
        "scene.play",
        "create|list|bind|add|remove|play|pause|resume|stop|reset|delete|gui <id> ...",
        (s, a) -> {
          String op = a.get(0, "list");
          if (List.of("create", "bind", "add", "remove", "delete", "append", "repeat", "restore")
              .contains(op)) access.require(s, "scene.edit");
          switch (op) {
            case "list" -> messages.ok(s, String.join(", ", scenes.ids()));
            case "create" -> scenes.create(a.get(1));
            case "append" ->
                scenes.put(
                    SceneEdits.append(
                        scenes.get(a.get(1)),
                        scenes.get(a.get(2)),
                        a.integer(3, 0, 720000),
                        settings.limit("scene-actions")));
            case "repeat" ->
                scenes.put(
                    SceneEdits.repeat(
                        scenes.get(a.get(1)),
                        a.integer(2, 0, 720000),
                        a.integer(3, 0, 720000),
                        a.integer(4, 1, 100),
                        a.integer(5, 1, 720000),
                        settings.limit("scene-actions")));
            case "restore" -> {
              Scene scene = scenes.get(a.get(1));
              scenes.put(
                  new Scene(
                      scene.id(),
                      scene.description(),
                      scene.bindings(),
                      scene.actions(),
                      Checks.bool(a.get(2))));
            }
            case "bind" -> scenes.bind(a.get(1), a.get(2), a.get(3));
            case "add" ->
                scenes.add(
                    a.get(1),
                    new Scene.Action(
                        a.integer(2, 0, 720000),
                        a.get(3),
                        a.get(4),
                        a.size() > 5 ? Args.pairs(a.rest(5)) : Map.of()));
            case "remove" -> scenes.removeAction(a.get(1), a.integer(2, 1, 100000) - 1);
            case "play" -> {
              scenes.play(a.get(1), s);
              messages.ok(s, "Playing scene '" + a.get(1) + "'.");
            }
            case "pause" -> scenes.pause(a.get(1));
            case "resume" -> scenes.resume(a.get(1));
            case "stop" -> scenes.stop(a.get(1), "Stopped by " + s.getName(), true);
            case "reset" -> scenes.reset(a.get(1));
            case "delete" -> scenes.delete(a.get(1));
            case "status" -> messages.ok(s, scenes.status(a.get(1)));
            case "gui" -> menus.timeline(Args.player(s), a.get(1), 0);
            default -> throw new IllegalArgumentException("Unknown scene operation.");
          }
        },
        (s, a) ->
            a.size() == 1
                ? List.of(
                    "create", "list", "append", "repeat", "restore", "bind", "add", "remove",
                    "play", "pause", "resume", "stop", "reset", "delete", "status", "gui")
                : a.size() == 2
                    ? scenes.ids()
                    : a.get(0).equals("add") && a.size() == 4
                        ? List.copyOf(actions.types())
                        : List.of());
    router.add(
        "actor",
        "actor",
        "create|list|info|randomize|set|here|move|copy|hide|show|respawn|attack|kit|pattern|all|delete|act|finish|cancel|play|stop|mode|recording|autoplay"
            + " <id> ...",
        (s, a) -> {
          settings.require("actors");
          String op = a.get(0, "list");
          switch (op) {
            case "list" -> messages.ok(s, String.join(", ", actors.ids()));
            case "create" -> {
              var actor =
                  actors.create(
                      a.get(1),
                      a.get(2, settings.file("config").getString("actors.default-type", "PLAYER")),
                      Args.player(s).getLocation());
              messages.ok(
                  s,
                  "Created "
                      + identitySummary(actor)
                      + ". Keep it as-is or use /actor set "
                      + actor.id()
                      + " name <username>"
                      + (actor.definition.type.equals("PLAYER")
                          ? " and /actor set " + actor.id() + " skin <account>."
                          : "."));
            }
            case "info" -> messages.ok(s, identitySummary(actors.get(a.get(1))));
            case "randomize" -> {
              actors.randomize(a.get(1));
              messages.ok(s, "Randomized " + identitySummary(actors.get(a.get(1))));
            }
            case "act" -> {
              access.require(s, "record");
              acting.start(
                  Args.player(s),
                  a.get(1),
                  a.get(2, "take_" + Long.toString(System.currentTimeMillis(), 36)));
            }
            case "finish" -> acting.finish(Args.player(s));
            case "cancel" -> acting.cancel(Args.player(s));
            case "play" -> {
              access.require(s, "record");
              recordings.playActor(a.get(1));
            }
            case "stop" -> {
              access.require(s, "record");
              recordings.stopActor(a.get(1));
            }
            case "mode" -> {
              actors.set(a.get(1), "mode", a.get(2));
            }
            case "autoplay" -> {
              access.require(s, "record");
              recordings.autoplay(a.get(1), Checks.bool(a.get(2)));
              messages.ok(
                  s,
                  "Autoplay "
                      + (Checks.bool(a.get(2)) ? "enabled" : "disabled")
                      + " for '"
                      + a.get(1)
                      + "'.");
            }
            case "recording" -> {
              access.require(s, "record");
              if (!recordings.ids().contains(a.get(2)))
                throw new IllegalArgumentException("Recording not found.");
              actors.available(a.get(1));
              actors.set(a.get(1), "recording", a.get(2));
            }
            case "set" -> {
              actors.set(a.get(1), a.get(2), a.rest(3));
            }
            case "here" -> actors.teleport(a.get(1), Args.player(s).getLocation());
            case "move" -> {
              actors.available(a.get(1));
              actors
                  .get(a.get(1))
                  .move(Args.player(s).getLocation(), Checks.decimal(a.get(2, "1"), .1, 5));
            }
            case "copy" -> actors.copy(a.get(1), a.get(2), Args.player(s).getLocation());
            case "hide" -> actors.hidden(a.get(1), true);
            case "show" -> actors.hidden(a.get(1), false);
            case "respawn" -> actors.respawn(a.get(1));
            case "attack" -> {
              Player victim = players.player(a.get(2));
              if (!victim.equals(s)) access.require(s, "player.others");
              actors.attack(a.get(1), victim, Checks.decimal(a.get(3, "1"), 0, 1000));
            }
            case "kit" -> {
              access.require(s, "kit");
              actors.available(a.get(1));
              kits.apply(a.get(2), actors.get(a.get(1)).requireEntity());
              actors.save(actors.get(a.get(1)));
            }
            case "pattern" ->
                actors.pattern(
                    a.get(1),
                    a.get(5, "PLAYER"),
                    a.get(2),
                    a.integer(3, 1, 200),
                    Checks.decimal(a.get(4, "2"), .5, 20),
                    Args.player(s).getLocation());
            case "all" -> {
              String group = a.get(1);
              for (ActorService.ManagedActor actor : actors.list())
                if (group.equals("*") || actor.group().equals(group)) {
                  actors.set(actor.id(), a.get(2), a.rest(3));
                }
            }
            case "group" -> {
              String group = a.get(1), operation = a.get(2);
              if (!List.of("hide", "show", "respawn", "jump", "kit").contains(operation))
                throw new IllegalArgumentException(
                    "Group operation must be hide, show, respawn, jump or kit.");
              if (operation.equals("kit")) {
                access.require(s, "kit");
                kits.contents(a.get(3));
              }
              List<ActorService.ManagedActor> members =
                  actors.list().stream()
                      .filter(actor -> group.equals("*") || actor.group().equals(group))
                      .toList();
              if (members.isEmpty()) throw new IllegalArgumentException("No actors in this group.");
              if (!operation.equals("hide")) members.forEach(actor -> actors.available(actor.id()));
              for (ActorService.ManagedActor actor : members)
                switch (operation) {
                  case "hide" -> actors.hidden(actor.id(), true);
                  case "show" -> actors.hidden(actor.id(), false);
                  case "respawn" -> actors.respawn(actor.id());
                  case "jump" ->
                      actor
                          .requireEntity()
                          .setVelocity(actor.requireEntity().getVelocity().setY(.42));
                  case "kit" -> {
                    kits.apply(a.get(3), actor.requireEntity());
                    actors.save(actor);
                  }
                  default -> throw new IllegalStateException("Unvalidated group operation");
                }
            }
            case "delete" -> actors.remove(a.get(1));
            case "gui" -> menus.actor(Args.player(s), a.get(1), a.get(2, "overview"));
            default -> throw new IllegalArgumentException("Unknown actor operation.");
          }
        },
        (s, a) ->
            a.size() == 1
                ? List.of(
                    "create",
                    "list",
                    "info",
                    "randomize",
                    "act",
                    "finish",
                    "cancel",
                    "play",
                    "stop",
                    "mode",
                    "autoplay",
                    "recording",
                    "group",
                    "set",
                    "here",
                    "move",
                    "copy",
                    "hide",
                    "show",
                    "respawn",
                    "attack",
                    "kit",
                    "pattern",
                    "all",
                    "delete",
                    "gui")
                : a.size() == 2
                    ? actors.ids()
                    : a.get(0).equals("gui") && a.size() == 3
                        ? List.of("overview", "appearance", "movement", "acting", "combat")
                        : a.get(0).equals("autoplay") && a.size() == 3
                            ? List.of("on", "off")
                            : a.get(0).equals("mode") && a.size() == 3
                                ? List.of("stop", "repeat", "reverse")
                                : a.get(0).equals("recording") && a.size() == 3
                                    ? recordings.ids()
                                    : a.get(0).equals("set") && a.size() == 3
                                        ? List.of(
                                            "name",
                                            "skin",
                                            "group",
                                            "immortal",
                                            "hittable",
                                            "collidable",
                                            "nametag",
                                            "tablist",
                                            "look",
                                            "wander",
                                            "pose",
                                            "glow",
                                            "sneak",
                                            "sprint")
                                        : List.of());
    router.add(
        "record",
        "record",
        "start <id> | startall <prefix> | stop|stopall | play <id> <actor> [loop] [reverse] |"
            + " playgroup <loop> <reverse> <recording=actor>... | stopplay <actor> | delete <id>",
        (s, a) -> {
          switch (a.get(0)) {
            case "start" -> recordings.start(Args.player(s), a.get(1));
            case "stop" -> recordings.stop(Args.player(s));
            case "startall" -> {
              access.require(s, "player.others");
              recordings.startAll(a.get(1), Bukkit.getOnlinePlayers());
            }
            case "stopall" -> {
              access.require(s, "player.others");
              recordings.stopAll();
            }
            case "playgroup" -> {
              Map<String, String> group = new LinkedHashMap<>();
              for (int i = 3; i < a.size(); i++) {
                String[] pair = a.get(i).split("=", -1);
                if (pair.length != 2
                    || group.putIfAbsent(Checks.id(pair[1]), Checks.id(pair[0])) != null)
                  throw new IllegalArgumentException(
                      "Use recording=actor pairs, each actor only once.");
              }
              recordings.playGroup(group, Checks.bool(a.get(1)), Checks.bool(a.get(2)));
            }
            case "play" ->
                recordings.play(
                    a.get(1), a.get(2), Checks.bool(a.get(3, "off")), Checks.bool(a.get(4, "off")));
            case "stopplay" -> recordings.stopPlayback(a.get(1));
            case "delete" -> recordings.delete(a.get(1));
            case "list" -> messages.ok(s, String.join(", ", recordings.ids()));
            default -> throw new IllegalArgumentException("Unknown recording operation.");
          }
        },
        (s, a) ->
            a.size() == 1
                ? List.of(
                    "start",
                    "startall",
                    "stop",
                    "stopall",
                    "play",
                    "playgroup",
                    "stopplay",
                    "delete",
                    "list")
                : a.size() == 2
                    ? recordings.ids()
                    : a.size() == 3 ? actors.ids() : List.of("on", "off"));
    router.add(
        "camera",
        "effects",
        "move <x> <y> <z> <ticks> [yaw] [pitch] | stop",
        (s, a) -> {
          Player p = Args.player(s);
          if (a.get(0).equals("stop")) cameras.stop(p);
          else if (a.get(0).equals("move"))
            cameras.move(
                p,
                Positions.parse(
                    p.getWorld().getName(),
                    a.get(1),
                    a.get(2),
                    a.get(3),
                    a.get(5, String.valueOf(p.getYaw())),
                    a.get(6, String.valueOf(p.getPitch()))),
                a.integer(4, 1, 72000));
          else throw new IllegalArgumentException("Use camera move or stop.");
        },
        "move",
        "stop");
  }

  private static String identitySummary(ActorService.ManagedActor actor) {
    var definition = actor.definition;
    return "actor '"
        + actor.id()
        + "': name="
        + definition.name
        + "; skin="
        + (definition.type.equals("PLAYER")
            ? (definition.skin.isBlank() ? "default" : definition.skin)
            : "mob appearance")
        + "; type="
        + definition.type
        + "; recording="
        + (definition.recording.isBlank() ? "none" : definition.recording)
        + "; mode="
        + definition.playbackMode.name().toLowerCase(Locale.ROOT)
        + "; autoplay="
        + definition.autoplay
        + "; hittable="
        + definition.hittable
        + "; immortal="
        + definition.immortal;
  }
}
