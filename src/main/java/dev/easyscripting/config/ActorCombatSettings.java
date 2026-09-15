package dev.easyscripting.config;

import static dev.easyscripting.config.ActorAiSettings.*;

import org.bukkit.configuration.file.YamlConfiguration;

public record ActorCombatSettings(
    boolean autoTotem,
    int refillTicks,
    double accuracy,
    int reactionMin,
    int reactionMax,
    int attackJitter,
    double jumpChance,
    boolean potions,
    int potionCount,
    int potionInterval,
    int potionCooldown,
    double shieldChance,
    int shieldCheck,
    int shieldHold) {
  public static ActorCombatSettings read(YamlConfiguration y) {
    int min = integer(y, "combat.reaction-min-ticks", 1, 40);
    int max = integer(y, "combat.reaction-max-ticks", min, 60);
    return new ActorCombatSettings(
        bool(y, "combat.auto-totem"),
        integer(y, "combat.totem-refill-ticks", 1, 20),
        number(y, "combat.accuracy", 0, 1),
        min,
        max,
        integer(y, "combat.attack-jitter-ticks", 0, 20),
        number(y, "combat.jump-reset-chance", 0, 1),
        bool(y, "combat.potions"),
        integer(y, "combat.potion-count", 1, 3),
        integer(y, "combat.potion-interval-ticks", 2, 40),
        integer(y, "combat.potion-cooldown-ticks", 20, 2400),
        number(y, "combat.shield-chance", 0, 1),
        integer(y, "combat.shield-check-ticks", 10, 100),
        integer(y, "combat.shield-hold-ticks", 5, 60));
  }

  private static boolean bool(YamlConfiguration y, String key) {
    if (!(y.get(key) instanceof Boolean value))
      throw new IllegalArgumentException("actor-ai.yml: " + key + " must be true or false.");
    return value;
  }
}
