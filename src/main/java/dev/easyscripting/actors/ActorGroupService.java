package dev.easyscripting.actors;

import dev.easyscripting.config.*;
import dev.easyscripting.core.*;
import dev.easyscripting.items.KitService;
import dev.easyscripting.storage.YamlStore;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Main-thread group simulation. Equipment, damage and deaths remain owned by individual entities.
 */
public final class ActorGroupService implements Listener, AutoCloseable {
  private final JavaPlugin plugin;
  private final ActorService actors;
  private final Settings settings;
  private final TickEngine ticks;
  private final YamlStore store;
  private final KitService kits;
  private final Function<Player, Optional<String>> acting;
  private final Map<String, ActorGroup> groups = new TreeMap<>();
  private final Map<String, Brain> brains = new HashMap<>();
  private final Map<String, GroupMotion> groupMotion = new HashMap<>();
  private final Map<String, UUID> assignments = new HashMap<>();
  private final Map<String, UUID> soloTargets = new HashMap<>();
  /** Actor id to {position among this target's attackers, attacker count}. */
  private final Map<String, int[]> engagementSlots = new HashMap<>();
  /** Actor id to its compacted formation slot; absent means the member has no place yet. */
  private final Map<String, Integer> formationSlots = new HashMap<>();
  private final Map<String, Integer> formationSizes = new HashMap<>();
  private final List<PathRequest> requests = new ArrayList<>();
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

  /** Ordinary decision interval; an engaged member thinks more often so hits land on time. */
  private static final int CADENCE = 5, COMBAT_CADENCE = 2;

  /** Pull an unreachable formation slot toward the leader before abandoning the member. */
  private static final double[] SLOT_FALLBACKS = {0.75, 0.5, 0.25};

  /** How far around its target a sidestep carries the NPC, in radians. */
  private static final double STRAFE_ARC = 0.7;

  /** Never wait longer than this for a weapon to charge, in case the ticker is not simulated. */
  private static final int CHARGE_WAIT = 40;

  private static final class Brain {
    String group;
    Location lastGoal;
    long nextPath, nextAttack, pauseUntil, critAt, chargeBy;
    boolean moving;
    boolean sprintOverride;
    /** True while a sidestep is still walking, so the next decision does not cancel it. */
    boolean strafing;
    /** True once the member reached its slot; it then waits for the wider resume distance. */
    boolean parked;
  }

  private static final class GroupMotion {
    Vector heading;
    Location previous;
  }

  /** A path the director would like to issue this tick, ranked before the budget is spent. */
  private static final class PathRequest {
    final ActorService.ManagedActor actor;
    final Brain brain;
    final Location goal;
    final double speed, priority;
    final boolean following, sprint;

    PathRequest(
        ActorService.ManagedActor actor,
        Brain brain,
        Location goal,
        double speed,
        boolean following,
        boolean sprint,
        double priority) {
      this.actor = actor;
      this.brain = brain;
      this.goal = goal;
      this.speed = speed;
      this.following = following;
      this.sprint = sprint;
      this.priority = priority;
    }
  }

