package dev.easyscripting.config;

import dev.easyscripting.players.GeneratedUsername;
import java.util.*;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.file.YamlConfiguration;

/** Validated identity choices; actor IDs never participate in the displayed username. */
public record NpcIdentities(
    boolean enabled, List<String> usernames, List<String> skins, List<String> suffixes) {
  public NpcIdentities(boolean enabled, List<String> usernames, List<String> skins) {
    this(enabled, usernames, skins, List.of());
  }

  public NpcIdentities {
    usernames = List.copyOf(usernames);
    skins = List.copyOf(skins);
    suffixes = List.copyOf(suffixes);
    if (usernames.isEmpty() || usernames.stream().anyMatch(name -> !GeneratedUsername.valid(name)))
      throw new IllegalArgumentException(
          "Generated username pools require 5..16 characters, at least one letter, and at least"
              + " one digit or underscore.");
  }

  public record Selection(String name, String skin) {}

  public static NpcIdentities read(YamlConfiguration yaml) {
    if (!Integer.valueOf(1).equals(yaml.get("schema")))
      throw invalid("schema", yaml.get("schema"), "integer 1");
    if (!(yaml.get("enabled") instanceof Boolean enabled))
      throw invalid("enabled", yaml.get("enabled"), "true or false");
    List<String> prefixes = names(yaml, "name-prefixes");
    List<String> suffixes = names(yaml, "name-suffixes");
    List<String> skins = names(yaml, "skin-owners");
    Set<String> unique = new HashSet<>();
    List<String> names = new ArrayList<>();
    for (String prefix : prefixes)
      for (String suffix : suffixes) {
        String base = (prefix + suffix).toLowerCase(Locale.ROOT);
        if (base.length() > 15)
          throw invalid(
              "name-prefixes/name-suffixes",
              base,
              "combined fallback name of at most 15 characters before its required digit");
        for (int digit = 0; digit <= 9; digit++) {
          String candidate = base + digit;
          if (unique.add(candidate.toLowerCase(Locale.ROOT))) names.add(candidate);
        }
        String underscored = (prefix + "_" + suffix).toLowerCase(Locale.ROOT);
        if (underscored.length() <= 16
            && GeneratedUsername.valid(underscored)
            && unique.add(underscored.toLowerCase(Locale.ROOT))) names.add(underscored);
      }
    return new NpcIdentities(enabled, names, skins, suffixes);
  }

  private static List<String> names(YamlConfiguration yaml, String key) {
    if (!(yaml.get(key) instanceof List<?> values) || values.isEmpty() || values.size() > 64)
      throw invalid(key, yaml.get(key), "a list of 1..64 Minecraft name fragments/accounts");
    List<String> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Object value : values) {
      if (!(value instanceof String name) || !name.matches("[A-Za-z0-9_]{1,16}"))
        throw invalid(key, value, "1..16 letters, digits or underscores");
      if (!seen.add(name.toLowerCase(Locale.ROOT)))
        throw invalid(key, value, "distinct entries, ignoring case");
      result.add(name);
    }
    return result;
  }

  public Selection choose(
      Collection<String> unavailable,
      Predicate<String> blacklisted,
      boolean playerActor,
      String previousSkin,
      RandomGenerator random) {
    return choose(unavailable, blacklisted, playerActor, previousSkin, random, List.of());
  }

  public Selection choose(
      Collection<String> unavailable,
      Predicate<String> blacklisted,
      boolean playerActor,
      String previousSkin,
      RandomGenerator random,
      Collection<String> recentNames) {
    return choose(
        unavailable,
        blacklisted,
        blacklisted,
        playerActor,
        previousSkin,
        random,
        recentNames,
        List.of(),
        List.of());
  }

  public Selection choose(
      Collection<String> unavailable,
      Predicate<String> blockedName,
      Predicate<String> blockedSkin,
      boolean playerActor,
      String previousSkin,
      RandomGenerator random,
      Collection<String> recentNames,
      Collection<String> publicNames,
      Collection<String> publicSkins) {
    Set<String> occupied = new HashSet<>();
    unavailable.forEach(name -> occupied.add(name.toLowerCase(Locale.ROOT)));
    List<String> availableNames = available(publicNames, occupied, blockedName, true);
    if (availableNames.isEmpty())
      availableNames = available(usernames, occupied, blockedName, false);
    if (availableNames.isEmpty())
      throw new IllegalArgumentException(
          "No unused generated usernames remain. Wait for the public provider or expand the"
              + " fallback fragments in npc-identities.yml.");
    // Prefer unused recent endings as well as unused full names. Respect even tiny custom pools.
    Set<String> recentSuffixes = new HashSet<>();
    for (String name : recentNames)
      suffixes.stream()
          .map(s -> s.toLowerCase(Locale.ROOT))
          .filter(s -> name.toLowerCase(Locale.ROOT).matches(".*" + s + "(?:[0-9]|$)"))
          .forEach(recentSuffixes::add);
    List<String> varied =
        availableNames.stream()
            .filter(
                name ->
                    recentSuffixes.stream()
                        .noneMatch(s -> name.toLowerCase(Locale.ROOT).matches(".*" + s + "(?:[0-9]|$)")))
            .toList();
    if (!varied.isEmpty()) availableNames = varied;
    String skin = "";
    if (playerActor) {
      List<String> availableSkins =
          publicSkins.stream().filter(name -> !blockedSkin.test(name)).distinct().toList();
      if (availableSkins.isEmpty())
        availableSkins = skins.stream().filter(name -> !blockedSkin.test(name)).toList();
      if (availableSkins.isEmpty())
        throw new IllegalArgumentException(
            "All npc-identities.yml skin-owners are blacklisted. Add an allowed skin account.");
      List<String> different =
          availableSkins.stream().filter(name -> !name.equalsIgnoreCase(previousSkin)).toList();
      if (!different.isEmpty()) availableSkins = different;
      skin = availableSkins.get(random.nextInt(availableSkins.size()));
    }
    return new Selection(availableNames.get(random.nextInt(availableNames.size())), skin);
  }

  private static List<String> available(
      Collection<String> source,
      Set<String> occupied,
      Predicate<String> blocked,
      boolean validateGenerated) {
    Map<String, String> unique = new LinkedHashMap<>();
    for (String name : source) {
      if (validateGenerated && !GeneratedUsername.valid(name)) continue;
      String normalized = name.toLowerCase(Locale.ROOT);
      if (!occupied.contains(normalized) && !blocked.test(name)) unique.putIfAbsent(normalized, name);
    }
    return List.copyOf(unique.values());
  }

  private static IllegalArgumentException invalid(String key, Object value, String expected) {
    return new IllegalArgumentException(
        "npc-identities.yml: " + key + " = " + value + "; expected " + expected + ".");
  }
}
