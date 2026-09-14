package dev.easyscripting.core;

import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;

public final class Positions {
  private Positions() {}

  public static void face(org.bukkit.entity.Entity entity, Location target) {
    Location direction =
        entity
            .getLocation()
            .setDirection(target.toVector().subtract(entity.getLocation().toVector()));
    entity.setRotation(direction.getYaw(), direction.getPitch());
  }

  public static Map<String, Object> encode(Location l) {
    return Map.of(
        "world",
        l.getWorld().getName(),
        "x",
        l.getX(),
        "y",
        l.getY(),
        "z",
        l.getZ(),
        "yaw",
        l.getYaw(),
        "pitch",
        l.getPitch());
  }

  public static Location read(ConfigurationSection s) {
    if (s == null) throw new IllegalArgumentException("Missing location section.");
    return parse(
        s.getString("world", ""),
        String.valueOf(s.get("x")),
        String.valueOf(s.get("y")),
        String.valueOf(s.get("z")),
        String.valueOf(s.getDouble("yaw")),
        String.valueOf(s.getDouble("pitch")));
  }

  public static Location parse(
      String world, String x, String y, String z, String yaw, String pitch) {
    World w = Bukkit.getWorld(world);
    if (w == null) throw new IllegalArgumentException("World '" + world + "' is not loaded.");
    return new Location(
        w,
        Checks.decimal(x, -29999984, 29999984),
        Checks.decimal(y, w.getMinHeight(), w.getMaxHeight()),
        Checks.decimal(z, -29999984, 29999984),
        (float) Checks.decimal(yaw, -360, 360),
        (float) Checks.decimal(pitch, -90, 90));
  }
}
