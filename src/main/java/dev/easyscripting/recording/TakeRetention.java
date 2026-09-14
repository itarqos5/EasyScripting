package dev.easyscripting.recording;

import java.util.Collection;

/** A shared performance remains until its final NPC stops referencing it. */
public final class TakeRetention {
  private TakeRetention() {}

  public static boolean unused(String recording, Collection<String> references) {
    return recording != null && !recording.isBlank() && !references.contains(recording);
  }
}
