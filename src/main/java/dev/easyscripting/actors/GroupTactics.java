package dev.easyscripting.actors;

import java.util.*;
import java.util.function.ToDoubleBiFunction;
import org.bukkit.util.Vector;

/** Pure target allocation and formation layout, independent of Bukkit's server singleton. */
public final class GroupTactics {
  private GroupTactics() {}

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