  public ActorGroupService(
      JavaPlugin plugin,
      ActorService actors,
      Settings settings,
      TickEngine ticks,
      YamlStore store,
      KitService kits,
      Function<Player, Optional<String>> acting) {
    this.plugin = plugin;
    this.actors = actors;
    this.settings = settings;
    this.ticks = ticks;
    this.store = store;
    this.kits = kits;
    this.acting = acting;
    actors.autonomousDirector(
        a ->
            enabled()
                && ((a.definition.aggressive
                        && (soloTargets.containsKey(a.id())
                            || (combat != null && combat.busy(a))))
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
          engagementSlots.remove(id);
          formationSlots.remove(id);
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

  /** Prefer an available assigned leader as the anchor for a newly deployed formation. */
  public Optional<Player> onlineLeader(String id) {
    ActorGroup group = groups.get(id);
    if (group == null || group.leader == null) return Optional.empty();
    Player leader = Bukkit.getPlayer(group.leader);
    return leader != null
            && leader.isOnline()
            && !leader.isDead()
            && leader.getGameMode() != GameMode.SPECTATOR
            && acting.apply(leader).isEmpty()
        ? Optional.of(leader)
        : Optional.empty();
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

  /** Delete every member NPC but keep the group, its leader, orders and shared defaults. */
  public int purge(String id) {
    ActorGroup group = get(id);
    List<ActorService.ManagedActor> doomed = List.copyOf(members(id));
    halt(group, true);
    RuntimeException failure = null;
    for (var actor : doomed)
      try {
        actors.remove(actor.id());
      } catch (RuntimeException error) {
        if (failure == null) failure = error;
        else failure.addSuppressed(error);
      }
    refreshAt = 0;
    if (failure != null) throw failure;
    return doomed.size();
  }

  /**
   * Teleport every available member onto its own block in rows and columns beside the anchor.
   * Unlike a Move order this does not path: it is the reliable way to get a clean formation.
   */
  public int lineUp(String id, Player anchor, String side, int columns) {
    ActorGroup group = get(id);
    if (!enabled() || !settings.actorAi().groupsEnabled())
      throw new IllegalArgumentException(
          "NPC groups are disabled in actor-ai.yml or features.yml.");
    List<ActorService.ManagedActor> team =
        members(id).stream()
            .filter(a -> a.entity().isPresent() && !actors.busy(a.id()))
            .toList();
    if (team.isEmpty())
      throw new IllegalArgumentException(
          "No member of " + id + " is present and free. Respawn or finish recordings first.");
    List<Location> places =
        PatternLayout.lineUp(
            team.size(),
            columns > 0 ? columns : GroupTactics.columns(0, team.size()),
            anchor.getLocation(),
            side);
    List<Location> safe = new ArrayList<>(places.size());
    for (Location place : places) {
      // Anchored near the caller's own feet, not the world's highest block: lining up indoors
      // must not drop the group onto the roof.
      Location ground = ActorWandering.ground(place, 4);
      // Refuse the whole line-up rather than stacking two members on one usable block.
      if (ground == null)
        throw new IllegalArgumentException(
            "No safe standing block at "
                + place.getBlockX()
                + ", "
                + place.getBlockZ()
                + ". Line up on clear, level, loaded ground.");
      ground.setYaw(place.getYaw());
      ground.setPitch(0);
      safe.add(ground);
    }
    halt(group, true);
    // Hold, or a following group would immediately walk back out of the formation it was put in.
    group.order = ActorGroup.Order.HOLD;
    group.destination = null;
    save(group);
    for (int i = 0; i < team.size(); i++) actors.teleport(team.get(i).id(), safe.get(i));
    refreshAt = 0;
    return team.size();
  }

  /** Targets this group could actually be ordered to attack right now. */
  public List<String> attackable(String id) {
    ActorGroup group = groups.get(id);
    if (group == null || !group.intelligence) return List.of();
    List<LivingEntity> present =
        members(id).stream().flatMap(a -> a.entity().stream()).toList();
    List<String> result = new ArrayList<>();
    for (Player player : Bukkit.getOnlinePlayers())
      if (targetable(player)
          && !friendly(group, player)
          && present.stream().anyMatch(e -> inRange(e, player))) result.add(player.getName());
    for (var actor : actors.list())
      actor
          .entity()
          .filter(this::targetable)
          .filter(e -> !friendly(group, e))
          .filter(e -> present.stream().anyMatch(member -> inRange(member, e)))
          .ifPresent(e -> result.add("actor:" + actor.id()));
    return List.copyOf(result);
  }

  /** Delete the faction and every actor assigned to it. Stale bound tools then fail safely. */
  public int delete(String id) {
    ActorGroup group = get(id);
    List<ActorService.ManagedActor> doomed = List.copyOf(members(id));
    halt(group, true);
    RuntimeException failure = null;
    for (var actor : doomed)
      try {
        actors.remove(actor.id());
      } catch (RuntimeException error) {
        if (failure == null) failure = error;
        else failure.addSuppressed(error);
      }
    if (failure != null) {
      refreshAt = 0;
      throw failure;
    }
    groups.remove(id);
    groupMotion.remove(id);
    store.delete("groups", id);
    groups.values().forEach(g -> g.enemies.remove(id));
    refreshAt = 0;
    return doomed.size();
  }

  /** Create one safely grounded, equipped member for a persistent bound tool. */
  public ActorService.ManagedActor createMember(
      String id, String requiredKit, String type, Location location) {
    ActorGroup group = get(id);
    kits.contents(requiredKit); // Validate before allocating an entity or advancing the counter.
    int index = group.nextActorIndex;
    String actorId;
    while (true) {
      if (index >= Integer.MAX_VALUE - 1)
        throw new IllegalArgumentException("This group's actor counter is exhausted.");
      actorId = id + "-actor-" + index;
      try {
        Checks.id(actorId);
      } catch (IllegalArgumentException invalid) {
        throw new IllegalArgumentException(
            "Group ID '"
                + id
                + "' is too long for <group>-actor-<number> actor IDs. Use a shorter group ID.");
      }
      if (!actors.ids().contains(actorId)) break;
      index++;
    }
    ActorService.ManagedActor actor = actors.create(actorId, type, location);
    try {
      initializeMember(id, actor, requiredKit);
      group.nextActorIndex = index + 1;
      save(group);
      return actor;
    } catch (RuntimeException error) {
      try {
        actors.remove(actorId);
      } catch (RuntimeException cleanup) {
        error.addSuppressed(cleanup);
      }
      throw error;
    }
  }

  public int add(String id, String selector) {
    ActorGroup group = get(id);
    List<ActorService.ManagedActor> selected;
    if (selector.startsWith("tag:")) {
      String tag = Checks.id(selector.substring(4));
      selected = actors.list().stream().filter(a -> a.group().equals(tag)).toList();
    } else selected = List.of(actors.get(selector));
    if (selected.isEmpty()) throw new IllegalArgumentException("No actors match " + selector + ".");
    org.bukkit.inventory.ItemStack[] sharedContents =
        group.memberKit.isBlank() ? null : kits.contents(group.memberKit);
    for (var actor : selected) {
      actors.available(actor.id());
    }
    for (var actor : selected) {
      actor.stop();
      actor.definition.group = id;
      if (group.memberImmortal != null) actor.definition.immortal = group.memberImmortal;
      if (sharedContents != null) actors.applyKit(actor.id(), sharedContents);
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

  public int sharedImmortal(String id, boolean value) {
    ActorGroup group = get(id);
    List<ActorService.ManagedActor> team = members(id);
    group.memberImmortal = value;
    for (var actor : team) {
      actor.definition.immortal = value;
      actors.save(actor);
    }
    save(group);
    return team.size();
  }

  public int sharedKit(String id, String kit) {
    settings.require("kits");
    ActorGroup group = get(id);
    org.bukkit.inventory.ItemStack[] contents = kits.contents(kit);
    List<ActorService.ManagedActor> team = members(id);
    for (var actor : team) actors.available(actor.id());
    for (var actor : team) actors.applyKit(actor.id(), contents);
    group.memberKit = kit;
    save(group);
    return team.size();
  }

  public int sharedIdentities(String id) {
    List<ActorService.ManagedActor> team = members(id);
    for (var actor : team) actors.randomize(actor.id());
    return team.size();
  }

  /** Apply persistent group defaults plus the required tool/pattern kit before first use. */
  public void initializeMember(String id, ActorService.ManagedActor actor, String requiredKit) {
    settings.require("kits");
    ActorGroup group = get(id);
    org.bukkit.inventory.ItemStack[] contents = kits.contents(requiredKit);
    actor.definition.group = id;
    if (group.memberImmortal != null) actor.definition.immortal = group.memberImmortal;
    actors.applyKit(actor.id(), contents);
    actors.save(actor);
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
        + "; shared immortal="
        + (g.memberImmortal == null ? "individual" : g.memberImmortal)
        + "; shared kit="
        + (g.memberKit.isBlank() ? "individual" : g.memberKit)
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
    groupMotion.remove(group.id);
    formationSizes.remove(group.id);
    for (var actor : members(group.id)) {
      if (!actors.busy(actor.id())) actor.stop();
      brains.remove(actor.id());
      assignments.remove(actor.id());
      engagementSlots.remove(actor.id());
      formationSlots.remove(actor.id());
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
        engagementSlots.remove(id);
        formationSlots.remove(id);
      }
    if (settings.actorAi().groupsEnabled()) {
      layout();
      for (ActorGroup group : groups.values()) allocate(group);
    }
    refreshAt = tick + 20;
  }

  /**
   * Give the members that can actually stand in the formation a place in it. Absent and reserved
   * actors are skipped rather than reserving an empty square, and each member takes the place
   * nearest to where it already stands instead of a fixed number, so a group that turns or
   * reforms never sends a member around the leader and through its neighbours to reach a square.
   */
  private void layout() {
    Map<String, Integer> previous = Map.copyOf(formationSlots);
    formationSlots.clear();
    formationSizes.clear();
    var ai = settings.actorAi();
    for (ActorGroup group : groups.values()) {
      List<ActorService.ManagedActor> present = new ArrayList<>();
      for (var actor : members.getOrDefault(group.id, List.of()))
        if (actor.entity().isPresent() && !actors.busy(actor.id())) present.add(actor);
      int count = present.size();
      formationSizes.put(group.id, count);
      if (count == 0) continue;
      boolean following = group.order == ActorGroup.Order.FOLLOW;
      Player leader = group.leader == null ? null : Bukkit.getPlayer(group.leader);
      Location center =
          following && leader != null
              ? leader.getLocation()
              : group.order == ActorGroup.Order.MOVE ? group.destination : null;
      if (center == null) {
        // Nothing to measure distances from; the roster order alone decides who stands where.
        for (int slot = 0; slot < count; slot++) formationSlots.put(present.get(slot).id(), slot);
        continue;
      }
      Vector facing = following ? heading(group, leader) : facing(center);
      List<Location> places = new ArrayList<>(count);
      for (int slot = 0; slot < count; slot++)
        places.add(
            center
                .clone()
                .add(
                    following
                        ? GroupTactics.trailingFormation(
                            slot, count, ai.followColumns(), ai.followSpacing(), facing)
                        : GroupTactics.blockFormation(
                            slot, count, ai.followColumns(), ai.followSpacing(), facing)));
      Map<String, Location> standing = new HashMap<>();
      present.forEach(a -> a.entity().ifPresent(e -> standing.put(a.id(), e.getLocation())));
      formationSlots.putAll(
          GroupTactics.nearestSlots(
              present.stream().map(ActorService.ManagedActor::id).toList(),
              count,
              previous,
              (id, slot) -> {
                Location at = standing.get(id), place = places.get(slot);
                return at == null || at.getWorld() != place.getWorld()
                    ? Double.POSITIVE_INFINITY
                    : at.distanceSquared(place);
              }));
    }
  }

  private void allocate(ActorGroup group) {
    var team = members.getOrDefault(group.id, List.of());
    if (!group.intelligence) {
      team.forEach(
          a -> {
            assignments.remove(a.id());
            engagementSlots.remove(a.id());
          });
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
    team.forEach(
        a -> {
          assignments.remove(a.id());
          engagementSlots.remove(a.id());
        });
    assignments.putAll(allocation);
    // Remember each attacker's place around its target so a squad surrounds instead of stacking.
    Map<UUID, List<String>> byTarget = new LinkedHashMap<>();
    allocation.forEach(
        (member, target) -> byTarget.computeIfAbsent(target, key -> new ArrayList<>()).add(member));
    byTarget
        .values()
        .forEach(
            attackers -> {
              for (int i = 0; i < attackers.size(); i++)
                engagementSlots.put(attackers.get(i), new int[] {i, attackers.size()});
            });
  }

  private void update() {
    tick++;
    if (!enabled()) {
      stopAll();
      return;
    }
    if (tick >= refreshAt) refresh();
    if (settings.actorAi().groupsEnabled())
      for (ActorGroup group : groups.values()) trackHeading(group);
    requests.clear();
    for (int offset = 0; offset < roster.size(); offset++) {
      var actor = roster.get((cursor + offset) % roster.size());
      if (actors.busy(actor.id()) || actor.entity().isEmpty()) {
        brains.remove(actor.id());
        continue;
      }
      if (Math.floorMod(tick + actor.id().hashCode(), cadence(actor)) != 0) continue;
      ActorGroup group = settings.actorAi().groupsEnabled() ? groups.get(actor.group()) : null;
      if (group == null && !actor.definition.aggressive) continue;
      Brain brain = brains.computeIfAbsent(actor.id(), key -> new Brain());
      String owner = group == null ? "@solo" : group.id;
      if (!Objects.equals(brain.group, owner)) {
        actor.stop();
        brain.group = owner;
        brain.lastGoal = null;
        brain.parked = false;
      }
      try {
        act(actor, group, brain);
      } catch (IllegalArgumentException | IllegalStateException ex) {
        stopMotion(actor, brain);
        brain.nextPath = tick + 40;
      }
    }
    dispatch();
    if (!roster.isEmpty()) cursor = (cursor + 1) % roster.size();
  }

  /** A member with an enemy reassesses more often, so its swings are not quantised into misses. */
  private int cadence(ActorService.ManagedActor actor) {
    boolean engaged =
        assignments.containsKey(actor.id())
            || soloTargets.containsKey(actor.id())
            || (combat != null && combat.defending(actor));
    return engaged ? COMBAT_CADENCE : CADENCE;
  }

  /**
   * Sample how far the leader actually travelled this tick. Player velocity is not filled in by
   * walking input, so reading it made the rows line up with where the leader looked rather than
   * where they were going.
   */
  private void trackHeading(ActorGroup group) {
    Player leader = group.leader == null ? null : Bukkit.getPlayer(group.leader);
    if (leader == null || group.order != ActorGroup.Order.FOLLOW) {
      groupMotion.remove(group.id);
      return;
    }
    GroupMotion motion = groupMotion.computeIfAbsent(group.id, ignored -> new GroupMotion());
    Location now = leader.getLocation();
    Vector travel =
        motion.previous != null && motion.previous.getWorld() == now.getWorld()
            ? now.toVector().subtract(motion.previous.toVector())
            : new Vector();
    motion.previous = now.clone();
    motion.heading = GroupTactics.movementHeading(motion.heading, travel, now.getYaw());
  }

  /** Spend the shared path budget on whoever is worst off rather than on whoever ticked first. */
  private void dispatch() {
    int budget = settings.actorAi().pathsPerTick();
    if (requests.size() > budget)
      requests.sort(Comparator.comparingDouble((PathRequest r) -> r.priority).reversed());
    for (PathRequest request : requests) {
      if (budget < 1) break;
      if (navigate(request)) budget--;
    }
    requests.clear();
  }

  private void act(ActorService.ManagedActor actor, ActorGroup group, Brain brain) {
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
      return;
    }
    if (tick < brain.pauseUntil) return;
    // A long drop belongs to gravity. Steering mid-air is what makes a fall look floaty, and a
    // path request on landing cancels the fall before the damage lands.
    if (falling(entity)) {
      if (brain.moving) stopMotion(actor, brain);
      return;
    }
    if (combat != null && combat.defending(actor)) {
      stopMotion(actor, brain);
      return;
    }
    UUID targetId = (group == null ? soloTargets : assignments).get(actor.id());
    LivingEntity target =
        targetId != null && Bukkit.getEntity(targetId) instanceof LivingEntity e ? e : null;
    // A target that vanished between sweeps would otherwise leave the squad idle for a second.
    if (targetId != null && (target == null || !targetable(target))) refreshAt = 0;
    if (intelligent(actor)
        && target != null
        && targetable(target)
        && !allied(entity, target)
        && inRange(entity, target)) {
      brain.parked = false;
      if (combat != null) {
        // The reaction decides first; only then is it clear whether it wants to stand or run.
        boolean reacting = combat.prepare(actor, target);
        if (combat.retreating(actor)) {
          brain.critAt = 0;
          brain.strafing = false;
          // Face the way it is running. Holding the enemy in view while the body walks the other
          // way is what reads as moonwalking, so a break-off turns around like a player does.
          actor.look(null);
          withdraw(actor, entity, brain, target);
          return;
        }
        if (reacting) {
          // Stop pathfinding without clearing the item's chosen look direction.
          if (brain.moving) {
            stopMotion(actor, brain);
            combat.prepare(actor, target);
          }
          return;
        }
      }
      actor.look(target.getEyeLocation());
      double distance = reach(entity, target);
      if (distance <= settings.actorAi().meleeReach() && entity.hasLineOfSight(target)) {
        if (melee(actor, entity, brain, target, distance)) {
          brain.strafing = false;
          return;
        }
        // Nothing to swing with yet. Waiting out a weapon is when a player circles, not freezes.
        if (brain.strafing && actor.navigating()) return;
        brain.strafing = false;
        if (combat != null && combat.strafe(actor)) {
          // The path itself is issued after the budget is shared out, so the intent is recorded
          // now; a sidestep the budget never funds simply gets rolled again next time.
          sidestep(actor, entity, brain, target);
          brain.strafing = true;
        } else stopMotion(actor, brain);
        return;
      }
      brain.critAt = 0;
      brain.strafing = false;
      Location approach = approach(actor, entity, target);
      double gap = entity.getLocation().distanceSquared(approach);
      double sprintAt = settings.actorAi().sprintChaseDistance();
      request(
          actor,
          brain,
          approach,
          settings.actorAi().chaseSpeed(),
          false,
          gap > sprintAt * sprintAt,
          gap);
      return;
    }
    brain.critAt = 0;
    brain.strafing = false;
    if (combat != null) combat.idle(actor);
    if (group == null) {
      actor.lookNearby(true);
      soloTargets.remove(actor.id());
      stopMotion(actor, brain);
      return;
    }
    actor.look(null);
    Location center =
        group.order == ActorGroup.Order.FOLLOW && leader != null
            ? leader.getLocation()
            : group.order == ActorGroup.Order.MOVE ? group.destination : null;
    if (center == null || center.getWorld() != entity.getWorld()) {
      stopMotion(actor, brain);
      return;
    }
    Integer index = formationSlots.get(actor.id());
    int count = formationSizes.getOrDefault(group.id, 0);
    // Without a numbered place the member would share slot zero and shove whoever holds it.
    if (index == null || index >= count) {
      stopMotion(actor, brain);
      return;
    }
    boolean following = group.order == ActorGroup.Order.FOLLOW;
    var ai = settings.actorAi();
    Vector facing = following ? heading(group, leader) : facing(center);
    Vector slot =
        following
            ? GroupTactics.trailingFormation(
                index, count, ai.followColumns(), ai.followSpacing(), facing)
            : GroupTactics.blockFormation(
                index, count, ai.followColumns(), ai.followSpacing(), facing);
    Location goal = slotGround(center, slot);
    if (goal == null) {
      stopMotion(actor, brain);
      return;
    }
    double distanceSquared = entity.getLocation().distanceSquared(goal);
    // Hysteresis: a member that has taken its place waits for the wider resume distance, so a
    // leader shuffling on the spot does not make the whole formation stutter in and out of walking.
    // A navigator that has already stopped this close counts as settled too. Citizens and Paper
    // both finish a path a little short of its destination, and asking for that last fraction of
    // a block back every few ticks is what makes a member shuffle on the spot instead of walking.
    double settle =
        brain.parked || !actor.navigating()
            ? ai.followResumeDistance()
            : ai.followArrivalDistance();
    if (distanceSquared <= settle * settle) {
      brain.parked = true;
      stopMotion(actor, brain);
      // Everyone faces the way the formation is facing. A hundred NPCs each swivelling to stare
      // at the leader hides the rows and columns they are standing in.
      if (leader != null)
        actor.look(entity.getEyeLocation().add(facing.clone().multiply(4)));
      return;
    }
    brain.parked = false;
    double speed =
        following && distanceSquared >= ai.followCatchUpDistance() * ai.followCatchUpDistance()
            ? ai.followCatchUpSpeed()
            : ai.followSpeed();
    if (distanceSquared > ai.followWaypointDistance() * ai.followWaypointDistance()) {
      Vector point =
          GroupTactics.waypoint(
              entity.getLocation().toVector(), goal.toVector(), ai.followWaypointDistance());
      Location intermediate =
          ActorWandering.ground(
              new Location(
                  entity.getWorld(), point.getX(), point.getY(), point.getZ(), goal.getYaw(), 0),
              12);
      if (intermediate != null) goal = intermediate;
    }
    request(actor, brain, goal, speed, following, speed > ai.followSpeed(), distanceSquared);
  }

  /**
   * Swing once the weapon is actually charged, optionally hopping first to land a critical.
   * Returns true while the NPC is mid-swing or mid-hop and must hold its ground; false means it
   * has nothing to do with its weapon yet and the caller may move it.
   */
  private boolean melee(
      ActorService.ManagedActor actor,
      LivingEntity entity,
      Brain brain,
      LivingEntity target,
      double distance) {
    if (tick < brain.nextAttack) return false;
    if (combat == null) {
      plant(actor, brain, target);
      brain.nextAttack = tick + settings.actorAi().attackCooldown();
      entity.swingMainHand();
      // Accuracy applies with or without the combat service; no swing lands unconditionally.
      var c = settings.actorCombat();
      if (ThreadLocalRandom.current().nextDouble()
          < ActorCombatService.hitChance(
              distance, settings.actorAi().meleeReach(), c.accuracy(), c.reachAccuracy()))
        entity.attack(target);
      return true;
    }
    if (brain.critAt > 0) {
      if (tick < brain.critAt) return true; // Still rising; the blow lands on the way back down.
      brain.critAt = 0;
    } else if (!ready(entity, brain)) {
      return false;
    } else if (combat.critJump(actor)) {
      plant(actor, brain, target);
      brain.critAt = tick + settings.actorCombat().critJumpDelay();
      return true;
    }
    plant(actor, brain, target);
    brain.chargeBy = 0;
    brain.nextAttack = tick + combat.attackDelay();
    combat.strike(actor, target, distance);
    return true;
  }

  /** Stop walking and face the enemy; stopping clears the look, so the order matters. */
  private void plant(ActorService.ManagedActor actor, Brain brain, LivingEntity target) {
    stopMotion(actor, brain);
    actor.look(target.getEyeLocation());
  }

  /** True once the held weapon has recharged, or once waiting any longer would stall the fight. */
  private boolean ready(LivingEntity entity, Brain brain) {
    if (combat.charged(entity)) return true;
    // Waiting for full charge must never become waiting forever on an unsimulated entity.
    if (brain.chargeBy == 0) brain.chargeBy = tick + CHARGE_WAIT;
    return tick >= brain.chargeBy;
  }

  /**
   * Break off and open a gap. The NPC keeps facing its enemy while it backs away, so a retreat
   * reads as a retreat rather than as the NPC losing interest and wandering off.
   */
  private void withdraw(
      ActorService.ManagedActor actor, LivingEntity entity, Brain brain, LivingEntity target) {
    double distance = settings.actorCombat().retreatDistance();
    Vector away = ActorCombatService.away(entity, target).multiply(distance);
    Location goal = groundNear(entity.getLocation().add(away), away);
    if (goal == null) {
      stopMotion(actor, brain);
      return;
    }
    request(
        actor,
        brain,
        goal,
        settings.actorAi().chaseSpeed(),
        false,
        true,
        entity.getLocation().distanceSquared(goal) + 512);
  }

  /** Circle the target at the distance already held, so the sidestep never gives up the reach. */
  private void sidestep(
      ActorService.ManagedActor actor, LivingEntity entity, Brain brain, LivingEntity target) {
    Vector bearing = entity.getLocation().toVector().subtract(target.getLocation().toVector());
    double radius = Math.max(1, bearing.clone().setY(0).length());
    double arc = ThreadLocalRandom.current().nextBoolean() ? STRAFE_ARC : -STRAFE_ARC;
    Location goal =
        ActorWandering.ground(
            target.getLocation().clone().add(GroupTactics.circleOffset(bearing, radius, arc)), 3);
    if (goal == null) {
      stopMotion(actor, brain);
      return;
    }
    request(actor, brain, goal, settings.actorAi().followSpeed(), false, false, 256);
  }

  /** Resolve a point to a standing surface, shortening the offset when the far end is blocked. */
  private Location groundNear(Location goal, Vector offset) {
    Location exact = ActorWandering.ground(goal, 4);
    if (exact != null) return exact;
    Location origin = goal.clone().subtract(offset);
    for (double shrink : SLOT_FALLBACKS) {
      Location nearer =
          ActorWandering.ground(origin.clone().add(offset.clone().multiply(shrink)), 4);
      if (nearer != null) return nearer;
    }
    return null;
  }

  /** Approach a share of the ring around the target instead of everyone's exact centre block. */
  private Location approach(
      ActorService.ManagedActor actor, LivingEntity entity, LivingEntity target) {
    int[] engagement = engagementSlots.get(actor.id());
    if (engagement == null || engagement[1] < 2) return target.getLocation();
    Vector bearing = entity.getLocation().toVector().subtract(target.getLocation().toVector());
    Vector offset =
        GroupTactics.engagementOffset(
            engagement[0],
            engagement[1],
            Math.max(1, settings.actorAi().meleeReach() - 0.6),
            bearing);
    Location spread = ActorWandering.ground(target.getLocation().clone().add(offset), 4);
    return spread == null ? target.getLocation() : spread;
  }

  /**
   * Resolve a formation slot to a standing surface. A slot inside a wall or over a drop pulls in
   * toward the anchor rather than freezing the member where it stands.
   */
  private Location slotGround(Location center, Vector slot) {
    Location exact = ActorWandering.ground(center.clone().add(slot), 6);
    if (exact != null) return exact;
    for (double shrink : SLOT_FALLBACKS) {
      Location nearer = ActorWandering.ground(center.clone().add(slot.clone().multiply(shrink)), 6);
      if (nearer != null) return nearer;
    }
    return ActorWandering.ground(center.clone(), 6);
  }

  private Vector heading(ActorGroup group, Player leader) {
    GroupMotion motion = groupMotion.get(group.id);
    if (motion != null && motion.heading != null) return motion.heading.clone();
    return leader == null ? new Vector(0, 0, 1) : facing(leader.getLocation());
  }

  private static Vector facing(Location location) {
    double angle = Math.toRadians(location.getYaw());
    return new Vector(-Math.sin(angle), 0, Math.cos(angle));
  }

  /**
   * Queue a path for this tick's shared budget. Requests that would repeat an active path, or that
   * are still inside their repath interval, never reach the queue and so never crowd it out.
   */
  private void request(
      ActorService.ManagedActor actor,
      Brain brain,
      Location goal,
      double speed,
      boolean following,
      boolean sprint,
      double priority) {
    boolean navigating = actor.navigating();
    double change = following ? settings.actorAi().followGoalChange() : 1.5;
    if (navigating
        && brain.lastGoal != null
        && brain.lastGoal.getWorld() == goal.getWorld()
        && brain.lastGoal.distanceSquared(goal) < change * change) return;
    // One interval covers a completed path as well as a running one. Re-issuing a path the moment
    // the last one ends restarts the walk cycle every few ticks, which reads as a stutter.
    if (tick < brain.nextPath) return;
    // A stopped member is further behind than its distance alone suggests, so it goes first.
    requests.add(
        new PathRequest(
            actor, brain, goal, speed, following, sprint, priority + (navigating ? 0 : 256)));
  }

  private boolean navigate(PathRequest request) {
    ActorService.ManagedActor actor = request.actor;
    Brain brain = request.brain;
    // A request queued before the NPC left the ground must not land during its fall.
    if (actor.entity().filter(this::falling).isPresent()) {
      brain.nextPath = tick + 5;
      return false;
    }
    brain.nextPath =
        tick
            + (request.following
                ? settings.actorAi().followRepathTicks()
                : settings.actorAi().repathTicks());
    try {
      actor.move(request.goal, request.speed);
      brain.lastGoal = request.goal.clone();
      brain.moving = true;
      if (actor.requireEntity() instanceof Player player) {
        player.setSprinting(actor.definition.sprinting || request.sprint);
        brain.sprintOverride = true;
      }
      return true;
    } catch (IllegalArgumentException | IllegalStateException ex) {
      stopMotion(actor, brain);
      brain.nextPath = tick + (request.following ? 10 : 40);
      return false;
    }
  }

  private void stopMotion(ActorService.ManagedActor actor, Brain brain) {
    if (brain.moving) actor.stop();
    if (brain.sprintOverride && actor.entity().orElse(null) instanceof Player player)
      player.setSprinting(actor.definition.sprinting);
    brain.moving = false;
    brain.sprintOverride = false;
    brain.lastGoal = null;
  }

  /**
   * Distance from the attacker's eyes to the nearest point of the target's hitbox, the way a real
   * player's reach is measured. Comparing foot positions denied hits on anything standing on a
   * slab, a stair or the attacker's own head.
   */
  private static double reach(LivingEntity from, LivingEntity to) {
    if (from.getWorld() != to.getWorld()) return Double.POSITIVE_INFINITY;
    Location eye = from.getEyeLocation();
    var box = to.getBoundingBox();
    double x = Math.max(box.getMinX(), Math.min(eye.getX(), box.getMaxX()));
    double y = Math.max(box.getMinY(), Math.min(eye.getY(), box.getMaxY()));
    double z = Math.max(box.getMinZ(), Math.min(eye.getZ(), box.getMaxZ()));
    return eye.toVector().distance(new Vector(x, y, z));
  }

  /**
   * True while the NPC is airborne with real ground well below it. Small step-downs and jumps are
   * excluded so ordinary walking is never mistaken for a fall.
   */
  private boolean falling(LivingEntity entity) {
    double pause = settings.actorAi().fallPause();
    if (pause <= 0 || entity.isOnGround() || entity.isInsideVehicle()) return false;
    if (entity.getVelocity().getY() > -0.08) return false; // Rising, or held up by something.
    if (entity instanceof Player player && player.isGliding()) return false;
    var world = entity.getWorld();
    Location at = entity.getLocation();
    int floor = at.getBlockY();
    int limit = (int) Math.ceil(pause);
    for (int drop = 1; drop <= limit; drop++) {
      int y = floor - drop;
      if (y < world.getMinHeight()) return false;
      var block = world.getBlockAt(at.getBlockX(), y, at.getBlockZ());
      if (!block.isPassable() || block.isLiquid()) return false;
    }
    return true;
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

  /** Group membership without treating the real player leader as an attacking member. */
  private Optional<String> memberGroupOf(Entity entity) {
    if (entity == null || !settings.actorAi().groupsEnabled()) return Optional.empty();
    var actor = actors.byEntity(entity.getUniqueId());
    if (actor.isPresent() && groups.containsKey(actor.get().group()))
      return Optional.of(actor.get().group());
    if (entity instanceof Player player) {
      var performing = acting.apply(player);
      if (performing.isPresent()) {
        try {
          String group = actors.get(performing.get()).group();
          if (groups.containsKey(group)) return Optional.of(group);
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
    return Optional.empty();
  }

  private boolean blocksFriendlyDamage(Entity source, Entity victim) {
    return GroupTactics.blocksFriendlyDamage(
        memberGroupOf(source).orElse(null), groupOf(victim).orElse(null));
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
    if (enabled() && blocksFriendlyDamage(source(event.getDamager()), event.getEntity()))
      event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void friendlyKnockback(
      io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent event) {
    if (enabled() && blocksFriendlyDamage(source(event.getPushedBy()), event.getEntity()))
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
      if (blocksFriendlyDamage(owner, target)) event.setIntensity(target, 0);
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
    event.getAffectedEntities().removeIf(target -> blocksFriendlyDamage(source(cloud), target));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void damage(EntityDamageEvent event) {
    if (!enabled() || event.getFinalDamage() <= 0) return;
    // The pause exists so real knockback can carry, so only a blow earns one. Fire, fall damage,
    // drowning and cactus throw an NPC nowhere, and pausing on those left it standing in the
    // damage it should have been walking out of.
    if (event instanceof EntityDamageByEntityEvent)
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

  /** Reassign at once; otherwise a squad keeps swinging at a corpse until the next sweep. */
  @EventHandler(priority = EventPriority.MONITOR)
  public void died(EntityDeathEvent event) {
    if (!enabled()) return;
    UUID dead = event.getEntity().getUniqueId();
    boolean assigned =
        assignments.values().removeIf(dead::equals) | soloTargets.values().removeIf(dead::equals);
    boolean ordered = false;
    for (ActorGroup group : groups.values()) ordered |= group.targets.remove(dead);
    if (assigned || ordered) refreshAt = 0;
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
    groupMotion.clear();
    assignments.clear();
    soloTargets.clear();
    engagementSlots.clear();
    formationSlots.clear();
    formationSizes.clear();
    requests.clear();
  }
}
