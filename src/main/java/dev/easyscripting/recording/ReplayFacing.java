package dev.easyscripting.recording;

/**
 * Which way a performer faces while its take is played backwards.
 *
 * <p>A take keeps the yaw the performer actually had, which is the way they were walking. Played
 * backwards, that yaw points at where the NPC has just come from, so a simple walk down a line
 * reads as moonwalking. While the frames are moving, the facing is taken from the direction of
 * travel instead. A performer who was standing still keeps the yaw they were recorded with: there
 * is no travel to face, and their head turn is part of the performance.
 */
public final class ReplayFacing {
  private ReplayFacing() {}

  /** Frame-to-frame movement below this many blocks is a standstill, not a walk. */
  public static final double MOVING = 0.05;

  /**
   * @param dx east/west distance to the frame being travelled towards
   * @param dz north/south distance to the frame being travelled towards
   * @param recorded the yaw saved in the frame, kept when there is no travel to face
   */
  public static float yaw(double dx, double dz, float recorded) {
    if (dx * dx + dz * dz <= MOVING * MOVING) return recorded;
    return (float) Math.toDegrees(Math.atan2(-dx, dz));
  }
}
