package dev.easyscripting.actors;

import java.util.function.DoubleUnaryOperator;

/** Keep a positive native hit below the health floor, including at half a heart. */
public final class ImmortalDamage {
  private ImmortalDamage() {}

  public static double floor(double maximum) {
    return Math.min(1, maximum / 2);
  }

  public static double healthBeforeHit(double health, double maximum) {
    return health <= floor(maximum) ? Math.min(maximum, floor(maximum) + 1) : health;
  }

  /** The callback applies raw damage and returns the recalculated final damage. */
  public static void limit(double raw, double allowed, DoubleUnaryOperator apply) {
    double finalDamage = apply.applyAsDouble(Math.min(raw, allowed));
    for (int i = 0; finalDamage > allowed && i < 40; i++) {
      raw = Math.min(raw, allowed) / 2;
      finalDamage = apply.applyAsDouble(raw);
    }
    if (finalDamage > allowed) apply.applyAsDouble(0);
  }
}
