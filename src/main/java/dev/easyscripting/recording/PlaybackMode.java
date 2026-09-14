package dev.easyscripting.recording;

import java.util.Locale;

public enum PlaybackMode {
  STOP,
  REPEAT,
  REVERSE;

  public static PlaybackMode parse(String value) {
    try {
      return valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Playback mode must be stop, repeat or reverse.");
    }
  }
}
