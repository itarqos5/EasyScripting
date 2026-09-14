package dev.easyscripting.config;

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
        String candidate = prefix + suffix;
        if (candidate.length() > 16)
          throw invalid(
              "name-prefixes/name-suffixes",
              candidate,
              "combined username of at most 16 characters");
        if (unique.add(candidate.toLowerCase(Locale.ROOT))) names.add(candidate);
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
    Set<String> occupied = new HashSet<>();
    unavailable.forEach(name -> occupied.add(name.toLowerCase(Locale.ROOT)));
    List<String> availableNames =
        usernames.stream()
            .filter(
                name ->
                    !occupied.contains(name.toLowerCase(Locale.ROOT)) && !blacklisted.test(name))
            .toList();
    if (availableNames.isEmpty())
      throw new IllegalArgumentException(
          "No unused NPC usernames remain. Add name-prefixes/name-suffixes in npc-identities.yml or"
              + " remove unused actors.");
    // Prefer unused recent endings as well as unused full names. Respect even tiny custom pools.
    Set<String> recentSuffixes = new HashSet<>();
    for (String name : recentNames)
      suffixes.stream()
          .map(s -> s.toLowerCase(Locale.ROOT))
          .filter(s -> name.toLowerCase(Locale.ROOT).endsWith(s))
          .forEach(recentSuffixes::add);
    List<String> varied =
        availableNames.stream()
            .filter(
                name ->
                    recentSuffixes.stream()
                        .noneMatch(s -> name.toLowerCase(Locale.ROOT).endsWith(s)))
            .toList();
    if (!varied.isEmpty()) availableNames = varied;
    String skin = "";
    if (playerActor) {
      List<String> availableSkins = skins.stream().filter(name -> !blacklisted.test(name)).toList();
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

  private static IllegalArgumentException invalid(String key, Object value, String expected) {
    return new IllegalArgumentException(
        "npc-identities.yml: " + key + " = " + value + "; expected " + expected + ".");
  }
}
