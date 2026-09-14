package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.recording.ReplayRecovery;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class ReplayRecoveryTest {
  @Test
  void uninterruptedPlaybackHasNoOffset() {
    var recovery = new ReplayRecovery(12, 10);
    assertFalse(recovery.yieldToPhysics());
    assertEquals(new Vector(), recovery.offset(new Vector(7, 2, 3), new Vector()));
  }

  @Test
  void pausesFramesThenBlendsBackWithoutMutatingLocations() {
    var recovery = new ReplayRecovery(3, 4);
    recovery.hit();
    for (int i = 0; i < 3; i++) assertTrue(recovery.yieldToPhysics());
    assertFalse(recovery.yieldToPhysics());
    var current = new Vector(14, 65, 8);
    var recorded = new Vector(10, 64, 8);
    assertEquals(new Vector(4, 1, 0), recovery.offset(current, recorded));
    assertEquals(new Vector(3, .75, 0), recovery.offset(new Vector(20, 70, 8), recorded));
    assertEquals(new Vector(2, .5, 0), recovery.offset(current, recorded));
    assertEquals(new Vector(1, .25, 0), recovery.offset(current, recorded));
    assertEquals(new Vector(), recovery.offset(current, recorded));
    assertEquals(new Vector(14, 65, 8), current);
    assertEquals(new Vector(10, 64, 8), recorded);
  }

  @Test
  void anotherHitRestartsPauseAndRecalculatesDisplacement() {
    var recovery = new ReplayRecovery(1, 2);
    recovery.hit();
    assertTrue(recovery.yieldToPhysics());
    recovery.offset(new Vector(2, 0, 0), new Vector());
    recovery.hit();
    assertTrue(recovery.yieldToPhysics());
    assertFalse(recovery.yieldToPhysics());
    assertEquals(new Vector(-6, 0, 0), recovery.offset(new Vector(-6, 0, 0), new Vector()));
  }

  @Test
  void durationsMustBePositive() {
    assertThrows(IllegalArgumentException.class, () -> new ReplayRecovery(0, 10));
    assertThrows(IllegalArgumentException.class, () -> new ReplayRecovery(12, 0));
  }
}
