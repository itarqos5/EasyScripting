package dev.easyscripting.actors;

import dev.easyscripting.config.ActorAiSettings;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;

/** Social destinations are anchored to people, or to home, never an unbounded random walk. */
public final class ActorWandering {
  private ActorWandering() {}

  public static Location destination(
      ActorService.ManagedActor actor,
      ActorService actors,
      ActorAiSettings settings,
      Location home) {
    LivingEntity entity = actor.requireEntity();
    Location here = entity.getLocation();
    var neighbors =
        entity
            .getWorld()
            .getNearbyEntities(here, settings.socialRadius(), 6, settings.socialRadius());
    Location anchor =
        neighbors.stream()
            .filter(
                e ->
                    e instanceof Player p
                        && realVisiblePlayer(p, actors)
                        && p.getLocation().distanceSquared(here)
                            <= settings.socialRadius() * settings.socialRadius()
                        && entity.hasLineOfSight(p))
            .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(here)))
            .map(Entity::getLocation)
            .orElse(null);
    boolean nearPlayer = anchor != null;
    if (anchor == null) {
      anchor =
          neighbors.stream()
              .filter(
                  e ->
                      !e.getUniqueId().equals(entity.getUniqueId())
                          && actors.byEntity(e.getUniqueId()).isPresent()
                          && e.getWorld() == home.getWorld()
                          && e.getLocation().distanceSquared(home)
                              <= settings.homeRadius() * settings.homeRadius())
              .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(here)))
              .map(Entity::getLocation)
              .orElse(home);
    }
    var random = ThreadLocalRandom.current();
    for (int attempt = 0; attempt < 12; attempt++) {
      double angle = random.nextDouble(Math.PI * 2);
      double radius = random.nextDouble(0.5, settings.wanderRadius());
      Location candidate =
          ground(anchor.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), 3);
      if (candidate == null || candidate.distanceSquared(here) < 2.25) continue;
      if (!nearPlayer
          && (candidate.getWorld() != home.getWorld()
              || candidate.distanceSquared(home) > settings.homeRadius() * settings.homeRadius()))
        continue;
      return candidate;
    }
    return null;
  }

  public static boolean realVisiblePlayer(Player p, ActorService actors) {
    return p.isOnline()
        && !p.isDead()
        && !p.hasMetadata("NPC")
        && actors.byEntity(p.getUniqueId()).isEmpty()
        && p.getGameMode() != GameMode.SPECTATOR
        && !p.isInvisible()
        && !p.hasMetadata("vanished");
  }

  /** Sample a local standing surface; never generate chunks or pick roofs far above the actor. */
  public static Location ground(Location near, int verticalRange) {
    World world = near.getWorld();
    int x = near.getBlockX(), z = near.getBlockZ();
    if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
    for (int offset = 0; offset <= verticalRange; offset++) {
      for (int sign : offset == 0 ? new int[] {1} : new int[] {1, -1}) {
        int y = near.getBlockY() - 1 + offset * sign;
        if (y < world.getMinHeight() || y + 3 >= world.getMaxHeight()) continue;
        Block floor = world.getBlockAt(x, y, z);
        if (!floor.getType().isSolid() || hazardous(floor.getType())) continue;
        double top = floor.getBoundingBox().getMaxY();
        Block feet = world.getBlockAt(x, (int) Math.floor(top + 0.001), z);
        Block head = world.getBlockAt(x, (int) Math.floor(top + 1.8), z);
        if ((!feet.isPassable() && !feet.equals(floor))
            || !head.isPassable()
            || feet.isLiquid()
            || head.isLiquid()
            || hazardous(feet.getType())
            || hazardous(head.getType())) continue;
        return new Location(world, x + 0.5, top, z + 0.5);
      }
    }
    return null;
  }

  private static boolean hazardous(Material material) {
    return switch (material) {
      case LAVA,
          WATER,
          FIRE,
          SOUL_FIRE,
          MAGMA_BLOCK,
          CACTUS,
          CAMPFIRE,
          SOUL_CAMPFIRE,
          POWDER_SNOW,
          SWEET_BERRY_BUSH,
          WITHER_ROSE ->
          true;
      default -> false;
    };
  }
}
