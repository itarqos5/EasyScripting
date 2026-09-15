package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.actors.GroupTactics;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GroupTacticsTest {
  @Test
  void hundredMembersSplitAcrossSeveralEnemies() {
    var members = IntStream.range(0, 100).mapToObj(i -> "npc_" + i).toList();
    var targets = IntStream.range(0, 7).mapToObj(i -> new UUID(0, i + 1)).toList();
    var result = GroupTactics.allocate(members, targets, Map.of(), (actor, target) -> 16);
    assertEquals(100, result.size());
    var counts =
        targets.stream()
            .map(t -> Collections.frequency(new ArrayList<>(result.values()), t))
            .toList();
    assertEquals(1, Collections.max(counts) - Collections.min(counts));
  }

  @Test
  void deadTargetsAreReassignedAndUnavailableTargetsIgnored() {
    UUID dead = new UUID(0, 1), alive = new UUID(0, 2), unreachable = new UUID(0, 3);
    var result =
        GroupTactics.allocate(
            List.of("one", "two"),
            List.of(unreachable, alive),
            Map.of("one", dead),
            (actor, target) -> target.equals(unreachable) ? Double.POSITIVE_INFINITY : 9);
    assertEquals(Map.of("one", alive, "two", alive), result);
    assertTrue(GroupTactics.allocate(List.of("one"), List.of(), result, (a, t) -> 1).isEmpty());
  }

  @Test
  void proximityAndExistingAssignmentsAvoidUnnecessarySwitches() {
    UUID left = new UUID(0, 1), right = new UUID(0, 2);
    var first =
        GroupTactics.allocate(
            List.of("one", "two"),
            List.of(left, right),
            Map.of(),
            (a, t) -> t.equals(right) ? 4 : 9);
    assertEquals(right, first.get("one"));
    assertEquals(left, first.get("two"));
    var second =
        GroupTactics.allocate(List.of("one", "two"), List.of(left, right), first, (a, t) -> 5);
    assertEquals(first, second);
  }

  @Test
  void targetsInDifferentWorldsDoNotReceiveAssignments() {
    assertTrue(
        GroupTactics.allocate(
                List.of("one"),
                List.of(UUID.randomUUID()),
                Map.of(),
                (a, t) -> Double.POSITIVE_INFINITY)
            .isEmpty());
  }

  @Test
  void hundredFormationSlotsAreDistinctSpacedAndLocal() {
    var slots = IntStream.range(0, 100).mapToObj(i -> GroupTactics.formation(i, 2.5)).toList();
    assertEquals(100, new HashSet<>(slots).size());
    for (int i = 0; i < slots.size(); i++) {
      assertTrue(slots.get(i).length() >= 2.49 && slots.get(i).length() <= 12.51);
      for (int j = i + 1; j < slots.size(); j++)
        assertTrue(slots.get(i).distance(slots.get(j)) > 1.8);
    }
  }
}
