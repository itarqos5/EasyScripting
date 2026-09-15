package dev.easyscripting.core;

import java.util.Locale;
import java.util.regex.Pattern;

public final class Checks {
  private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,47}");

  private Checks() {}

  public static String id(String value) {
    if (value == null || !ID.matcher(value).matches())
      throw new IllegalArgumentException(
          "Identifier '" + value + "' must match [a-z0-9][a-z0-9_-]{0,47}.");
    return value;
  }

  public static int integer(String value, int min, int max) {
    try {
      int n = Integer.parseInt(value);
      if (n >= min && n <= max) return n;
    } catch (NumberFormatException ignored) {
    }
    throw new IllegalArgumentException(
        "Value '" + value + "' must be an integer from " + min + " to " + max + ".");
  }

  public static double decimal(String value, double min, double max) {
    try {
      double n = Double.parseDouble(value);
      if (Double.isFinite(n) && n >= min && n <= max) return n;
    } catch (NumberFormatException ignored) {
    }
    throw new IllegalArgumentException(
        "Value '" + value + "' must be a finite number from " + min + " to " + max + ".");
  }

  public static boolean bool(String value) {
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "true", "on", "enable", "enabled" -> true;
      case "false", "off", "disable", "disabled" -> false;
      default -> throw new IllegalArgumentException("Value '" + value + "' must be on or off.");
    };
  }

  public static <T extends Enum<T>> T choice(Class<T> type, String value) {
    try {
      return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(
          "Expected "
              + type.getSimpleName()
              + ": "
              + java.util.Arrays.stream(type.getEnumConstants())
                  .limit(32)
                  .map(option -> option.name().toLowerCase(Locale.ROOT))
                  .collect(java.util.stream.Collectors.joining(", "))
              + (type.getEnumConstants().length > 32 ? " (more available with Tab)." : "."));
    }
  }
}
