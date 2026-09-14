package dev.easyscripting.recording;

import org.bukkit.util.Vector;

/** Pauses the cursor for native physics, then fades the displacement back onto the route. */
public final class ReplayRecovery {
  private final int pauseTicks, blendTicks;
  private int pause, remaining;
  private Vector offset;

  public ReplayRecovery(int pauseTicks, int blendTicks) {
    if (pauseTicks < 1 || blendTicks < 1)
      throw new IllegalArgumentException("Recovery durations must be positive.");
    this.pauseTicks = pauseTicks;
    this.blendTicks = blendTicks;
  }

  public void hit() {
    pause = pauseTicks;
    remaining = blendTicks;
    offset = null;
  }

  public boolean yieldToPhysics() {
    if (pause == 0) return false;
    pause--;
    return true;
  }

  public Vector offset(Vector current, Vector recorded) {
    if (remaining == 0) return new Vector();
    if (offset == null) offset = current.clone().subtract(recorded);
    Vector result = offset.clone().multiply((double) remaining / blendTicks);
    remaining--;
    return result;
  }
}
