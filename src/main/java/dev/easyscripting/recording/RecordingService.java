package dev.easyscripting.recording;

import dev.easyscripting.actors.ActorService;
import dev.easyscripting.config.*;
import dev.easyscripting.core.*;
import dev.easyscripting.players.EntitySnapshot;
import dev.easyscripting.storage.YamlStore;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

public final class RecordingService implements Listener, AutoCloseable {
  public record Frame(
      Location location,
      boolean sneak,
      boolean sprint,
      boolean swing,
      boolean boat,
      ItemStack main,
      ItemStack off,
      ItemStack[] armor,
      Pose pose,
      boolean hurt,
      boolean offSwing,
      int fire) {
    Map<String, Object> yaml() {
      Map<String, Object> map = new LinkedHashMap<>(Positions.encode(location));
      map.put("sneak", sneak);
      map.put("sprint", sprint);
      map.put("swing", swing);
      map.put("boat", boat);
      map.put("main", main);
      map.put("off", off);
      if (armor != null) map.put("armor", Arrays.asList(armor));
      map.put("pose", pose.name());
      map.put("hurt", hurt);
      map.put("off-swing", offSwing);
      map.put("fire", fire);
      return map;
    }
  }

  private final Settings settings;
  private final Messages messages;
  private final YamlStore store;
  private final TickEngine ticks;
  private final ActorService actors;
  private final Map<String, List<Frame>> recordings = new TreeMap<>();
  private final Map<UUID, Capture> captures = new HashMap<>();
  private final Map<String, UUID> playback = new HashMap<>();
  private final Set<UUID> swung = new HashSet<>();
  private final Set<UUID> offSwung = new HashSet<>(), hurt = new HashSet<>();
  private java.util.function.Consumer<Player> captureStopped = player -> {};

  public void onCaptureStopped(java.util.function.Consumer<Player> listener) {
    captureStopped = listener;
  }

  public boolean capturing(Player player) {
    return captures.containsKey(player.getUniqueId());
  }

  public boolean playing(String actor) {
    return playback.containsKey(actor);
  }

  public RecordingService(
      Settings settings,
      Messages messages,
      YamlStore store,
      TickEngine ticks,
      ActorService actors) {
    this.settings = settings;
    this.messages = messages;
    this.store = store;
    this.ticks = ticks;
    this.actors = actors;
    actors.onRemoved(
        id -> {
          if (playing(id)) stopPlayback(id);
        });
  }

  public void load() {
    store
        .load("recordings")
        .forEach(
            (id, y) -> {
              try {
                List<Frame> frames = new ArrayList<>();
                for (Map<?, ?> raw : y.getMapList("frames")) {
                  if (frames.size() >= settings.limit("recording-ticks"))
                    throw new IllegalArgumentException("Recording exceeds configured frame limit.");
                  YamlConfiguration f = new YamlConfiguration();
                  raw.forEach((k, v) -> f.set(String.valueOf(k), v));
                  frames.add(
                      new Frame(
                          Positions.read(f),
                          f.getBoolean("sneak"),
                          f.getBoolean("sprint"),
                          f.getBoolean("swing"),
                          f.getBoolean("boat"),
                          f.getItemStack("main", new ItemStack(Material.AIR)),
                          f.getItemStack("off", new ItemStack(Material.AIR)),
                          f.contains("armor") ? EntitySnapshot.items(f.getList("armor"), 4) : null,
                          Checks.choice(
                              Pose.class,
                              f.getString("pose", f.getBoolean("sneak") ? "SNEAKING" : "STANDING")),
                          f.getBoolean("hurt"),
                          f.getBoolean("off-swing"),
                          f.getInt("fire")));
                }
                recordings.put(id, List.copyOf(frames));
              } catch (RuntimeException ex) {
                Bukkit.getLogger().warning("recordings/" + id + ".yml: " + ex.getMessage());
              }
            });
  }

  public List<String> ids() {
    return List.copyOf(recordings.keySet());
  }

  public void start(Player player, String id) {
    validateStart(player, id);
    Capture capture = new Capture(id, player);
    capture.frames.add(frame(player));
    captures.put(player.getUniqueId(), capture);
    capture.job =
        ticks.add(
            new TickEngine.Job() {
              public boolean tick() {
                if (!player.isOnline()
                    || player.isDead()
                    || !settings.enabled("recording")
                    || capture.frames.size() >= settings.limit("recording-ticks")) {
                  stop(player);
                  return false;
                }
                capture.frames.add(frame(player));
                return true;
              }

              public void stopped() {
                if (captures.containsKey(player.getUniqueId())) cancel(player);
              }
            });
  }

