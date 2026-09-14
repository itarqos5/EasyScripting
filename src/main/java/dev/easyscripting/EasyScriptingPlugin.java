package dev.easyscripting;

import dev.easyscripting.actors.*;
import dev.easyscripting.api.*;
import dev.easyscripting.commands.*;
import dev.easyscripting.config.*;
import dev.easyscripting.core.*;
import dev.easyscripting.effects.EffectService;
import dev.easyscripting.gui.MenuService;
import dev.easyscripting.integration.*;
import dev.easyscripting.items.*;
import dev.easyscripting.players.*;
import dev.easyscripting.recording.*;
import dev.easyscripting.scenes.*;
import dev.easyscripting.storage.YamlStore;
import dev.easyscripting.utilities.*;
import dev.easyscripting.world.*;
import java.util.*;
import java.util.logging.Level;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.event.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class EasyScriptingPlugin extends JavaPlugin {
  private final List<AutoCloseable> resources = new ArrayList<>();
  private boolean closing;

  @Override
  public void onEnable() {
    try {
      closing = false;
      YamlStore store = own(new YamlStore(this));
      Settings settings = new Settings(this, store);
      settings.load(MenuService::validate);
      Messages messages = new Messages(settings);
      Access access = new Access(settings);
      TickEngine ticks = own(new TickEngine(this));
      // Nicknames restore last, after scenes, acting and deferred player snapshots release.
      IdentityService identities = own(new IdentityService(this, settings, store, messages));
      identities.load();
      PlayerService players = own(new PlayerService(this, settings, store, ticks));
      players.load();
      ActorBackend playerBackend = null;
      if (settings.file("config").getBoolean("actors.citizens-enabled", true)
          && getServer().getPluginManager().isPluginEnabled("Citizens"))
        playerBackend = new CitizensBackend(this);
      ActorService actors = own(new ActorService(this, settings, store, ticks, playerBackend));
      actors.load();
      KitService kits = new KitService(store);
      kits.load();
      WarpService warps = new WarpService(store);
      warps.load();
      warps.settings(settings);
      ItemService items = new ItemService(this, settings, ticks);
      items.playerFreeze(
          player -> players.flag(player, "freeze", !players.flag(player.getUniqueId(), "freeze")));
      identities.onBlacklist(actors::purgeIdentity);
      actors.identityFilter(identities::blocked);
      ActionRegistry actions = new ActionRegistry(settings);
      SceneService scenes =
          own(new SceneService(this, settings, messages, store, ticks, actors, players, actions));
      scenes.load();
      RecordingService recordings =
          own(new RecordingService(settings, messages, store, ticks, actors));
      recordings.load();
      recordings.autoplayAll();
      ActingService acting =
          own(new ActingService(settings, messages, actors, players, recordings));
      identities.guards(p -> players.available(p.getUniqueId()), p -> acting.actor(p).isPresent());
      players.onRestore(identities::afterRestore);
      players.onCapture(identities::captureIdentity);
      NicknameService nicknames =
          own(new NicknameService(settings, messages, identities, actors, ticks));
      CameraService cameras = own(new CameraService(ticks, players));
      ModerationService moderation =
          own(new ModerationService(this, settings, messages, players, ticks));
      WorldService worlds = own(new WorldService(settings, ticks));
      RegionService regions = own(new RegionService(settings, messages, items, ticks, store));
      regions.load();
      EffectService effects = own(new EffectService(settings, ticks));
      TeamService teams = own(new TeamService(store));
      teams.load();
      VillagerService villagers = own(new VillagerService(store));
      villagers.load();
      DeathService deaths = new DeathService(settings, ticks, store);
      deaths.load();
      deaths.scenes(
          scenes::get,
          (player, id) -> {
            access.require(player, "scene.play");
            scenes.play(id, player);
          });
      LockService locks = new LockService(this, settings, players, store);
      locks.load();
      VoiceBridge voice = own(createVoiceBridge());
      moderation.voice(voice);
      boolean[] actorsEnabled = {settings.enabled("actors")};
      settings.onChange(
          () -> {
            if (actorsEnabled[0] != settings.enabled("actors")) {
              actorsEnabled[0] = settings.enabled("actors");
              actors.refresh();
            }
            players.refresh();
            if (!settings.enabled("recording") && moderation.recording())
              moderation.recordingStop(true);
            if (!settings.enabled("effects")) effects.stop();
            if (!settings.enabled("voice") && voice.available()) voice.mute(false);
          });
      MenuService menus =
          own(
              new MenuService(
                  this,
                  settings,
                  messages,
                  access,
                  scenes,
                  actors,
                  players,
                  kits,
                  warps,
                  recordings,
                  acting,
                  teams,
                  villagers));
      for (Listener listener :
          List.of(
              players,
              actors,
              items,
              identities,
              scenes,
              recordings,
              acting,
              cameras,
              moderation,
              worlds,
              warps,
              regions,
              effects,
              teams,
              deaths,
              locks,
              menus)) getServer().getPluginManager().registerEvents(listener, this);
      CommandRouter router = new CommandRouter(this, access, messages);
      KitImports imports = new KitImports(kits, settings);
      menus.kitImports(imports);
      router.add(
          "kits",
          "kit",
          "[import <provider> <kit> [new-id] | imports | export <id>]",
          (sender, args) -> {
            settings.require("kits");
            if (args.size() == 0) {
              menus.open(Args.player(sender), "kits", 0);
              return;
            }
            access.require(sender, "kit.edit");
            switch (args.get(0)) {
              case "imports" -> menus.kitImportMenu(Args.player(sender));
              case "import" -> {
                String destination = args.get(3, args.get(2).toLowerCase(Locale.ROOT));
                imports.importKit(args.get(1), args.get(2), destination, Args.player(sender));
                messages.ok(sender, "Imported kit '" + destination + "'. Only items are copied.");
              }
              case "export" -> {
                kits.export(args.get(1));
                messages.ok(
                    sender,
                    "Export queued: plugins/EasyScripting/kit-exports/" + args.get(1) + ".yml");
              }
              default ->
                  throw new IllegalArgumentException(
                      "Use /es kits, /es kits imports, import or export.");
            }
          },
          (sender, args) -> {
            if (args.size() == 1) return List.of("imports", "import", "export");
            if (args.get(0).equals("export")) return kits.ids();
            if (args.size() == 2 && args.get(0).equals("import")) return imports.sources();
            if (args.size() == 3 && args.get(0).equals("import")) return imports.names(args.get(1));
            return List.of();
          });
      router.add(
          "nickname",
          "identity",
          "<online-player-or-nickname> [off] | off",
          (sender, args) -> {
            if (args.size() == 1 && args.get(0).equalsIgnoreCase("off")) {
              access.require(sender, "player.others");
              nicknames.resetAll(sender);
              return;
            }
            if (args.size() > 2 || (args.size() == 2 && !args.get(1).equalsIgnoreCase("off")))
              throw new IllegalArgumentException("Use /nickname <player> [off] or /nickname off.");
            var target = nicknames.target(args.get(0));
            if (!target.equals(sender)) access.require(sender, "player.others");
            if (args.size() == 2) nicknames.reset(target, sender);
            else nicknames.random(target, sender);
          },
          (sender, args) -> {
            if (args.size() == 2) return List.of("off");
            List<String> names = new ArrayList<>(nicknames.names());
            names.add("off");
            return names;
          });
      router.add(
          "menu",
          "use",
          "[main|scenes|actors|players|kits|warps|recording|features|item|teams|production|world|effects|permissions|villagers]",
          (s, a) -> menus.open(Args.player(s), a.get(0, "main"), 0),
          "main",
          "scenes",
          "actors",
          "players",
          "kits",
          "warps",
          "recording",
          "features",
          "item",
          "teams",
          "production",
          "world",
          "effects",
          "permissions",
          "villagers");
      router.add(
          "features",
          "admin",
          "[feature]",
          (s, a) -> {
            if (a.size() == 0) menus.open(Args.player(s), "features", 0);
            else {
              settings.toggle(a.get(0));
              messages.ok(s, a.get(0) + " enabled=" + settings.enabled(a.get(0)));
            }
          },
          (s, a) -> Settings.FEATURES);
      router.add(
          "permissions",
          "admin",
          "<feature> <everyone|permission.node>",
          (s, a) -> {
            access.set(a.get(0), a.get(1));
            messages.ok(s, "Permission rule saved.");
          },
          (s, a) -> Access.EDITABLE);
      router.add(
          "reload",
          "admin",
          "",
          (s, a) -> {
            settings.load(MenuService::validate);
            menus.close();
            moderation.reload();
            worlds.reload();
            messages.ok(
                s, "Configuration and menus reloaded. Definitions reload on a full restart.");
          });
      router.add(
          "status",
          "use",
          "",
          (s, a) ->
              messages.ok(
                  s,
                  "EasyScripting "
                      + getPluginMeta().getVersion()
                      + "; scenes="
                      + scenes.ids().size()
                      + "; actors="
                      + actors.ids().size()
                      + "; active jobs="
                      + ticks.activeJobs()
                      + "; voice="
                      + voice.available()));
      router.add(
          "lock",
          "locks",
          "container|frame on|off",
          (s, a) -> {
            settings.require("locks");
            if (a.get(0).equals("container"))
              locks.container(Args.player(s), Checks.bool(a.get(1)));
            else if (a.get(0).equals("frame")) locks.frame(Args.player(s), Checks.bool(a.get(1)));
            else throw new IllegalArgumentException("Use lock container or frame.");
          },
          "container",
          "frame");
      SceneCommands.register(
          router,
          access,
          settings,
          messages,
          scenes,
          actions,
          actors,
          recordings,
          acting,
          cameras,
          kits,
          players,
          menus);
      PlayerCommands.register(
          router,
          access,
          settings,
          messages,
          players,
          identities,
          kits,
          items,
          warps,
          deaths,
          moderation,
          menus);
      ProductionCommands.register(
          router,
          access,
          settings,
          messages,
          worlds,
          regions,
          items,
          effects,
          moderation,
          teams,
          villagers,
          voice);
      for (String name : List.of("es", "scene", "actor", "nickname")) {
        var command = Objects.requireNonNull(getCommand(name));
        command.setExecutor(router);
        command.setTabCompleter(router);
      }
      getServer()
          .getServicesManager()
          .register(
              EasyScriptingApi.class,
              new EasyScriptingApi() {
                private void mainThread() {
                  if (!Bukkit.isPrimaryThread() || closing)
                    throw new IllegalStateException(
                        "EasyScripting API requires the active server thread.");
                }

                public List<String> sceneIds() {
                  mainThread();
                  return scenes.ids();
                }

                public Scene scene(String id) {
                  mainThread();
                  return scenes.get(id);
                }

                public void saveScene(Scene scene) {
                  mainThread();
                  scenes.put(scene);
                }

                public UUID play(String id, CommandSender director) {
                  mainThread();
                  access.require(director, "scene.play");
                  return scenes.play(id, director);
                }

                public void stop(String id, boolean restore) {
                  mainThread();
                  scenes.stop(id, "API request", restore);
                }

                public void pause(String id) {
                  mainThread();
                  scenes.pause(id);
                }

                public void resume(String id) {
                  mainThread();
                  scenes.resume(id);
                }

                public void reset(String id) {
                  mainThread();
                  scenes.reset(id);
                }

                public void deleteScene(String id) {
                  mainThread();
                  scenes.delete(id);
                }

                public List<String> actorIds() {
                  mainThread();
                  return actors.ids();
                }

                public Actor actor(String id) {
                  mainThread();
                  return actors.get(id);
                }

                public Actor createActor(
                    String id, org.bukkit.entity.EntityType type, Location location) {
                  mainThread();
                  return actors.create(id, type.name(), location);
                }

                public void removeActor(String id) {
                  mainThread();
                  actors.remove(id);
                }
              },
              this,
              ServicePriority.Normal);
      getLogger()
          .info(
              "EasyScripting "
                  + getPluginMeta().getVersion()
                  + " enabled on "
                  + Bukkit.getMinecraftVersion()
                  + "; scenes="
                  + scenes.ids().size()
                  + ", actors="
                  + actors.ids().size()
                  + ", Citizens="
                  + (playerBackend != null)
                  + ", voice="
                  + voice.available());
    } catch (Exception | LinkageError ex) {
      getLogger()
          .log(
              Level.SEVERE,
              "EasyScripting could not start. Correct the configuration or dependency error below;"
                  + " original files are preserved.",
              ex);
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  private VoiceBridge createVoiceBridge() {
    if (!getServer().getPluginManager().isPluginEnabled("voicechat")) return VoiceBridge.absent();
    return loadVoiceService();
  }

  private VoiceBridge loadVoiceService() {
    var service =
        getServer()
            .getServicesManager()
            .load(de.maxhenkel.voicechat.api.BukkitVoicechatService.class);
    return service == null ? VoiceBridge.absent() : new SimpleVoiceBridge(service);
  }

  private <T extends AutoCloseable> T own(T resource) {
    resources.add(resource);
    return resource;
  }

  @Override
  public void onDisable() {
    closing = true;
    for (AutoCloseable resource : resources)
      if (resource instanceof TickEngine engine) engine.beginShutdown();
    getServer().getServicesManager().unregisterAll(this);
    for (int i = resources.size() - 1; i >= 0; i--)
      try {
        resources.get(i).close();
      } catch (Exception ex) {
        getLogger()
            .log(
                Level.SEVERE,
                "Could not clean up " + resources.get(i).getClass().getSimpleName(),
                ex);
      }
    resources.clear();
    getServer().getScheduler().cancelTasks(this);
    HandlerList.unregisterAll(this);
  }
}
