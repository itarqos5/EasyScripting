package dev.easyscripting.players;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How long a player who was kicked for dying has to wait before the server lets them back in.
 *
 * <p>The clock is passed in rather than read here so the whole rule can be tested without waiting
 * for real time to pass. The map is concurrent because a login is refused on Netty's login thread
 * while deaths and kicks happen on the main thread.
 */
public final class DeathLockout {
  private final Map<UUID, Long> until = new ConcurrentHashMap<>();

  /** Lock a player out for {@code duration} milliseconds. A death never shortens an active wait. */
  public void start(UUID id, long now, long duration) {
    until.values().removeIf(expiry -> expiry <= now);
    if (duration <= 0) return;
    until.merge(id, now + duration, Math::max);
  }

  /** Milliseconds left, or 0 when the player may join. Expired entries are dropped as they pass. */
  public long remaining(UUID id, long now) {
    Long expiry = until.get(id);
    if (expiry == null) return 0;
    if (expiry <= now) {
      until.remove(id, expiry);
      return 0;
    }
    return expiry - now;
  }

  /** Whole seconds left, rounded up, so a message never tells a player to wait "0" more seconds. */
  public long seconds(UUID id, long now) {
    return (remaining(id, now) + 999) / 1000;
  }

  public void clear(UUID id) {
    until.remove(id);
  }

  public int size() {
    return until.size();
  }
}
