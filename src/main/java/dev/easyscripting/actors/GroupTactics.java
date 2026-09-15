package dev.easyscripting.actors;

import java.util.*;
import java.util.function.ToDoubleBiFunction;
import org.bukkit.util.Vector;

/** Pure target allocation and formation layout, independent of Bukkit's server singleton. */
public final class GroupTactics {
  private GroupTactics() {}

  /**
   * Pick a stable horizontal heading. Movement wins while the leader is walking; while stopped,
   * keep the previous heading so looking around does not make a large formation cross through
   * itself.
   */
  public static Vector movementHeading(Vector previous, Vector velocity, float yaw) {
    Vector moving = velocity == null ? new Vector() : velocity.clone().setY(0);
    if (moving.lengthSquared() < 0.0025) {
      if (previous != null && previous.clone().setY(0).lengthSquared() > 0.000001)
        return previous.clone().setY(0).normalize();
      double angle = Math.toRadians(yaw);
      return new Vector(-Math.sin(angle), 0, Math.cos(angle));
    }
    moving.normalize();
    if (previous == null || previous.clone().setY(0).lengthSquared() < 0.000001) return moving;
    Vector smooth = previous.clone().setY(0).normalize().multiply(0.55).add(moving.multiply(0.45));
    return smooth.lengthSquared() < 0.000001 ? moving : smooth.normalize();
  }

  /** Compact rows trail the leader instead of surrounding or crossing in front of them. */
  public static Vector trailingFormation(
      int index, int count, double spacing, Vector forwardDirection) {
    if (index < 0 || index >= count || count < 1 || !Double.isFinite(spacing) || spacing <= 0)
      throw new IllegalArgumentException("Invalid trailing formation index, count or spacing.");
    int columns = (int) Math.ceil(Math.sqrt(count));
    int row = index / columns;
    int first = row * columns;
    int inRow = Math.min(columns, count - first);
    int column = index - first;
    double lateral = (column - (inRow - 1) / 2.0) * spacing;
    double behind = -(row + 1) * spacing;

    Vector forward =
        forwardDirection == null ? new Vector(0, 0, 1) : forwardDirection.clone().setY(0);
    if (forward.lengthSquared() < 0.000001) forward.setZ(1);
    forward.normalize();
    Vector right = new Vector(forward.getZ(), 0, -forward.getX());
    return right.multiply(lateral).add(forward.multiply(behind));
  }

  /** Limit a distant navigation target to a walkable intermediate waypoint. */
  public static Vector waypoint(Vector from, Vector target, double maximumDistance) {
    if (from == null
        || target == null
        || !Double.isFinite(maximumDistance)
        || maximumDistance <= 0)
      throw new IllegalArgumentException("Invalid waypoint input.");
    Vector delta = target.clone().subtract(from);
    return delta.lengthSquared() <= maximumDistance * maximumDistance
        ? target.clone()
        : from.clone().add(delta.normalize().multiply(maximumDistance));
  }

  /** The real leader is not a member source, making friendly protection intentionally one-way. */
  public static boolean blocksFriendlyDamage(String attackingMemberGroup, String victimGroup) {
    return attackingMemberGroup != null && attackingMemberGroup.equals(victimGroup);
  }

  public static Map<String, UUID> allocate(
      List<String> members,
      List<UUID> targets,
      Map<String, UUID> previous,
      ToDoubleBiFunction<String, UUID> distance) {
    Map<String, UUID> result = new LinkedHashMap<>();
    Map<UUID, Integer> loads = new HashMap<>();
    for (String member : members) {
      UUID best = null;
      int load = Integer.MAX_VALUE;
      double score = Double.POSITIVE_INFINITY;
      for (UUID target : targets) {
        double candidate = distance.applyAsDouble(member, target);
        if (!Double.isFinite(candidate)) continue;
        if (target.equals(previous.get(member))) candidate *= 0.75; // Reduce needless switching.
        int assigned = loads.getOrDefault(target, 0);
        if (assigned < load || (assigned == load && candidate < score)) {
          best = target;
          load = assigned;
          score = candidate;
        }
      }
      if (best != null) {
        result.put(member, best);
        loads.merge(best, 1, Integer::sum);
      }
    }
    return result;
  }

  /** Expanding rings give every member its own slot, without piling 100 actors onto the leader. */
  public static Vector formation(int index, double spacing) {
    int ring = 1;
    while (index >= ring * 8) {
      index -= ring * 8;
      ring++;
    }
    double angle = index * Math.PI * 2 / (ring * 8);
    return new Vector(Math.cos(angle) * ring * spacing, 0, Math.sin(angle) * ring * spacing);
  }
}
