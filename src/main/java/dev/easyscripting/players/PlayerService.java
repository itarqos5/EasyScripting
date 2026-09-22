package dev.easyscripting.players;

import dev.easyscripting.config.*;
import dev.easyscripting.core.*;
import dev.easyscripting.storage.YamlStore;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;

public final class PlayerService implements Listener, AutoCloseable {
  public static final List<String> FLAGS =
      List.of(
          "freeze",
          "halfheart",
          "keepinv",
          "no-hunger",
          "no-durability",
          "no-build",
          "no-break",
          "no-pvp",
          "lock-inventory",
          "lock-armor",
          "lock-pickup",
          "vanish",
          "pauseeffects");
  private final JavaPlugin plugin;
  private final Settings settings;
  private final YamlStore store;
  private final TickEngine ticks;
  private final Map<UUID, Set<String>> flags = new HashMap<>();
  private final Map<UUID, EntitySnapshot> takes = new HashMap<>();
  private final Map<UUID, String> reservations = new HashMap<>();

  public void available(UUID player) {
    if (reservations.containsKey(player))
      throw new IllegalArgumentException(
          "Player is busy with " + reservations.get(player) + ". Stop it first.");
  }

  public void reserve(UUID player, String owner) {
    available(player);
    reservations.put(player, owner);
  }

  public void release(UUID player, String owner) {
    reservations.remove(player, owner);
  }

  public boolean hasTake(UUID player) {
    return takes.containsKey(player);
  }

  public boolean hasPending(UUID player) {
    return pending.containsKey(player);
  }

  public void clearDeferred(UUID player) {
    pending.remove(player);
    store.delete("pending", player.toString());
  }

  private final Map<UUID, EntitySnapshot> pending = new HashMap<>();
  private final Map<UUID, LinkedHashMap<String, EntitySnapshot>> rollback = new HashMap<>();
  private final Map<UUID, List<PotionEffect>> pausedEffects = new HashMap<>();
  private final Map<UUID, Location> lastLocations = new HashMap<>();
  private final Map<String, UUID> lastNames = new HashMap<>();

  public PlayerService(JavaPlugin plugin, Settings settings, YamlStore store, TickEngine ticks) {
    this.plugin = plugin;
    this.settings = settings;
    this.store = store;
    this.ticks = ticks;
  }

  public void load() {
    YamlConfiguration y = store.read("state", "players");
    for (String id : y.getKeys(false))
      try {
        flags.put(UUID.fromString(id), new HashSet<>(y.getStringList(id)));
      } catch (IllegalArgumentException ex) {
        plugin.getLogger().warning("state/players.yml: invalid UUID " + id);
      }
    store
        .load("pending")
        .forEach(
            (id, doc) -> {
              try {
                pending.put(UUID.fromString(doc.getString("entity", "")), EntitySnapshot.read(doc));
              } catch (RuntimeException ex) {
                plugin.getLogger().warning("pending/" + id + ": " + ex.getMessage());
              }
            });
    store
        .load("rollback")
        .forEach(
            (id, doc) -> {
              try {
                UUID owner = UUID.fromString(doc.getString("entity", ""));
                rollback
                    .computeIfAbsent(owner, k -> new LinkedHashMap<>())
                    .put(id, EntitySnapshot.read(doc));
              } catch (RuntimeException ex) {
                plugin.getLogger().warning("rollback/" + id + ": " + ex.getMessage());
              }
            });
    store
        .load("positions")
        .forEach(
            (id, doc) -> {
              try {
                lastLocations.put(
                    UUID.fromString(doc.getString("uuid", "")),
                    Positions.read(doc.getConfigurationSection("location")));
                lastNames.put(
                    doc.getString("name", "").toLowerCase(Locale.ROOT),
                    UUID.fromString(doc.getString("uuid", "")));
              } catch (RuntimeException ex) {
                plugin.getLogger().warning("positions/" + id + ": " + ex.getMessage());
              }
            });
  }

