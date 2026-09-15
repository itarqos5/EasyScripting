package dev.easyscripting.actors;

import dev.easyscripting.config.*;
import dev.easyscripting.core.*;
import dev.easyscripting.storage.YamlStore;
import java.util.*;
import java.util.function.Function;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main-thread group simulation. Equipment, damage and deaths remain owned by individual entities.
 */
public final class ActorGroupService implements Listener, AutoCloseable {
  private final JavaPlugin plugin;
  private final ActorService actors;
  private final Settings settings;
  private final TickEngine ticks;
  private final YamlStore store;
  private final Function<Player, Optional<String>> acting;
  private final Map<String, ActorGroup> groups = new TreeMap<>();
  private final Map<String, Brain> brains = new HashMap<>();
  private final Map<String, UUID> assignments = new HashMap<>();
  private final Map<String, UUID> soloTargets = new HashMap<>();
  private ActorCombatService combat;

  public void combat(ActorCombatService combat) {
    this.combat = combat;
  }

  public boolean intelligent(ActorService.ManagedActor actor) {
    ActorGroup group = settings.actorAi().groupsEnabled() ? groups.get(actor.group()) : null;
    return enabled() && (group == null ? actor.definition.aggressive : group.intelligence);
  }

  private List<ActorService.ManagedActor> roster = List.of();
  private Map<String, List<ActorService.ManagedActor>> members = Map.of();
  private UUID job;
  private long tick, refreshAt;
  private int cursor;
  private boolean closing;

  private static final class Brain {
    String group;
    Location lastGoal;
    long nextPath, nextAttack, pauseUntil;
    boolean moving;
  }

  public ActorGroupService(
      JavaPlugin plugin,
      ActorService actors,
      Settings settings,
      TickEngine ticks,
      YamlStore store,
      Function<Player, Optional<String>> acting) {
    this.plugin = plugin;
    this.actors = actors;
    this.settings = settings;
    this.ticks = ticks;
    this.store = store;
    this.acting = acting;
    actors.autonomousDirector(
        a ->
            enabled()
                && ((a.definition.aggressive
                        && (soloTargets.containsKey(a.id())
                            || (combat != null && combat.defending(a))))
                    || (settings.actorAi().groupsEnabled() && groups.containsKey(a.group()))));
    actors.onSpawned(
        a -> {
          refreshAt = 0;
          ensureRunning();
        });
    actors.onRemoved(
        id -> {
          brains.remove(id);
          assignments.remove(id);
          soloTargets.remove(id);
          refreshAt = 0;
        });
  }

  public void load() {
    store
        .load("groups")
        .forEach(
            (id, y) -> {
              try {
                ActorGroup group = ActorGroup.read(id, y);
                if (group.leader != null
                    && groups.values().stream().anyMatch(g -> group.leader.equals(g.leader)))
                  throw new IllegalArgumentException("Leader already belongs to another group.");
                groups.put(id, group);
              } catch (RuntimeException ex) {
                plugin.getLogger().warning("groups/" + id + ".yml: " + ex.getMessage());
              }
            });
    ensureRunning();
  }

  public boolean enabled() {
    return !closing && settings.enabled("actors");
  }

  public List<String> ids() {
    return List.copyOf(groups.keySet());
  }

  public ActorGroup get(String id) {
    ActorGroup group = groups.get(id);
    if (group == null)
      throw new IllegalArgumentException(
          "Group '" + id + "' does not exist. Use /es group create " + id + ".");
    return group;
  }

  public List<ActorService.ManagedActor> members(String id) {
    get(id);
    return actors.list().stream().filter(a -> a.group().equals(id)).toList();
  }

  public List<String> visible(org.bukkit.command.CommandSender sender) {
    UUID player = sender instanceof Player p ? p.getUniqueId() : null;
    return groups.values().stream()
        .filter(g -> g.canOrder(sender.isOp(), player))
        .map(g -> g.id)
        .toList();
  }

  public void requireOrder(org.bukkit.command.CommandSender sender, String id) {
    if (!get(id).canOrder(sender.isOp(), sender instanceof Player p ? p.getUniqueId() : null))
      throw new IllegalArgumentException("Only this group's leader or an operator can order it.");
  }

  public static void requireManager(org.bukkit.command.CommandSender sender) {
    if (!sender.isOp()) throw new IllegalArgumentException("Only operators can manage NPC groups.");
  }

