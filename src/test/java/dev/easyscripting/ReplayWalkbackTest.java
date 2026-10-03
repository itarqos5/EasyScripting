package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.recording.ReplayWalkback;
import org.junit.jupiter.api.Test;

class ReplayWalkbackTest {
  @Test
  void aKnockedBackActorWalksUntilItReachesItsRoute() {
    var walkback = new ReplayWalkback(true, 0.8, 100);
    walkback.begin(6);
    assertTrue(walkback.returning());
    assertTrue(walkback.walking(6));
    assertTrue(walkback.walking(3.5));
    assertTrue(walkback.walking(1.2));
    assertFalse(walkback.walking(0.8), "arriving hands the frames back immediately");
    assertFalse(walkback.returning());
    assertFalse(walkback.walking(9), "a finished walk does not restart on its own");
  }

  @Test
  void aNudgeIsBlendedInsteadOfWalked() {
    var walkback = new ReplayWalkback(true, 0.8, 100);
    walkback.begin(0.5);
    assertFalse(walkback.returning());
    assertFalse(walkback.walking(0.5));
  }

  @Test
  void anImpossibleWalkBackGivesUpSoTheTakeContinues() {
    var walkback = new ReplayWalkback(true, 0.8, 20);
    walkback.begin(40);
    int ticks = 0;
    while (walkback.walking(40)) ticks++;
    assertEquals(19, ticks, "the attempt is bounded by its timeout");
    assertFalse(walkback.returning());
  }

  @Test
  void leavingTheRecordedWorldAbandonsTheWalk() {
    var walkback = new ReplayWalkback(true, 0.8, 100);
    walkback.begin(5);
    walkback.abandon();
    assertFalse(walkback.returning());
  }

  @Test
  void disabledWalkBackKeepsTheOldBlendForEveryHit() {
    var walkback = new ReplayWalkback(false, 0.8, 100);
    walkback.begin(30);
    assertFalse(walkback.returning());
  }

  @Test
  void rangesAreChecked() {
    assertThrows(IllegalArgumentException.class, () -> new ReplayWalkback(true, 0, 100));
    assertThrows(IllegalArgumentException.class, () -> new ReplayWalkback(true, 17, 100));
    assertThrows(IllegalArgumentException.class, () -> new ReplayWalkback(true, 0.8, 0));
  }
}
