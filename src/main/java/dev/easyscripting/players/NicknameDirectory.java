package dev.easyscripting.players;

import java.util.*;

/** Online account names stay reserved even while a player uses an alias. Main-thread owned. */
public final class NicknameDirectory {
  public record Entry(String account, String nickname) {
    public String visible() {
      return nickname == null ? account : nickname;
    }
  }

  private final Map<UUID, Entry> entries = new LinkedHashMap<>();

  public Set<UUID> join(UUID id, String account) {
    Set<UUID> displaced = new HashSet<>();
    entries.forEach(
        (other, entry) -> {
          if (!other.equals(id)
              && entry.nickname() != null
              && account.equalsIgnoreCase(entry.nickname())) displaced.add(other);
        });
    displaced.forEach(this::reset);
    entries.put(id, new Entry(account, null));
    return Set.copyOf(displaced);
  }

  public void leave(UUID id) {
    entries.remove(id);
  }

  public Entry get(UUID id) {
    return entries.get(id);
  }

  public Set<UUID> ids() {
    return Set.copyOf(entries.keySet());
  }

  public boolean nicknamed(UUID id) {
    return get(id) != null && get(id).nickname() != null;
  }

  public Optional<UUID> resolve(String name) {
    return entries.entrySet().stream()
        .filter(
            e ->
                e.getValue().account().equalsIgnoreCase(name)
                    || e.getValue().visible().equalsIgnoreCase(name))
        .map(Map.Entry::getKey)
        .findFirst();
  }

  public boolean available(UUID id, String name) {
    return name.matches("[A-Za-z0-9_]{3,16}")
        && entries.entrySet().stream()
            .noneMatch(
                e ->
                    !e.getKey().equals(id)
                        && (e.getValue().account().equalsIgnoreCase(name)
                            || e.getValue().visible().equalsIgnoreCase(name)));
  }

  public void assign(UUID id, String name) {
    Entry entry = Objects.requireNonNull(get(id), "Player is not online.");
    if (!available(id, name))
      throw new IllegalArgumentException("Nickname is invalid or already in use.");
    entries.put(id, new Entry(entry.account(), name));
  }

  public void reset(UUID id) {
    Entry entry = get(id);
    if (entry != null) entries.put(id, new Entry(entry.account(), null));
  }

  public List<String> names() {
    return entries.values().stream()
        .flatMap(e -> java.util.stream.Stream.of(e.account(), e.visible()))
        .distinct()
        .toList();
  }

  public Map<String, String> replacements() {
    Map<String, String> result = new HashMap<>();
    entries.values().stream()
        .filter(e -> e.nickname() != null)
        .forEach(e -> result.put(e.account(), e.nickname()));
    return Map.copyOf(result);
  }
}
