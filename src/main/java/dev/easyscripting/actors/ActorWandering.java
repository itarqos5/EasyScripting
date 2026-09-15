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
        Location standing = standingOn(floor, near.getYaw(), near.getPitch());
        if (standing != null) return standing;
      }
    }
    return null;
  }

  /** Resolve this X/Z column to its highest safe standing surface without copying the caller's Y. */
  public static Location highestGround(Location column) {
    World world = column.getWorld();
    int x = column.getBlockX(), z = column.getBlockZ();
    if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
    Block floor = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
    return standingOn(floor, column.getYaw(), column.getPitch());
  }

  /** Reserve three blocks of clear space so both PLAYER actors and taller mob actors spawn safely. */
  private static Location standingOn(Block floor, float yaw, float pitch) {
    World world = floor.getWorld();
    if (!floor.getType().isSolid() || hazardous(floor.getType())) return null;
    double top = floor.getBoundingBox().getMaxY();
    if (!Double.isFinite(top)
        || top < world.getMinHeight()
        || top + 3 >= world.getMaxHeight()) return null;
    int first = (int) Math.floor(top + 0.0001);
    int last = (int) Math.floor(top + 2.9999);
    for (int y = first; y <= last; y++) {
      Block space = world.getBlockAt(floor.getX(), y, floor.getZ());
      // A slab or similar support can share the feet block; its collision ends at top.
      if (space.equals(floor)) continue;
      if (!space.isPassable() || space.isLiquid() || hazardous(space.getType())) return null;
    }
    return new Location(world, floor.getX() + 0.5, top, floor.getZ() + 0.5, yaw, pitch);
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
