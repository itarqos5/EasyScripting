package dev.easyscripting.items;

import dev.easyscripting.core.Checks;
import dev.easyscripting.players.EntitySnapshot;
import dev.easyscripting.storage.YamlStore;
import java.util.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;

public final class KitService {
  private final YamlStore store;
  private final Map<String, YamlConfiguration> kits = new TreeMap<>();

  public KitService(YamlStore store) {
    this.store = store;
  }

  public void load() {
    kits.putAll(store.load("loadouts"));
  }

  public List<String> ids() {
    return List.copyOf(kits.keySet());
  }

  public boolean exists(String id) {
    return kits.containsKey(id);
  }

  public List<String> ids(CommandSender sender) {
    return kits.keySet().stream().filter(id -> canClaim(id, sender)).toList();
  }

  private YamlConfiguration definition(String id) {
    YamlConfiguration yaml = kits.get(id);
    if (yaml == null) throw new IllegalArgumentException("Kit '" + id + "' does not exist.");
    return yaml;
  }

  public KitAccess access(String id) {
    return KitAccess.read(definition(id));
  }

  public void access(String id, KitAccess policy) {
    YamlConfiguration yaml = new YamlConfiguration();
    definition(id).getValues(false).forEach(yaml::set);
    policy.write(yaml);
    store.save("loadouts", id, yaml);
    kits.put(id, yaml);
  }

  public boolean canClaim(String id, CommandSender sender) {
    definition(id);
    if (sender.isOp()) return true;
    try {
      return access(id).allows(false, sender instanceof Player p ? p.getUniqueId() : null);
    } catch (IllegalArgumentException ex) {
      return false; // Malformed policies never make a private kit public.
    }
  }

  public void requireClaim(String id, CommandSender sender) {
    if (!canClaim(id, sender))
      throw new IllegalArgumentException(
          "You cannot claim kit '" + id + "'. Ask an operator about its access settings.");
  }

  public void claim(String id, Player player) {
    requireClaim(id, player);
    if (player.isDead()) throw new IllegalArgumentException("Respawn before claiming a kit.");
    apply(id, player);
  }

  public void save(String id, Player player) {
    save(id, player.getInventory().getContents());
  }

  public void save(String id, ItemStack[] items) {
    Checks.id(id);
    save(id, items, kits.containsKey(id) ? access(id) : KitAccess.operators());
  }

  private void save(String id, ItemStack[] items, KitAccess policy) {
    Checks.id(id);
    YamlConfiguration y = new YamlConfiguration();
    y.set("schema", 1);
    policy.write(y);
    y.set(
        "contents",
        Arrays.stream(Arrays.copyOf(items, 41))
            .map(item -> item == null ? null : item.clone())
            .toList());
    store.save("loadouts", id, y);
    kits.put(id, y);
  }

  public ItemStack[] contents(String id) {
    YamlConfiguration y = definition(id);
    return EntitySnapshot.items(y.getList("contents", List.of()), 41);
  }

  public void apply(String id, LivingEntity entity) {
    ItemStack[] items = contents(id);
    if (entity instanceof Player p) {
      p.closeInventory();
      p.getInventory().setContents(items);
    } else {
      EntityEquipment eq = entity.getEquipment();
      if (eq == null) throw new IllegalArgumentException("This actor has no equipment.");
      eq.setItemInMainHand(items[0]);
      eq.setBoots(items[36]);
      eq.setLeggings(items[37]);
      eq.setChestplate(items[38]);
      eq.setHelmet(items[39]);
      eq.setItemInOffHand(items[40]);
    }
  }

  public void delete(String id) {
    contents(id);
    kits.remove(id);
    store.delete("loadouts", id);
  }

  public void create(String id) {
    if (kits.containsKey(id)) throw new IllegalArgumentException("Kit already exists: " + id);
    save(id, new ItemStack[41]);
  }

  public void export(String id) {
    contents(id);
    store.save("kit-exports", id, kits.get(id));
  }

  public List<String> exports() {
    return List.copyOf(store.load("kit-exports").keySet());
  }

  public void importExport(String source, String destination) {
    if (kits.containsKey(destination))
      throw new IllegalArgumentException("Kit already exists: " + destination);
    YamlConfiguration yaml = store.read("kit-exports", source);
    if (yaml.getInt("schema") != 1 || !yaml.isList("contents"))
      throw new IllegalArgumentException("Expected an EasyScripting kit YAML in kit-exports/.");
    List<?> contents = yaml.getList("contents", List.of());
    if (contents.size() > 41
        || contents.stream().anyMatch(item -> item != null && !(item instanceof ItemStack)))
      throw new IllegalArgumentException("Invalid kit item data; nothing was imported.");
    save(destination, EntitySnapshot.items(contents, 41), KitAccess.read(yaml));
  }
}