  public Player player(String name) {
    Player p = Bukkit.getPlayerExact(name);
    if (p == null) throw new IllegalArgumentException("Player '" + name + "' is not online.");
    return p;
  }

  public boolean flag(UUID id, String flag) {
    return settings.enabled("players") && flags.getOrDefault(id, Set.of()).contains(flag);
  }

  public void flag(Player p, String flag, boolean enabled) {
    if (!FLAGS.contains(flag))
      throw new IllegalArgumentException("Unknown player flag '" + flag + "'.");
    Set<String> set = flags.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
    if (enabled) set.add(flag);
    else set.remove(flag);
    if (flag.equals("vanish"))
      for (Player viewer : Bukkit.getOnlinePlayers()) {
        if (enabled && !viewer.hasPermission("easyscripting.see.vanish"))
          viewer.hidePlayer(plugin, p);
        else viewer.showPlayer(plugin, p);
      }
    if (flag.equals("pauseeffects")) {
      if (enabled) {
        pausedEffects.putIfAbsent(p.getUniqueId(), List.copyOf(p.getActivePotionEffects()));
        for (PotionEffect e : p.getActivePotionEffects())
          p.addPotionEffect(
              new PotionEffect(
                  e.getType(),
                  PotionEffect.INFINITE_DURATION,
                  e.getAmplifier(),
                  e.isAmbient(),
                  e.hasParticles(),
                  e.hasIcon()));
      } else restoreEffects(p);
    }
    saveFlags();
  }

  public void control(Player p, String operation, String value) {
    settings.require("players");
    if (FLAGS.contains(operation)) {
      flag(p, operation, Checks.bool(value));
      return;
    }
    switch (operation) {
      case "health" ->
          p.setHealth(
              Checks.decimal(
                  value,
                  .01,
                  Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue()));
      case "heal" -> {
        p.setHealth(Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue());
        p.setFireTicks(0);
      }
      case "feed" -> {
        p.setFoodLevel(20);
        p.setSaturation(20);
      }
      case "hunger" -> {
        p.setFoodLevel(Checks.integer(value, 0, 20));
        p.setSaturation(0);
      }
      case "gamemode" -> p.setGameMode(Checks.choice(GameMode.class, value));
      case "flight" -> p.setAllowFlight(Checks.bool(value));
      case "invulnerable" -> p.setInvulnerable(Checks.bool(value));
      case "invisible" -> p.setInvisible(Checks.bool(value));
      case "glow" -> p.setGlowing(Checks.bool(value));
      case "speed" -> p.setWalkSpeed((float) Checks.decimal(value, 0, 1));
      case "fire" -> p.setFireTicks(Checks.integer(value, 0, 12000));
      case "clear-effects" -> {
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
      }
      default ->
          throw new IllegalArgumentException("Unknown player operation '" + operation + "'.");
    }
  }

