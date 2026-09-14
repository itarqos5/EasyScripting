package dev.easyscripting.recording;

import dev.easyscripting.actors.ActorService;
import dev.easyscripting.config.*;
import dev.easyscripting.players.*;
import dev.easyscripting.players.EntitySnapshot;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;

/** Owns temporary performer state; recording frames and playback remain in RecordingService. */
public final class ActingService implements Listener, AutoCloseable {
  private final Settings settings;
  private final Messages messages;
  private final ActorService actors;
  private final PlayerService players;
  private final RecordingService recordings;
  private final Map<UUID, Session> sessions = new HashMap<>();

  public ActingService(
      Settings settings,
      Messages messages,
      ActorService actors,
      PlayerService players,
      RecordingService recordings) {
    this.settings = settings;
    this.messages = messages;
    this.actors = actors;
    this.players = players;
    this.recordings = recordings;
    recordings.onCaptureStopped(this::finished);
    actors.onRemoved(
        id -> {
          for (Session session : List.copyOf(sessions.values()))
            if (session.actor.id().equals(id)) {
              session.restoreActor = false;
              cancel(session.player);
            }
        });
  }

  public Optional<String> actor(Player player) {
    Session session = sessions.get(player.getUniqueId());
    return session == null ? Optional.empty() : Optional.of(session.actor.id());
  }

  public void start(Player player, String actorId, String recording) {
    settings.require("actors");
    recordings.validateStart(player, recording);
    players.available(player.getUniqueId());
    actors.available(actorId);
    if (player.isDead() || player.isInsideVehicle())
      throw new IllegalArgumentException("Be alive and leave your vehicle before acting.");
    if (players.hasTake(player.getUniqueId()) || players.hasPending(player.getUniqueId()))
      throw new IllegalArgumentException(
          "Reset/discard your existing take or finish pending restoration before acting.");
    ActorService.ManagedActor actor = actors.get(actorId);
    LivingEntity entity = actor.requireEntity();
    actors.save(actor);
    if (entity instanceof Player
        && !actor.definition.skin.isBlank()
        && actor.definition.skinTexture.isBlank())
      throw new IllegalArgumentException(
          "The NPC skin is still loading. Try acting again in a few seconds.");
    EntitySnapshot original = players.capture(player);
    original.captureProfile(player);
    Session session =
        new Session(player, actor, recording, original, EntitySnapshot.capture(entity));
    players.reserve(player.getUniqueId(), session.owner());
    actors.reserve(actorId, session.owner());
    sessions.put(player.getUniqueId(), session);
    try {
      // Recovery checkpoint before costume/profile changes; normal completion removes it.
      players.defer(player.getUniqueId(), original);
      player.closeInventory();
      var profile =
          Bukkit.createProfileExact(
              player.getUniqueId(),
              actor.definition.name.matches("[A-Za-z0-9_]{1,16}")
                  ? actor.definition.name
                  : player.getName());
      profile.clearProperties();
      profile.setProperties(
          (entity instanceof Player npc ? npc.getPlayerProfile() : player.getPlayerProfile())
              .getProperties());
      ItemStack[] costume = actor.definition.equipment;
      actors.suspend(actorId);
      player.setPlayerProfile(profile);
      player.displayName(Component.text(actor.definition.name));
      player.playerListName(Component.text(actor.definition.name));
      player.setGameMode(GameMode.SURVIVAL);
      player.setFlying(false);
      player.getInventory().clear();
      player.getInventory().setHeldItemSlot(0);
      player.getInventory().setItemInMainHand(copy(costume[0]));
      player.getInventory().setItemInOffHand(copy(costume[1]));
      player.getInventory().setHelmet(copy(costume[2]));
      player.getInventory().setChestplate(copy(costume[3]));
      player.getInventory().setLeggings(copy(costume[4]));
      player.getInventory().setBoots(copy(costume[5]));
      if (players.flag(player.getUniqueId(), "freeze")) players.flag(player, "freeze", false);
      if (!player.teleport(actor.definition.location))
        throw new IllegalArgumentException("Teleport to the actor was cancelled.");
      recordings.start(player, recording);
      messages.ok(
          player,
          "Acting as '"
              + actorId
              + "'. Move and perform, then /actor finish to save or /actor cancel to discard.");
    } catch (RuntimeException ex) {
      session.save = false;
      if (recordings.capturing(player)) recordings.cancel(player);
      else finished(player);
      throw ex;
    }
  }

