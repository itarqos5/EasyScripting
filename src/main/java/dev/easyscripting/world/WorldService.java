package dev.easyscripting.world;

import dev.easyscripting.config.Settings;
import dev.easyscripting.core.Checks;
import dev.easyscripting.core.TickEngine;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;

public final class WorldService implements Listener, AutoCloseable {
  private final Settings settings;
  private final Map<UUID, WorldBorder> previousBorders = new HashMap<>();
  private final Set<UUID> customBorders = new HashSet<>();
  private final TickEngine ticks;
  private UUID autoClear;

  public WorldService(Settings settings, TickEngine ticks) {
    this.settings = settings;
    this.ticks = ticks;
    reload();
  }

  public void reload() {
    if (autoClear != null) {
      ticks.cancel(autoClear);
      autoClear = null;
    }
    int seconds =
        Checks.integer(
            settings.file("config").getString("world.auto-clear-seconds", "0"), 0, 86400);
    if (seconds == 0) return;
    autoClear =
        ticks.add(
            new TickEngine.Job() {
              int remaining = seconds * 20;

              public boolean tick() {
                if (--remaining > 0) return true;
                remaining = seconds * 20;
                if (settings.enabled("world"))
                  for (Player player : Bukkit.getOnlinePlayers()) {
                    if (settings
                        .file("config")
                        .getStringList("world.auto-clear-worlds")
                        .contains(player.getWorld().getName()))
                      cleanup(
                          player,
                          "items",
                          Checks.decimal(
                              settings.file("config").getString("world.auto-clear-radius", "32"),
                              1,
                              128));
                  }
                return true;
              }

              public void stopped() {
                autoClear = null;
              }
            });
  }

  public void border(Player p, double size) {
    Checks.decimal(Double.toString(size), 1, 59999968);
    if (customBorders.add(p.getUniqueId()))
      previousBorders.put(p.getUniqueId(), p.getWorldBorder());
    WorldBorder border = Bukkit.createWorldBorder();
    border.setCenter(p.getLocation());
    border.setSize(size);
    border.setWarningDistance(0);
    border.setDamageAmount(0);
    border.setDamageBuffer(59999968);
    p.setWorldBorder(border);
  }

  public void resetBorder(Player p) {
    if (customBorders.remove(p.getUniqueId()))
      p.setWorldBorder(previousBorders.remove(p.getUniqueId()));
  }

  public void time(World world, long time) {
    world.setTime(Checks.integer(Long.toString(time), 0, 24000));
  }

  public void weather(World world, String value) {
    if (!List.of("clear", "rain", "thunder").contains(value))
      throw new IllegalArgumentException("Weather must be clear, rain or thunder.");
    world.setStorm(!value.equals("clear"));
    world.setThundering(value.equals("thunder"));
  }

  public int cleanup(Player director, String type, double radius) {
    settings.require("world");
    Checks.decimal(
        Double.toString(radius), 1, settings.file("config").getInt("limits.cleanup-radius", 128));
    EntityType exact = null;
    if (!List.of("mobs", "hostile", "passive", "items", "all").contains(type))
      exact = Checks.choice(EntityType.class, type);
    int count = 0;
    for (Entity entity :
        director.getWorld().getNearbyEntities(director.getLocation(), radius, radius, radius)) {
      if (entity instanceof Player
          || entity.getLocation().distanceSquared(director.getLocation()) > radius * radius
          || entity.hasMetadata("NPC")
          || entity.getPersistentDataContainer().has(new NamespacedKey("easyscripting", "actor")))
        continue;
      boolean match =
          switch (type) {
            case "mobs" -> entity instanceof Mob;
            case "hostile" -> entity instanceof Monster;
            case "passive" -> entity instanceof Animals || entity instanceof Villager;
            case "items" -> entity instanceof Item;
            case "all" ->
                entity instanceof Mob || entity instanceof Item || entity instanceof Projectile;
            default -> entity.getType() == exact;
          };
      if (match) {
        entity.remove();
        count++;
      }
    }
    return count;
  }

  public void top(Player player) {
    Location at = player.getLocation();
    at.setY(player.getWorld().getHighestBlockYAt(at) + 1);
    player.teleport(at);
  }

  public void limit(Player p, String kind, int distance) {
    Checks.integer(Integer.toString(distance), 2, 32);
    switch (kind) {
      case "view" -> p.setViewDistance(distance);
      case "send" -> p.setSendViewDistance(distance);
      case "simulation" -> p.setSimulationDistance(distance);
      default ->
          throw new IllegalArgumentException("Limit kind must be view, send, or simulation.");
    }
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    resetBorder(e.getPlayer());
  }

  @Override
  public void close() {
    if (autoClear != null) ticks.cancel(autoClear);
    for (Player p : Bukkit.getOnlinePlayers()) resetBorder(p);
  }
}