  public void potion(Player p, String name) {
    var preset = settings.file("potions").getConfigurationSection("presets." + Checks.id(name));
    if (preset == null)
      throw new IllegalArgumentException("potions.yml: preset '" + name + "' does not exist.");
    for (String effect : preset.getKeys(false)) {
      PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(effect));
      if (type == null) throw new IllegalArgumentException("potions.yml: unknown effect " + effect);
      p.addPotionEffect(
          new PotionEffect(
              type,
              Checks.integer(preset.getString(effect + ".ticks", "1200"), 1, 72000),
              Checks.integer(preset.getString(effect + ".amplifier", "0"), 0, 10)));
    }
  }

  public EntitySnapshot capture(LivingEntity entity) {
    available(entity.getUniqueId());
    EntitySnapshot snapshot = EntitySnapshot.capture(entity);
    if (entity instanceof Player player) {
      snapshot
          .yaml()
          .set("controls.flags", List.copyOf(flags.getOrDefault(player.getUniqueId(), Set.of())));
      snapshot
          .yaml()
          .set(
              "controls.paused-effects",
              pausedEffects.getOrDefault(player.getUniqueId(), List.of()));
      afterCapture.accept(player, snapshot);
    }
    return snapshot;
  }

  public void restore(LivingEntity entity, EntitySnapshot snapshot) {
    if (entity instanceof Player player && snapshot.yaml().contains("controls.flags")) {
      restoreEffects(player);
      Set<String> saved = new HashSet<>(snapshot.yaml().getStringList("controls.flags"));
      flags.put(player.getUniqueId(), saved);
      for (Player viewer : Bukkit.getOnlinePlayers()) {
        if (saved.contains("vanish") && !viewer.hasPermission("easyscripting.see.vanish"))
          viewer.hidePlayer(plugin, player);
        else viewer.showPlayer(plugin, player);
      }
      if (saved.contains("pauseeffects"))
        pausedEffects.put(
            player.getUniqueId(),
            snapshot.yaml().getList("controls.paused-effects", List.of()).stream()
                .filter(PotionEffect.class::isInstance)
                .map(PotionEffect.class::cast)
                .toList());
      saveFlags();
    }
    snapshot.restore(entity);
    if (entity instanceof Player p) afterRestore.accept(p, snapshot);
  }

  private java.util.function.BiConsumer<Player, EntitySnapshot>
      afterRestore = (player, snapshot) -> {},
      afterCapture = (player, snapshot) -> {};

  public void onRestore(java.util.function.BiConsumer<Player, EntitySnapshot> callback) {
    afterRestore = callback;
  }

  public void onCapture(java.util.function.BiConsumer<Player, EntitySnapshot> callback) {
    afterCapture = callback;
  }

  public void snapshot(Player p) {
    available(p.getUniqueId());
    if (takes.containsKey(p.getUniqueId()))
      throw new IllegalArgumentException(
          "A take snapshot already exists; reset or discard it first.");
    takes.put(p.getUniqueId(), capture(p));
  }

  public void reset(Player p) {
    available(p.getUniqueId());
    EntitySnapshot s = takes.get(p.getUniqueId());
    if (s == null) throw new IllegalArgumentException("No take snapshot exists for " + p.getName());
    if (p.isDead()) defer(p.getUniqueId(), s);
    else restore(p, s);
  }

  public void discard(Player p) {
    takes.remove(p.getUniqueId());
  }

  public void defer(UUID player, EntitySnapshot snapshot) {
    pending.put(player, snapshot);
    store.save("pending", player.toString(), snapshot.yaml());
  }

  public void rollbackSave(Player p) {
    String id =
        p.getUniqueId().toString().replace("-", "")
            + "_"
            + Long.toString(System.currentTimeMillis(), 36);
    LinkedHashMap<String, EntitySnapshot> records =
        rollback.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>());
    EntitySnapshot snapshot = EntitySnapshot.capture(p);
    records.put(id, snapshot);
    store.save("rollback", id, snapshot.yaml());
    while (records.size() > 10) {
      String oldest = records.keySet().iterator().next();
      records.remove(oldest);
      store.delete("rollback", oldest);
    }
  }

  public List<String> history(Player p) {
    return new ArrayList<>(rollback.getOrDefault(p.getUniqueId(), new LinkedHashMap<>()).keySet());
  }

  public void rollback(Player p, String id) {
    EntitySnapshot s = rollback.getOrDefault(p.getUniqueId(), new LinkedHashMap<>()).get(id);
    if (s == null)
      throw new IllegalArgumentException(
          "Inventory snapshot '" + id + "' not found for " + p.getName());
    s.restoreInventory(p);
  }

  public Location lastLocation(UUID id) {
    Location l = lastLocations.get(id);
    if (l == null) throw new IllegalArgumentException("No saved location for this player.");
    return l.clone();
  }

  public Location lastLocation(String name) {
    UUID id = lastNames.get(name.toLowerCase(Locale.ROOT));
    if (id == null)
      throw new IllegalArgumentException(
          "No saved logout position for '"
              + name
              + "'. They must have disconnected while EasyScripting was installed.");
    return lastLocation(id);
  }

  private void saveFlags() {
    YamlConfiguration y = new YamlConfiguration();
    flags.forEach((id, f) -> y.set(id.toString(), List.copyOf(f)));
    store.save("state", "players", y);
  }

  private void restoreEffects(Player p) {
    List<PotionEffect> effects = pausedEffects.remove(p.getUniqueId());
    if (effects != null) {
      for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
      p.addPotionEffects(effects);
    }
  }

  public void refresh() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      for (Player viewer : Bukkit.getOnlinePlayers()) {
        if (flag(player.getUniqueId(), "vanish")
            && !viewer.hasPermission("easyscripting.see.vanish")) viewer.hidePlayer(plugin, player);
        else viewer.showPlayer(plugin, player);
      }
      if (!settings.enabled("players")) {
        restoreEffects(player);
        flags.getOrDefault(player.getUniqueId(), new HashSet<>()).remove("pauseeffects");
      }
    }
    saveFlags();
  }

  @EventHandler(ignoreCancelled = true)
  public void move(PlayerMoveEvent e) {
    if (flag(e.getPlayer().getUniqueId(), "freeze")
        && e.hasChangedPosition()
        && !(e instanceof PlayerTeleportEvent)) e.setTo(e.getFrom());
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void damage(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) return;
    if (e instanceof EntityDamageByEntityEvent hit) {
      Entity attacker = hit.getDamager();
      if (attacker instanceof Projectile projectile
          && projectile.getShooter() instanceof Entity source) attacker = source;
      if (attacker instanceof Player a
          && (flag(a.getUniqueId(), "no-pvp") || flag(p.getUniqueId(), "no-pvp"))) {
        e.setCancelled(true);
        return;
      }
    }
    if (HalfHeartPolicy.intercept(
        flag(p.getUniqueId(), "halfheart"),
        e.getFinalDamage(),
        p.getHealth(),
        p.getInventory().getItemInMainHand().getType(),
        p.getInventory().getItemInOffHand().getType())) {
      // Keep the hit and take its damage away rather than cancelling it. Cancelling swallows the
      // whole attack: no knockback, no hurt animation, no mace smash, no hit sound. Zeroing the
      // base damage recalculates every dependent modifier to zero, so the blow still lands and
      // still throws the player around; it simply costs nothing.
      e.setDamage(0);
      p.setHealth(
          Math.min(1, Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue()));
    }
  }

  @EventHandler(ignoreCancelled = true)
  public void hunger(FoodLevelChangeEvent e) {
    if (flag(e.getEntity().getUniqueId(), "no-hunger")) e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void durability(PlayerItemDamageEvent e) {
    if (flag(e.getPlayer().getUniqueId(), "no-durability")) e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void build(BlockPlaceEvent e) {
    if (flag(e.getPlayer().getUniqueId(), "no-build")) e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void breakBlock(BlockBreakEvent e) {
    if (flag(e.getPlayer().getUniqueId(), "no-break")) e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void pickup(EntityPickupItemEvent e) {
    if (flag(e.getEntity().getUniqueId(), "lock-pickup")) e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void drop(PlayerDropItemEvent e) {
    if (flag(e.getPlayer().getUniqueId(), "lock-inventory")) e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void swap(PlayerSwapHandItemsEvent e) {
    if (flag(e.getPlayer().getUniqueId(), "lock-inventory")) e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void click(InventoryClickEvent e) {
    UUID id = e.getWhoClicked().getUniqueId();
    if (flag(id, "lock-inventory")) e.setCancelled(true);
    if (flag(id, "lock-armor")
        && (e.getSlotType() == InventoryType.SlotType.ARMOR || e.isShiftClick()))
      e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void drag(InventoryDragEvent e) {
    UUID id = e.getWhoClicked().getUniqueId();
    if (flag(id, "lock-inventory") || flag(id, "lock-armor")) e.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void death(PlayerDeathEvent e) {
    Player p = e.getEntity();
    // Fallback if a totem could not resurrect (for example, a cancelled resurrection event).
    // Held totems reach vanilla first, and the halfheart flag remains enabled after a normal pop.
    if (flag(p.getUniqueId(), "halfheart")) {
      e.setReviveHealth(
          Math.min(1, Objects.requireNonNull(p.getAttribute(Attribute.MAX_HEALTH)).getValue()));
      e.setCancelled(true);
      return;
    }
    if (e.isCancelled()) return;
    if (settings.enabled("inventory")) rollbackSave(p);
    if (flag(p.getUniqueId(), "keepinv")) {
      if (settings.file("death").getBoolean("keep-inventory-respects-vanishing", true)) {
        for (int slot = 0; slot < p.getInventory().getSize(); slot++) {
          org.bukkit.inventory.ItemStack item = p.getInventory().getItem(slot);
          if (item != null
              && item.containsEnchantment(org.bukkit.enchantments.Enchantment.VANISHING_CURSE))
            p.getInventory().setItem(slot, null);
        }
      }
      e.setKeepInventory(true);
      e.setKeepLevel(true);
      e.setDroppedExp(0);
      e.getDrops().clear();
    }
  }

  @EventHandler
  public void join(PlayerJoinEvent e) {
    Player p = e.getPlayer();
    for (Player target : Bukkit.getOnlinePlayers())
      if (flag(target.getUniqueId(), "vanish") && !p.hasPermission("easyscripting.see.vanish"))
        p.hidePlayer(plugin, target);
    if (flag(p.getUniqueId(), "vanish")) {
      e.joinMessage(null);
      flag(p, "vanish", true);
    }
    if (pending.containsKey(p.getUniqueId()))
      ticks.later(
          1,
          () -> {
            if (!p.isOnline() || p.isDead()) return;
            EntitySnapshot s = pending.get(p.getUniqueId());
            if (s == null) return;
            restore(p, s);
            pending.remove(p.getUniqueId());
            store.delete("pending", p.getUniqueId().toString());
          });
  }

  @EventHandler
  public void respawn(PlayerRespawnEvent e) {
    if (pending.containsKey(e.getPlayer().getUniqueId()))
      ticks.later(
          1,
          () -> {
            Player p = e.getPlayer();
            EntitySnapshot saved = pending.get(p.getUniqueId());
            if (saved != null && p.isOnline() && !p.isDead()) {
              restore(p, saved);
              pending.remove(p.getUniqueId());
              store.delete("pending", p.getUniqueId().toString());
            }
          });
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    Player p = e.getPlayer();
    if (flag(p.getUniqueId(), "vanish")) e.quitMessage(null);
    restoreEffects(p);
    flags.getOrDefault(p.getUniqueId(), new HashSet<>()).remove("pauseeffects");
    EntitySnapshot take = takes.remove(p.getUniqueId());
    if (take != null) defer(p.getUniqueId(), take);
    lastLocations.put(p.getUniqueId(), p.getLocation());
    lastNames.put(p.getName().toLowerCase(Locale.ROOT), p.getUniqueId());
    YamlConfiguration y = new YamlConfiguration();
    y.set("uuid", p.getUniqueId().toString());
    y.set("name", p.getName());
    y.createSection("location", Positions.encode(p.getLocation()));
    store.save("positions", p.getUniqueId().toString(), y);
    if (settings.enabled("inventory")) rollbackSave(p);
  }

  @Override
  public void close() {
    for (Player p : Bukkit.getOnlinePlayers()) {
      restoreEffects(p);
      flags.getOrDefault(p.getUniqueId(), new HashSet<>()).remove("pauseeffects");
      EntitySnapshot take = takes.get(p.getUniqueId());
      if (take != null) {
        if (p.isDead()) defer(p.getUniqueId(), take);
        else restore(p, take);
      }
      for (Player viewer : Bukkit.getOnlinePlayers()) viewer.showPlayer(plugin, p);
    }
    saveFlags();
  }
}
