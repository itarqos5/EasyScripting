package dev.easyscripting.world;

import dev.easyscripting.config.*;
import dev.easyscripting.players.PlayerService;
import dev.easyscripting.storage.YamlStore;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class LockService implements Listener {
  private final Settings settings;
  private final PlayerService players;
  private final YamlStore store;
  private final NamespacedKey frameKey;
  private final Set<String> containers = new HashSet<>();

  public LockService(JavaPlugin plugin, Settings settings, PlayerService players, YamlStore store) {
    this.settings = settings;
    this.players = players;
    this.store = store;
    frameKey = new NamespacedKey(plugin, "locked-frame");
  }

  public void load() {
    containers.addAll(store.read("state", "locks").getStringList("containers"));
  }

  private static String key(Block b) {
    return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
  }

  public void container(Player p, boolean lock) {
    Block block = p.getTargetBlockExact(6);
    if (block == null || !(block.getState() instanceof Container))
      throw new IllegalArgumentException("Look at a container within 6 blocks.");
    if (lock) containers.add(key(block));
    else containers.remove(key(block));
    YamlConfiguration y = new YamlConfiguration();
    y.set("containers", List.copyOf(containers));
    store.save("state", "locks", y);
  }

  public void frame(Player p, boolean lock) {
    var result =
        p.getWorld()
            .rayTraceEntities(
                p.getEyeLocation(),
                p.getEyeLocation().getDirection(),
                6,
                .3,
                e -> e instanceof ItemFrame);
    if (result == null || !(result.getHitEntity() instanceof ItemFrame frame))
      throw new IllegalArgumentException("Look at an item frame within 6 blocks.");
    if (lock) frame.getPersistentDataContainer().set(frameKey, PersistentDataType.BYTE, (byte) 1);
    else frame.getPersistentDataContainer().remove(frameKey);
  }

  private boolean locked(Inventory inventory) {
    InventoryHolder holder = inventory.getHolder();
    if (holder instanceof Container c) return containers.contains(key(c.getBlock()));
    if (holder instanceof DoubleChest c)
      return (c.getLeftSide() instanceof Container left
              && containers.contains(key(left.getBlock())))
          || (c.getRightSide() instanceof Container right
              && containers.contains(key(right.getBlock())));
    return false;
  }

  @EventHandler(ignoreCancelled = true)
  public void open(InventoryOpenEvent e) {
    if (settings.enabled("locks")
        && !e.getPlayer().hasPermission("easyscripting.locks.bypass")
        && locked(e.getInventory())) e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void hopper(InventoryMoveItemEvent e) {
    if (settings.enabled("locks") && (locked(e.getSource()) || locked(e.getDestination())))
      e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void breakBlock(BlockBreakEvent e) {
    if (!settings.file("moderation").getBoolean("break", true)
        && !e.getPlayer().hasPermission("easyscripting.world.bypass")) e.setCancelled(true);
    if (settings.enabled("locks")
        && containers.contains(key(e.getBlock()))
        && !e.getPlayer().hasPermission("easyscripting.locks.bypass")) e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void place(BlockPlaceEvent e) {
    if (!settings.file("moderation").getBoolean("build", true)
        && !e.getPlayer().hasPermission("easyscripting.world.bypass")) e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void pvp(EntityDamageByEntityEvent e) {
    Entity damager = e.getDamager();
    if (damager instanceof Projectile projectile
        && projectile.getShooter() instanceof Entity source) damager = source;
    if (!settings.file("moderation").getBoolean("pvp", true)
        && damager instanceof Player
        && e.getEntity() instanceof Player
        && !e.getEntity().hasMetadata("NPC")) e.setCancelled(true);
    if (settings.enabled("locks")
        && e.getEntity() instanceof ItemFrame frame
        && frame.getPersistentDataContainer().has(frameKey)
        && (!(damager instanceof Player p) || !p.hasPermission("easyscripting.locks.bypass")))
      e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void interact(PlayerInteractEntityEvent e) {
    if (settings.enabled("locks")
        && e.getRightClicked().getPersistentDataContainer().has(frameKey)
        && !e.getPlayer().hasPermission("easyscripting.locks.bypass")) e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void hanging(HangingBreakByEntityEvent e) {
    if (settings.enabled("locks")
        && e.getEntity().getPersistentDataContainer().has(frameKey)
        && (!(e.getRemover() instanceof Player p)
            || !p.hasPermission("easyscripting.locks.bypass"))) e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void equip(PlayerInteractEvent e) {
    if (!players.flag(e.getPlayer().getUniqueId(), "lock-armor")
        || !e.getAction().isRightClick()
        || e.getItem() == null) return;
    EquipmentSlot slot = e.getItem().getType().getEquipmentSlot();
    if (slot == EquipmentSlot.HEAD
        || slot == EquipmentSlot.CHEST
        || slot == EquipmentSlot.LEGS
        || slot == EquipmentSlot.FEET) e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void dispense(BlockDispenseArmorEvent e) {
    if (players.flag(e.getTargetEntity().getUniqueId(), "lock-armor")) e.setCancelled(true);
  }

  @EventHandler(ignoreCancelled = true)
  public void explode(EntityExplodeEvent e) {
    if (settings.enabled("locks")) e.blockList().removeIf(b -> containers.contains(key(b)));
  }

  @EventHandler(ignoreCancelled = true)
  public void explode(BlockExplodeEvent e) {
    if (settings.enabled("locks")) e.blockList().removeIf(b -> containers.contains(key(b)));
  }
}