  public void create(String id) {
    if (!enabled() || !settings.actorAi().groupsEnabled())
      throw new IllegalArgumentException(
          "NPC groups are disabled in actor-ai.yml or features.yml.");
    ActorGroup group = new ActorGroup(id);
    if (groups.containsKey(id)) throw new IllegalArgumentException("Group already exists: " + id);
    if (groups.size() >= settings.actorAi().maxGroups())
      throw new IllegalArgumentException("Group limit reached.");
    groups.put(id, group);
    save(group);
    ensureRunning();
    refreshAt = 0;
  }

  public void delete(String id) {
    ActorGroup group = get(id);
    halt(group, true);
    for (var actor : members(id)) {
      actor.definition.group = "default";
      actors.save(actor);
    }
    groups.remove(id);
    store.delete("groups", id);
    groups.values().forEach(g -> g.enemies.remove(id));
    refreshAt = 0;
  }

  public int add(String id, String selector) {
    get(id);
    List<ActorService.ManagedActor> selected;
    if (selector.startsWith("tag:")) {
      String tag = Checks.id(selector.substring(4));
      selected = actors.list().stream().filter(a -> a.group().equals(tag)).toList();
    } else selected = List.of(actors.get(selector));
    if (selected.isEmpty()) throw new IllegalArgumentException("No actors match " + selector + ".");
    for (var actor : selected) actors.available(actor.id());
    for (var actor : selected) {
      actor.stop();
      actor.definition.group = id;
      actors.save(actor);
      brains.remove(actor.id());
    }
    refreshAt = 0;
    return selected.size();
  }

  public void remove(String id, String actorId) {
    get(id);
    var actor = actors.get(actorId);
    actors.available(actorId);
    if (!actor.group().equals(id))
      throw new IllegalArgumentException("That actor is not in " + id + ".");
    actor.stop();
    actor.definition.group = "default";
    actors.save(actor);
    brains.remove(actorId);
    assignments.remove(actorId);
    refreshAt = 0;
  }

  public void leader(String id, Player leader) {
    ActorGroup group = get(id);
    if (leader != null) {
      if (leader.hasMetadata("NPC")
          || actors.byEntity(leader.getUniqueId()).isPresent()
          || acting.apply(leader).isPresent())
        throw new IllegalArgumentException(
            "Choose a real online player who is not acting as an NPC.");
      if (groups.values().stream()
          .anyMatch(g -> g != group && leader.getUniqueId().equals(g.leader)))
        throw new IllegalArgumentException("That player already leads another group.");
    }
    halt(group, true);
    group.leader = leader == null ? null : leader.getUniqueId();
    group.order = leader == null ? ActorGroup.Order.HOLD : ActorGroup.Order.FOLLOW;
    save(group);
    refreshAt = 0;
  }

  public void intelligence(String id, boolean value) {
    ActorGroup group = get(id);
    group.intelligence = value;
    if (!value) {
      group.targets.clear();
      group.enemies.clear();
      halt(group, false);
    }
    save(group);
    refreshAt = 0;
  }

  public void order(String id, ActorGroup.Order order, Location destination) {
    if (!enabled() || !settings.actorAi().groupsEnabled())
      throw new IllegalArgumentException(
          "NPC groups are disabled in actor-ai.yml or features.yml.");
    ActorGroup group = get(id);
    if (order == ActorGroup.Order.FOLLOW && group.leader == null)
      throw new IllegalArgumentException(
          "Assign a leader first with /es group leader " + id + " <player>.");
    if (order == ActorGroup.Order.MOVE && destination == null)
      throw new IllegalArgumentException("Move here requires a player location.");
    halt(group, true);
    group.order = order;
    group.destination = destination == null ? null : destination.clone();
    save(group);
    refreshAt = 0;
  }

  public void attack(String id, LivingEntity target) {
    ActorGroup group = get(id);
    if (!enabled() || !settings.actorAi().groupsEnabled() || !group.intelligence)
      throw new IllegalArgumentException("Enable group intelligence before ordering an attack.");
    if (!targetable(target) || friendly(group, target))
      throw new IllegalArgumentException(
          "Choose a living enemy in survival/adventure mode, outside this group.");
    if (members(id).stream().flatMap(a -> a.entity().stream()).noneMatch(e -> inRange(e, target)))
      throw new IllegalArgumentException(
          "The target must be within the engagement radius of a group member.");
    engage(group, target);
    refreshAt = 0;
  }

