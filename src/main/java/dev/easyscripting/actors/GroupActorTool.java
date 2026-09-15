package dev.easyscripting.actors;

import dev.easyscripting.config.Messages;
import dev.easyscripting.config.Settings;
import dev.easyscripting.core.Checks;
import dev.easyscripting.items.KitService;
import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Persistent operator tool which creates one grounded, equipped group member per block click. */
public final class GroupActorTool implements Listener {
  public record Binding(String group, String kit, String type) {}

  private final Settings settings;
  private final Messages messages;
  private final ActorGroupService groups;
  private final KitService kits;
  private final NamespacedKey markerKey, groupKey, kitKey, typeKey;

  public GroupActorTool(
      JavaPlugin plugin,
      Settings settings,
      Messages messages,
      ActorGroupService groups,
      KitService kits) {
    this.settings = settings;
    this.messages = messages;
    this.groups = groups;
    this.kits = kits;
    markerKey = new NamespacedKey(plugin, "group_actor_tool");
    groupKey = new NamespacedKey(plugin, "group_actor_group");
    kitKey = new NamespacedKey(plugin, "group_actor_kit");
    typeKey = new NamespacedKey(plugin, "group_actor_type");
  }

  public String defaultType() {
    return settings.file("items").getString("group-actor-tool.actor-type", "PLAYER");
  }

  public List<String> kitIds() {
    return kits.ids();
  }

  public List<String> livingTypes() {
    return Arrays.stream(EntityType.values())
        .filter(EntityType::isAlive)
        .map(EntityType::name)
        .toList();
  }

  public void give(Player player, String group, String kit, String requestedType) {
    settings.require("actors");
    settings.require("kits");
    groups.get(group);
    kits.contents(kit);
    EntityType type = Checks.choice(EntityType.class, requestedType);
    if (!type.isAlive())
      throw new IllegalArgumentException("A group actor tool requires a living entity type.");

    var config = settings.file("items");
    Material material =
        Objects.requireNonNullElse(
            Material.matchMaterial(config.getString("group-actor-tool.material", "BLAZE_ROD")),
            Material.BLAZE_ROD);
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    Map<String, String> values =
        Map.of("{group}", group, "{kit}", kit, "{type}", type.name());
    meta.displayName(
        Messages.rich(
            replace(
                config.getString("group-actor-tool.name", "<gold>{group} actor tool"), values)));
    List<Component> lore =
        config.getStringList("group-actor-tool.lore").stream()
            .map(line -> Messages.rich(replace(line, values)))
            .toList();
    meta.lore(lore);
    meta.setEnchantmentGlintOverride(true);
    meta.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
    meta.getPersistentDataContainer().set(groupKey, PersistentDataType.STRING, group);
    meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, kit);
    meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());
    item.setItemMeta(meta);
    Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
    overflow
        .values()
        .forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
    messages.ok(
        player,
        "Created a "
            + type.name()
            + " actor tool for group '"
            + group
            + "' and kit '"
            + kit
            + "'. Right-click a block to use it.");
  }

  public Optional<Binding> binding(ItemStack item) {
    if (item == null || !item.hasItemMeta()) return Optional.empty();
    var data = item.getItemMeta().getPersistentDataContainer();
    if (!Byte.valueOf((byte) 1).equals(data.get(markerKey, PersistentDataType.BYTE)))
      return Optional.empty();
    String group = data.get(groupKey, PersistentDataType.STRING);
    String kit = data.get(kitKey, PersistentDataType.STRING);
    String type = data.get(typeKey, PersistentDataType.STRING);
    if (group == null || kit == null || type == null)
      throw new IllegalArgumentException("This group actor tool has incomplete binding data.");
    return Optional.of(new Binding(Checks.id(group), Checks.id(kit), type));
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void use(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK)
      return;
    Optional<Binding> selected;
    try {
      selected = binding(event.getPlayer().getInventory().getItemInMainHand());
    } catch (IllegalArgumentException error) {
      messages.error(event.getPlayer(), error.getMessage());
      return;
    }
    if (selected.isEmpty()) return;
    event.setCancelled(true);
    Player player = event.getPlayer();
    if (!player.isOp()) {
      messages.error(player, "Only current server operators can use group actor tools.");
      return;
    }
    try {
      settings.require("actors");
      settings.require("kits");
      Binding binding = selected.get();
      if (player.hasCooldown(event.getMaterial())) return;
      Location column =
          Objects.requireNonNull(event.getClickedBlock()).getLocation().add(0.5, 0, 0.5);
      Location spawn = ActorWandering.highestGround(column);
      if (spawn == null)
        throw new IllegalArgumentException(
            "That column has no loaded, safe standing surface. Choose another block.");
      spawn.setYaw(player.getLocation().getYaw());
      spawn.setPitch(0);
      ActorService.ManagedActor actor =
          groups.createMember(binding.group(), binding.kit(), binding.type(), spawn);
      int cooldown = settings.file("items").getInt("group-actor-tool.cooldown-ticks", 8);
      player.setCooldown(event.getMaterial(), cooldown);
      messages.ok(
          player,
          "Created "
              + actor.id()
              + " as "
              + actor.definition.name
              + " in group '"
              + binding.group()
              + "' with kit '"
              + binding.kit()
              + "'.");
    } catch (IllegalArgumentException | IllegalStateException error) {
      messages.error(player, error.getMessage());
    }
  }

  private static String replace(String value, Map<String, String> replacements) {
    String result = value;
    for (var replacement : replacements.entrySet())
      result = result.replace(replacement.getKey(), replacement.getValue());
    return result;
  }
}
