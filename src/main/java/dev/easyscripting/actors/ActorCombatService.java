package dev.easyscripting.actors;

import dev.easyscripting.config.Settings;
import dev.easyscripting.core.TickEngine;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
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

  /** A landing ender pearl hurts whoever threw it; vanilla applies this much to the thrower. */
  private static final double PEARL_DAMAGE = 5;

  /** Vanilla golden apples take 32 ticks to eat; the slack absorbs a late server tick. */
  private static final int EAT_TICKS = 34;

  /**
   * The box a smash can fall from: at least this far above the NPC's feet, no higher than a fall
   * it would survive aiming, and within a horizontal radius the smash could still cover.
   */
  private static final double OVERHEAD_MINIMUM = 1.5, OVERHEAD_HEIGHT = 10, OVERHEAD_RADIUS = 4;

  private static final class Reaction {
    long refillAt, nextPotion, potionCooldown, shieldCheck, shieldRaise, shieldUntil, jumpAt;
    long blockingSince, healAt, healBy, healUntil, healCooldown;
    long escapeAt, escapeCooldown, retreatUntil, strafeAt;
    int potionsLeft;
    boolean blocking;
    /** The weapon set aside while the main hand is holding a golden apple; null when not eating. */
    ItemStack stowed;

    int stowedSlot = -1;
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

  /**
   * A vanilla attack made before the held weapon finishes recharging deals a fraction of its
   * damage. Waiting for full charge is what makes an NPC with a real sword hit like one.
   */
  public boolean charged(LivingEntity entity) {
    return !settings.actorCombat().weaponCooldown()
        || !(entity instanceof HumanEntity human)
        || human.getAttackCooldown() >= 0.92f;
  }

  /**
   * Roll for a hop before a ready swing. The caller strikes a few ticks later, while the NPC is
   * still falling, which is exactly the condition Minecraft scores as a critical hit.
   */
  public boolean critJump(ActorService.ManagedActor actor) {
    var entity = actor.requireEntity();
    if (!groups.intelligent(actor)
        || !entity.isOnGround()
        || ThreadLocalRandom.current().nextDouble() >= settings.actorCombat().critJumpChance())
      return false;
    entity.setVelocity(entity.getVelocity().setY(0.42));
    return true;
  }

  /**
   * The chance a swing from this far away connects. Close in an NPC is as accurate as
   * `combat.accuracy` allows; at the very edge of `groups.melee-reach` it is only as accurate as
   * `combat.reach-accuracy`, falling off linearly between the two. Without this an NPC lands
   * every single blow at exactly its maximum reach, which no player can do.
   */
  public static double hitChance(double distance, double reach, double accuracy, double atReach) {
    double comfortable = reach / 2;
    if (!(reach > comfortable) || distance <= comfortable) return accuracy;
    double far = Math.min(1, (distance - comfortable) / (reach - comfortable));
    return accuracy + (atReach - accuracy) * far;
  }

  /** Roll this NPC's accuracy for a swing at the given distance, using the configured falloff. */
  public boolean connects(double distance) {
    var c = settings.actorCombat();
    return ThreadLocalRandom.current().nextDouble()
        < hitChance(distance, settings.actorAi().meleeReach(), c.accuracy(), c.reachAccuracy());
  }

  /** Swing, and deal damage only when the accuracy roll for this distance succeeds. */
  public void strike(ActorService.ManagedActor actor, LivingEntity target, double distance) {
    var entity = actor.requireEntity();
    entity.swingMainHand();
    if (connects(distance)) entity.attack(target);
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

  /** True while a timed reaction owns this NPC's hands, so ambient behavior must yield to it. */
  public boolean busy(ActorService.ManagedActor actor) {
    Reaction r = reactions.get(actor.id());
    return r != null
        && (r.blocking || r.stowed != null || r.potionsLeft > 0 || tick < r.retreatUntil);
  }

  /**
   * True while the NPC wants distance from its enemy rather than a place next to it. A meal and a
   * raised shield both pin it in place, so neither one walks while the gap is still open.
   */
  public boolean retreating(ActorService.ManagedActor actor) {
    Reaction r = reactions.get(actor.id());
    return r != null && r.stowed == null && !r.blocking && tick < r.retreatUntil;
  }

  /**
   * Roll a sidestep for an NPC that is in reach and waiting on its weapon. Standing perfectly
   * still between swings is the clearest tell that something is not a player.
   */
  public boolean strafe(ActorService.ManagedActor actor) {
    Reaction r = reactions.get(actor.id());
    var c = settings.actorCombat();
    if (r == null || tick < r.strafeAt || !groups.intelligent(actor)) return false;
    r.strafeAt = tick + c.strafeInterval();
    return ThreadLocalRandom.current().nextDouble() < c.strafeChance();
  }

  private void pulse() {
    tick++;
    var c = settings.actorCombat();
    for (var actor : actors.list()) {
      LivingEntity entity = actor.entity().orElse(null);
      if (entity == null) continue;
      Reaction r = reactions.computeIfAbsent(actor.id(), key -> new Reaction());
      if (!settings.enabled("actors") || actors.busy(actor.id())) {
        if (r.blocking) entity.clearActiveItem();
        abandonMeal(actor, entity, r);
        reactions.remove(actor.id());
        continue;
      }
      // Both hands are busy during a meal: no shield, no totem swap, no potion.
      if (r.stowed != null) {
        if (tick >= r.healUntil) finishEating(actor, entity, r);
        continue;
      }
      // A fight that ends at two hearts still needs patching up, and no enemy means no gap to
      // open first, so the meal starts as soon as the reaction delay passes.
      if (Math.floorMod(tick + actor.id().hashCode(), 10) == 0
          && !r.blocking
          && r.potionsLeft == 0
          && c.healHealth() > 0
          && tick >= r.healCooldown
          && groups.intelligent(actor)
          && healthFraction(entity) <= c.healHealth()) eat(actor, entity, null, r);
      boolean scan =
          Math.floorMod(tick + actor.id().hashCode(), 5) == 0
              && groups.intelligent(actor)
              && r.potionsLeft == 0
              && (r.shieldRaise > 0 || r.blocking || tick >= r.shieldCheck);
      LivingEntity mace = scan || (r.blocking && tick >= r.shieldUntil) ? maceHolder(entity) : null;
      if (r.blocking && tick >= r.shieldUntil) {
        // Keep the guard up while the mace is still a threat, but never block forever.
        if (mace != null && tick - r.blockingSince < c.shieldMaxHold()) r.shieldUntil = tick + 5;
        else {
          entity.clearActiveItem();
          r.blocking = false;
          r.refillAt = tick + c.refillTicks();
        }
      }
      if (scan) shield(actor, mace, r);
      if (c.autoTotem() && !r.blocking && r.shieldRaise == 0 && tick >= r.refillAt) {
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
      r.retreatUntil = 0;
      abandonMeal(actor, entity, r);
      return false;
    }
    // Running and eating come before trading blows; a raised shield still beats reaching for food.
    if (escape(actor, entity, target, r)) return true;
    if (!r.blocking && eat(actor, entity, target, r)) return true;
    if (r.potionsLeft > 0) {
      if (!c.potions()) r.potionsLeft = 0;
      else {
        actor.look(entity.getEyeLocation().add(0, 10, 0));
        if (tick >= r.nextPotion) {
          var items = ActorSupplies.inventory(actor);
          // Re-checked before every throw: the first one may already have supplied the effect.
          int slot = ActorSupplies.find(items, item -> useful(entity, item));
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

  private static double maximum(LivingEntity entity) {
    var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
    return attribute == null ? 20 : attribute.getValue();
  }

  /** Remaining health as a fraction of this NPC's own maximum, which kits and mobs both change. */
  private static double healthFraction(LivingEntity entity) {
    double max = maximum(entity);
    return max <= 0 ? 1 : Math.min(1, entity.getHealth() / max);
  }

  /**
   * Throw a carried ender pearl away from the fight. Vanilla teleports the thrower when it lands
   * and hurts them, so an NPC that would die to its own pearl keeps fighting rather than killing
   * itself escaping. Only PLAYER actors are teleported; a mob actor throws the pearl and stays.
   */
  private boolean escape(
      ActorService.ManagedActor actor, LivingEntity entity, LivingEntity target, Reaction r) {
    var c = settings.actorCombat();
    if (r.escapeAt > 0) {
      if (tick < r.escapeAt) return true; // Reacting; the caller keeps it backing away meanwhile.
      r.escapeAt = 0;
      var items = ActorSupplies.inventory(actor);
      int slot = ActorSupplies.find(items, item -> item.getType() == Material.ENDER_PEARL);
      if (slot < 0) return false;
      ItemStack pearl = ActorSupplies.takeOne(items, slot);
      ActorSupplies.inventory(actor, items);
      entity.launchProjectile(
          EnderPearl.class, away(entity, target).multiply(0.9).setY(0.5),
          spawned -> spawned.setItem(pearl));
      entity.swingMainHand();
      actors.save(actor);
      r.retreatUntil = tick + c.retreatTicks();
      return true;
    }
    if (c.escapeHealth() <= 0
        || target == null
        || tick < r.escapeCooldown
        || r.stowed != null
        || healthFraction(entity) > c.escapeHealth()
        || entity.getHealth() <= PEARL_DAMAGE + 1
        || !carries(actor, Material.ENDER_PEARL)) return false;
    r.escapeAt = tick + reactionDelay();
    r.escapeCooldown = tick + c.escapeCooldown();
    r.retreatUntil = tick + c.retreatTicks();
    return true;
  }

  /**
   * Back off, then eat a carried golden apple. The meal only starts once the NPC has opened a gap
   * or run out of room to open one, and Paper's own consumption applies the apple's effects, so
   * nothing here invents healing that the item would not have given a player.
   */
  private boolean eat(
      ActorService.ManagedActor actor, LivingEntity entity, LivingEntity target, Reaction r) {
    var c = settings.actorCombat();
    if (r.stowed != null) {
      if (target != null) actor.look(target.getEyeLocation());
      if (tick >= r.healUntil) finishEating(actor, entity, r);
      return true;
    }
    if (r.healAt > 0) {
      // A cornered NPC that cannot open a gap eats anyway rather than backing into a wall forever.
      if (tick < r.healAt || !(clear(entity, target, c.retreatDistance()) || tick >= r.healBy))
        return true;
      r.healAt = 0;
      return startEating(actor, entity, r);
    }
    if (c.healHealth() <= 0
        || tick < r.healCooldown
        || healthFraction(entity) > c.healHealth()
        || !carries(actor, Material.GOLDEN_APPLE, Material.ENCHANTED_GOLDEN_APPLE)) return false;
    r.healAt = tick + reactionDelay();
    r.healBy = tick + c.retreatTicks();
    r.healCooldown = tick + c.healCooldown();
    // Open the gap first; a player does not stand inside an axe's reach to eat either.
    if (target != null) r.retreatUntil = tick + c.retreatTicks();
    return true;
  }

  /**
   * Move a carried apple into the main hand and stow the weapon in the slot it came out of. A
   * PLAYER actor's held slot is part of its own backpack, so the apple is never taken from the
   * hand slot itself; an NPC already holding one simply eats it where it is.
   */
  private boolean startEating(ActorService.ManagedActor actor, LivingEntity entity, Reaction r) {
    var equipment = entity.getEquipment();
    if (equipment == null) return false;
    if (goldenApple(equipment.getItemInMainHand())) {
      r.stowed = new ItemStack(Material.AIR);
      r.stowedSlot = -1; // Nothing was moved, so nothing has to be moved back.
    } else {
      var items = ActorSupplies.inventory(actor);
      int held = entity instanceof Player player ? player.getInventory().getHeldItemSlot() : -1;
      int slot = -1;
      for (int i = 0; i < items.length && slot < 0; i++)
        if (i != held && items[i] != null && goldenApple(items[i])) slot = i;
      if (slot < 0) return false;
      ItemStack apple = items[slot];
      ItemStack weapon = equipment.getItemInMainHand();
      items[slot] = weapon.getType().isAir() ? null : weapon;
      ActorSupplies.inventory(actor, items);
      equipment.setItemInMainHand(apple);
      r.stowed = weapon;
      r.stowedSlot = slot;
    }
    r.healUntil = tick + EAT_TICKS;
    actor.stop();
    // Paper marks startUsingItem experimental; isolated here and covered by API compilation.
    entity.startUsingItem(EquipmentSlot.HAND);
    actors.save(actor);
    return true;
  }

  /**
   * Hand the consumption to Paper so the apple's own effects apply exactly as they would for a
   * player. If nothing was actually eaten — an entity whose item use Paper does not simulate —
   * the apple comes back untouched rather than being destroyed or traded for invented effects.
   */
  private void finishEating(ActorService.ManagedActor actor, LivingEntity entity, Reaction r) {
    if (entity.hasActiveItem()) entity.completeUsingActiveItem();
    unstow(actor, entity, r);
  }

  /** Put the weapon back without finishing the meal: a reload, a hit, a recording or shutdown. */
  private void abandonMeal(ActorService.ManagedActor actor, LivingEntity entity, Reaction r) {
    if (r.stowed == null) return;
    entity.clearActiveItem();
    unstow(actor, entity, r);
  }

  /**
   * Undo the swap. The backpack is written before the hand, so the slot holding the weapon is
   * overwritten by whatever is left of the apples and the weapon exists in exactly one place.
   */
  private void unstow(ActorService.ManagedActor actor, LivingEntity entity, Reaction r) {
    var equipment = entity.getEquipment();
    if (equipment != null && r.stowedSlot >= 0) {
      ItemStack leftover = equipment.getItemInMainHand();
      var items = ActorSupplies.inventory(actor);
      if (r.stowedSlot < items.length)
        items[r.stowedSlot] =
            leftover.getType().isAir() || leftover.getAmount() < 1 ? null : leftover;
      ActorSupplies.inventory(actor, items);
      equipment.setItemInMainHand(r.stowed);
    }
    r.stowed = null;
    r.stowedSlot = -1;
    r.healUntil = 0;
    actors.save(actor);
  }

  private static boolean goldenApple(ItemStack item) {
    return item.getType() == Material.ENCHANTED_GOLDEN_APPLE
        || item.getType() == Material.GOLDEN_APPLE;
  }

  private static boolean carries(ActorService.ManagedActor actor, Material... materials) {
    var wanted = Set.of(materials);
    var items = ActorSupplies.inventory(actor);
    return ActorSupplies.find(items, item -> wanted.contains(item.getType())) >= 0;
  }

  /** True once the NPC has opened the requested gap, or has no enemy left to open one from. */
  private static boolean clear(LivingEntity entity, LivingEntity target, double distance) {
    return target == null
        || target.isDead()
        || target.getWorld() != entity.getWorld()
        || entity.getLocation().distanceSquared(target.getLocation()) >= distance * distance;
  }

  /** A horizontal unit vector pointing from the threat toward the NPC. */
  static Vector away(LivingEntity entity, LivingEntity target) {
    Vector delta =
        target == null || target.getWorld() != entity.getWorld()
            ? new Vector()
            : entity.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
    if (delta.lengthSquared() < 0.000001) {
      double angle = Math.toRadians(entity.getLocation().getYaw());
      return new Vector(-Math.sin(angle), 0, Math.cos(angle));
    }
    return delta.normalize();
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
          r.blockingSince = tick;
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

  /** The nearest visible non-allied mace holder overhead, the one a shield actually helps with. */
  private LivingEntity maceHolder(LivingEntity entity) {
    // Threat sensing does not need a previous hit: a descending mace can be the first attack.
    return entity.getNearbyEntities(OVERHEAD_RADIUS, OVERHEAD_HEIGHT, OVERHEAD_RADIUS).stream()
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
            Comparator.comparingDouble(e -> e.getLocation().distanceSquared(entity.getLocation())))
        .orElse(null);
  }


  /**
   * A mace is a threat wherever it is carried, not only where it is held. Players swap a mace in
   * for the hit itself — attribute swapping — so reacting only to a held mace means reacting
   * after the smash has already landed. `shield-inventory-mace` turns the backpack check off.
   */
  private boolean carriesMace(LivingEntity entity) {
    var equipment = entity.getEquipment();
    if (equipment != null
        && (equipment.getItemInMainHand().getType() == Material.MACE
            || equipment.getItemInOffHand().getType() == Material.MACE)) return true;
    return settings.actorCombat().shieldInventoryMace()
        && entity instanceof HumanEntity human
        && human.getInventory().contains(Material.MACE);
  }

  /**
   * Only a mace held overhead is a mace threat. The falling smash is the attack a shield is worth
   * raising against; a mace carried at the NPC's own level is an ordinary melee weapon, and
   * turtling against one leaves the NPC standing behind its shield through a normal ground fight.
   */
  private boolean maceThreat(LivingEntity actor, LivingEntity target) {
    if (target == null || target.getWorld() != actor.getWorld() || !carriesMace(target))
      return false;
    var delta = target.getLocation().toVector().subtract(actor.getLocation().toVector());
    double flat = delta.getX() * delta.getX() + delta.getZ() * delta.getZ();
    return delta.getY() > OVERHEAD_MINIMUM
        && delta.getY() < OVERHEAD_HEIGHT
        && flat < OVERHEAD_RADIUS * OVERHEAD_RADIUS;
  }

  public static boolean beneficialSplash(ItemStack item) {
    if (item.getType() != Material.SPLASH_POTION
        || !(item.getItemMeta() instanceof PotionMeta meta)) return false;
    var effects = effects(meta);
    return !effects.isEmpty()
        && effects.stream().allMatch(effect -> beneficial(effect.getType().getKey().getKey()));
  }

  private static List<PotionEffect> effects(PotionMeta meta) {
    var effects = new ArrayList<>(meta.getCustomEffects());
    if (meta.getBasePotionType() != null)
      effects.addAll(meta.getBasePotionType().getPotionEffects());
    return effects;
  }

  /**
   * A splash potion is only worth throwing when the NPC is actually missing what it would give:
   * the effect ran out, was cleared, or is weaker than the one in the bottle. Healing counts as
   * missing whenever the NPC is hurt. This is what stops an NPC re-dosing an effect it still has.
   */
  static boolean useful(LivingEntity entity, ItemStack item) {
    if (!beneficialSplash(item) || !(item.getItemMeta() instanceof PotionMeta meta)) return false;
    for (PotionEffect effect : effects(meta)) {
      if (effect.getType().getKey().getKey().equals("instant_health")) {
        if (entity.getHealth() < maximum(entity)) return true;
        continue;
      }
      PotionEffect active = entity.getPotionEffect(effect.getType());
      if (active == null || active.getAmplifier() < effect.getAmplifier()) return true;
    }
    return false;
  }

  private boolean carriesUseful(ActorService.ManagedActor actor) {
    LivingEntity entity = actor.entity().orElse(null);
    return entity != null
        && ActorSupplies.find(ActorSupplies.inventory(actor), item -> useful(entity, item)) >= 0;
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
              // A hit taken mid-meal interrupts it, exactly as it does for a player.
              if (r.stowed != null)
                actor.entity().ifPresent(entity -> abandonMeal(actor, entity, r));
              if (c.potions()
                  && tick >= r.potionCooldown
                  && r.potionsLeft == 0
                  && carriesUseful(actor)) {
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
      if (r == null) continue;
      if (r.blocking) actor.entity().ifPresent(LivingEntity::clearActiveItem);
      actor.entity().ifPresent(entity -> abandonMeal(actor, entity, r));
    }
    reactions.clear();
  }
}