  public void fight(String id, String enemy) {
    ActorGroup group = get(id), other = get(enemy);
    if (group == other) throw new IllegalArgumentException("A group cannot fight itself.");
    if (!enabled() || !settings.actorAi().groupsEnabled() || !group.intelligence)
      throw new IllegalArgumentException("Enable group intelligence first.");
    group.enemies.add(enemy);
    if (other.intelligence) other.enemies.add(id);
    refreshAt = 0;
  }

  public String info(String id) {
    ActorGroup g = get(id);
    Player leader = g.leader == null ? null : Bukkit.getPlayer(g.leader);
    long live = members(id).stream().filter(a -> a.entity().isPresent()).count();
    return "Group "
        + id
        + ": "
        + members(id).size()
        + " actors ("
        + live
        + " present); leader="
        + (g.leader == null ? "none" : leader == null ? g.leader + " (offline)" : leader.getName())
        + "; order="
        + g.order.name().toLowerCase(Locale.ROOT)
        + "; intelligence="
        + g.intelligence
        + "; targets="
        + g.targets.size()
        + "; enemy groups="
        + g.enemies
        + ". Equipment and health are individual.";
  }

  private void save(ActorGroup group) {
    store.save("groups", group.id, group.yaml());
  }

  private void ensureRunning() {
    if (closing || job != null || !ticks.acceptingWork()) return;
    job =
        ticks.add(
            new TickEngine.Job() {
              public boolean tick() {
                update();
                return !closing && (!groups.isEmpty() || !actors.ids().isEmpty());
              }

              public void stopped() {
                job = null;
              }
            });
  }

  private void halt(ActorGroup group, boolean clearTargets) {
    if (clearTargets) {
      group.targets.clear();
      group.enemies.clear();
    }
    for (var actor : members(group.id)) {
      if (!actors.busy(actor.id())) actor.stop();
      brains.remove(actor.id());
      assignments.remove(actor.id());
    }
  }

  private void refresh() {
    Map<String, List<ActorService.ManagedActor>> next = new HashMap<>();
    for (var a : actors.list())
      if (a.definition.aggressive
          || (settings.actorAi().groupsEnabled() && groups.containsKey(a.group())))
        next.computeIfAbsent(a.group(), key -> new ArrayList<>()).add(a);
    members = next;
    roster = next.values().stream().flatMap(Collection::stream).toList();
    Set<String> active = new HashSet<>();
    roster.forEach(a -> active.add(a.id()));
    for (String id : List.copyOf(brains.keySet()))
      if (!active.contains(id)) {
        if (!actors.busy(id)) {
          try {
            actors.get(id).stop();
          } catch (IllegalArgumentException ignored) {
          }
        }
        brains.remove(id);
        assignments.remove(id);
      }
    if (settings.actorAi().groupsEnabled())
      for (ActorGroup group : groups.values()) allocate(group);
    refreshAt = tick + 20;
  }

  private void allocate(ActorGroup group) {
    var team = members.getOrDefault(group.id, List.of());
    if (!group.intelligence) {
      team.forEach(a -> assignments.remove(a.id()));
      return;
    }
    group.targets.removeIf(
        id ->
            !(Bukkit.getEntity(id) instanceof LivingEntity e)
                || !targetable(e)
                || friendly(group, e)
                || team.stream().flatMap(a -> a.entity().stream()).noneMatch(a -> inRange(a, e)));
    LinkedHashMap<UUID, LivingEntity> targets = new LinkedHashMap<>();
    group.targets.forEach(
        id -> {
          if (Bukkit.getEntity(id) instanceof LivingEntity e) targets.put(id, e);
        });
    group.enemies.removeIf(id -> !groups.containsKey(id));
    for (String enemy : group.enemies) {
      for (var a : members.getOrDefault(enemy, List.of()))
        a.entity()
            .filter(this::targetable)
            .filter(e -> !friendly(group, e))
            .ifPresent(
                e -> {
                  if (targets.size() < settings.actorAi().maxTargets())
                    targets.put(e.getUniqueId(), e);
                });
      UUID leader = get(enemy).leader;
      Player p = leader == null ? null : Bukkit.getPlayer(leader);
      if (p != null
          && targetable(p)
          && !friendly(group, p)
          && targets.size() < settings.actorAi().maxTargets()) targets.put(leader, p);
    }
    Map<String, LivingEntity> available = new LinkedHashMap<>();
    team.stream()
        .filter(a -> !actors.busy(a.id()))
        .forEach(a -> a.entity().ifPresent(e -> available.put(a.id(), e)));
    var allocation =
        GroupTactics.allocate(
            List.copyOf(available.keySet()),
            List.copyOf(targets.keySet()),
            assignments,
            (id, target) ->
                inRange(available.get(id), targets.get(target))
                    ? available
                        .get(id)
                        .getLocation()
                        .distanceSquared(targets.get(target).getLocation())
                    : Double.POSITIVE_INFINITY);
    team.forEach(a -> assignments.remove(a.id()));
    assignments.putAll(allocation);
  }

