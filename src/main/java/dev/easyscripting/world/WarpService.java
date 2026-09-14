package dev.easyscripting.world;

import dev.easyscripting.core.*;
import dev.easyscripting.storage.YamlStore;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class WarpService implements org.bukkit.event.Listener {
  private dev.easyscripting.config.Settings settings;

  public void settings(dev.easyscripting.config.Settings settings) {
    this.settings = settings;
  }

  @org.bukkit.event.EventHandler
  public void respawn(org.bukkit.event.player.PlayerRespawnEvent event) {
    Warp spawn = warps.get("spawn");
    if (settings != null
        && settings.enabled("warps")
        && spawn != null
        && settings.file("config").getBoolean("spawn.on-respawn")
        && !event.getPlayer().hasPermission("easyscripting.spawn.bypass"))
      event.setRespawnLocation(spawn.location().clone());
  }

  @org.bukkit.event.EventHandler
  public void join(org.bukkit.event.player.PlayerJoinEvent event) {
    Warp spawn = warps.get("spawn");
    if (settings != null
        && settings.enabled("warps")
        && spawn != null
        && settings.file("config").getBoolean("spawn.on-first-join")
        && !event.getPlayer().hasPlayedBefore()
        && !event.getPlayer().hasPermission("easyscripting.spawn.bypass"))
      event.getPlayer().teleportAsync(spawn.location().clone());
  }

  public record Warp(String id, Location location, String permission) {
    public boolean accessible(CommandSender sender) {
      return permission.equals("everyone")
          || sender.hasPermission(
              permission.equals("op") ? "easyscripting.warp.admin" : permission);
    }
  }

  private final YamlStore store;
  private final Map<String, Warp> warps = new TreeMap<>();

  public WarpService(YamlStore store) {
    this.store = store;
  }

  public void load() {
    store
        .load("warps")
        .forEach(
            (id, y) -> {
              try {
                warps.put(
                    id,
                    new Warp(
                        id,
                        Positions.read(y.getConfigurationSection("location")),
                        y.getString("permission", "easyscripting.warp")));
              } catch (IllegalArgumentException ex) {
                Bukkit.getLogger().warning("warps/" + id + ".yml: " + ex.getMessage());
              }
            });
  }

  public List<String> ids(CommandSender sender) {
    return warps.values().stream().filter(w -> w.accessible(sender)).map(Warp::id).toList();
  }

  public Warp get(String id) {
    Warp w = warps.get(id);
    if (w == null) throw new IllegalArgumentException("Warp '" + id + "' does not exist.");
    return w;
  }

  public void save(String id, Location location) {
    Warp old = warps.get(id);
    put(
        new Warp(
            Checks.id(id), location.clone(), old == null ? "easyscripting.warp" : old.permission));
  }

  public void permission(String id, String permission) {
    if (!permission.matches("[a-zA-Z0-9_.-]{1,100}"))
      throw new IllegalArgumentException("Invalid permission node.");
    Warp w = get(id);
    put(new Warp(id, w.location, permission));
  }

  private void put(Warp w) {
    warps.put(w.id, w);
    YamlConfiguration y = new YamlConfiguration();
    y.set("schema", 1);
    y.createSection("location", Positions.encode(w.location));
    y.set("permission", w.permission);
    store.save("warps", w.id, y);
  }

  public void teleport(String id, CommandSender director, Player target) {
    Warp w = get(id);
    if (!w.accessible(director))
      throw new IllegalArgumentException("You do not have permission to use this warp.");
    target
        .teleportAsync(w.location.clone())
        .thenAccept(
            success -> {
              if (!success)
                Bukkit.getLogger()
                    .warning("Warp " + id + " teleport cancelled for " + target.getUniqueId());
            });
  }

  public void delete(String id) {
    get(id);
    warps.remove(id);
    store.delete("warps", id);
  }
}
