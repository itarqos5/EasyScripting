package dev.easyscripting.actors;

import java.util.*;
import java.util.function.ToDoubleBiFunction;
import org.bukkit.util.Vector;

/** Pure target allocation and formation layout, independent of Bukkit's server singleton. */
public final class GroupTactics {
  private GroupTactics() {}

  /** Below this travel per tick the leader is treated as standing still rather than walking. */
  private static final double MOVING = 0.03;

  /** Heading smoothing per tick. Most of the old heading is kept so a whole block turns as one. */
  private static final double INERTIA = 0.85;

  /** Preference for the place a member already holds, so a settled formation stops renumbering. */
  private static final double STICKY = 0.75;

  /**
   * Pick a stable horizontal heading from the distance the leader actually covered this tick.
   * Player velocity is not populated by walking input, so the caller samples position deltas.
   * While the leader is stopped the previous heading is kept, so looking around does not make a
   * large formation rotate through itself.
   */
  public static Vector movementHeading(Vector previous, Vector travel, float yaw) {
    Vector moving = travel == null ? new Vector() : travel.clone().setY(0);
    if (moving.lengthSquared() < MOVING * MOVING) {
      if (previous != null && previous.clone().setY(0).lengthSquared() > 0.000001)
        return previous.clone().setY(0).normalize();
      double angle = Math.toRadians(yaw);
      return new Vector(-Math.sin(angle), 0, Math.cos(angle));
    }
    moving.normalize();
    if (previous == null || previous.clone().setY(0).lengthSquared() < 0.000001) return moving;
    Vector smooth =
        previous.clone().setY(0).normalize().multiply(INERTIA).add(moving.multiply(1 - INERTIA));
    return smooth.lengthSquared() < 0.000001 ? moving : smooth.normalize();
  }

  /**
   * Give every member the formation slot nearest to where it already stands. A fixed numbered
   * place makes a turning or reforming group send members around each other — and around the
   * leader — to reach a square someone else is already standing on, which is what reads as
   * members running off to the wrong side. Assigning by proximity keeps each member on its own
   * side of the block, and preferring the slot it already held keeps a settled group still.
   *
   * <p>Slots are greedily paired cheapest-first; any member whose distances are all unusable
   * still receives a leftover slot, so nobody is left without a place to stand.
   */
  public static Map<String, Integer> nearestSlots(
      List<String> members,
      int slots,
      Map<String, Integer> previous,
      ToDoubleBiFunction<String, Integer> distance) {
    if (members == null || slots < members.size())
      throw new IllegalArgumentException("A formation needs a slot for every member.");
    record Pairing(String member, int slot, double cost) {}
    List<Pairing> pairs = new ArrayList<>();
    for (String member : members)
      for (int slot = 0; slot < slots; slot++) {
        double cost = distance.applyAsDouble(member, slot);
        if (!Double.isFinite(cost) || cost < 0) continue;
        Integer held = previous.get(member);
        pairs.add(new Pairing(member, slot, held != null && held == slot ? cost * STICKY : cost));
      }
    pairs.sort(Comparator.comparingDouble(Pairing::cost));
    Map<String, Integer> result = new LinkedHashMap<>();
    boolean[] taken = new boolean[slots];
    for (Pairing pair : pairs) {
      if (result.containsKey(pair.member()) || taken[pair.slot()]) continue;
      result.put(pair.member(), pair.slot());
      taken[pair.slot()] = true;
    }
    int spare = 0;
    for (String member : members)
      if (!result.containsKey(member)) {
        while (spare < slots && taken[spare]) spare++;
        if (spare >= slots) break;
        result.put(member, spare);
        taken[spare] = true;
      }
    return result;
  }

  /** Resolve the requested column count; 0 asks for the squarest block that fits the group. */
  public static int columns(int requested, int count) {
    if (count < 1) throw new IllegalArgumentException("A formation needs at least one member.");
    int columns = requested > 0 ? requested : (int) Math.ceil(Math.sqrt(count));
    return Math.max(1, Math.min(columns, count));
  }

  /** Compact rows trail the leader instead of surrounding or crossing in front of them. */
  public static Vector trailingFormation(
      int index, int count, int columns, double spacing, Vector forwardDirection) {
    return grid(index, count, columns, spacing, forwardDirection, true);
  }

  /** The same rows and columns, centred on a stationary anchor such as a Move destination. */
  public static Vector blockFormation(
      int index, int count, int columns, double spacing, Vector forwardDirection) {
    return grid(index, count, columns, spacing, forwardDirection, false);
  }

  /**
   * Lay members out on one shared lateral grid so columns stay aligned from row to row. A partial
   * last row is centred by whole slots, which keeps it aligned instead of half a space off.
   */
  private static Vector grid(
      int index,
      int count,
      int requestedColumns,
      double spacing,
      Vector forwardDirection,
      boolean trailing) {
    if (index < 0 || index >= count || count < 1 || !Double.isFinite(spacing) || spacing <= 0)
      throw new IllegalArgumentException("Invalid formation index, count or spacing.");
    int columns = columns(requestedColumns, count);
    int rows = (int) Math.ceil(count / (double) columns);
    int row = index / columns;
    int first = row * columns;
    int inRow = Math.min(columns, count - first);
    int column = (columns - inRow) / 2 + (index - first);
    double lateral = (column - (columns - 1) / 2.0) * spacing;
    double depth = trailing ? -(row + 1) * spacing : ((rows - 1) / 2.0 - row) * spacing;

    Vector forward =
        forwardDirection == null ? new Vector(0, 0, 1) : forwardDirection.clone().setY(0);
    if (forward.lengthSquared() < 0.000001) forward.setZ(1);
    forward.normalize();
    Vector right = new Vector(forward.getZ(), 0, -forward.getX());
    return right.multiply(lateral).add(forward.multiply(depth));
  }

  /**
   * Spread the attackers sharing one target around it instead of stacking every one of them on the
   * target's own block. Slot 0 of a lone attacker keeps the direct approach; further slots fan out
   * to either side of their own bearing, so nobody has to run around the fight to reach a place.
   */
  public static Vector engagementOffset(int slot, int attackers, double radius, Vector fromTarget) {
    if (slot < 0 || attackers < 1 || slot >= attackers || !Double.isFinite(radius) || radius <= 0)
      throw new IllegalArgumentException("Invalid engagement slot, attacker count or radius.");
    double step = Math.min(Math.PI * 2 / attackers, Math.PI / 2);
    return circleOffset(fromTarget, radius, (slot - (attackers - 1) / 2.0) * step);
  }

  /**
   * Rotate the attacker's own bearing around its target by a fixed angle. A sidestep that keeps
   * the same radius stays inside reach, so circling never costs the NPC its attack.
   */
  public static Vector circleOffset(Vector fromTarget, double radius, double radians) {
    if (!Double.isFinite(radius) || radius <= 0 || !Double.isFinite(radians))
      throw new IllegalArgumentException("Invalid circling radius or angle.");
    Vector bearing = fromTarget == null ? new Vector(0, 0, 1) : fromTarget.clone().setY(0);
    if (bearing.lengthSquared() < 0.000001) bearing.setZ(1);
    bearing.normalize();
    double sin = Math.sin(radians), cos = Math.cos(radians);
    return new Vector(
            bearing.getX() * cos - bearing.getZ() * sin,
            0,
            bearing.getX() * sin + bearing.getZ() * cos)
        .multiply(radius);
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
}