  private static ItemStack copy(ItemStack item) {
    return item == null ? null : item.clone();
  }

  public void finish(Player player) {
    require(player);
    recordings.stop(player);
  }

  public void cancel(Player player) {
    require(player).save = false;
    if (recordings.capturing(player)) recordings.cancel(player);
    else finished(player);
  }

  private Session require(Player player) {
    Session session = sessions.get(player.getUniqueId());
    if (session == null) throw new IllegalArgumentException("You are not acting as an NPC.");
    return session;
  }

  private void finished(Player player) {
    Session session = sessions.remove(player.getUniqueId());
    if (session == null) return;
    session.save = session.save && recordings.ids().contains(session.recording);
    actors.release(session.actor.id(), session.owner());
    players.release(player.getUniqueId(), session.owner());
    try {
      if (session.deferred || !player.isOnline() || player.isDead())
        players.defer(player.getUniqueId(), session.original);
      else {
        try {
          player.leaveVehicle();
          players.restore(player, session.original);
          players.clearDeferred(player.getUniqueId());
        } catch (RuntimeException error) {
          session.original.restoreProfile(player);
          players.defer(player.getUniqueId(), session.original);
          messages.error(
              player, "Your original state is queued for restoration: " + error.getMessage());
        }
      }
    } finally {
      if (actors.ids().contains(session.actor.id())) {
        if (session.restoreActor) {
          actors.resume(session.actor.id());
          session.actor.entity().ifPresent(session.actorState::restore);
        }
        if (session.save && recordings.ids().contains(session.recording)) {
          session.actor.definition.recording = session.recording;
          actors.save(session.actor);
        }
      }
    }
    if (player.isOnline() && !session.deferred)
      messages.ok(
          player,
          session.save
              ? "Performance saved. Use /actor play " + session.actor.id() + " to play it."
              : "Acting cancelled; original state restored.");
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void quit(PlayerQuitEvent event) {
    Session session = sessions.get(event.getPlayer().getUniqueId());
    if (session != null) {
      session.deferred = true;
      finish(session.player);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void death(PlayerDeathEvent event) {
    Session session = sessions.get(event.getEntity().getUniqueId());
    if (session != null) {
      event.getDrops().clear();
      event.setDroppedExp(0);
      session.deferred = true;
      finish(session.player);
    }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void damage(EntityDamageEvent event) {
    Session session = sessions.get(event.getEntity().getUniqueId());
    if (session == null) return;
    if (!session.actor.definition.hittable
        || (session.actor.definition.immortal
            && event.getFinalDamage() >= session.player.getHealth())) {
      event.setCancelled(true);
      if (session.actor.definition.hittable) session.player.playHurtAnimation(0);
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void unload(WorldUnloadEvent event) {
    for (Session session : List.copyOf(sessions.values()))
      if (session.actor.definition.location.getWorld().equals(event.getWorld())
          || session.player.getWorld().equals(event.getWorld())) cancel(session.player);
  }

  @Override
  public void close() {
    for (Session session : List.copyOf(sessions.values())) finish(session.player);
  }

  private static final class Session {
    final Player player;
    final ActorService.ManagedActor actor;
    final String recording;
    final EntitySnapshot original, actorState;
    boolean save = true, restoreActor = true, deferred;

    Session(
        Player player,
        ActorService.ManagedActor actor,
        String recording,
        EntitySnapshot original,
        EntitySnapshot actorState) {
      this.player = player;
      this.actor = actor;
      this.recording = recording;
      this.original = original;
      this.actorState = actorState;
    }

    String owner() {
      return "acting " + recording;
    }
  }
}
