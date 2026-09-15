package dev.easyscripting.actors;

import dev.easyscripting.api.Actor;
import dev.easyscripting.config.Settings;
import dev.easyscripting.core.*;
import dev.easyscripting.players.IdentityProvider;
import dev.easyscripting.storage.YamlStore;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class ActorService implements Listener, AutoCloseable {
  private final JavaPlugin plugin;
  private final Settings settings;
  private final YamlStore store;
  private final TickEngine ticks;
  private final ActorBackend mobs, players;
  private final Map<String, ManagedActor> actors = new TreeMap<>();
  private final Map<UUID, ManagedActor> entities = new HashMap<>();
  private final Deque<String> recentNames = new ArrayDeque<>();
  private Consumer<String> removed = id -> {};
  private Consumer<ActorDefinition> deleted = definition -> {};
  private Consumer<ActorDefinition> died = definition -> {};
  private Consumer<ManagedActor> spawned = actor -> {};
  private UUID behaviorJob;
  private boolean closing;
  private final Map<String, String> leases = new HashMap<>();
  private final Map<Chunk, Integer> chunkTickets = new HashMap<>();
  private java.util.function.Predicate<String> blockedIdentity = name -> false;
  private java.util.function.Predicate<String> retiredIdentity = name -> false;
  private Supplier<? extends Collection<String>> reservedIdentityNames = List::of;
  private IdentityProvider identityProvider;
  private java.util.function.Predicate<ManagedActor> directed = actor -> false;

  public void autonomousDirector(java.util.function.Predicate<ManagedActor> director) {
    directed = director;
  }

  public boolean busy(String id) {
    return leases.containsKey(id);
  }

  public void identityFilter(java.util.function.Predicate<String> filter) {
    blockedIdentity = filter;
  }

  public void retiredIdentityFilter(java.util.function.Predicate<String> filter) {
    retiredIdentity = filter;
  }

  public void identityProvider(IdentityProvider provider) {
    identityProvider = provider;
  }

  /** Names held by other live identity systems, including aliases hidden during NPC acting. */
  public void reservedIdentityNames(Supplier<? extends Collection<String>> names) {
    reservedIdentityNames = Objects.requireNonNull(names);
  }

  public void purgeIdentity(String name) {
    for (ManagedActor actor : list())
      if (actor.definition.name.equalsIgnoreCase(name)
          || actor.definition.skin.equalsIgnoreCase(name)) remove(actor.id());
  }

  public void available(String id) {
    if (leases.containsKey(id))
      throw new IllegalArgumentException(
          "Actor '" + id + "' is busy with " + leases.get(id) + ". Stop it first.");
  }

  public void editable(String id, String key) {
    String owner = leases.get(id);
    if (owner == null || key.equals("immortal") || key.equals("hittable")) return;
    if (owner.startsWith("recording ")
        && Set.of("name", "skin", "randomize", "mode", "tablist", "nametag", "glow", "group")
            .contains(key)) return;
    available(id);
  }

  public void reserve(String id, String owner) {
    available(id);
    get(id).stop();
    leases.put(id, owner);
  }

  public void release(String id, String owner) {
    leases.remove(id, owner);
  }

  public ActorService(
      JavaPlugin plugin,
      Settings settings,
      YamlStore store,
      TickEngine ticks,
      ActorBackend players) {
    this.plugin = plugin;
    this.settings = settings;
    this.store = store;
    this.ticks = ticks;
    this.players = players;
    this.mobs = new MobBackend(plugin);
  }

  public void onRemoved(Consumer<String> listener) {
    removed = removed.andThen(listener);
  }

  public void onDeleted(Consumer<ActorDefinition> listener) {
    deleted = deleted.andThen(listener);
  }

  /** Natural mortal deaths only; manual/group deletion deliberately does not invoke this hook. */
  public void onDied(Consumer<ActorDefinition> listener) {
    died = died.andThen(listener);
  }

  public void onSpawned(Consumer<ManagedActor> listener) {
    spawned = spawned.andThen(listener);
  }

  public Optional<ManagedActor> byEntity(UUID id) {
    return Optional.ofNullable(entities.get(id));
  }

  public void refresh() {
    for (ManagedActor actor : list()) {
      if (!settings.enabled("actors")) {
        removed.accept(actor.id());
        save(actor);
        despawn(actor);
      } else if (!actor.definition.hidden && actor.entity().isEmpty()) spawn(actor);
    }
    ensureBehaviors();
  }

  public void load() {
    store
        .load("actors")
        .forEach(
            (id, y) -> {
              try {
                ActorDefinition d = ActorDefinition.read(id, y);
                ManagedActor a = new ManagedActor(d);
                actors.put(id, a);
                if (settings.enabled("actors") && !d.hidden) spawn(a);
              } catch (RuntimeException ex) {
                plugin.getLogger().warning("actors/" + id + ".yml: " + ex.getMessage());
              }
            });
    ensureBehaviors();
  }

  public List<String> ids() {
    return actors.values().stream().filter(a -> !a.pendingDeletion).map(ManagedActor::id).toList();
  }

  public List<ManagedActor> list() {
    return actors.values().stream().filter(a -> !a.pendingDeletion).toList();
  }

  public ManagedActor get(String id) {
    ManagedActor actor = actors.get(id);
    if (actor == null || actor.pendingDeletion)
      throw new IllegalArgumentException(
          "Actor '" + id + "' does not exist. Create it with /actor create " + id + ".");
    return actor;
  }

  public ManagedActor create(String id, String type, Location at) {
    settings.require("actors");
    Checks.id(id);
    if (actors.containsKey(id))
      throw new IllegalArgumentException("Actor '" + id + "' already exists.");
    if (actors.size() >= settings.limit("actors"))
      throw new IllegalArgumentException("Actor limit reached.");
    ActorDefinition definition = new ActorDefinition(id, type.toUpperCase(Locale.ROOT), at);
    if (settings.npcIdentities().enabled()) chooseIdentity(definition);
    else if (blockedIdentity.test(id))
      throw new IllegalArgumentException("Actor name is blacklisted.");
    var defaults = settings.file("config");
    definition.immortal = defaults.getBoolean("actors.defaults.immortal", false);
    definition.hittable = defaults.getBoolean("actors.defaults.hittable", true);
    definition.collidable = defaults.getBoolean("actors.defaults.collidable", true);
    definition.nametag = defaults.getBoolean("actors.defaults.nametag", true);
    definition.tablist = defaults.getBoolean("actors.defaults.tablist", false);
    definition.lookNearby = defaults.getBoolean("actors.defaults.look-nearby");
    definition.wander = defaults.getBoolean("actors.defaults.wander");
    definition.autoplay = defaults.getBoolean("actors.defaults.autoplay", true);
    ManagedActor actor = new ManagedActor(definition);
    spawn(actor);
    actors.put(id, actor);
    save(actor);
    return actor;
  }

  private void spawn(ManagedActor actor) {
    if (actor.pendingDeletion)
      throw new IllegalArgumentException("This NPC was deleted after death.");
    ActorDefinition d = actor.definition;
    ActorBackend backend = d.type.equals("PLAYER") ? players : mobs;
    if (backend == null)
      throw new IllegalArgumentException(
          "PLAYER actors require Citizens. Install a compatible Citizens build or create a ZOMBIE"
              + " actor.");
    ticket(actor, d.location.getChunk());
    try {
      actor.handle = backend.spawn(d);
    } catch (RuntimeException ex) {
      unticket(actor);
      throw ex;
    }
    LivingEntity e = actor.entity().orElse(null);
    if (e == null) {
      despawn(actor);
      throw new IllegalArgumentException(
          "Actor spawn was rejected by the world or another plugin. Check difficulty and spawn"
              + " protection.");
    }
    entities.put(e.getUniqueId(), actor);
    actor.nextWander = 0;
    actor.handle.onEntityChanged(
        replacement -> {
          if (actor.pendingDeletion || closing) return;
          entities.values().removeIf(value -> value == actor);
          entities.put(replacement.getUniqueId(), actor);
          var update = actor.afterRefresh;
          actor.afterRefresh = null;
          if (update != null) update.accept(replacement);
          presentation(actor);
        });
    e.setCollidable(d.collidable);
    e.setCustomNameVisible(d.nametag);
    e.setGlowing(d.glowing);
    if (e instanceof Player player) {
      player.getInventory().setStorageContents(d.inventory);
      player.getInventory().setHeldItemSlot(d.heldSlot);
      player.setSneaking(d.sneaking);
      player.setSprinting(d.sprinting);
    }
    e.setPose(d.pose, true);
    actor.handle.name(d.name);
    EntityEquipment eq = e.getEquipment();
    if (eq != null) {
      eq.setItemInMainHand(d.equipment[0]);
      eq.setItemInOffHand(d.equipment[1]);
      eq.setHelmet(d.equipment[2]);
      eq.setChestplate(d.equipment[3]);
      eq.setLeggings(d.equipment[4]);
      eq.setBoots(d.equipment[5]);
      if (!(e instanceof Player)) {
        eq.setItemInMainHandDropChance(0);
        eq.setItemInOffHandDropChance(0);
        eq.setHelmetDropChance(0);
        eq.setChestplateDropChance(0);
        eq.setLeggingsDropChance(0);
        eq.setBootsDropChance(0);
      }
    }
    presentation(actor);
    spawned.accept(actor);
  }

  /** Reapply current identity after a replay snapshot or Citizens refresh. */
  public void presentation(ManagedActor actor) {
    actor
        .entity()
        .ifPresent(
            e -> {
              e.setCustomNameVisible(actor.definition.nametag);
              e.setCollidable(actor.definition.collidable);
              if (e instanceof Player p) {
                p.displayName(dev.easyscripting.config.Messages.rich(actor.definition.name));
                p.playerListName(dev.easyscripting.config.Messages.rich(actor.definition.name));
              }
            });
    if (actor.handle != null) {
      actor.handle.appearance(actor.definition.nametag, actor.definition.collidable);
      actor.handle.tablist(actor.definition.tablist);
    }
  }

  public void save(ManagedActor actor) {
    if (actor.pendingDeletion) return;
    if (actor.handle != null) actor.handle.captureSkin(actor.definition);
    actor
        .entity()
        .ifPresent(
            e -> {
              actor.definition.location = e.getLocation();
              actor.definition.pose = e.getPose();
              actor.definition.glowing = e.isGlowing();
              if (e instanceof Player player) {
                actor.definition.inventory = player.getInventory().getStorageContents();
                actor.definition.heldSlot = player.getInventory().getHeldItemSlot();
                actor.definition.sneaking = player.isSneaking();
                actor.definition.sprinting = player.isSprinting();
              }
              EntityEquipment eq = e.getEquipment();
              if (eq != null)
                actor.definition.equipment =
                    new ItemStack[] {
                      eq.getItemInMainHand(),
                      eq.getItemInOffHand(),
                      eq.getHelmet(),
                      eq.getChestplate(),
                      eq.getLeggings(),
                      eq.getBoots()
                    };
            });
    store.save("actors", actor.id(), actor.definition.yaml());
    ensureBehaviors();
  }

  public void remove(String id) {
    ManagedActor actor = get(id);
    removed.accept(id);
    despawn(actor);
    actors.remove(id);
    store.delete("actors", id);
    deleted.accept(actor.definition);
  }

  private void despawn(ManagedActor actor) {
    if (actor.handle != null) {
      entities.values().removeIf(a -> a == actor);
      actor.handle.remove();
      actor.handle = null;
    }
    unticket(actor);
  }

  private void ticket(ManagedActor actor, Chunk chunk) {
    if (chunk.equals(actor.chunk)) return;
    chunk.addPluginChunkTicket(plugin);
    chunkTickets.merge(chunk, 1, Integer::sum);
    unticket(actor);
    actor.chunk = chunk;
  }

  private void unticket(ManagedActor actor) {
    if (actor.chunk == null) return;
    Chunk chunk = actor.chunk;
    actor.chunk = null;
    int remaining = chunkTickets.getOrDefault(chunk, 1) - 1;
    if (remaining <= 0) {
      chunkTickets.remove(chunk);
      chunk.removePluginChunkTicket(plugin);
    } else chunkTickets.put(chunk, remaining);
  }

  public void hidden(String id, boolean hide) {
    if (!hide) available(id);
    ManagedActor actor = get(id);
    if (hide) {
      removed.accept(id);
      despawn(actor);
    } else if (actor.entity().isEmpty()) spawn(actor);
    actor.definition.hidden = hide;
    save(actor);
  }

  public void respawn(String id) {
    available(id);
    ManagedActor a = get(id);
    despawn(a);
    spawn(a);
    a.definition.hidden = false;
    save(a);
  }

  public void copy(String id, String newId, Location location) {
    ManagedActor source = get(id);
    save(source);
    if (actors.containsKey(Checks.id(newId)))
      throw new IllegalArgumentException("Actor already exists: " + newId);
    if (actors.size() >= settings.limit("actors"))
      throw new IllegalArgumentException("Actor limit reached.");
    ActorDefinition copy = ActorDefinition.read(newId, source.definition.yaml());
    copy.location = location;
    copy.hidden = false;
    prepareCopyIdentity(copy, settings.npcIdentities().enabled(), this::chooseIdentity);
    ManagedActor a = new ManagedActor(copy);
    spawn(a);
    actors.put(newId, a);
    save(a);
  }

  public void set(String id, String key, String value) {
    editable(id, key);
    ManagedActor a = get(id);
    ActorDefinition d = a.definition;
    if ((key.equals("name") || key.equals("skin")) && blockedIdentity.test(value))
      throw new IllegalArgumentException("Actor identity is blacklisted.");
    if (key.equals("name")
        && (retiredIdentity.test(value)
            || (identityProvider != null && identityProvider.reservedRealName(value))))
      throw new IllegalArgumentException(
          "That username belongs to a dead identity, joined real player, or operator.");
    switch (key) {
      case "name" -> {
        if (value.length() > 48)
          throw new IllegalArgumentException("Name must be at most 48 characters.");
        if (a.handle != null) a.handle.name(value);
        d.name = value;
      }
      case "skin" -> {
        if (!d.type.equals("PLAYER"))
          throw new IllegalArgumentException(
              "Player skins require a PLAYER actor. Mob actors keep their entity appearance.");
        if (!value.matches("[A-Za-z0-9_]{1,16}"))
          throw new IllegalArgumentException(
              "Skin owner must be a Java account name (1..16 letters, digits or underscores).");
        if (a.handle != null) a.handle.skin(value);
        d.skin = value;
        d.skinTexture = "";
        d.skinSignature = "";
      }
      case "group" -> d.group = Checks.id(value);
      case "mode" -> d.playbackMode = dev.easyscripting.recording.PlaybackMode.parse(value);
      case "recording" -> d.recording = Checks.id(value);
      case "immortal" -> d.immortal = Checks.bool(value);
      case "hittable" -> d.hittable = Checks.bool(value);
      case "collidable" -> {
        d.collidable = Checks.bool(value);
        a.entity().ifPresent(e -> e.setCollidable(d.collidable));
      }
      case "nametag" -> {
        d.nametag = Checks.bool(value);
        a.entity().ifPresent(e -> e.setCustomNameVisible(d.nametag));
      }
      case "tablist" -> {
        if (!d.type.equals("PLAYER"))
          throw new IllegalArgumentException("Only player NPCs can appear in the tab list.");
        d.tablist = Checks.bool(value);
      }
      case "look" -> d.lookNearby = Checks.bool(value);
      case "aggressive" -> d.aggressive = Checks.bool(value);
      case "wander" -> {
        d.wander = Checks.bool(value);
        if (d.wander) a.wanderHome = a.entity().map(Entity::getLocation).orElse(d.location).clone();
        if (!d.wander && a.handle != null) a.handle.stop();
      }
      case "pose" -> a.requireEntity().setPose(Checks.choice(Pose.class, value), true);
      case "glow" -> a.requireEntity().setGlowing(Checks.bool(value));
      case "sneak" -> {
        if (a.requireEntity() instanceof Player p) p.setSneaking(Checks.bool(value));
        else a.requireEntity().setPose(Checks.bool(value) ? Pose.SNEAKING : Pose.STANDING, true);
      }
      case "sprint" -> {
        if (a.requireEntity() instanceof Player p) p.setSprinting(Checks.bool(value));
        else
          throw new IllegalArgumentException(
              "Sprint flag requires a player actor; use move speed for mobs.");
      }
      default -> throw new IllegalArgumentException("Unknown actor setting '" + key + "'.");
    }
    presentation(a);
    save(a);
  }

  private void chooseIdentity(ActorDefinition definition) {
    List<String> actorNames = new ArrayList<>(), onlineNames = new ArrayList<>();
    for (ManagedActor actor : list()) actorNames.add(actor.definition.name);
    for (Player player : Bukkit.getOnlinePlayers()) {
      onlineNames.add(player.getName());
      if (player.getPlayerProfile().getName() != null)
        onlineNames.add(player.getPlayerProfile().getName());
    }
    List<String> occupied =
        identityReservations(
            actorNames, onlineNames, reservedIdentityNames.get(), definition.id);
    java.util.function.Predicate<String> blockedName =
        name ->
            blockedIdentity.test(name)
                || retiredIdentity.test(name)
                || (identityProvider != null && identityProvider.reservedRealName(name));
    var identity =
        settings
            .npcIdentities()
            .choose(
                occupied,
                blockedName,
                blockedIdentity,
                definition.type.equals("PLAYER"),
                definition.skin,
                java.util.concurrent.ThreadLocalRandom.current(),
                recentNames,
                identityProvider == null ? List.of() : identityProvider.usernames(),
                identityProvider == null ? List.of() : identityProvider.skinOwners());
    definition.name = identity.name();
    definition.skin = identity.skin();
    definition.skinTexture = "";
    definition.skinSignature = "";
    recentNames.addLast(identity.name());
    while (recentNames.size() > 8) recentNames.removeFirst();
    if (identityProvider != null) identityProvider.claim(identity.name());
  }

  static void prepareCopyIdentity(
      ActorDefinition copy,
      boolean generatedIdentities,
      Consumer<ActorDefinition> identityAllocator) {
    if (generatedIdentities) identityAllocator.accept(copy);
    else copy.name = copy.id;
  }

  static List<String> identityReservations(
      Collection<String> actorNames,
      Collection<String> onlineNames,
      Collection<String> externallyReservedNames,
      String actorId) {
    Map<String, String> unique = new LinkedHashMap<>();
    for (Collection<String> names :
        List.of(actorNames, onlineNames, externallyReservedNames, List.of(actorId)))
      for (String name : names)
        if (name != null && !name.isBlank())
          unique.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
    return List.copyOf(unique.values());
  }

  /**
   * Replace an actor's saved loadout and, when it is spawned, its live loadout. Hidden actors keep
   * the same kit for their next spawn. Mob slot zero is equipped in the main hand instead of being
   * duplicated in the reserve inventory.
   */
  public void applyKit(String id, ItemStack[] contents) {
    if (contents == null || contents.length != 41)
      throw new IllegalArgumentException("An actor kit must contain exactly 41 inventory slots.");
    ManagedActor actor = get(id);
    ItemStack[] kit = cloneItems(contents, 41);
    actor.definition.inventory = cloneItems(kit, 36);
    int mainSlot = actor.definition.type.equals("PLAYER") ? actor.definition.heldSlot : 0;
    actor.definition.equipment =
        new ItemStack[] {
          cloneItem(kit[mainSlot]),
          cloneItem(kit[40]),
          cloneItem(kit[39]),
          cloneItem(kit[38]),
          cloneItem(kit[37]),
          cloneItem(kit[36])
        };
    if (!actor.definition.type.equals("PLAYER")) actor.definition.inventory[0] = null;
    actor
        .entity()
        .ifPresent(
            entity -> {
              if (entity instanceof Player player) {
                player.closeInventory();
                player.getInventory().setContents(cloneItems(kit, 41));
                return;
              }
              EntityEquipment equipment = entity.getEquipment();
              if (equipment == null)
                throw new IllegalArgumentException("This actor has no equipment.");
              equipment.setItemInMainHand(cloneItem(kit[0]));
              equipment.setBoots(cloneItem(kit[36]));
              equipment.setLeggings(cloneItem(kit[37]));
              equipment.setChestplate(cloneItem(kit[38]));
              equipment.setHelmet(cloneItem(kit[39]));
              equipment.setItemInOffHand(cloneItem(kit[40]));
            });
    save(actor);
  }

  private static ItemStack[] cloneItems(ItemStack[] source, int length) {
    ItemStack[] copy = Arrays.copyOf(source, length);
    for (int slot = 0; slot < copy.length; slot++) copy[slot] = cloneItem(copy[slot]);
    return copy;
  }

  private static ItemStack cloneItem(ItemStack item) {
    return item == null ? null : item.clone();
  }

  /** Temporary possession removes the entity without changing its saved visibility or position. */
  public void suspend(String id) {
    ManagedActor actor = get(id);
    save(actor);
    actor.stop();
    despawn(actor);
  }

  public void resume(String id) {
    ManagedActor actor = get(id);
    if (settings.enabled("actors") && !actor.definition.hidden && actor.entity().isEmpty())
      spawn(actor);
    ensureBehaviors();
  }

  public void randomize(String id) {
    settings.require("actors");
    editable(id, "randomize");
    ManagedActor actor = get(id);
    // Choose completely before touching the live NPC, so pool exhaustion leaves it intact.
    ActorDefinition next = ActorDefinition.read(id, actor.definition.yaml());
    chooseIdentity(next);
    if (actor.handle != null) {
      if (next.type.equals("PLAYER")) actor.handle.skin(next.skin);
      actor.handle.name(next.name);
    }
    actor.definition.name = next.name;
    actor.definition.skin = next.skin;
    actor.definition.skinTexture = "";
    actor.definition.skinSignature = "";
    presentation(actor);
    save(actor);
  }

  public void teleport(String id, Location to) {
    available(id);
    ManagedActor a = get(id);
    a.stop();
    if (!a.requireEntity().teleport(to))
      throw new IllegalArgumentException("Actor teleport was cancelled.");
    a.wanderHome = to.clone();
    save(a);
  }

  public void attack(String id, LivingEntity target, double damage) {
    ManagedActor a = get(id);
    LivingEntity e = a.requireEntity();
    if (e.getWorld() != target.getWorld()
        || e.getLocation().distanceSquared(target.getLocation()) > 36)
      throw new IllegalArgumentException(
          "Attack target must be within 6 blocks in the same world.");
    a.look(target.getEyeLocation());
    e.swingMainHand();
    target.damage(damage, e);
  }

  public void kill(String id) {
    if (get(id).definition.immortal)
      throw new IllegalArgumentException("Turn Immortal OFF before killing this NPC.");
    get(id).requireEntity().setHealth(0);
  }

  public List<String> pattern(
      String prefix,
      String type,
      String shape,
      String side,
      int count,
      double spacing,
      Location anchor,
      Consumer<ManagedActor> initialize) {
    Checks.id(prefix);
    if (actors.size() + count > settings.limit("actors"))
      throw new IllegalArgumentException("Pattern would exceed actor limit.");
    if (prefix.length() > 44)
      throw new IllegalArgumentException("Pattern prefix must be at most 44 characters.");
    String normalizedType = type.toUpperCase(Locale.ROOT);
    if (normalizedType.equals("PLAYER")) {
      if (players == null)
        throw new IllegalArgumentException(
            "PLAYER actors require Citizens. Install it or choose a living mob type.");
    } else {
      EntityType entityType = Checks.choice(EntityType.class, normalizedType);
      if (!entityType.isAlive() || !entityType.isSpawnable())
        throw new IllegalArgumentException("Pattern type must be PLAYER or a spawnable living mob.");
    }
    List<String> ids = new ArrayList<>(count);
    for (int suffix = 1; ids.size() < count; suffix++) {
      String id = prefix + "_" + suffix;
      Checks.id(id);
      if (!actors.containsKey(id)) ids.add(id);
    }
    List<Location> nominal = PatternLayout.locations(shape, count, spacing, anchor, side);
    List<Location> locations = new ArrayList<>(count);
    Set<Long> columns = new HashSet<>();
    for (Location column : nominal) {
      int x = column.getBlockX(), z = column.getBlockZ();
      if (!column.getWorld().isChunkLoaded(x >> 4, z >> 4))
        throw new IllegalArgumentException(
            "Formation reaches unloaded terrain at " + x + ", " + z + "; load the area first.");
      if (!column.getWorld().getWorldBorder().isInside(column))
        throw new IllegalArgumentException(
            "Formation reaches outside the world border at " + x + ", " + z + ".");
      long key = ((long) x << 32) ^ (z & 0xffffffffL);
      if (!columns.add(key))
        throw new IllegalArgumentException(
            "Formation positions overlap after block alignment; increase spacing.");
      Location safe = ActorWandering.highestGround(column);
      if (safe == null)
        throw new IllegalArgumentException(
            "No safe highest surface at " + x + ", " + z + "; clear water, hazards and overhead blocks.");
      locations.add(safe);
    }
    List<String> made = new ArrayList<>();
    try {
      for (int i = 0; i < count; i++) {
        String id = ids.get(i);
        ManagedActor a = create(id, normalizedType, locations.get(i));
        made.add(id);
        a.definition.group = prefix;
        initialize.accept(a);
        save(a);
      }
    } catch (RuntimeException ex) {
      for (String id : made.reversed()) {
        try {
          remove(id);
        } catch (RuntimeException cleanup) {
          ex.addSuppressed(cleanup);
        }
      }
      throw ex;
    }
    return List.copyOf(made);
  }

  private void ensureBehaviors() {
    if (closing || !ticks.acceptingWork() || behaviorJob != null || actors.isEmpty()) return;
    behaviorJob =
        ticks.add(
            new TickEngine.Job() {
              int tick;

              public boolean tick() {
                boolean active = false;
                tick++;
                for (ManagedActor a : list()) {
                  if (!settings.enabled("actors")) continue;
                  LivingEntity e = a.entity().orElse(null);
                  if (e == null) continue;
                  active = true;
                  if (tick % 20 == 0) {
                    ticket(a, e.getLocation().getChunk());
                    // Citizens resolves skins asynchronously. Persist once its main-thread trait
                    // has data.
                    if (a.handle.captureSkin(a.definition))
                      store.save("actors", a.id(), a.definition.yaml());
                  }
                  if (leases.containsKey(a.id())) continue;
                  if (directed.test(a)) continue;
                  ActorDefinition d = a.definition;
                  if (Math.floorMod(tick + a.id().hashCode(), 5) == 0)
                    a.lookNearby(d.lookNearby || d.wander || a.navigating());
                  if (d.wander && tick >= a.nextWander && !a.navigating()) {
                    a.nextWander =
                        tick
                            + settings.actorAi().wanderInterval()
                            + Math.floorMod(a.id().hashCode(), 20);
                    Location goal =
                        ActorWandering.destination(
                            a, ActorService.this, settings.actorAi(), a.wanderHome);
                    if (goal != null) {
                      try {
                        a.move(goal, settings.actorAi().walkSpeed());
                      } catch (IllegalArgumentException ex) {
                        /* Retry a different safe point later. */
                      }
                    }
                  }
                }
                return active;
              }

              public void stopped() {
                behaviorJob = null;
              }
            });
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void damage(EntityDamageEvent event) {
    ManagedActor a = entities.get(event.getEntity().getUniqueId());
    if (a == null) return;
    boolean projectile =
        event instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof Projectile;
    if (ActorDamagePolicy.blocksDamage(a.definition.hittable, event.getCause(), projectile)) {
      event.setCancelled(true);
      return;
    }
    LivingEntity entity =
        event.getEntity() instanceof LivingEntity living ? living : a.requireEntity();
    if (a.definition.immortal && event.getFinalDamage() >= entity.getHealth()) {
      double maximum =
          Objects.requireNonNull(entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH))
              .getValue();
      double health = ImmortalDamage.healthBeforeHit(entity.getHealth(), maximum);
      // Citizens schedules removal after die(), even if Paper cancels its death event.
      // Keep lethal hits positive but nonlethal so the native hit/knockback path is retained.
      ImmortalDamage.limit(
          event.getDamage(),
          health - ImmortalDamage.floor(maximum),
          raw -> {
            event.setDamage(raw);
            return event.getFinalDamage();
          });
      if (health != entity.getHealth()) entity.setHealth(health);
    }
  }

  // LOWEST precedes Citizens' LOW death listener. Damage itself stays uncancelled,
  // so native hurt feedback, armor wear and knockback still happen on lethal hits.
  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void preventDeath(EntityDeathEvent event) {
    ManagedActor actor = entities.get(event.getEntity().getUniqueId());
    if (actor == null || !actor.definition.immortal) return;
    event.setReviveHealth(
        Math.min(
            1,
            Objects.requireNonNull(
                    event.getEntity().getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH))
                .getValue()));
    event.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void deathDrops(EntityDeathEvent event) {
    if (!entities.containsKey(event.getEntity().getUniqueId())) return;
    event.getDrops().clear();
    event.setDroppedExp(0);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void death(EntityDeathEvent event) {
    ManagedActor actor = entities.remove(event.getEntity().getUniqueId());
    if (actor == null) return;
    // Tombstone immediately: restoration and shutdown saves must not resurrect this definition.
    actor.pendingDeletion = true;
    store.delete("actors", actor.id());
    try {
      died.accept(actor.definition);
    } catch (RuntimeException error) {
      plugin
          .getLogger()
          .log(
              java.util.logging.Level.SEVERE,
              "Could not retire dead actor identity '" + actor.definition.name + "'",
              error);
    }
    try {
      deleted.accept(actor.definition);
    } catch (RuntimeException error) {
      plugin
          .getLogger()
          .log(
              java.util.logging.Level.SEVERE,
              "Could not clean up data owned by dead actor '" + actor.id() + "'",
              error);
    }
    if (settings.file("config").getBoolean("actors.announce-death-leave", true))
      Bukkit.broadcast(
          new dev.easyscripting.config.Messages(settings)
              .text(
                  "actor-left",
                  Map.of(
                      "name",
                      net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                          .plainText()
                          .serialize(
                              dev.easyscripting.config.Messages.rich(actor.definition.name)))));
    Runnable cleanup =
        () -> {
          try {
            removed.accept(actor.id());
          } finally {
            despawn(actor);
            actors.remove(actor.id(), actor);
            leases.remove(actor.id());
          }
        };
    // Defer backend destruction until the death event finishes, unless already shutting down.
    if (ticks.acceptingWork()) ticks.later(1, cleanup);
    else cleanup.run();
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void knockback(io.papermc.paper.event.entity.EntityKnockbackEvent event) {
    ManagedActor actor = entities.get(event.getEntity().getUniqueId());
    boolean projectile =
        event instanceof io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent hit
            && hit.getPushedBy() instanceof Projectile;
    if (actor != null
        && ActorDamagePolicy.blocksKnockback(
            actor.definition.hittable, event.getCause(), projectile)) event.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
  public void unload(WorldUnloadEvent event) {
    for (ManagedActor a : list())
      if (a.definition.location.getWorld().equals(event.getWorld())) {
        removed.accept(a.id());
        save(a);
        despawn(a);
      }
  }

  @Override
  public void close() {
    closing = true;
    if (behaviorJob != null) ticks.cancel(behaviorJob);
    for (ManagedActor a : List.copyOf(actors.values())) {
      try {
        save(a);
      } finally {
        despawn(a);
      }
    }
    actors.clear();
    leases.clear();
    mobs.close();
    if (players != null) players.close();
  }

  public final class ManagedActor implements Actor {
    public final ActorDefinition definition;
    private ActorBackend.Handle handle;
    private Chunk chunk;
    private boolean pendingDeletion;
    private java.util.function.Consumer<LivingEntity> afterRefresh;
    private Location wanderHome;
    private int nextWander;

    private ManagedActor(ActorDefinition definition) {
      this.definition = definition;
      this.wanderHome = definition.location.clone();
    }

    public String id() {
      return definition.id;
    }

    public String group() {
      return definition.group;
    }

    public Optional<LivingEntity> entity() {
      return Optional.ofNullable(pendingDeletion || handle == null ? null : handle.entity());
    }

    public boolean refreshing() {
      return !pendingDeletion && handle != null && handle.refreshing();
    }

    /** Replay cleanup must also apply if Stop coincides with a Citizens identity refresh. */
    public void whenReady(java.util.function.Consumer<LivingEntity> update) {
      if (refreshing()) afterRefresh = update;
      else entity().ifPresent(update);
    }

    public LivingEntity requireEntity() {
      return entity()
          .orElseThrow(
              () ->
                  new IllegalArgumentException(
                      "Actor '"
                          + id()
                          + "' is hidden, dead, or unavailable. Use /actor respawn "
                          + id()
                          + "."));
    }

    public void move(Location to, double speed) {
      requireEntity();
      handle.move(to, speed);
    }

    public void stop() {
      if (handle != null) {
        handle.stop();
        handle.look(null);
      }
    }

    public boolean navigating() {
      return handle != null && handle.navigating();
    }

    public void look(Location at) {
      if (handle != null) handle.look(at);
    }

    public void lookNearby(boolean enabled) {
      LivingEntity e = entity().orElse(null);
      if (e == null) return;
      Location target =
          enabled
              ? e
                  .getWorld()
                  .getNearbyPlayers(e.getLocation(), settings.actorAi().lookRadius())
                  .stream()
                  .filter(
                      p ->
                          ActorWandering.realVisiblePlayer(p, ActorService.this)
                              && e.hasLineOfSight(p))
                  .min(
                      Comparator.comparingDouble(
                          p -> p.getLocation().distanceSquared(e.getLocation())))
                  .map(Player::getEyeLocation)
                  .orElse(null)
              : null;
      look(target);
    }
  }
}
