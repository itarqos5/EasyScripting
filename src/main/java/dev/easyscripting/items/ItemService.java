package dev.easyscripting.items;

import dev.easyscripting.config.*;
import dev.easyscripting.core.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemService implements Listener {
  private final JavaPlugin plugin;
  private final Settings settings;
  private final TickEngine ticks;
  private final Set<UUID> silentKicks = new HashSet<>();
  private java.util.function.Consumer<Player> freeze = player -> {};

  public void playerFreeze(java.util.function.Consumer<Player> operation) {
    freeze = operation;
  }

  private final NamespacedKey tool, destination, delay, count;

  public ItemService(JavaPlugin plugin, Settings settings, TickEngine ticks) {
    this.plugin = plugin;
    this.settings = settings;
    this.ticks = ticks;
    tool = new NamespacedKey(plugin, "tool");
    destination = new NamespacedKey(plugin, "destination");
    delay = new NamespacedKey(plugin, "delay");
    count = new NamespacedKey(plugin, "count");
  }

  public ItemStack held(Player p) {
    ItemStack item = p.getInventory().getItemInMainHand();
    if (item.getType().isAir())
      throw new IllegalArgumentException("Hold an item in your main hand.");
    return item;
  }

  public void edit(Player p, String operation, String value) {
    ItemStack item = held(p);
    ItemMeta meta = item.getItemMeta();
    switch (operation) {
      case "name" -> meta.displayName(Messages.rich(value));
      case "lore" -> meta.lore(Arrays.stream(value.split("\\|", -1)).map(Messages::rich).toList());
      case "unbreakable" -> meta.setUnbreakable(Checks.bool(value));
      case "repair" -> {
        if (!(meta instanceof Damageable d))
          throw new IllegalArgumentException("This item has no durability.");
        d.setDamage(0);
      }
      case "durability" -> {
        if (!(meta instanceof Damageable d))
          throw new IllegalArgumentException("This item has no durability.");
        d.setDamage(Checks.integer(value, 0, item.getType().getMaxDurability()));
      }
      case "amount" -> item.setAmount(Checks.integer(value, 1, item.getMaxStackSize()));
      default -> throw new IllegalArgumentException("Unknown item operation '" + operation + "'.");
    }
    item.setItemMeta(meta);
    p.getInventory().setItemInMainHand(item);
  }

  public void enchant(Player p, String name, int level) {
    Enchantment enchant =
        io.papermc.paper.registry.RegistryAccess.registryAccess()
            .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
            .get(NamespacedKey.minecraft(name));
    if (enchant == null) throw new IllegalArgumentException("Unknown enchantment '" + name + "'.");
    if (level < 0 || level > 255)
      throw new IllegalArgumentException("Enchantment level must be 0..255.");
    ItemStack item = held(p);
    if (level == 0) item.removeEnchantment(enchant);
    else item.addUnsafeEnchantment(enchant, level);
    p.getInventory().setItemInMainHand(item);
  }

  public void attribute(Player player, String name, double amount, String operation, String slot) {
    org.bukkit.attribute.Attribute attribute =
        Registry.ATTRIBUTE.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
    if (attribute == null)
      throw new IllegalArgumentException(
          "Unknown attribute '" + name + "'. Use names such as attack_damage or movement_speed.");
    Checks.decimal(Double.toString(amount), -1000, 1000);
    EquipmentSlotGroup group =
        switch (slot.toLowerCase(Locale.ROOT)) {
          case "hand" -> EquipmentSlotGroup.HAND;
          case "mainhand" -> EquipmentSlotGroup.MAINHAND;
          case "offhand" -> EquipmentSlotGroup.OFFHAND;
          case "head" -> EquipmentSlotGroup.HEAD;
          case "chest" -> EquipmentSlotGroup.CHEST;
          case "legs" -> EquipmentSlotGroup.LEGS;
          case "feet" -> EquipmentSlotGroup.FEET;
          case "any" -> EquipmentSlotGroup.ANY;
          default ->
              throw new IllegalArgumentException(
                  "Attribute slot must be hand, mainhand, offhand, head, chest, legs, feet or"
                      + " any.");
        };
    var mode = Checks.choice(org.bukkit.attribute.AttributeModifier.Operation.class, operation);
    ItemStack item = held(player);
    ItemMeta meta = item.getItemMeta();
    NamespacedKey key = new NamespacedKey(plugin, "item_" + name.toLowerCase(Locale.ROOT));
    var modifiers = meta.getAttributeModifiers(attribute);
    if (modifiers != null)
      for (var modifier : List.copyOf(modifiers))
        if (modifier.getKey().equals(key)) meta.removeAttributeModifier(attribute, modifier);
    meta.addAttributeModifier(
        attribute, new org.bukkit.attribute.AttributeModifier(key, amount, mode, group));
    item.setItemMeta(meta);
    player.getInventory().setItemInMainHand(item);
  }

  public void give(Player player, Material material, int amount) {
    if (!material.isItem() || material.isAir())
      throw new IllegalArgumentException("Material must be an item.");
    ItemStack item =
        new ItemStack(
            material, Checks.integer(Integer.toString(amount), 1, material.getMaxStackSize()));
    if (player.getInventory().firstEmpty() < 0)
      throw new IllegalArgumentException("Inventory is full; make a free slot first.");
    player.getInventory().addItem(item);
  }

  public void head(Player player, String name) {
    if (!name.matches("[A-Za-z0-9_]{1,16}"))
      throw new IllegalArgumentException("Head owner must be a Minecraft name.");
    ItemStack item = new ItemStack(Material.PLAYER_HEAD);
    SkullMeta meta = (SkullMeta) item.getItemMeta();
    meta.setPlayerProfile(Bukkit.createProfileExact(null, name));
    meta.displayName(
        Messages.rich(
            settings
                .file("items")
                .getString("head-name", "<white>{name}")
                .replace("{name}", name)));
    item.setItemMeta(meta);
    deliver(player, item);
  }

  public ItemStack tool(String kind) {
    Material material =
        switch (kind) {
          case "kickstick" -> Material.BLAZE_ROD;
          case "stasisrod" -> Material.FISHING_ROD;
          case "regionwand" -> Material.WOODEN_AXE;
          default -> throw new IllegalArgumentException("Unknown production tool.");
        };
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Messages.rich("<aqua>EasyScripting " + kind));
    meta.getPersistentDataContainer().set(tool, PersistentDataType.STRING, kind);
    item.setItemMeta(meta);
    return item;
  }

  public void deliver(Player player, ItemStack item) {
    if (player.getInventory().firstEmpty() < 0)
      throw new IllegalArgumentException("Inventory is full.");
    player.getInventory().addItem(item);
  }

  public boolean isTool(ItemStack item, String kind) {
    return item != null
        && item.hasItemMeta()
        && kind.equals(
            item.getItemMeta().getPersistentDataContainer().get(tool, PersistentDataType.STRING));
  }

  public void stasis(Player p, int pops, int ticksDelay, Location at) {
    if (pops < 1 || pops > 64 || ticksDelay < 0 || ticksDelay > 72000)
      throw new IllegalArgumentException("Stasis pops must be 1..64 and delay 0..72000 ticks.");
    ItemStack item = new ItemStack(Material.TOTEM_OF_UNDYING);
    ItemMeta meta = item.getItemMeta();
    meta.displayName(Messages.rich("<aqua>Stasis totem <gray>(" + pops + " pops)"));
    String loc =
        at.getWorld().getName()
            + ";"
            + at.getX()
            + ";"
            + at.getY()
            + ";"
            + at.getZ()
            + ";"
            + at.getYaw()
            + ";"
            + at.getPitch();
    var data = meta.getPersistentDataContainer();
    data.set(destination, PersistentDataType.STRING, loc);
    data.set(delay, PersistentDataType.INTEGER, ticksDelay);
    data.set(count, PersistentDataType.INTEGER, pops);
    item.setItemMeta(meta);
    deliver(p, item);
  }

  public void restock(Inventory inventory) {
    for (int slot = 0; slot < inventory.getSize(); slot++) {
      ItemStack item = inventory.getItem(slot);
      if (item != null && !item.getType().isAir()) {
        item.setAmount(item.getMaxStackSize());
        inventory.setItem(slot, item);
      }
    }
  }

  public void randomFill(Inventory inventory) {
    List<String> configured = settings.file("items").getStringList("random-fill-materials");
    List<Material> materials =
        configured.stream()
            .map(Material::matchMaterial)
            .filter(Objects::nonNull)
            .filter(Material::isItem)
            .toList();
    if (materials.isEmpty())
      throw new IllegalArgumentException(
          "items.yml: random-fill-materials must contain item materials.");
    for (int slot = 0; slot < inventory.getSize(); slot++)
      if (inventory.getItem(slot) == null || inventory.getItem(slot).getType().isAir()) {
        Material material =
            materials.get(
                java.util.concurrent.ThreadLocalRandom.current().nextInt(materials.size()));
        inventory.setItem(slot, new ItemStack(material, material.getMaxStackSize()));
      }
  }

  @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
  public void kick(EntityDamageByEntityEvent e) {
    if (!settings.enabled("items")
        || !(e.getDamager() instanceof Player p)
        || !(e.getEntity() instanceof Player victim)
        || !isTool(p.getInventory().getItemInMainHand(), "kickstick")) return;
    e.setCancelled(true);
    if (!p.hasPermission("easyscripting.moderation")) return;
    silentKicks.add(victim.getUniqueId());
    victim.kick(
        Messages.rich(
            settings.file("messages").getString("kick", "<gray>Removed from the current take.")));
    ticks.later(2, () -> silentKicks.remove(victim.getUniqueId()));
  }

  @EventHandler(ignoreCancelled = true)
  public void rod(PlayerInteractEntityEvent e) {
    if (!settings.enabled("items")
        || !e.getPlayer().hasPermission("easyscripting.player")
        || !isTool(e.getPlayer().getInventory().getItem(e.getHand()), "stasisrod")) return;
    e.setCancelled(true);
    if (e.getRightClicked() instanceof Player player) {
      if (!e.getPlayer().hasPermission("easyscripting.player.others")) return;
      freeze.accept(player);
    } else if (e.getRightClicked() instanceof LivingEntity living) {
      living.setAI(!living.hasAI());
      living.setVelocity(new org.bukkit.util.Vector());
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void quit(org.bukkit.event.player.PlayerQuitEvent event) {
    if (silentKicks.remove(event.getPlayer().getUniqueId())) event.quitMessage(null);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void resurrect(EntityResurrectEvent e) {
    if (!settings.enabled("items") || !(e.getEntity() instanceof Player p) || e.getHand() == null)
      return;
    EquipmentSlot hand = e.getHand();
    ItemStack item = p.getInventory().getItem(hand).clone();
    if (!item.hasItemMeta()) return;
    ItemMeta meta = item.getItemMeta();
    var data = meta.getPersistentDataContainer();
    String encoded = data.get(destination, PersistentDataType.STRING);
    if (encoded == null) return;
    int left = data.getOrDefault(count, PersistentDataType.INTEGER, 1);
    int wait = data.getOrDefault(delay, PersistentDataType.INTEGER, 0);
    if (left < 1 || left > 64 || wait < 0 || wait > 72000) {
      plugin
          .getLogger()
          .warning("Invalid stasis totem count or delay; leaving normal resurrection unchanged.");
      return;
    }
    // One resurrection consumes one totem, even if another plugin allowed stacked totems.
    item.setAmount(1);
    String[] loc = encoded.split(";");
    if (loc.length != 6) {
      plugin.getLogger().warning("Invalid stasis totem destination.");
      return;
    }
    Location at;
    try {
      at = Positions.parse(loc[0], loc[1], loc[2], loc[3], loc[4], loc[5]);
    } catch (IllegalArgumentException ex) {
      plugin.getLogger().warning(ex.getMessage());
      return;
    }
    if (left > 1) {
      data.set(count, PersistentDataType.INTEGER, left - 1);
      item.setItemMeta(meta);
      ticks.later(
          1,
          () -> {
            if (!p.isOnline()) return;
            ItemStack current = p.getInventory().getItem(hand);
            if (current.getType().isAir()) p.getInventory().setItem(hand, item);
            else if (p.getInventory().firstEmpty() >= 0) p.getInventory().addItem(item);
            else p.getWorld().dropItemNaturally(p.getLocation(), item);
          });
    } else
      ticks.later(
          Math.max(1, wait),
          () -> {
            if (p.isOnline() && !p.isDead()) p.teleport(at);
          });
  }
}
