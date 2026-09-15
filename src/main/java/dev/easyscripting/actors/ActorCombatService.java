package dev.easyscripting.actors;

import dev.easyscripting.config.Settings;
import dev.easyscripting.core.TickEngine;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.util.Vector;

/** Tick-owned supply use and fallible combat reactions; never synthesizes replacement items. */
public final class ActorCombatService implements Listener, AutoCloseable {
  private final ActorService actors;
  private final ActorGroupService groups;
  private final Settings settings;
  private final TickEngine ticks;
  private final Map<String, Reaction> reactions = new HashMap<>();
  private long tick;
  private UUID job;
  private boolean closing;

  private static final class Reaction {
    long refillAt, nextPotion, potionCooldown, shieldCheck, shieldRaise, shieldUntil, jumpAt;
    int potionsLeft;
    boolean blocking;
  }

  public ActorCombatService(
      ActorService actors, ActorGroupService groups, Settings settings, TickEngine ticks) {
    this.actors = actors;
    this.groups = groups;
    this.settings = settings;
    this.ticks = ticks;
    actors.onRemoved(id -> reactions.remove(id));
    actors.onSpawned(actor -> ensureRunning());
    ensureRunning();
  }

  private void ensureRunning() {
    if (closing || job != null || !ticks.acceptingWork()) return;
    job =
        ticks.add(
            new TickEngine.Job() {
              public boolean tick() {
                pulse();
                return !closing && !actors.ids().isEmpty();
              }

              public void stopped() {
                job = null;
              }
            });
  }

  private int reactionDelay() {
    var c = settings.actorCombat();
    return ThreadLocalRandom.current().nextInt(c.reactionMin(), c.reactionMax() + 1);
  }

  public int attackDelay() {
    return settings.actorAi().attackCooldown()
        + ThreadLocalRandom.current().nextInt(settings.actorCombat().attackJitter() + 1);
  }

  public void strike(ActorService.ManagedActor actor, LivingEntity target) {
    var entity = actor.requireEntity();
    entity.swingMainHand();
    if (ThreadLocalRandom.current().nextDouble() < settings.actorCombat().accuracy())
      entity.attack(target);
  }

  public void idle(ActorService.ManagedActor actor) {
    Reaction r = reactions.get(actor.id());
    if (r == null) return;
    r.potionsLeft = 0;
  }

  public boolean defending(ActorService.ManagedActor actor) {
    Reaction r = reactions.get(actor.id());
    return r != null && r.blocking;
  }

  private void pulse() {
    tick++;
    for (var actor : actors.list()) {
      LivingEntity entity = actor.entity().orElse(null);
      if (entity == null) continue;
      Reaction r = reactions.computeIfAbsent(actor.id(), key -> new Reaction());
      if (!settings.enabled("actors") || actors.busy(actor.id())) {
        if (r.blocking) entity.clearActiveItem();
        reactions.remove(actor.id());
        continue;
      }
      if (r.blocking && tick >= r.shieldUntil) {
        entity.clearActiveItem();
        r.blocking = false;
        r.refillAt = tick + settings.actorCombat().refillTicks();
      }
      if (Math.floorMod(tick + actor.id().hashCode(), 5) == 0
          && groups.intelligent(actor)
          && r.potionsLeft == 0
          && (r.shieldRaise > 0 || r.blocking || tick >= r.shieldCheck)) {
        // Threat sensing does not need a previous hit: a descending mace can be the first attack.
        LivingEntity overhead =
            entity.getNearbyEntities(4, 10, 4).stream()
                .filter(e -> e instanceof LivingEntity && !e.isDead() && !groups.allied(entity, e))
                .map(e -> (LivingEntity) e)
                .filter(
                    e ->
                        !(e instanceof Player p)
                            || (p.getGameMode() != GameMode.CREATIVE
                                && p.getGameMode() != GameMode.SPECTATOR
                                && !p.isInvisible()))
                .filter(e -> maceThreat(entity, e) && entity.hasLineOfSight(e))
                .min(
                    Comparator.comparingDouble(
                        e -> e.getLocation().distanceSquared(entity.getLocation())))
                .orElse(null);
        shield(actor, overhead, r);
      }
      if (settings.actorCombat().autoTotem()
          && !r.blocking
          && r.shieldRaise == 0
          && tick >= r.refillAt) {
        if (ActorSupplies.offhand(actor, Material.TOTEM_OF_UNDYING)) actors.save(actor);
        r.refillAt = tick + 10;
      }
      if (r.jumpAt > 0 && tick >= r.jumpAt) {
        r.jumpAt = 0;
        if (entity.isOnGround() && groups.intelligent(actor))
          entity.setVelocity(entity.getVelocity().setY(0.42));
      }
    }
  }

