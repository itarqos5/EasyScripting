package dev.easyscripting.recording;

/**
 * Decides whether a replay that was knocked off its route walks back to it on its own legs.
 *
 * <p>Sliding the recorded path back under a displaced NPC is instant and reads as teleporting: the
 * NPC crosses whatever is between it and the route without ever travelling. Walking costs time and
 * can fail, so the attempt is bounded. When it reaches the route, or runs out of time trying, the
 * ordinary blend closes the last fraction of a block and the performance carries on.
 */
public final class ReplayWalkback {
  private final boolean enabled;
  private final double arrival;
  private final int timeout;
  private int elapsed = -1;

  public ReplayWalkback(boolean enabled, double arrival, int timeout) {
    if (arrival <= 0 || arrival > 16)
      throw new IllegalArgumentException("Walk-back arrival distance must be above 0 and under 16.");
    if (timeout < 1) throw new IllegalArgumentException("Walk-back timeout must be positive.");
    this.enabled = enabled;
    this.arrival = arrival;
    this.timeout = timeout;
  }

  /**
   * Called once the knockback has finished carrying the NPC. A hit that barely moved it is not
   * worth a walk: the blend covers that distance without anyone seeing a step.
   */
  public void begin(double distance) {
    elapsed = enabled && distance > arrival ? 0 : -1;
  }

  public boolean returning() {
    return elapsed >= 0;
  }

  /**
   * One tick of walking back. False means the route has been reached, the attempt has timed out,
   * or no walk was wanted, and the frames should drive the NPC again.
   */
  public boolean walking(double distance) {
    if (elapsed < 0) return false;
    if (distance <= arrival || ++elapsed >= timeout) {
      elapsed = -1;
      return false;
    }
    return true;
  }

  /** A walk-back is abandoned when the actor leaves the world its route is recorded in. */
  public void abandon() {
    elapsed = -1;
  }
}
