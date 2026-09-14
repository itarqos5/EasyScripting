package dev.easyscripting.items;

import dev.easyscripting.core.Checks;
import dev.easyscripting.players.EntitySnapshot;
import dev.easyscripting.storage.YamlStore;
import java.util.*;
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

  public void save(String id, Player player) {
    save(id, player.getInventory().getContents());
  }

  public void save(String id, ItemStack[] items) {
    Checks.id(id);
    YamlConfiguration y = new YamlConfiguration();
    y.set("schema", 1);
    y.set(
        "contents", Arrays.stream(items).map(item -> item == null ? null : item.clone()).toList());
    kits.put(id, y);
    store.save("loadouts", id, y);
  }

  public ItemStack[] contents(String id) {
    YamlConfiguration y = kits.get(id);
    if (y == null) throw new IllegalArgumentException("Kit '" + id + "' does not exist.");
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
    save(destination, EntitySnapshot.items(contents, 41));
  }
}
