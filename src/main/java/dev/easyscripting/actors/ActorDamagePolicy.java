package dev.easyscripting.actors;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

/** Hittable governs melee only; projectiles and environmental damage use normal combat rules. */
public final class ActorDamagePolicy {
  private ActorDamagePolicy() {}

  public static boolean blocksDamage(boolean hittable, DamageCause cause, boolean projectile) {
    return !hittable
        && !projectile
        && (cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK);
  }

  public static boolean blocksKnockback(
      boolean hittable, EntityKnockbackEvent.Cause cause, boolean projectile) {
    return !hittable
        && !projectile
        && (cause == EntityKnockbackEvent.Cause.ENTITY_ATTACK
            || cause == EntityKnockbackEvent.Cause.SWEEP_ATTACK);
  }
}