  public void validateStart(Player player, String id) {
    settings.require("recording");
    Checks.id(id);
    if (captures.containsKey(player.getUniqueId())
        || captures.values().stream().anyMatch(c -> c.id.equals(id)))
      throw new IllegalArgumentException("A recording with this player or id is already active.");
    if (recordings.containsKey(id))
      throw new IllegalArgumentException("Recording already exists; delete it or choose a new id.");
    if (captures.size() >= 8)
      throw new IllegalArgumentException("Eight movement recordings are already active.");
  }

  public void startAll(String prefix, Collection<? extends Player> players) {
    Checks.id(prefix);
    if (players.isEmpty() || captures.size() + players.size() > 8)
      throw new IllegalArgumentException(
          "Synchronized capture requires 1..8 available recording slots.");
    Map<Player, String> batch = new LinkedHashMap<>();
    for (Player player : players) {
      String id = prefix + "_" + player.getName().toLowerCase(Locale.ROOT);
      validateStart(player, id);
      batch.put(player, id);
    }
    // All jobs start in the next shared scheduler pulse, after the complete preflight.
    batch.forEach(this::start);
  }

  public void stopAll() {
    for (Capture capture : List.copyOf(captures.values())) stop(capture.player);
  }

  public void playGroup(Map<String, String> actorRecordings, boolean loop, boolean reverse) {
    if (actorRecordings.isEmpty() || actorRecordings.size() > settings.limit("actors"))
      throw new IllegalArgumentException("Provide a bounded list of recording=actor pairs.");
    actorRecordings.forEach((actor, recording) -> validatePlayback(recording, actor));
    List<String> started = new ArrayList<>();
    try {
      for (var entry : actorRecordings.entrySet()) {
        play(entry.getValue(), entry.getKey(), loop, reverse);
        started.add(entry.getKey());
      }
    } catch (RuntimeException ex) {
      started.forEach(this::stopPlayback);
      throw ex;
    }
  }

  public void stop(Player player) {
    finishCapture(player, true);
  }

  public void cancel(Player player) {
    finishCapture(player, false);
  }

  private Frame frame(Player player) {
    return new Frame(
        player.getLocation(),
        player.isSneaking(),
        player.isSprinting(),
        swung.remove(player.getUniqueId()),
        player.getVehicle() instanceof Boat,
        player.getInventory().getItemInMainHand().clone(),
        player.getInventory().getItemInOffHand().clone(),
        Arrays.stream(player.getInventory().getArmorContents())
            .map(i -> i == null ? null : i.clone())
            .toArray(ItemStack[]::new),
        player.getPose(),
        hurt.remove(player.getUniqueId()),
        offSwung.remove(player.getUniqueId()),
        player.getFireTicks());
  }

