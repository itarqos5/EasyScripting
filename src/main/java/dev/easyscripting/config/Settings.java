package dev.easyscripting.config;

import dev.easyscripting.core.Checks;
import dev.easyscripting.storage.YamlStore;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class Settings {
  public static final List<String> FEATURES =
      List.of(
          "actors",
          "scenes",
          "recording",
          "players",
          "identity",
          "kits",
          "warps",
          "items",
          "inventory",
          "locks",
          "death",
          "chat",
          "world",
          "regions",
          "teams",
          "villagers",
          "effects",
          "voice");
  private final JavaPlugin plugin;
  private final YamlStore store;
  private volatile Map<String, YamlConfiguration> files = Map.of();
  private volatile Map<String, Boolean> features = Map.of();
  private NpcIdentities npcIdentities;
  private final List<Runnable> changeListeners = new ArrayList<>();

  public void onChange(Runnable listener) {
    changeListeners.add(listener);
  }

  public Settings(JavaPlugin plugin, YamlStore store) {
    this.plugin = plugin;
    this.store = store;
  }

  public void load() {
    load(yaml -> {});
  }

  public void load(Consumer<YamlConfiguration> validateMenus) {
    Map<String, YamlConfiguration> next = new HashMap<>();
    for (String file :
        List.of(
            "config",
            "npc-identities",
            "messages",
            "features",
            "moderation",
            "recording",
            "items",
            "potions",
            "effects",
            "death",
            "permissions",
            "guis")) {
      Path path = plugin.getDataFolder().toPath().resolve(file + ".yml");
      if (!path.toFile().exists()) plugin.saveResource(file + ".yml", false);
      next.put(file, YamlStore.read(path));
      if (file.equals("guis")) {
        try (var input = plugin.getResource("guis.yml")) {
          inheritActorMenus(
              next.get(file),
              YamlConfiguration.loadConfiguration(
                  new java.io.InputStreamReader(
                      Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.io.IOException ex) {
          throw new IllegalStateException("Could not read bundled GUI defaults", ex);
        }
      }
    }
    YamlConfiguration config = next.get("config");
    bounded(config, "schema", 1, 1);
    bounded(config, "limits.actors", 1, 1000);
    bounded(config, "limits.active-scenes", 1, 100);
    bounded(config, "limits.actions-per-tick", 1, 10000);
    bounded(config, "limits.scene-actions", 1, 100000);
    bounded(config, "limits.recording-ticks", 20, 72000);
    bounded(config, "limits.region-blocks", 1, 1000000);
    bounded(config, "limits.region-blocks-per-tick", 1, 10000);
    bounded(config, "world.auto-clear-seconds", 0, 86400);
    bounded(config, "world.auto-clear-radius", 1, 128);
    Map<String, Boolean> toggles = new HashMap<>();
    for (String key : FEATURES) {
      Object value = next.get("features").get(key);
      if (!(value instanceof Boolean enabled))
        throw new IllegalArgumentException(
            "features.yml: " + key + " = " + value + "; expected true or false.");
      toggles.put(key, enabled);
    }
    validateMenus.accept(next.get("guis"));
    NpcIdentities nextIdentities = NpcIdentities.read(next.get("npc-identities"));
    files = Map.copyOf(next);
    features = Map.copyOf(toggles);
    npcIdentities = nextIdentities;
    changeListeners.forEach(Runnable::run);
  }

  private static void bounded(YamlConfiguration file, String key, int min, int max) {
    Object v = file.get(key);
    try {
      if (!(v instanceof Number))
        throw new IllegalArgumentException("expected a YAML integer, not a quoted string");
      Checks.integer(String.valueOf(v), min, max);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("config.yml: " + key + " = " + v + "; " + ex.getMessage());
    }
  }

  public YamlConfiguration file(String name) {
    return Objects.requireNonNull(files.get(name), name);
  }

  /** Merge new actor-menu leaves in memory; preserve customized values and the original file. */
  public static void inheritActorMenus(YamlConfiguration target, YamlConfiguration defaults) {
    for (String key : defaults.getKeys(true)) {
      if (defaults.isConfigurationSection(key)) continue;
      if ((key.startsWith("menus.actor")
              || key.startsWith("dynamic.actor-")
              || key.startsWith("dynamic.controls.actor-"))
          && !target.contains(key)) target.set(key, defaults.get(key));
    }
  }

  public int limit(String name) {
    return file("config").getInt("limits." + name);
  }

  public NpcIdentities npcIdentities() {
    return npcIdentities;
  }

  public boolean enabled(String key) {
    return features.getOrDefault(key, false);
  }

  public void require(String key) {
    if (!enabled(key))
      throw new IllegalArgumentException(
          "Feature '" + key + "' is disabled. Enable it in /es features.");
  }

  public void toggle(String key) {
    if (!FEATURES.contains(key))
      throw new IllegalArgumentException("Unknown feature '" + key + "'.");
    Map<String, Boolean> next = new HashMap<>(features);
    next.put(key, !enabled(key));
    features = Map.copyOf(next);
    file("features").set(key, enabled(key));
    persist("features");
    changeListeners.forEach(Runnable::run);
  }

  public void persist(String name) {
    store.write(plugin.getDataFolder().toPath().resolve(name + ".yml"), file(name).saveToString());
  }
}
