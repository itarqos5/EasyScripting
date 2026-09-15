package dev.easyscripting.players;

import dev.easyscripting.config.*;
import dev.easyscripting.core.TickEngine;
import dev.easyscripting.storage.YamlStore;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class DeathService implements Listener {
  private final Settings settings;
  private final TickEngine ticks;
  private final YamlStore store;
  private YamlConfiguration modes;
  private final Set<UUID> spectator = new HashSet<>();
  private final Map<UUID, Long> lastTrigger = new HashMap<>();
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

  public void mode(Player p, String mode) {
    if (!List.of("normal", "spectator", "kick", "respawn").contains(mode))
      throw new IllegalArgumentException("Death mode must be normal, spectator, kick or respawn.");
    modes.set(p.getUniqueId().toString(), mode);
    store.save("state", "deaths", modes);
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void death(PlayerDeathEvent e) {
    if (!settings.enabled("death")) return;
    Player p = e.getEntity();
    String mode =
        modes.getString(
            p.getUniqueId().toString(), settings.file("death").getString("default-mode", "normal"));
    double radius = settings.file("death").getDouble("message-radius", -1);
    if (radius >= 0) {
      var message = e.deathMessage();
      e.deathMessage(null);
      if (message != null && radius > 0)
        for (Player viewer : p.getWorld().getNearbyPlayers(p.getLocation(), radius))
          viewer.sendMessage(message);
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
      case "kick" ->
          ticks.later(
              1,
              () -> {
                if (p.isOnline())
                  p.kick(
                      Messages.rich(
                          settings.file("death").getString("kick-message", "Take complete.")));
              });
      default -> {}
    }
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

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    spectator.remove(e.getPlayer().getUniqueId());
    lastTrigger.remove(e.getPlayer().getUniqueId());
  }
}
