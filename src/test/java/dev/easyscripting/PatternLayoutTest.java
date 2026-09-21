package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.actors.PatternLayout;
import java.util.*;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class PatternLayoutTest {
  @Test
  void everyShapeReturnsTheExactDistinctCount() {
    for (String shape : List.of("line", "circle", "disc", "grid", "square"))
      for (int count : List.of(1, 2, 25, 100, 200)) {
        var points = PatternLayout.locations(shape, count, 2, anchor(0), "behind");
        assertEquals(count, points.size(), shape + " count");
        assertEquals(
            count,
            points.stream()
                .map(p -> String.format(Locale.ROOT, "%.4f:%.4f", p.getX(), p.getZ()))
                .distinct()
                .count(),
            shape + " distinct");
      }
  }

  @Test
  void discAndSquareAreFilledAndStayEntirelyOnTheRequestedSide() {
    for (String shape : List.of("disc", "square")) {
      var front = PatternLayout.locations(shape, 25, 2, anchor(0), "front");
      var behind = PatternLayout.locations(shape, 25, 2, anchor(0), "behind");
      assertTrue(front.stream().allMatch(point -> point.getZ() >= 2.99), shape + " front");
      assertTrue(behind.stream().allMatch(point -> point.getZ() <= -2.99), shape + " behind");
      // A filled shape has a point at the translated center, unlike an outline.
      assertTrue(front.stream().anyMatch(point -> Math.abs(point.getX()) < 0.001));
      assertTrue(front.stream().map(Location::getZ).distinct().count() >= 3);
    }
  }

  @Test
  void leaderYawRotatesFrontAndBehindWithoutUsingPitch() {
    Location eastFacing = anchor(90);
    eastFacing.setPitch(70);
    var front = PatternLayout.locations("square", 9, 2, eastFacing, "front");
    var behind = PatternLayout.locations("square", 9, 2, eastFacing, "behind");
    assertTrue(front.stream().allMatch(point -> point.getX() <= -2.99));
    assertTrue(behind.stream().allMatch(point -> point.getX() >= 2.99));
    assertTrue(front.stream().allMatch(point -> point.getPitch() == 0));
  }

  @Test
  void invalidFormationInputsExplainTheirExpectedValues() {
    assertThrows(
        IllegalArgumentException.class,
        () -> PatternLayout.locations("triangle", 5, 2, anchor(0), "front"));
    assertThrows(
        IllegalArgumentException.class,
        () -> PatternLayout.locations("disc", 0, 2, anchor(0), "front"));
    assertThrows(
        IllegalArgumentException.class,
        () -> PatternLayout.locations("disc", 5, 0.5, anchor(0), "front"));
    assertThrows(
        IllegalArgumentException.class,
        () -> PatternLayout.locations("disc", 5, 2, anchor(0), "beside"));
  }

  private static Location anchor(float yaw) {
    return new Location(null, 0, 80, 0, yaw, 0);
  }

  @Test
  void aLineUpGivesEveryMemberItsOwnWholeBlockInAlignedRows() {
    // A deliberately awkward yaw and fractional stance: free-angle placement would round two
    // members onto one block, which is exactly what a line-up must never do.
    var at = new Location(null, 100.3, 70, -40.8, 37f, 12f);
    var places = PatternLayout.lineUp(23, 5, at, "behind");
    assertEquals(23, places.size());
    var blocks = new HashSet<String>();
    for (var place : places) {
      blocks.add(place.getBlockX() + ":" + place.getBlockZ());
      assertEquals(0.5, Math.abs(place.getX() % 1), 0.0001); // Centred on its block.
      assertEquals(0.5, Math.abs(place.getZ() % 1), 0.0001);
      assertEquals(0, place.getPitch(), 0.0001);
    }
    assertEquals(23, blocks.size(), "every member must stand on its own block");
    // Five lanes across and five rows deep, all on the world grid.
    assertEquals(5, places.stream().map(Location::getBlockX).distinct().count());
    assertEquals(5, places.stream().map(Location::getBlockZ).distinct().count());
    assertTrue(places.stream().map(Location::getYaw).distinct().count() == 1);
  }

  @Test
  void everyCardinalFacingKeepsTheLineUpOnDistinctBlocks() {
    for (float yaw : new float[] {0, 44, 46, 90, 135, 180, -135, -90, -44, 270, 359}) {
      var places = PatternLayout.lineUp(12, 4, new Location(null, 8.2, 64, 8.7, yaw, 0), "front");
      assertEquals(
          12,
          places.stream().map(place -> place.getBlockX() + ":" + place.getBlockZ()).distinct()
              .count(),
          "yaw " + yaw);
    }
  }

  @Test
  void aLineUpSitsBehindOrInFrontAccordingToTheSideAndRejectsBadInput() {
    var at = new Location(null, 0.5, 70, 0.5, 0f, 0f); // Facing +Z.
    var behind = PatternLayout.lineUp(1, 1, at, "behind").get(0);
    var front = PatternLayout.lineUp(1, 1, at, "front").get(0);
    assertTrue(behind.getZ() < at.getZ() && front.getZ() > at.getZ());
    // Equally far from the anchor on each side, measured from where the anchor stands.
    assertEquals(at.getZ() - behind.getZ(), front.getZ() - at.getZ(), 0.0001);
    assertThrows(IllegalArgumentException.class, () -> PatternLayout.lineUp(1, 1, at, "sideways"));
    assertThrows(IllegalArgumentException.class, () -> PatternLayout.lineUp(0, 1, at, "front"));
    assertThrows(IllegalArgumentException.class, () -> PatternLayout.lineUp(4, 0, at, "front"));
    assertThrows(IllegalArgumentException.class, () -> PatternLayout.lineUp(4, 33, at, "front"));
  }
}
