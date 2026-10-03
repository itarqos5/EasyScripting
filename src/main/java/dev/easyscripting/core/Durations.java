package dev.easyscripting.core;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Waits written the way an operator says them: 30s, 5min, 1h30m, or off. */
public final class Durations {
  private Durations() {}

  /** One day. A production wait longer than this belongs in a ban, not in a death kick. */
  public static final long MAXIMUM = 86400;

  private static final Pattern PART = Pattern.compile("(\\d+)\\s*([a-z]*)");

  /**
   * Seconds in {@code input}, where 0 means no wait at all. A bare number counts as seconds, so
   * {@code 90} and {@code 90s} agree, and several parts may be written together as {@code 1h30m}.
   */
  public static long seconds(String input) {
    String text = input == null ? "" : input.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    if (List.of("off", "none", "no", "disabled", "0").contains(text)) return 0;
    if (text.isEmpty()) throw refusal(input);
    Matcher parts = PART.matcher(text);
    long total = 0;
    int consumed = 0;
    int counted = 0;
    boolean unitless = false;
    while (parts.find()) {
      if (parts.start() != consumed) throw refusal(input);
      consumed = parts.end();
      counted++;
      unitless |= parts.group(2).isEmpty();
      total += Long.parseLong(parts.group(1)) * unit(parts.group(2), input);
      if (total > MAXIMUM)
        throw new IllegalArgumentException(
            "'" + input + "' is longer than a day. Use at most 24h.");
    }
    // A number on its own is seconds. Beside another part it is ambiguous — 1h30 is 30 of what? —
    // so every part of a compound wait has to name its unit.
    if (consumed != text.length() || total == 0 || (unitless && counted > 1)) throw refusal(input);
    return total;
  }

  private static long unit(String suffix, String input) {
    return switch (suffix) {
      case "", "s", "sec", "secs", "second", "seconds" -> 1;
      case "m", "min", "mins", "minute", "minutes" -> 60;
      case "h", "hr", "hrs", "hour", "hours" -> 3600;
      case "d", "day", "days" -> 86400;
      default -> throw refusal(input);
    };
  }

  private static IllegalArgumentException refusal(String input) {
    return new IllegalArgumentException(
        "'"
            + input
            + "' is not a length of time. Use a number with s, min, h or d — 30s, 5min, 1h30m —"
            + " or off.");
  }

  /** The same wait read back to whoever set it, so a command's answer confirms what it did. */
  public static String describe(long seconds) {
    if (seconds <= 0) return "off";
    StringBuilder text = new StringBuilder();
    long left = seconds;
    for (long[] unit : new long[][] {{86400, 'd'}, {3600, 'h'}, {60, 'm'}, {1, 's'}}) {
      long count = left / unit[0];
      if (count == 0) continue;
      left -= count * unit[0];
      if (!text.isEmpty()) text.append(' ');
      text.append(count).append(name((char) unit[1], count));
    }
    return text.toString();
  }

  private static String name(char unit, long count) {
    String word =
        switch (unit) {
          case 'd' -> " day";
          case 'h' -> " hour";
          case 'm' -> " minute";
          default -> " second";
        };
    return count == 1 ? word : word + "s";
  }
}
