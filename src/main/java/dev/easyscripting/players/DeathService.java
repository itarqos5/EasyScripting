package dev.easyscripting.players;

import dev.easyscripting.config.*;
import dev.easyscripting.core.Durations;
import dev.easyscripting.core.TickEngine;
import dev.easyscripting.storage.YamlStore;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class DeathService implements Listener {
  private final Settings settings;
  private final TickEngine ticks;
  private final YamlStore store;
  private YamlConfiguration modes;
  private final Set<UUID> spectator = new HashSet<>();
  private final Map<UUID, Long> lastTrigger = new HashMap<>();
  private final DeathLockout lockout = new DeathLockout();
  private final Set<UUID> pendingKick = new HashSet<>();
  private java.util.function.BiConsumer<Player, String> trigger = (player, scene) -> {};
  private java.util.function.Consumer<String> validateScene = id -> {};

  public void scenes(
      java.util.function.Consumer<String> validate,
      java.util.function.BiConsumer<Player, String> execute) {
    validateScene = validate;
    trigger = execute;
  }

  public void scene(Player player, String id) {
    if (!id.equals("off")) validateScene.accept(id);
    modes.set("scenes." + player.getUniqueId(), id.equals("off") ? null : id);
    store.save("state", "deaths", modes);
  }

  public DeathService(Settings settings, TickEngine ticks, YamlStore store) {
    this.settings = settings;
    this.ticks = ticks;
    this.store = store;
  }

  public void load() {
    modes = store.read("state", "deaths");
  }

  public boolean kicking() {
    return settings.file("death").getBoolean("kick-on-death", true);
  }

  /** Turning the kick off also lets go of everyone still waiting out a lockout from it. */
  public void kicking(boolean enabled) {
    settings.require("death");
    settings.file("death").set("kick-on-death", enabled);
    settings.persist("death");
    if (!enabled) release();
  }

  public long cooldown() {
    return lockSeconds();
  }

  /** Shortening the wait, including to nothing, applies to the players already serving one. */
  public void cooldown(long seconds) {
    settings.require("death");
    if (seconds < 0 || seconds > Durations.MAXIMUM)
      throw new IllegalArgumentException("A death-kick cooldown runs from off up to 24h.");
    settings.file("death").set("rejoin-lockout-seconds", (int) seconds);
    settings.persist("death");
    release();
  }

  /** How many players cannot join right now because they died. */
  public int waiting() {
    return lockout.size();
  }

  public void release() {
    lockout.clearAll();
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void death(PlayerDeathEvent e) {
    if (!settings.enabled("death")) return;
    Player p = e.getEntity();
    String mode = settings.file("death").getString("default-mode", "normal");
    double radius = settings.file("death").getDouble("message-radius", -1);
    if (radius >= 0) {
      var message = e.deathMessage();
      e.deathMessage(null);
      if (message != null && radius > 0)
        for (Player viewer : p.getWorld().getNearbyPlayers(p.getLocation(), radius))
          viewer.sendMessage(message);
    }
    if (kicks(p)) {
      kickAfterDeath(p);
      return;
    }
    switch (mode) {
      case "spectator" -> {
        if (!p.hasPermission(
            settings
                .file("death")
                .getString("spectator-permission", "easyscripting.death.spectator"))) return;
        spectator.add(p.getUniqueId());
        ticks.later(
            1,
            () -> {
              if (p.isOnline() && p.isDead()) p.spigot().respawn();
            });
      }
      case "respawn" ->
          ticks.later(
              1,
              () -> {
                if (p.isOnline() && p.isDead()) p.spigot().respawn();
              });
      default -> {}
    }
  }

  /**
   * Whether this death ends with a kick. One switch decides it for everybody, so /es deathkick off
   * really means nobody is kicked. The bypass permission is granted to nobody by default, which is
   * what makes 'everybody' include operators.
   */
  private boolean kicks(Player p) {
    var config = settings.file("death");
    if (!kicking()) return false;
    return !p.hasPermission(
        config.getString("kick-bypass-permission", "easyscripting.death.kick.bypass"));
  }

  /**
   * Let the death itself finish first. The kick waits a moment so the player dies in front of
   * everyone and the server's death message is already out, and only then is the connection
   * closed and the rejoin lockout started.
   */
  private void kickAfterDeath(Player p) {
    var config = settings.file("death");
    UUID id = p.getUniqueId();
    pendingKick.add(id);
    ticks.later(
        Math.clamp(config.getInt("kick-delay-ticks", 20), 1, 200),
        () -> {
          if (!pendingKick.remove(id)) return;
          long seconds = lockSeconds();
          lockout.start(id, System.currentTimeMillis(), seconds * 1000L);
          Player live = Bukkit.getPlayer(id);
          if (live == null) return;
          live.kick(
              Messages.rich(
                  config.getString("kick-message", "<gray>Your part in this take is complete."),
                  Map.of("seconds", String.valueOf(seconds))));
        });
  }

  private long lockSeconds() {
    return Math.clamp(settings.file("death").getLong("rejoin-lockout-seconds", 60), 0, 86400);
  }

  /** A player kept out by a death of their own is told how much of the wait is left. */
  @EventHandler(priority = EventPriority.HIGHEST)
  public void login(PlayerLoginEvent e) {
    if (!settings.enabled("death") || e.getResult() != PlayerLoginEvent.Result.ALLOWED) return;
    long seconds = lockout.seconds(e.getPlayer().getUniqueId(), System.currentTimeMillis());
    if (seconds <= 0) return;
    e.disallow(
        PlayerLoginEvent.Result.KICK_OTHER,
        Messages.rich(
            settings
                .file("death")
                .getString(
                    "lockout-message",
                    "<red>You died. You can join again in <white><seconds></white> seconds."),
            Map.of("seconds", String.valueOf(seconds))));
  }

  @EventHandler
  public void respawn(PlayerRespawnEvent e) {
    Player player = e.getPlayer();
    if (spectator.remove(player.getUniqueId()))
      ticks.later(
          1,
          () -> {
            if (player.isOnline()) player.setGameMode(GameMode.SPECTATOR);
          });
    String scene = modes.getString("scenes." + player.getUniqueId());
    if (scene == null || !settings.enabled("death")) return;
    long now = System.currentTimeMillis();
    if (now - lastTrigger.getOrDefault(player.getUniqueId(), 0L) < 10000) return;
    lastTrigger.put(player.getUniqueId(), now);
    ticks.later(
        2,
        () -> {
          if (!player.isOnline() || player.isDead()) return;
          try {
            trigger.accept(player, scene);
          } catch (IllegalArgumentException ex) {
            player.sendMessage(
                net.kyori.adventure.text.Component.text(
                    "Death scene '" + scene + "': " + ex.getMessage()));
          }
        });
  }

  /** Disconnecting in the moment between the death and its kick does not skip the lockout. */
  @EventHandler
  public void quit(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    spectator.remove(id);
    lastTrigger.remove(id);
    if (pendingKick.remove(id))
      lockout.start(id, System.currentTimeMillis(), lockSeconds() * 1000L);
  }
}
