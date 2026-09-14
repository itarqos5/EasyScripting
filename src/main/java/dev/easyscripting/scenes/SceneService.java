package dev.easyscripting.scenes;

import dev.easyscripting.actors.ActorService;
import dev.easyscripting.api.*;
import dev.easyscripting.config.*;
import dev.easyscripting.core.*;
import dev.easyscripting.players.*;
import dev.easyscripting.players.EntitySnapshot;
import dev.easyscripting.storage.YamlStore;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SceneService implements Listener, AutoCloseable {
  private final JavaPlugin plugin;
  private final Settings settings;
  private final Messages messages;
  private final YamlStore store;
  private final TickEngine ticks;
  private final ActorService actors;
  private final PlayerService players;
  private final ActionRegistry actions;
  private final Map<String, Scene> scenes = new TreeMap<>();
  private final Map<String, Run> active = new LinkedHashMap<>();
  private final Map<String, Run> completed = new HashMap<>();
  private final Map<UUID, String> locks = new HashMap<>();

  public SceneService(
      JavaPlugin plugin,
      Settings settings,
      Messages messages,
      YamlStore store,
      TickEngine ticks,
      ActorService actors,
      PlayerService players,
      ActionRegistry actions) {
    this.plugin = plugin;
    this.settings = settings;
    this.messages = messages;
    this.store = store;
    this.ticks = ticks;
    this.actors = actors;
    this.players = players;
    this.actions = actions;
    actors.onRemoved(
        id -> {
          for (Run run : List.copyOf(active.values()))
            if (run.references.containsValue("actor:" + id))
              stop(run.scene.id(), "Actor " + id + " became unavailable", true);
        });
  }

  public void load() {
    store
        .load("scenes")
        .forEach(
            (id, yaml) -> {
              try {
                Scene scene = SceneCodec.decode(id, yaml, settings.limit("scene-actions"));
                for (Scene.Action a : scene.actions()) actions.spec(a.type());
                scenes.put(id, scene);
              } catch (RuntimeException ex) {
                plugin.getLogger().warning(ex.getMessage());
              }
            });
  }

  public List<String> ids() {
    return List.copyOf(scenes.keySet());
  }

  public Scene get(String id) {
    Scene scene = scenes.get(id);
    if (scene == null)
      throw new IllegalArgumentException(
          "Scene '" + id + "' not found. Create it with /scene create " + id);
    return scene;
  }

  public void put(Scene scene) {
    if (active.containsKey(scene.id()))
      throw new IllegalArgumentException("Stop the scene before editing it.");
    if (scene.actions().size() > settings.limit("scene-actions"))
      throw new IllegalArgumentException("Scene action limit exceeded.");
    scene.actions().forEach(action -> actions.spec(action.type()));
    scenes.put(scene.id(), scene);
    store.save("scenes", scene.id(), SceneCodec.encode(scene));
  }

  public void create(String id) {
    if (scenes.containsKey(id)) throw new IllegalArgumentException("Scene already exists.");
    put(new Scene(id, id, Map.of(), List.of(), false));
  }

  public void delete(String id) {
    get(id);
    if (active.containsKey(id)) stop(id, "Deleted", true);
    scenes.remove(id);
    completed.remove(id);
    store.delete("scenes", id);
  }

  public void bind(String id, String name, String target) {
    Scene s = get(id);
    Map<String, String> bindings = new LinkedHashMap<>(s.bindings());
    bindings.put(Checks.id(name), target);
    put(new Scene(s.id(), s.description(), bindings, s.actions(), s.restoreOnComplete()));
  }

  public void add(String id, Scene.Action action) {
    actions.spec(action.type());
    Scene s = get(id);
    if (s.actions().size() >= settings.limit("scene-actions"))
      throw new IllegalArgumentException("Scene action limit reached.");
    List<Scene.Action> next = new ArrayList<>(s.actions());
    next.add(action);
    put(new Scene(s.id(), s.description(), s.bindings(), next, s.restoreOnComplete()));
  }

  public void removeAction(String id, int index) {
    Scene s = get(id);
    List<Scene.Action> next = new ArrayList<>(s.actions());
    if (index < 0 || index >= next.size())
      throw new IllegalArgumentException("Action index is out of range.");
    next.remove(index);
    put(new Scene(s.id(), s.description(), s.bindings(), next, s.restoreOnComplete()));
  }

  public UUID play(String id, CommandSender sender) {
    settings.require("scenes");
    Scene scene = get(id);
    if (active.containsKey(id)) throw new IllegalArgumentException("Scene is already playing.");
    if (active.size() >= settings.limit("active-scenes"))
      throw new IllegalArgumentException("Active scene limit reached.");
    ActionRegistry.Context context =
        new ActionRegistry.Context(sender, scene.bindings(), actors, players);
    Run run = new Run(scene, context);
    for (Scene.Action a : scene.actions()) {
      actions.validate(context, a);
      run.capture(a.target());
      if (a.arguments().containsKey("at")) run.capture(a.arg("at"));
      if (a.arguments().containsKey("victim")) run.capture(a.arg("victim"));
      if (List.of("time", "weather").contains(a.type())) {
        World world = context.entity(a.target()).getWorld();
        run.worlds.computeIfAbsent(world.getUID(), k -> new WorldState(world));
      }
    }
    for (UUID resource : run.resources())
      if (locks.containsKey(resource))
        throw new IllegalArgumentException(
            "Scene '"
                + locks.get(resource)
                + "' is already using a required player, actor or world.");
    run.actorIds().forEach(actors::available);
    run.playerIds().forEach(players::available);
    SceneStartEvent event = new SceneStartEvent(scene, run.id);
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled())
      throw new IllegalArgumentException("Scene start was cancelled by another plugin.");
    // Start-event listeners may have started another scene, so validate again after the event.
    for (UUID resource : run.resources())
      if (locks.containsKey(resource))
        throw new IllegalArgumentException("A start-event listener reserved a required target.");
    run.actorIds().forEach(actors::available);
    run.playerIds().forEach(players::available);
    run.actorIds().forEach(actorId -> actors.reserve(actorId, "scene " + id));
    run.playerIds().forEach(player -> players.reserve(player, "scene " + id));
    run.resources().forEach(resource -> locks.put(resource, id));
    active.put(id, run);
    completed.remove(id);
    run.timeline.start();
    run.job =
        ticks.add(
            new TickEngine.Job() {
              public boolean tick() {
                if (!active.containsKey(scene.id())) return false;
                try {
                  if (!settings.enabled("scenes")) {
                    stop(scene.id(), "Scenes disabled", true);
                    return false;
                  }
                  run.timeline.advance(
                      settings.limit("actions-per-tick"), a -> actions.execute(context, a));
                  if (run.timeline.state() == Timeline.State.COMPLETED) {
                    finish(run);
                    return false;
                  }
                  return !run.timeline.terminal();
                } catch (RuntimeException ex) {
                  messages.error(
                      sender,
                      "Scene '"
                          + id
                          + "' failed at tick "
                          + run.timeline.tick()
                          + ": "
                          + ex.getMessage());
                  stop(id, ex.getMessage(), true);
                  return false;
                }
              }
            });
    return run.id;
  }

  private void finish(Run run) {
    active.remove(run.scene.id());
    release(run);
    if (!run.scene.restoreOnComplete() || !run.restore()) completed.put(run.scene.id(), run);
    Bukkit.getPluginManager().callEvent(new SceneCompleteEvent(run.scene.id(), run.id));
    messages.ok(run.context.sender(), "Scene '" + run.scene.id() + "' completed.");
  }

  public void stop(String id, String reason, boolean restore) {
    Run run = active.remove(id);
    if (run == null) throw new IllegalArgumentException("Scene '" + id + "' is not running.");
    run.timeline.cancel();
    release(run);
    if (run.job != null) ticks.cancel(run.job);
    if (!restore || !run.restore()) completed.put(id, run);
    Bukkit.getPluginManager().callEvent(new SceneStopEvent(id, run.id, reason));
  }

  public void pause(String id) {
    requireRun(id).timeline.pause();
  }

  public void resume(String id) {
    requireRun(id).timeline.resume();
  }

  public String status(String id) {
    Run run = active.get(id);
    return run == null ? "idle" : run.timeline.state() + " at tick " + run.timeline.tick();
  }

  public void reset(String id) {
    if (active.containsKey(id)) {
      stop(id, "Reset", true);
      return;
    }
    Run run = completed.get(id);
    if (run == null)
      throw new IllegalArgumentException("No completed take to reset for '" + id + "'.");
    for (UUID resource : run.resources())
      if (locks.containsKey(resource))
        throw new IllegalArgumentException("A target is being used by another scene.");
    run.actorIds().forEach(actors::available);
    run.playerIds().forEach(players::available);
    if (run.restore()) completed.remove(id);
    else
      throw new IllegalArgumentException(
          "Some targets could not be restored; the take is retained. Correct the reported cause and"
              + " retry /scene reset "
              + id);
  }

  private Run requireRun(String id) {
    Run run = active.get(id);
    if (run == null) throw new IllegalArgumentException("Scene is not running.");
    return run;
  }

  private void release(Run run) {
    run.resources().forEach(resource -> locks.remove(resource, run.scene.id()));
    run.actorIds().forEach(id -> actors.release(id, "scene " + run.scene.id()));
    run.playerIds().forEach(id -> players.release(id, "scene " + run.scene.id()));
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    for (Run r : List.copyOf(active.values()))
      if (r.snapshots.containsKey(e.getPlayer().getUniqueId())
          || r.context.sender().equals(e.getPlayer()))
        stop(r.scene.id(), "Player disconnected", true);
  }

  @EventHandler(ignoreCancelled = true)
  public void unload(WorldUnloadEvent e) {
    for (Run r : List.copyOf(active.values()))
      if (r.snapshots.values().stream().anyMatch(s -> s.location().getWorld().equals(e.getWorld())))
        stop(r.scene.id(), "World unloaded", true);
  }

  @Override
  public void close() {
    for (String id : List.copyOf(active.keySet())) stop(id, "Plugin disabled", true);
    completed.clear();
  }

  private final class Run {
    final UUID id = UUID.randomUUID();
    final Scene scene;
    final ActionRegistry.Context context;
    final Timeline timeline;
    final Map<UUID, EntitySnapshot> snapshots = new LinkedHashMap<>();
    final Map<UUID, String> references = new HashMap<>();
    final Map<UUID, ActorService.ManagedActor> capturedActors = new HashMap<>();
    final Map<UUID, WorldState> worlds = new HashMap<>();
    UUID job;

    Run(Scene scene, ActionRegistry.Context context) {
      this.scene = scene;
      this.context = context;
      timeline = new Timeline(scene.actions());
    }

    void capture(String target) {
      LivingEntity e = context.entity(target);
      snapshots.computeIfAbsent(e.getUniqueId(), k -> players.capture(e));
      String reference = context.reference(target);
      references.put(e.getUniqueId(), reference);
      if (reference.startsWith("actor:"))
        capturedActors.putIfAbsent(e.getUniqueId(), actors.get(reference.substring(6)));
    }

    Set<UUID> resources() {
      Set<UUID> result = new HashSet<>(snapshots.keySet());
      result.addAll(worlds.keySet());
      return result;
    }

    Set<String> actorIds() {
      Set<String> result = new HashSet<>();
      references.values().stream()
          .filter(ref -> ref.startsWith("actor:"))
          .forEach(ref -> result.add(ref.substring(6)));
      return result;
    }

    Set<UUID> playerIds() {
      Set<UUID> ids = new HashSet<>();
      references.forEach(
          (id, ref) -> {
            if (!ref.startsWith("actor:")) ids.add(id);
          });
      return ids;
    }

    boolean restore() {
      boolean[] restored = {true};
      snapshots.forEach(
          (uuid, snapshot) -> {
            try {
              Entity entity = Bukkit.getEntity(uuid);
              String ref = references.get(uuid);
              if (ref.startsWith("actor:")) {
                String actorId = ref.substring(6);
                if (!actors.ids().contains(actorId)) return;
                ActorService.ManagedActor a = actors.get(actorId);
                if (a != capturedActors.get(uuid)) return;
                a.stop();
                if (a.entity().isEmpty()) actors.respawn(actorId);
                entity = a.requireEntity();
              }
              if (entity instanceof Player p && (p.isDead() || !p.isOnline()))
                players.defer(uuid, snapshot);
              else if (entity instanceof LivingEntity living && !living.isDead())
                players.restore(living, snapshot);
              else if (!ref.startsWith("actor:")) players.defer(uuid, snapshot);
            } catch (RuntimeException ex) {
              restored[0] = false;
              plugin
                  .getLogger()
                  .warning("Could not restore " + references.get(uuid) + ": " + ex.getMessage());
            }
          });
      for (WorldState world : worlds.values())
        try {
          world.restore();
        } catch (RuntimeException ex) {
          restored[0] = false;
          plugin.getLogger().warning("Could not restore scene world: " + ex.getMessage());
        }
      if (!restored[0])
        messages.error(
            context.sender(),
            "Scene '"
                + scene.id()
                + "' finished with incomplete restoration. Its take is retained for /scene reset;"
                + " see the server log.");
      return restored[0];
    }
  }

  private record WorldState(
      World world, long time, boolean storm, boolean thunder, int weather, int thunderDuration) {
    WorldState(World w) {
      this(
          w,
          w.getFullTime(),
          w.hasStorm(),
          w.isThundering(),
          w.getWeatherDuration(),
          w.getThunderDuration());
    }

    void restore() {
      world.setFullTime(time);
      world.setStorm(storm);
      world.setThundering(thunder);
      world.setWeatherDuration(weather);
      world.setThunderDuration(thunderDuration);
    }
  }
}