  private void update() {
    tick++;
    if (!enabled()) {
      stopAll();
      return;
    }
    if (tick >= refreshAt) refresh();
    int budget = settings.actorAi().pathsPerTick();
    for (int offset = 0; offset < roster.size(); offset++) {
      var actor = roster.get((cursor + offset) % roster.size());
      if (Math.floorMod(tick + actor.id().hashCode(), 5) != 0) continue;
      if (actors.busy(actor.id()) || actor.entity().isEmpty()) {
        brains.remove(actor.id());
        continue;
      }
      ActorGroup group = settings.actorAi().groupsEnabled() ? groups.get(actor.group()) : null;
      if (group == null && !actor.definition.aggressive) continue;
      Brain brain = brains.computeIfAbsent(actor.id(), key -> new Brain());
      String owner = group == null ? "@solo" : group.id;
      if (!Objects.equals(brain.group, owner)) {
        actor.stop();
        brain.group = owner;
        brain.lastGoal = null;
      }
      try {
        budget = act(actor, group, brain, budget);
      } catch (IllegalArgumentException | IllegalStateException ex) {
        stopMotion(actor, brain);
        brain.nextPath = tick + 40;
      }
    }
    if (!roster.isEmpty()) cursor = (cursor + 1) % roster.size();
  }

  private int act(ActorService.ManagedActor actor, ActorGroup group, Brain brain, int budget) {
    LivingEntity entity = actor.requireEntity();
    Player leader = group == null || group.leader == null ? null : Bukkit.getPlayer(group.leader);
    if (group != null
        && group.leader != null
        && (leader == null
            || leader.isDead()
            || leader.getGameMode() == GameMode.SPECTATOR
            || acting.apply(leader).isPresent())) {
      stopMotion(actor, brain);
      actor.look(null);
      group.targets.clear();
      group.enemies.clear();
      return budget;
    }
    if (tick < brain.pauseUntil) return budget;
    if (combat != null && combat.defending(actor)) {
      stopMotion(actor, brain);
      return budget;
    }
    UUID targetId = (group == null ? soloTargets : assignments).get(actor.id());
    LivingEntity target =
        targetId != null && Bukkit.getEntity(targetId) instanceof LivingEntity e ? e : null;
    if (intelligent(actor)
        && target != null
        && targetable(target)
        && !allied(entity, target)
        && inRange(entity, target)) {
      if (combat != null && combat.prepare(actor, target)) {
        // Stop pathfinding without clearing the item's chosen look direction.
        if (brain.moving) {
          stopMotion(actor, brain);
          combat.prepare(actor, target);
        }
        return budget;
      }
      actor.look(target.getEyeLocation());
      if (entity.getLocation().distanceSquared(target.getLocation())
              <= Math.pow(settings.actorAi().meleeReach(), 2)
          && entity.hasLineOfSight(target)) {
        stopMotion(actor, brain);
        actor.look(target.getEyeLocation());
        if (tick >= brain.nextAttack) {
          brain.nextAttack =
              tick + (combat == null ? settings.actorAi().attackCooldown() : combat.attackDelay());
          if (combat != null) combat.strike(actor, target);
          else {
            entity.swingMainHand();
            entity.attack(target);
          }
        }
        return budget;
      }
      return navigate(actor, brain, target.getLocation(), settings.actorAi().chaseSpeed(), budget);
    }
    if (combat != null) combat.idle(actor);
    actor.lookNearby(true);
    if (group == null) {
      soloTargets.remove(actor.id());
      stopMotion(actor, brain);
      return budget;
    }
    Location center =
        group.order == ActorGroup.Order.FOLLOW && leader != null
            ? leader.getLocation()
            : group.order == ActorGroup.Order.MOVE ? group.destination : null;
    if (center == null || center.getWorld() != entity.getWorld()) {
      stopMotion(actor, brain);
      return budget;
    }
    int index = members.getOrDefault(group.id, List.of()).indexOf(actor);
    Location goal =
        ActorWandering.ground(
            center
                .clone()
                .add(
                    GroupTactics.formation(Math.max(0, index), settings.actorAi().followSpacing())),
            3);
    if (goal == null || entity.getLocation().distanceSquared(goal) > 96 * 96) {
      stopMotion(actor, brain);
      return budget;
    }
    if (entity.getLocation().distanceSquared(goal) < 1.5) {
      stopMotion(actor, brain);
      return budget;
    }
    return navigate(actor, brain, goal, settings.actorAi().followSpeed(), budget);
  }

