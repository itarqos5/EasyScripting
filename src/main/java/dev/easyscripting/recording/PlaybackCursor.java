package dev.easyscripting.recording;

/** Bounded frame index; reversal does not duplicate either endpoint. */
public final class PlaybackCursor {
  private final int size;
  private PlaybackMode mode;
  private int index, direction;

  public PlaybackCursor(int size, PlaybackMode mode, boolean backwards) {
    if (size < 1) throw new IllegalArgumentException("A recording must contain a frame.");
    this.size = size;
    this.mode = mode;
    index = backwards ? size - 1 : 0;
    direction = backwards ? -1 : 1;
  }

  public int index() {
    return index;
  }

  public void mode(PlaybackMode mode) {
    if (this.mode == mode) return;
    this.mode = java.util.Objects.requireNonNull(mode);
    // Leaving ping-pong playback resumes forward without moving the current frame.
    if (mode != PlaybackMode.REVERSE) direction = 1;
  }

  public boolean advance() {
    int next = index + direction;
    if (next >= 0 && next < size) {
      index = next;
      return true;
    }
    if (mode == PlaybackMode.STOP) return false;
    if (mode == PlaybackMode.REPEAT) index = direction > 0 ? 0 : size - 1;
    else {
      direction = -direction;
      index = size == 1 ? 0 : index + direction;
    }
    return true;
  }
}
