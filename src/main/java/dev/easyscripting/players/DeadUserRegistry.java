package dev.easyscripting.players;

import dev.easyscripting.storage.YamlStore;
import java.time.Instant;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Persistent, case-insensitive retirement list for actor usernames and temporary nicknames. */
public final class DeadUserRegistry {
  public record Entry(
      String name,
      String kind,
      String owner,
      long diedAt,
      String skinOwner,
      String texture,
      String signature) {}

  private final YamlStore store;
  private final Map<String, Entry> entries = new HashMap<>();

  public DeadUserRegistry(YamlStore store) {
    this.store = store;
  }

  public void load() {
    YamlConfiguration yaml = store.read("state", "dead-users");
    if (!yaml.contains("schema")) {
      save();
      return;
    }
    if (yaml.getInt("schema") != 1 || !(yaml.get("entries") instanceof List<?>))
      throw new IllegalArgumentException(
          "state/dead-users.yml: expected schema 1 and an entries list.");
    for (Map<?, ?> value : yaml.getMapList("entries")) {
      Entry entry = read(value);
      entries.putIfAbsent(normalize(entry.name()), entry);
    }
  }

  public boolean contains(String name) {
    return name != null && entries.containsKey(normalize(name));
  }

  public Optional<Entry> get(String name) {
    return name == null ? Optional.empty() : Optional.ofNullable(entries.get(normalize(name)));
  }

  public List<Entry> list(String query) {
    String filter = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
    return entries.values().stream()
        .filter(entry -> entry.name().toLowerCase(Locale.ROOT).contains(filter))
        .sorted(
            Comparator.comparingLong(Entry::diedAt)
                .reversed()
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  public boolean retire(
      String name,
      String kind,
      String owner,
      String skinOwner,
      String texture,
      String signature) {
    validateName(name);
    if (!Set.of("actor", "player").contains(kind))
      throw new IllegalArgumentException("Dead identity kind must be actor or player.");
    Entry entry =
        new Entry(
            name,
            kind,
            Objects.requireNonNullElse(owner, ""),
            Instant.now().toEpochMilli(),
            Objects.requireNonNullElse(skinOwner, ""),
            Objects.requireNonNullElse(texture, ""),
            Objects.requireNonNullElse(signature, ""));
    if (entries.putIfAbsent(normalize(name), entry) != null) return false;
    save();
    return true;
  }

  public Entry remove(String name) {
    Entry removed = entries.remove(normalize(name));
    if (removed == null)
      throw new IllegalArgumentException("Dead username '" + name + "' is not saved.");
    save();
    return removed;
  }

  /**
   * Release every retired name at once. Returns how many were freed, so an empty list can be
   * reported as such instead of looking like a successful but silent wipe.
   */
  public int clear() {
    int released = entries.size();
    if (released == 0) return 0;
    entries.clear();
    save();
    return released;
  }

  private void save() {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("schema", 1);
    yaml.set(
        "entries",
        entries.values().stream()
            .sorted(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER))
            .map(
                entry -> {
                  Map<String, Object> value = new LinkedHashMap<>();
                  value.put("name", entry.name());
                  value.put("kind", entry.kind());
                  value.put("owner", entry.owner());
                  value.put("died-at", entry.diedAt());
                  value.put("skin-owner", entry.skinOwner());
                  value.put("texture", entry.texture());
                  value.put("signature", entry.signature());
                  return value;
                })
            .toList());
    yaml.options()
        .setHeader(
            List.of(
                "Retired generated identities. Manage them with /deadusers; do not edit while the"
                    + " server is running."));
    yaml.setComments("schema", List.of("Storage format version; keep 1."));
    yaml.setComments(
        "entries",
        List.of(
            "Natural actor deaths and nicknamed-player deaths are retained here until explicitly"
                + " removed."));
    store.save("state", "dead-users", yaml);
  }

  private static Entry read(Map<?, ?> value) {
    String name = text(value, "name"), kind = text(value, "kind");
    validateName(name);
    if (!Set.of("actor", "player").contains(kind))
      throw new IllegalArgumentException("state/dead-users.yml: invalid entry kind " + kind + ".");
    Object died = value.get("died-at");
    if (!(died instanceof Number number) || number.longValue() < 0)
      throw new IllegalArgumentException(
          "state/dead-users.yml: died-at must be a positive epoch timestamp.");
    return new Entry(
        name,
        kind,
        text(value, "owner"),
        number.longValue(),
        text(value, "skin-owner"),
        text(value, "texture"),
        text(value, "signature"));
  }

  private static String text(Map<?, ?> value, String key) {
    Object result = value.get(key);
    return result instanceof String text ? text : "";
  }

  private static void validateName(String name) {
    if (name == null
        || name.isBlank()
        || name.length() > 48
        || name.chars().anyMatch(character -> Character.isISOControl(character)))
      throw new IllegalArgumentException("Dead username must be 1..48 visible characters.");
  }

  private static String normalize(String name) {
    return name.toLowerCase(Locale.ROOT);
  }
}
