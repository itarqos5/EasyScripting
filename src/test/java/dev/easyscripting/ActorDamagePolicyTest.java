package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.actors.ActorDamagePolicy;
import io.papermc.paper.event.entity.EntityKnockbackEvent.Cause;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;

class ActorDamagePolicyTest {
  @Test
  void unhittableBlocksMeleeAndSweepOnly() {
    assertTrue(ActorDamagePolicy.blocksDamage(false, DamageCause.ENTITY_ATTACK, false));
    assertTrue(ActorDamagePolicy.blocksDamage(false, DamageCause.ENTITY_SWEEP_ATTACK, false));
    for (var cause :
        new DamageCause[] {
          DamageCause.FALL,
          DamageCause.PROJECTILE,
          DamageCause.ENTITY_EXPLOSION,
          DamageCause.BLOCK_EXPLOSION,
          DamageCause.WITHER,
          DamageCause.MAGIC,
          DamageCause.FIRE_TICK,
          DamageCause.FALLING_BLOCK
        }) assertFalse(ActorDamagePolicy.blocksDamage(false, cause, false), cause.name());
  }

  @Test
  void projectileIsAllowedEvenWithGenericAttackCause() {
    assertFalse(ActorDamagePolicy.blocksDamage(false, DamageCause.ENTITY_ATTACK, true));
    assertFalse(ActorDamagePolicy.blocksKnockback(false, Cause.ENTITY_ATTACK, true));
  }

  @Test
  void hittableAllowsAllDamageCauses() {
    for (var cause : DamageCause.values())
      assertFalse(ActorDamagePolicy.blocksDamage(true, cause, false));
  }

  @Test
  void knockbackBlocksMeleeButKeepsExplosionsAndPhysics() {
    assertTrue(ActorDamagePolicy.blocksKnockback(false, Cause.ENTITY_ATTACK, false));
    assertTrue(ActorDamagePolicy.blocksKnockback(false, Cause.SWEEP_ATTACK, false));
    assertFalse(ActorDamagePolicy.blocksKnockback(false, Cause.EXPLOSION, false));
    assertFalse(ActorDamagePolicy.blocksKnockback(false, Cause.DAMAGE, false));
    assertFalse(ActorDamagePolicy.blocksKnockback(false, Cause.PUSH, false));
    for (var cause : Cause.values())
      assertFalse(ActorDamagePolicy.blocksKnockback(true, cause, false));
  }
}
