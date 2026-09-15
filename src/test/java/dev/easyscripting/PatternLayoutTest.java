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
}
