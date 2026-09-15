package dev.easyscripting.config;

import org.bukkit.configuration.file.YamlConfiguration;

/** Validated, immutable AI tuning. No live server access. */
public record ActorAiSettings(
    double lookRadius,
    double walkSpeed,
    double socialRadius,
    double wanderRadius,
    double homeRadius,
    int wanderInterval,
    boolean groupsEnabled,
    int maxGroups,
    int maxTargets,
    int pathsPerTick,
    int repathTicks,
    int followRepathTicks,
    double followGoalChange,
    double followArrivalDistance,
    double followSpacing,
    double followSpeed,
    double followCatchUpDistance,
    double followCatchUpSpeed,
    double followWaypointDistance,
    double chaseSpeed,
    double engagementRadius,
    double meleeReach,
    int attackCooldown,
    int knockbackPause) {
  public static ActorAiSettings read(YamlConfiguration y) {
    integer(y, "schema", 1, 1);
    if (!(y.get("groups.enabled") instanceof Boolean))
      throw new IllegalArgumentException("actor-ai.yml: groups.enabled must be true or false.");
    double followSpeed = number(y, "groups.follow-speed", 0.1, 3);
    double arrival = number(y, "groups.follow-arrival-distance", 0.5, 3);
    double catchUp = number(y, "groups.follow-catch-up-distance", 3, 48);
    double catchUpSpeed = number(y, "groups.follow-catch-up-speed", 0.1, 3);
    double waypoint = number(y, "groups.follow-waypoint-distance", 8, 80);
    if (arrival >= catchUp)
      throw new IllegalArgumentException(
          "actor-ai.yml: follow-arrival-distance must be below follow-catch-up-distance.");
    if (catchUp >= waypoint)
      throw new IllegalArgumentException(
          "actor-ai.yml: follow-catch-up-distance must be below follow-waypoint-distance.");
    if (catchUpSpeed < followSpeed)
      throw new IllegalArgumentException(
          "actor-ai.yml: follow-catch-up-speed must be at least follow-speed.");
    return new ActorAiSettings(
        number(y, "movement.look-radius", 1, 32),
        number(y, "movement.walk-speed", 0.1, 3),
        number(y, "movement.social-radius", 4, 64),
        number(y, "movement.wander-radius", 1, 8),
        number(y, "movement.home-radius", 2, 32),
        integer(y, "movement.wander-interval-ticks", 10, 400),
        y.getBoolean("groups.enabled"),
        integer(y, "groups.max-groups", 1, 1000),
        integer(y, "groups.max-targets", 1, 256),
        integer(y, "groups.paths-per-tick", 1, 32),
        integer(y, "groups.repath-ticks", 10, 100),
        integer(y, "groups.follow-repath-ticks", 1, 20),
        number(y, "groups.follow-goal-change", 0.1, 4),
        arrival,
        number(y, "groups.follow-spacing", 1.5, 5),
        followSpeed,
        catchUp,
        catchUpSpeed,
        waypoint,
        number(y, "groups.chase-speed", 0.1, 3),
        number(y, "groups.engagement-radius", 8, 96),
        number(y, "groups.melee-reach", 1, 3),
        integer(y, "groups.attack-cooldown-ticks", 10, 100),
        integer(y, "groups.knockback-pause-ticks", 1, 60));
  }

  static double number(YamlConfiguration y, String key, double min, double max) {
    if (!(y.get(key) instanceof Number n)
        || !Double.isFinite(n.doubleValue())
        || n.doubleValue() < min
        || n.doubleValue() > max)
      throw new IllegalArgumentException(
          "actor-ai.yml: " + key + " must be " + min + ".." + max + ".");
    return y.getDouble(key);
  }

  static int integer(YamlConfiguration y, String key, int min, int max) {
    double value = number(y, key, min, max);
    if (value != Math.rint(value))
      throw new IllegalArgumentException("actor-ai.yml: " + key + " must be a whole number.");
    return (int) value;
  }
}
