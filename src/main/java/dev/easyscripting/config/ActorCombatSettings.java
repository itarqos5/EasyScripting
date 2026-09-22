package dev.easyscripting.config;

import static dev.easyscripting.config.ActorAiSettings.*;

import org.bukkit.configuration.file.YamlConfiguration;

public record ActorCombatSettings(
    boolean autoTotem,
    int refillTicks,
    double accuracy,
    double reachAccuracy,
    int reactionMin,
    int reactionMax,
    int attackJitter,
    boolean weaponCooldown,
    double critJumpChance,
    int critJumpDelay,
    double jumpChance,
    boolean potions,
    int potionCount,
    int potionInterval,
    int potionCooldown,
    double shieldChance,
    int shieldCheck,
    int shieldHold,
    int shieldMaxHold,
    boolean shieldInventoryMace,
    double healHealth,
    int healCooldown,
    double escapeHealth,
    int escapeCooldown,
    double retreatDistance,
    int retreatTicks,
    double strafeChance,
    int strafeInterval) {
  public static ActorCombatSettings read(YamlConfiguration y) {
    int min = integer(y, "combat.reaction-min-ticks", 1, 40);
    int max = integer(y, "combat.reaction-max-ticks", min, 60);
    int hold = integer(y, "combat.shield-hold-ticks", 5, 60);
    double heal = number(y, "survival.heal-health", 0, 1);
    double escape = number(y, "survival.escape-health", 0, 1);
    // An NPC gaps before it runs, so the escape threshold cannot be the higher of the two.
    if (heal > 0 && escape > heal)
      throw new IllegalArgumentException(
          "actor-ai.yml: survival.escape-health must not be above survival.heal-health.");
    return new ActorCombatSettings(
        bool(y, "combat.auto-totem"),
        integer(y, "combat.totem-refill-ticks", 1, 20),
        number(y, "combat.accuracy", 0, 1),
        number(y, "combat.reach-accuracy", 0, 1),
        min,
        max,
        integer(y, "combat.attack-jitter-ticks", 0, 20),
        bool(y, "combat.weapon-cooldown"),
        number(y, "combat.crit-jump-chance", 0, 1),
        integer(y, "combat.crit-jump-delay-ticks", 4, 12),
        number(y, "combat.jump-reset-chance", 0, 1),
        bool(y, "combat.potions"),
        integer(y, "combat.potion-count", 1, 3),
        integer(y, "combat.potion-interval-ticks", 2, 40),
        integer(y, "combat.potion-cooldown-ticks", 20, 2400),
        number(y, "combat.shield-chance", 0, 1),
        integer(y, "combat.shield-check-ticks", 10, 100),
        hold,
        Math.max(hold, integer(y, "combat.shield-max-hold-ticks", 20, 200)),
        bool(y, "combat.shield-inventory-mace"),
        heal,
        integer(y, "survival.heal-cooldown-ticks", 40, 2400),
        escape,
        integer(y, "survival.escape-cooldown-ticks", 40, 2400),
        number(y, "survival.retreat-distance", 3, 24),
        integer(y, "survival.retreat-ticks", 10, 200),
        number(y, "survival.strafe-chance", 0, 1),
        integer(y, "survival.strafe-interval-ticks", 5, 60));
  }

  private static boolean bool(YamlConfiguration y, String key) {
    if (!(y.get(key) instanceof Boolean value))
      throw new IllegalArgumentException("actor-ai.yml: " + key + " must be true or false.");
    return value;
  }
}