  /** Returns true while a timed item reaction owns the pose and movement. */
  public boolean prepare(ActorService.ManagedActor actor, LivingEntity target) {
    Reaction r = reactions.computeIfAbsent(actor.id(), key -> new Reaction());
    var entity = actor.requireEntity();
    var c = settings.actorCombat();
    if (!groups.intelligent(actor)) {
      r.potionsLeft = 0;
      r.shieldRaise = 0;
      return false;
    }
    if (r.potionsLeft > 0) {
      if (!c.potions()) r.potionsLeft = 0;
      else {
        actor.look(entity.getEyeLocation().add(0, 10, 0));
        if (tick >= r.nextPotion) {
          var items = ActorSupplies.inventory(actor);
          int slot = ActorSupplies.find(items, ActorCombatService::beneficialSplash);
          if (slot < 0) r.potionsLeft = 0;
          else {
            ItemStack potion = ActorSupplies.takeOne(items, slot);
            ActorSupplies.inventory(actor, items);
            // Real projectiles: normal splash effects, travel time and obstruction apply.
            entity.launchProjectile(
                SplashPotion.class, new Vector(0, 0.65, 0), spawned -> spawned.setItem(potion));
            entity.swingMainHand();
            r.potionsLeft--;
            r.nextPotion = tick + c.potionInterval();
            actors.save(actor);
          }
        }
        if (r.potionsLeft > 0 || tick < r.nextPotion) return true;
      }
    }
    return shield(actor, target, r);
  }

  private boolean shield(ActorService.ManagedActor actor, LivingEntity target, Reaction r) {
    var entity = actor.requireEntity();
    var c = settings.actorCombat();
    if (r.shieldRaise > 0) {
      if (tick < r.shieldRaise) return false;
      r.shieldRaise = 0;
      if (maceThreat(entity, target)) {
        ActorSupplies.offhand(actor, Material.SHIELD);
        if (entity.getEquipment() != null
            && entity.getEquipment().getItemInOffHand().getType() == Material.SHIELD) {
          actor.stop();
          actor.look(target.getEyeLocation());
          // Paper marks startUsingItem experimental; isolated here and covered by API compilation.
          entity.startUsingItem(EquipmentSlot.OFF_HAND);
          r.blocking = true;
          r.shieldUntil = tick + c.shieldHold();
          actors.save(actor);
        }
      }
    }
    if (r.blocking) {
      if (target != null) actor.look(target.getEyeLocation());
      return true;
    }
    if (tick >= r.shieldCheck) {
      r.shieldCheck = tick + c.shieldCheck();
      if (maceThreat(entity, target) && ThreadLocalRandom.current().nextDouble() < c.shieldChance())
        r.shieldRaise = tick + reactionDelay();
    }
    return false;
  }

  private static boolean maceThreat(LivingEntity actor, LivingEntity target) {
    if (target == null
        || target.getWorld() != actor.getWorld()
        || target.getEquipment() == null
        || target.getEquipment().getItemInMainHand().getType() != Material.MACE) return false;
    var delta = target.getLocation().toVector().subtract(actor.getLocation().toVector());
    return delta.getY() > 1.5
        && delta.getY() < 10
        && delta.getX() * delta.getX() + delta.getZ() * delta.getZ() < 16;
  }

  public static boolean beneficialSplash(ItemStack item) {
    if (item.getType() != Material.SPLASH_POTION
        || !(item.getItemMeta() instanceof PotionMeta meta)) return false;
    var effects = new ArrayList<>(meta.getCustomEffects());
    if (meta.getBasePotionType() != null)
      effects.addAll(meta.getBasePotionType().getPotionEffects());
    return !effects.isEmpty()
        && effects.stream().allMatch(effect -> beneficial(effect.getType().getKey().getKey()));
  }

  static boolean beneficial(String effect) {
    return Set.of(
            "instant_health",
            "regeneration",
            "strength",
            "speed",
            "fire_resistance",
            "resistance",
            "absorption",
            "health_boost",
            "jump_boost",
            "water_breathing",
            "night_vision",
            "invisibility",
            "slow_falling")
        .contains(effect);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void popped(EntityResurrectEvent event) {
    actors
        .byEntity(event.getEntity().getUniqueId())
        .ifPresent(
            actor -> {
              var r = reactions.computeIfAbsent(actor.id(), key -> new Reaction());
              r.refillAt = tick + settings.actorCombat().refillTicks();
            });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void hurt(EntityDamageByEntityEvent event) {
    if (!settings.enabled("actors") || event.getFinalDamage() <= 0) return;
    actors
        .byEntity(event.getEntity().getUniqueId())
        .ifPresent(
            actor -> {
              if (actors.busy(actor.id()) || !groups.intelligent(actor)) return;
              Reaction r = reactions.computeIfAbsent(actor.id(), key -> new Reaction());
              var c = settings.actorCombat();
              if (c.potions() && tick >= r.potionCooldown && r.potionsLeft == 0) {
                r.potionsLeft = c.potionCount();
                r.nextPotion = tick + reactionDelay();
                r.potionCooldown = tick + c.potionCooldown();
              }
              if (r.jumpAt == 0 && ThreadLocalRandom.current().nextDouble() < c.jumpChance())
                r.jumpAt = tick + reactionDelay();
            });
  }

  @Override
  public void close() {
    closing = true;
    if (job != null) ticks.cancel(job);
    for (var actor : actors.list()) {
      Reaction r = reactions.get(actor.id());
      if (r != null && r.blocking) actor.entity().ifPresent(LivingEntity::clearActiveItem);
    }
    reactions.clear();
  }
}
