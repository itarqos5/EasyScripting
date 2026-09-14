package dev.easyscripting.items;

import java.util.function.Predicate;

/** Accepts kit-first and recipient-first syntax without guessing when both are kit IDs. */
public record KitClaimRequest(String kit, String target) {
  public static KitClaimRequest parse(String[] args, Predicate<String> exists) {
    if (args.length < 1 || args.length > 2)
      throw new IllegalArgumentException(
          "Use /es kits claim <kit> [player:name|*|actor:id], or claim <player> <kit>.");
    if (args.length == 1) {
      if (!exists.test(args[0])) throw new IllegalArgumentException("Unknown kit: " + args[0]);
      return new KitClaimRequest(args[0], null);
    }
    boolean first = exists.test(args[0]), second = exists.test(args[1]);
    if (first && second)
      throw new IllegalArgumentException(
          "Both names are kit IDs. Use claim <kit> player:<name> or actor:<id>.");
    if (first) return new KitClaimRequest(args[0], args[1]);
    if (second) return new KitClaimRequest(args[1], args[0]);
    throw new IllegalArgumentException("No matching kit. Use /es kits to see available kits.");
  }
}
