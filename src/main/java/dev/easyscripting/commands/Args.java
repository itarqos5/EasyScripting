package dev.easyscripting.commands;

import dev.easyscripting.core.Checks;
import java.util.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Args {
  private final String[] values;

  public Args(String[] values) {
    this.values = values;
  }

  public String get(int i) {
    if (i >= values.length)
      throw new IllegalArgumentException(
          "Missing argument " + (i + 1) + ". Use /es help for command syntax.");
    return values[i];
  }

  public String get(int i, String fallback) {
    return i < values.length ? values[i] : fallback;
  }

  public String rest(int i) {
    get(i);
    return String.join(" ", Arrays.copyOfRange(values, i, values.length));
  }

  public int size() {
    return values.length;
  }

  public int integer(int i, int min, int max) {
    return Checks.integer(get(i), min, max);
  }

  public double decimal(int i, double min, double max) {
    return Checks.decimal(get(i), min, max);
  }

  public static Player player(CommandSender sender) {
    if (sender instanceof Player p) return p;
    throw new IllegalArgumentException("This operation requires an in-game player.");
  }

  public static Map<String, String> pairs(String input) {
    Map<String, String> result = new LinkedHashMap<>();
    // Semicolon separates arguments; spaces remain available inside text values.
    for (String pair : input.split(";")) {
      int at = pair.indexOf('=');
      if (at < 1) throw new IllegalArgumentException("Action arguments use key=value;key=value.");
      String key = Checks.id(pair.substring(0, at).trim());
      if (result.put(key, pair.substring(at + 1).trim()) != null)
        throw new IllegalArgumentException("Duplicate argument: " + key);
    }
    return result;
  }
}
