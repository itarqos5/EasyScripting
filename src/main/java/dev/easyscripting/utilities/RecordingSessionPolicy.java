package dev.easyscripting.utilities;

/** A recording session has no allow-list/reconnect/bypass exceptions beyond current operators. */
public final class RecordingSessionPolicy {
  private RecordingSessionPolicy() {}

  public static boolean blocksLogin(boolean recording, boolean operator) {
    return recording && !operator;
  }
}
