package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.recording.ReplayFacing;
import org.junit.jupiter.api.Test;

class ReplayFacingTest {
  @Test
  void aReversedWalkFacesTheWayItIsTravelling() {
    // Minecraft yaw: 0 is south (+Z), -90 is east (+X), 180 is north (-Z), 90 is west (-X).
    assertEquals(0, ReplayFacing.yaw(0, 4, 180), 1.0e-4);
    assertEquals(180, Math.abs(ReplayFacing.yaw(0, -4, 0)), 1.0e-4);
    assertEquals(-90, ReplayFacing.yaw(4, 0, 90), 1.0e-4);
    assertEquals(90, ReplayFacing.yaw(-4, 0, -90), 1.0e-4);
    assertEquals(-45, ReplayFacing.yaw(1, 1, 135), 1.0e-4);
  }

  @Test
  void aPerformerStandingStillKeepsTheYawTheyWereRecordedWith() {
    assertEquals(137.5f, ReplayFacing.yaw(0, 0, 137.5f));
    assertEquals(137.5f, ReplayFacing.yaw(0.01, -0.01, 137.5f), "a breath of drift is not a walk");
    assertNotEquals(137.5f, ReplayFacing.yaw(0.2, 0, 137.5f));
  }
}
