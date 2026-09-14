package dev.easyscripting.utilities;

import dev.easyscripting.config.Messages;
import dev.easyscripting.core.Checks;
import dev.easyscripting.storage.YamlStore;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;

public final class VillagerService implements AutoCloseable {
  private final YamlStore store;
  private final Map<String, YamlConfiguration> templates = new TreeMap<>();
  private final Map<String, Villager> spawned = new HashMap<>();

  public VillagerService(YamlStore store) {
    this.store = store;
  }

  public void load() {
    templates.putAll(store.load("villagers"));
  }

  public List<String> ids() {
    return List.copyOf(templates.keySet());
  }

  public void create(String id, Location at) {
    Checks.id(id);
    if (templates.containsKey(id))
      throw new IllegalArgumentException("Villager template already exists.");
    YamlConfiguration y = new YamlConfiguration();
    y.set("schema", 1);
    y.set("name", id);
    y.set("profession", "FARMER");
    y.set("type", "PLAINS");
    y.set("level", 1);
    y.set("trades", List.of());
    templates.put(id, y);
    save(id);
    spawn(id, at);
  }

  private YamlConfiguration get(String id) {
    YamlConfiguration y = templates.get(id);
    if (y == null) throw new IllegalArgumentException("Villager template not found.");
    return y;
  }

  public void spawn(String id, Location at) {
    YamlConfiguration y = get(id);
    if (spawned.size() >= 100 && !spawned.containsKey(id))
      throw new IllegalArgumentException("Villager actor limit reached.");
    Villager old = spawned.remove(id);
    if (old != null) old.remove();
    Villager v = at.getWorld().spawn(at, Villager.class);
    v.setPersistent(false);
    v.setAI(false);
    v.setInvulnerable(true);
    spawned.put(id, v);
    apply(id, y, v);
  }

  private void apply(String id, YamlConfiguration y, Villager v) {
    v.customName(Messages.rich(y.getString("name", id)));
    v.setCustomNameVisible(true);
    Villager.Profession profession =
        Registry.VILLAGER_PROFESSION.get(
            NamespacedKey.minecraft(y.getString("profession", "farmer").toLowerCase(Locale.ROOT)));
    Villager.Type type =
        Registry.VILLAGER_TYPE.get(
            NamespacedKey.minecraft(y.getString("type", "plains").toLowerCase(Locale.ROOT)));
    if (profession == null || type == null)
      throw new IllegalArgumentException("Invalid villager profession or biome type.");
    v.setProfession(profession);
    v.setVillagerType(type);
    v.setVillagerLevel(Checks.integer(y.getString("level", "1"), 1, 5));
    List<MerchantRecipe> recipes = new ArrayList<>();
    for (Map<?, ?> record : y.getMapList("trades")) {
      if (!(record.get("result") instanceof ItemStack result)
          || !(record.get("cost") instanceof ItemStack cost))
        throw new IllegalArgumentException("Trade must contain result and cost ItemStacks.");
      MerchantRecipe recipe =
          new MerchantRecipe(result.clone(), Boolean.TRUE.equals(record.get("once")) ? 1 : 999999);
      recipe.addIngredient(cost.clone());
      recipe.setExperienceReward(false);
      recipes.add(recipe);
    }
    v.setRecipes(recipes);
  }

  public void set(String id, String option, String value) {
    YamlConfiguration y = get(id);
    switch (option) {
      case "name" -> y.set("name", value);
      case "profession" -> {
        if (Registry.VILLAGER_PROFESSION.get(
                NamespacedKey.minecraft(value.toLowerCase(Locale.ROOT)))
            == null) throw new IllegalArgumentException("Invalid profession.");
        y.set(option, value);
      }
      case "type" -> {
        if (Registry.VILLAGER_TYPE.get(NamespacedKey.minecraft(value.toLowerCase(Locale.ROOT)))
            == null) throw new IllegalArgumentException("Invalid biome type.");
        y.set(option, value);
      }
      case "level" -> y.set(option, Checks.integer(value, 1, 5));
      default ->
          throw new IllegalArgumentException("Option must be name, profession, type or level.");
    }
    if (spawned.containsKey(id)) apply(id, y, spawned.get(id));
    save(id);
  }

  public void trade(String id, ItemStack cost, ItemStack result, boolean once) {
    if (cost.getType().isAir() || result.getType().isAir())
      throw new IllegalArgumentException("Hold result in main hand and cost in offhand.");
    YamlConfiguration y = get(id);
    List<Map<?, ?>> trades = new ArrayList<>(y.getMapList("trades"));
    if (trades.size() >= 32) throw new IllegalArgumentException("Maximum 32 trades per villager.");
    trades.add(Map.of("cost", cost.clone(), "result", result.clone(), "once", once));
    y.set("trades", trades);
    if (spawned.containsKey(id)) apply(id, y, spawned.get(id));
    save(id);
  }

  public void clear(String id) {
    get(id).set("trades", List.of());
    if (spawned.containsKey(id)) apply(id, get(id), spawned.get(id));
    save(id);
  }

  public void delete(String id) {
    get(id);
    Villager v = spawned.remove(id);
    if (v != null) v.remove();
    templates.remove(id);
    store.delete("villagers", id);
  }

  private void save(String id) {
    store.save("villagers", id, get(id));
  }

  @Override
  public void close() {
    spawned.values().forEach(Entity::remove);
    spawned.clear();
  }
}