  private void finishCapture(Player player, boolean save) {
    Capture capture = captures.remove(player.getUniqueId());
    if (capture == null) throw new IllegalArgumentException("This player is not recording.");
    ticks.cancel(capture.job);
    swung.remove(player.getUniqueId());
    offSwung.remove(player.getUniqueId());
    hurt.remove(player.getUniqueId());
    try {
      if (save) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("schema", 2);
        y.set("frames", capture.frames.stream().map(Frame::yaml).toList());
        store.save("recordings", capture.id, y);
        recordings.put(capture.id, List.copyOf(capture.frames));
        messages.ok(
            player, "Saved movement '" + capture.id + "' (" + capture.frames.size() + " ticks).");
      }
    } finally {
      captureStopped.accept(player);
    }
  }

  public void play(String recording, String actorId, boolean loop, boolean reverse) {
    play(recording, actorId, loop ? PlaybackMode.REPEAT : PlaybackMode.STOP, reverse, true);
  }

  public void playActor(String actorId) {
    var definition = actors.get(actorId).definition;
    play(definition.recording, actorId, definition.playbackMode, false, false);
  }

  private void play(
      String recording,
      String actorId,
      PlaybackMode mode,
      boolean reverse,
      boolean restoreOnComplete) {
    validatePlayback(recording, actorId);
    List<Frame> frames = recordings.get(recording);
    ActorService.ManagedActor actor = actors.get(actorId);
    LivingEntity entity = actor.requireEntity();
    actor.stop();
    EntitySnapshot snapshot = EntitySnapshot.capture(entity);
    actors.reserve(actorId, "recording " + recording);
    boolean gravity = entity.hasGravity();
    var visualFire = entity.getVisualFire();
    entity.setGravity(false);
    entity.setVelocity(new org.bukkit.util.Vector());
    UUID job =
        ticks.add(
            new TickEngine.Job() {
              final PlaybackCursor cursor = new PlaybackCursor(frames.size(), mode, reverse);
              boolean completed;
              Boat vehicle;

              public boolean tick() {
                if (!settings.enabled("recording") || actor.entity().isEmpty()) return false;
                Frame f = frames.get(cursor.index());
                if (Bukkit.getWorld(f.location.getWorld().getUID()) != f.location.getWorld())
                  return false;
                LivingEntity e = actor.requireEntity();
                if (f.boat) {
                  if (vehicle == null || !vehicle.isValid()) {
                    vehicle =
                        (Boat) f.location.getWorld().spawnEntity(f.location, EntityType.OAK_BOAT);
                    vehicle.setPersistent(false);
                    vehicle.setGravity(false);
                    vehicle.addPassenger(e);
                  }
                  vehicle.teleport(
                      f.location,
                      org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN,
                      io.papermc.paper.entity.TeleportFlag.EntityState.RETAIN_PASSENGERS);
                } else {
                  if (vehicle != null) {
                    vehicle.remove();
                    vehicle = null;
                  }
                  if (!e.teleport(f.location))
                    throw new IllegalArgumentException("Recording teleport was cancelled.");
                }
                if (e instanceof Player p) {
                  p.setSneaking(f.sneak);
                  p.setSprinting(f.sprint);
                }
                if (f.swing) e.swingMainHand();
                if (f.offSwing) e.swingOffHand();
                if (f.hurt) e.playHurtAnimation(0);
                e.setPose(f.pose, true);
                e.setVisualFire(net.kyori.adventure.util.TriState.byBoolean(f.fire > 0));
                e.setFallDistance(0);
                e.setVelocity(new org.bukkit.util.Vector());
                if (e.getEquipment() != null) {
                  if (!e.getEquipment().getItemInMainHand().equals(f.main))
                    e.getEquipment().setItemInMainHand(f.main.clone());
                  if (!e.getEquipment().getItemInOffHand().equals(f.off))
                    e.getEquipment().setItemInOffHand(f.off.clone());
                  if (f.armor != null
                      && !Arrays.equals(e.getEquipment().getArmorContents(), f.armor))
                    e.getEquipment()
                        .setArmorContents(
                            Arrays.stream(f.armor)
                                .map(i -> i == null ? null : i.clone())
                                .toArray(ItemStack[]::new));
                }
                completed = !cursor.advance();
                return !completed;
              }

              public void stopped() {
                playback.remove(actorId);
                actors.release(actorId, "recording " + recording);
                if (vehicle != null) vehicle.remove();
                actor
                    .entity()
                    .ifPresent(
                        e -> {
                          e.setGravity(gravity);
                          e.setVisualFire(visualFire);
                          actor.stop();
                          if (!completed || restoreOnComplete) snapshot.restore(e);
                          else {
                            e.setVelocity(new org.bukkit.util.Vector());
                            actor.definition.wander = false;
                            actors.save(actor);
                          }
                        });
              }
            });
    playback.put(actorId, job);
  }

  private void validatePlayback(String recording, String actorId) {
    settings.require("recording");
    List<Frame> frames = recordings.get(recording);
    if (frames == null || frames.isEmpty())
      throw new IllegalArgumentException("Recording does not exist or is empty.");
    if (playback.containsKey(actorId))
      throw new IllegalArgumentException("Actor already has active playback.");
    actors.available(actorId);
    actors.get(actorId).requireEntity();
  }

  public void stopPlayback(String actorId) {
    UUID job = playback.get(actorId);
    if (job == null) throw new IllegalArgumentException("Actor has no active recording playback.");
    ticks.cancel(job);
  }

  public void delete(String id) {
    if (!recordings.containsKey(id)) throw new IllegalArgumentException("Recording not found.");
    recordings.remove(id);
    store.delete("recordings", id);
  }

  @EventHandler
  public void swing(PlayerAnimationEvent e) {
    if (captures.containsKey(e.getPlayer().getUniqueId())) {
      if (e.getAnimationType() == PlayerAnimationType.OFF_ARM_SWING)
        offSwung.add(e.getPlayer().getUniqueId());
      else swung.add(e.getPlayer().getUniqueId());
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void damaged(org.bukkit.event.entity.EntityDamageEvent event) {
    if (captures.containsKey(event.getEntity().getUniqueId()) && event.getFinalDamage() > 0)
      hurt.add(event.getEntity().getUniqueId());
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    if (captures.containsKey(e.getPlayer().getUniqueId())) stop(e.getPlayer());
  }

  @Override
  public void close() {
    for (Capture c : List.copyOf(captures.values())) stop(c.player);
    for (UUID job : List.copyOf(playback.values())) ticks.cancel(job);
  }

  private static final class Capture {
    final String id;
    final Player player;
    final List<Frame> frames = new ArrayList<>();
    UUID job;

    Capture(String id, Player player) {
      this.id = id;
      this.player = player;
    }
  }
}