  private int navigate(
      ActorService.ManagedActor actor, Brain brain, Location goal, double speed, int budget) {
    if (budget < 1 || tick < brain.nextPath) return budget;
    if (actor.navigating()
        && brain.lastGoal != null
        && brain.lastGoal.getWorld() == goal.getWorld()
        && brain.lastGoal.distanceSquared(goal) < 2.25) return budget;
    brain.nextPath = tick + settings.actorAi().repathTicks();
    try {
      actor.move(goal, speed);
      brain.lastGoal = goal.clone();
      brain.moving = true;
    } catch (IllegalArgumentException | IllegalStateException ex) {
      stopMotion(actor, brain);
      brain.nextPath = tick + 40;
    }
    return budget - 1;
  }

  private void stopMotion(ActorService.ManagedActor actor, Brain brain) {
    if (brain.moving) actor.stop();
    brain.moving = false;
    brain.lastGoal = null;
  }

  private boolean inRange(LivingEntity from, LivingEntity to) {
    return from != null
        && to != null
        && from.getWorld() == to.getWorld()
        && from.getLocation().distanceSquared(to.getLocation())
            <= Math.pow(settings.actorAi().engagementRadius(), 2);
  }

  private boolean targetable(LivingEntity target) {
    if (!target.isValid() || target.isDead()) return false;
    var actor = actors.byEntity(target.getUniqueId());
    if (actor.isPresent()) return actor.get().definition.hittable && !actors.busy(actor.get().id());
    if (target.hasMetadata("NPC")) return false;
    return !(target instanceof Player p)
        || (p.isOnline()
            && !p.isInvulnerable()
            && p.getGameMode() != GameMode.CREATIVE
            && p.getGameMode() != GameMode.SPECTATOR
            && acting.apply(p).isEmpty());
  }

  public Optional<String> groupOf(Entity entity) {
    if (entity == null || !settings.actorAi().groupsEnabled()) return Optional.empty();
    var actor = actors.byEntity(entity.getUniqueId());
    if (actor.isPresent() && groups.containsKey(actor.get().group()))
      return Optional.of(actor.get().group());
    if (entity instanceof Player p) {
      var performing = acting.apply(p);
      if (performing.isPresent()) {
        try {
          String group = actors.get(performing.get()).group();
          if (groups.containsKey(group)) return Optional.of(group);
        } catch (IllegalArgumentException ignored) {
        }
      }
      return groups.values().stream()
          .filter(g -> p.getUniqueId().equals(g.leader))
          .map(g -> g.id)
          .findFirst();
    }
    return Optional.empty();
  }

  private boolean friendly(ActorGroup group, Entity entity) {
    return groupOf(entity).filter(group.id::equals).isPresent();
  }

  private Entity source(Entity entity) {
    if (entity instanceof Projectile p && p.getShooter() instanceof Entity shooter) return shooter;
    if (entity instanceof TNTPrimed tnt) return tnt.getSource();
    if (entity instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Entity owner)
      return owner;
    if (entity instanceof EvokerFangs fangs) return fangs.getOwner();
    return entity;
  }

