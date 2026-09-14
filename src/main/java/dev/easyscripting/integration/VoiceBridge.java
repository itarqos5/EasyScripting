package dev.easyscripting.integration;

import org.bukkit.entity.Player;

public interface VoiceBridge extends AutoCloseable {
  boolean available();

  boolean muted();

  void mute(boolean muted);

  void broadcast(Player speaker, boolean enabled);

  @Override
  void close();

  static VoiceBridge absent() {
    return new VoiceBridge() {
      public boolean available() {
        return false;
      }

      public boolean muted() {
        return false;
      }

      public void mute(boolean muted) {
        throw new IllegalArgumentException(
            "Simple Voice Chat is not installed or its API is unavailable.");
      }

      public void broadcast(Player speaker, boolean enabled) {
        throw new IllegalArgumentException(
            "Simple Voice Chat is not installed or its API is unavailable.");
      }

      public void close() {}
    };
  }
}
