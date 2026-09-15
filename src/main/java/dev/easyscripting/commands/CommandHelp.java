package dev.easyscripting.commands;

import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Contextual help from the same documented command catalogue shipped to server owners. */
public final class CommandHelp {
  private CommandHelp() {}

  public static List<String> explain(
      YamlConfiguration yaml, String route, Args args, String fallback) {
    String sub = args.get(0, "").toLowerCase(Locale.ROOT);
    String canonical = route.equals("kit") ? "kits" : route;
    List<Map<?, ?>> catalogue =
        yaml.getMapList("commands").stream()
            .filter(
                entry -> {
                  String syntax = String.valueOf(entry.get("syntax"));
                  String[] tokens = syntax.split("\\s+");
                  return tokens.length >= 2 && tokens[1].equals(canonical);
                })
            .toList();
    List<Map<?, ?>> matches =
        catalogue.stream()
            .filter(
                entry -> {
                  String[] tokens = String.valueOf(entry.get("syntax")).split("\\s+");
                  if (sub.isBlank() || tokens.length < 3) return true;
                  String first = tokens[2].replace("[", "").replace("]", "");
                  return first.startsWith("<") || Arrays.asList(first.split("\\|")).contains(sub);
                })
            .toList();
    List<String> result = new ArrayList<>();
    if (matches.isEmpty()) {
      result.add("Use: /es " + route + " " + fallback);
      matches = catalogue.stream().limit(2).toList();
    }
    for (var entry : matches.stream().limit(sub.isBlank() ? 2 : 4).toList()) {
      result.add("Use: " + entry.get("syntax"));
      if (entry.get("description") instanceof String description && !description.isBlank())
        result.add(description);
      if (entry.get("example") instanceof String example && example.startsWith("/"))
        result.add("Example: " + example);
    }
    if (route.equals("actor") && sub.equals("set")) {
      result.add(
          "Settings: name, skin, group, immortal, hittable, collidable, nametag, tablist, look,"
              + " wander, aggressive, pose, glow, sneak, sprint, recording, mode.");
      String setting = args.get(2, "");
      if (Set.of(
              "immortal",
              "hittable",
              "collidable",
              "nametag",
              "tablist",
              "look",
              "wander",
              "aggressive",
              "glow",
              "sneak",
              "sprint")
          .contains(setting))
        result.add(
            "Expected "
                + setting
                + " value: on or off. Example: /actor set "
                + args.get(1, "npc1")
                + " "
                + setting
                + " on");
    }
    result.add(
        "<value> is required; [value] is optional; | separates choices. Use Tab to see available"
            + " IDs and players.");
    return result;
  }

  public static boolean syntaxProblem(String reason) {
    String text = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
    return text.startsWith("missing argument")
        || text.startsWith("unknown ")
        || text.startsWith("invalid ")
        || text.startsWith("use ");
  }
}
