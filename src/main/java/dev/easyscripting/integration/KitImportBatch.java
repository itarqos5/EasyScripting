package dev.easyscripting.integration;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** One item definition per step; failure in one provider kit does not discard earlier imports. */
public final class KitImportBatch {
  private final List<String> names;
  private final List<String> errors = new ArrayList<>();
  private int processed, imported;
  private boolean stopped;

  public KitImportBatch(List<String> names) {
    this.names = List.copyOf(names);
  }

  public boolean step(BooleanSupplier allowed, Consumer<String> importer) {
    if (stopped) return false;
    if (!allowed.getAsBoolean() || processed == names.size()) {
      stopped = true;
      return false;
    }
    String name = names.get(processed++);
    try {
      importer.accept(name);
      imported++;
    } catch (RuntimeException ex) {
      if (errors.size() < 5) {
        String message = name + ": " + ex.getMessage();
        errors.add(message.substring(0, Math.min(240, message.length())));
      }
    }
    stopped = processed == names.size();
    return !stopped;
  }

  public int total() {
    return names.size();
  }

  public int processed() {
    return processed;
  }

  public int imported() {
    return imported;
  }

  public int failed() {
    return processed - imported;
  }

  public List<String> errors() {
    return List.copyOf(errors);
  }

  public static String destination(String source, String name, Predicate<String> exists) {
    String base = (source + "_" + name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    if (base.isEmpty() || !Character.isLetterOrDigit(base.charAt(0))) base = "kit_" + base;
    base = base.substring(0, Math.min(40, base.length()));
    String destination = base;
    for (int suffix = 2; exists.test(destination); suffix++) {
      if (suffix > 999999)
        throw new IllegalArgumentException("Too many kits share this ID prefix.");
      destination = base + "_" + suffix;
    }
    return destination;
  }
}