  public boolean allied(Entity source, Entity victim) {
    var group = groupOf(source);
    return group.isPresent() && group.equals(groupOf(victim));
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void friendlyFire(EntityDamageByEntityEvent event) {
    if (enabled() && allied(source(event.getDamager()), event.getEntity()))
      event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void friendlyKnockback(
      io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent event) {
    if (enabled() && allied(source(event.getPushedBy()), event.getEntity()))
      event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void friendlySplash(PotionSplashEvent event) {
    if (!enabled()
        || event.getEntity().getEffects().stream()
            .allMatch(effect -> ActorCombatService.beneficial(effect.getType().getKey().getKey())))
      return;
    Entity owner = source(event.getEntity());
    for (LivingEntity target : event.getAffectedEntities())
      if (allied(owner, target)) event.setIntensity(target, 0);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void friendlyCloud(AreaEffectCloudApplyEvent event) {
    if (!enabled()) return;
    var cloud = event.getEntity();
    var effects = new ArrayList<>(cloud.getCustomEffects());
    if (cloud.getBasePotionType() != null)
      effects.addAll(cloud.getBasePotionType().getPotionEffects());
    if (effects.stream()
        .allMatch(effect -> ActorCombatService.beneficial(effect.getType().getKey().getKey())))
      return;
    event.getAffectedEntities().removeIf(target -> allied(source(cloud), target));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void damage(EntityDamageEvent event) {
    if (!enabled() || event.getFinalDamage() <= 0) return;
    actors
        .byEntity(event.getEntity().getUniqueId())
        .ifPresent(
            actor -> {
              if (actors.busy(actor.id())) return;
              Brain brain = brains.get(actor.id());
              if (brain != null) {
                stopMotion(actor, brain);
                brain.pauseUntil = tick + settings.actorAi().knockbackPause();
              }
            });
    if (!(event instanceof EntityDamageByEntityEvent hit)
        || !(event.getEntity() instanceof LivingEntity victim)) return;
    Entity source = source(hit.getDamager());
    if (!(source instanceof LivingEntity attacker)) return;
    actors
        .byEntity(victim.getUniqueId())
        .ifPresent(
            actor -> {
              if (actor.definition.aggressive
                  && !actors.busy(actor.id())
                  && targetable(attacker)
                  && !allied(attacker, victim)) {
                soloTargets.put(actor.id(), attacker.getUniqueId());
                refreshAt = 0;
              }
            });
    groupOf(attacker)
        .ifPresent(
            id -> {
              ActorGroup group = get(id);
              // Only a leader's own uncancelled hits issue new orders; NPC hits never recursively
              // recruit targets.
              if (attacker.getUniqueId().equals(group.leader)
                  && group.intelligence
                  && group.order != ActorGroup.Order.HOLD
                  && !friendly(group, victim)
                  && targetable(victim)) engage(group, victim);
            });
    groupOf(victim)
        .ifPresent(
            id -> {
              ActorGroup group = get(id);
              if (group.intelligence && !friendly(group, attacker) && targetable(attacker))
                engage(group, attacker);
            });
  }

  private void engage(ActorGroup group, LivingEntity target) {
    boolean changed =
        group.targets.size() < settings.actorAi().maxTargets()
            && group.targets.add(target.getUniqueId());
    var other = groupOf(target).filter(id -> !id.equals(group.id));
    if (other.isPresent()) {
      changed |= group.enemies.add(other.get());
      if (get(other.get()).intelligence) changed |= get(other.get()).enemies.add(group.id);
    }
    if (changed) refreshAt = 0;
  }

  @EventHandler
  public void quit(PlayerQuitEvent event) {
    for (var group : groups.values())
      if (event.getPlayer().getUniqueId().equals(group.leader)) halt(group, true);
  }

  @Override
  public void close() {
    closing = true;
    if (job != null) ticks.cancel(job);
    stopAll();
    actors.autonomousDirector(a -> false);
    roster = List.of();
    members = Map.of();
  }

  private void stopAll() {
    for (var actor : actors.list())
      if (brains.containsKey(actor.id()) && !actors.busy(actor.id())) actor.stop();
    groups
        .values()
        .forEach(
            g -> {
              g.targets.clear();
              g.enemies.clear();
            });
    brains.clear();
    assignments.clear();
    soloTargets.clear();
  }
}
