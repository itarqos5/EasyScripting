package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.actors.GroupTactics;
import java.util.*;
import java.util.stream.IntStream;
import org.bukkit.util.Vector;
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
  void hundredTrailingSlotsAreDistinctSpacedAndBehindTheLeader() {
    var slots =
        IntStream.range(0, 100)
            .mapToObj(i -> GroupTactics.trailingFormation(i, 100, 0, 2.5, new Vector(0, 0, 1)))
            .toList();
    assertEquals(100, new HashSet<>(slots).size());
    for (int i = 0; i < slots.size(); i++) {
      assertTrue(slots.get(i).getZ() <= -2.49);
      for (int j = i + 1; j < slots.size(); j++)
        assertTrue(slots.get(i).distance(slots.get(j)) > 2.49);
    }
  }

  @Test
  void rowsShareOneColumnGridEvenWhenTheLastRowIsPartial() {
    // Seven members over three columns: two full rows and a centred pair, all on the same lanes.
    var lanes =
        IntStream.range(0, 7)
            .mapToObj(i -> GroupTactics.trailingFormation(i, 7, 3, 2.0, new Vector(0, 0, 1)))
            .map(v -> Math.round(v.getX() * 1000) / 1000.0)
            .collect(java.util.stream.Collectors.toSet());
    assertEquals(Set.of(-2.0, 0.0, 2.0), lanes);
    var last = GroupTactics.trailingFormation(6, 7, 3, 2.0, new Vector(0, 0, 1));
    assertEquals(0, last.getX(), 0.0001);
    assertEquals(-6, last.getZ(), 0.0001);
  }

  @Test
  void requestedColumnsShapeTheFormationAndNeverExceedTheGroup() {
    assertEquals(4, GroupTactics.columns(4, 20));
    assertEquals(10, GroupTactics.columns(0, 100)); // Automatic picks the squarest block.
    assertEquals(3, GroupTactics.columns(8, 3)); // Never more lanes than there are members.
    var wide = GroupTactics.trailingFormation(19, 20, 20, 2.0, new Vector(0, 0, 1));
    assertEquals(-2, wide.getZ(), 0.0001); // One wide line sits in a single row.
  }

  @Test
  void aMoveDestinationCentresTheSameRowsInsteadOfTrailingThem() {
    var rows =
        IntStream.range(0, 4)
            .mapToObj(i -> GroupTactics.blockFormation(i, 4, 2, 2.0, new Vector(0, 0, 1)))
            .map(Vector::getZ)
            .toList();
    assertEquals(List.of(1.0, 1.0, -1.0, -1.0), rows);
  }

  @Test
  void movementHeadingStaysStableWhileStoppedAndTurnsWithTravel() {
    var east = new Vector(1, 0, 0);
    assertEquals(east, GroupTactics.movementHeading(east, new Vector(), 180));
    // A sampled walking step turns the formation; head movement alone no longer does.
    var turned = GroupTactics.movementHeading(east, new Vector(0, 0, 0.21), 90);
    assertTrue(turned.getX() > 0 && turned.getZ() > 0);
    assertEquals(east, GroupTactics.movementHeading(east, new Vector(0, 0, 0.01), 90));
    var slot = GroupTactics.trailingFormation(0, 1, 0, 2.5, east);
    assertTrue(slot.getX() < 0);
  }

  @Test
  void attackersShareTheRingAroundOneTargetAndALoneAttackerGoesStraightIn() {
    var bearing = new Vector(0, 0, 4); // The attacker stands north of its target.
    var alone = GroupTactics.engagementOffset(0, 1, 2, bearing);
    assertEquals(0, alone.getX(), 0.0001);
    assertEquals(2, alone.getZ(), 0.0001);
    var spread =
        IntStream.range(0, 4)
            .mapToObj(i -> GroupTactics.engagementOffset(i, 4, 2, bearing))
            .toList();
    assertEquals(4, new HashSet<>(spread).size());
    for (var offset : spread) assertEquals(2, offset.length(), 0.0001);
    for (int i = 0; i + 1 < spread.size(); i++)
      assertTrue(spread.get(i).distance(spread.get(i + 1)) > 1.5);
  }

  @Test
  void aSidestepKeepsTheDistanceItAlreadyHeldSoReachIsNotGivenUp() {
    var bearing = new Vector(0, 0, 3); // Standing three blocks north of the target.
    var left = GroupTactics.circleOffset(bearing, 3, 0.7);
    var right = GroupTactics.circleOffset(bearing, 3, -0.7);
    assertEquals(3, left.length(), 0.0001);
    assertEquals(3, right.length(), 0.0001);
    // Opposite arcs step to opposite sides, and neither one walks into the target.
    assertTrue(left.getX() * right.getX() < 0);
    assertTrue(left.distance(right) > 1);
    // A zero arc is the place the NPC already occupies.
    assertEquals(0, GroupTactics.circleOffset(bearing, 3, 0).distance(bearing), 0.0001);
  }

  @Test
  void distantGoalsUseWalkingWaypointsAndFriendlyDamageIsOneWay() {
    var waypoint =
        GroupTactics.waypoint(new Vector(), new Vector(100, 0, 0), 32);
    assertEquals(32, waypoint.getX(), 0.0001);
    assertFalse(GroupTactics.blocksFriendlyDamage(null, "red")); // real leader -> member
    assertTrue(GroupTactics.blocksFriendlyDamage("red", "red")); // member -> leader/member
    assertFalse(GroupTactics.blocksFriendlyDamage("red", "blue"));
  }
}
