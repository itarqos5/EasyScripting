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
    boolean upgradeGui = false;
    for (String file :
        List.of(
            "config",
            "npc-identities",
            "nicknames",
            "kits",
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
      if (file.equals("messages") || file.equals("moderation")) {
        try (var input = plugin.getResource(file + ".yml")) {
          var defaults =
              YamlConfiguration.loadConfiguration(
                  new java.io.InputStreamReader(
                      Objects.requireNonNull(input), java.nio.charset.StandardCharsets.UTF_8));
          inheritMissing(next.get(file), defaults);
          if (file.equals("messages")) migrateMessages(next.get(file), defaults);
        } catch (java.io.IOException ex) {
          throw new IllegalStateException("Could not read bundled " + file + " defaults", ex);
        }
      }
      if (file.equals("guis")) {
        try (var input = plugin.getResource("guis.yml")) {
          upgradeGui = next.get(file).getInt("schema", 1) < 2;
          next.put(
              file,
              GuiSchema.prepare(
                  next.get(file),
                  YamlConfiguration.loadConfiguration(
                      new java.io.InputStreamReader(
                          Objects.requireNonNull(input),
                          java.nio.charset.StandardCharsets.UTF_8))));
        } catch (java.io.IOException ex) {
          throw new IllegalStateException("Could not read bundled GUI defaults", ex);
        }
      }
    }
    YamlConfiguration config = next.get("config");
    validateModeration(next.get("moderation"));
    validateNicknames(next.get("nicknames"));
    if (next.get("kits").getInt("schema") != 1
        || !(next.get("kits").get("max-provider-kits") instanceof Integer)
        || next.get("kits").getInt("max-provider-kits") < 1
        || next.get("kits").getInt("max-provider-kits") > 10000)
      throw new IllegalArgumentException(
          "kits.yml: use schema 1 and max-provider-kits from 1..10000.");
    for (String provider : List.of("PlayerKits2", "PlayerKits", "Essentials", "CMI"))
      if (!(next.get("kits").get("providers." + provider) instanceof Boolean))
        throw new IllegalArgumentException(
            "kits.yml: providers." + provider + " must be true or false.");
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
    for (String key : List.of("playback.knockback-pause-ticks", "playback.return-to-route-ticks")) {
      var recording = next.get("recording");
      if (recording.contains(key)) {
        int value = recording.getInt(key);
        if (!(recording.get(key) instanceof Integer) || value < 1 || value > 100)
          throw new IllegalArgumentException(
              "recording.yml: " + key + " must be an integer from 1 to 100.");
      }
    }
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
    if (ActorDefaults.migrate(config)) {
      Path path = plugin.getDataFolder().toPath().resolve("config.yml");
      Path backup = path.resolveSibling("config-before-0.1.5-" + UUID.randomUUID() + ".yml");
      try {
        java.nio.file.Files.copy(path, backup);
        store.write(path, config.saveToString()).join();
        plugin
            .getLogger()
            .info("New NPCs now default to mortal. Previous config: " + backup.getFileName());
      } catch (java.io.IOException | java.util.concurrent.CompletionException ex) {
        throw new IllegalStateException("Could not migrate NPC creation defaults", ex);
      }
    }
    if (upgradeGui) {
      Path path = plugin.getDataFolder().toPath().resolve("guis.yml");
      Path backup = path.resolveSibling("guis-v1-backup-" + UUID.randomUUID() + ".yml");
      try {
        java.nio.file.Files.copy(path, backup);
        store.write(path, next.get("guis").saveToString()).join();
        plugin
            .getLogger()
            .info("Installed GUI layout v2. Previous layout saved as " + backup.getFileName());
      } catch (java.io.IOException | java.util.concurrent.CompletionException ex) {
        throw new IllegalStateException(
            "Could not upgrade guis.yml. Check the original file and backup path " + backup, ex);
      }
    }
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

  public static void inheritMissing(YamlConfiguration target, YamlConfiguration defaults) {
    for (String key : defaults.getKeys(true))
      if (!defaults.isConfigurationSection(key) && !target.contains(key))
        target.set(key, defaults.get(key));
  }

  public static void migrateMessages(YamlConfiguration target, YamlConfiguration defaults) {
    Map<String, String> old =
        Map.of(
            "fake-death", "<gray><detail> died",
            "chat-muted", "<dark_gray>[<aqua>EasyScripting<dark_gray>] <red>Chat has been muted.",
            "chat-unmuted",
                "<dark_gray>[<aqua>EasyScripting<dark_gray>] <green>Chat has been unmuted.");
    old.forEach(
        (key, value) -> {
          if (value.equals(target.get(key))) target.set(key, defaults.get(key));
        });
  }

  public static void validateModeration(YamlConfiguration yaml) {
    String enabled = "broadcast-title.enabled";
    if (yaml.contains(enabled) && !(yaml.get(enabled) instanceof Boolean))
      throw new IllegalArgumentException("moderation.yml: " + enabled + " must be true or false.");
    for (String timing : List.of("fade-in-ticks", "stay-ticks", "fade-out-ticks")) {
      String key = "broadcast-title." + timing;
      int minimum = timing.equals("stay-ticks") ? 1 : 0;
      if (yaml.contains(key)
          && (!(yaml.get(key) instanceof Integer)
              || yaml.getInt(key) < minimum
              || yaml.getInt(key) > 1200))
        throw new IllegalArgumentException(
            "moderation.yml: " + key + " must be an integer from " + minimum + " to 1200.");
    }
  }

  public static void validateNicknames(YamlConfiguration yaml) {
    if (yaml.getInt("schema") != 1)
      throw new IllegalArgumentException("nicknames.yml: schema must be 1.");
    for (String key : List.of("api-enabled", "local-fallback"))
      if (!(yaml.get(key) instanceof Boolean))
        throw new IllegalArgumentException("nicknames.yml: " + key + " must be true or false.");
    if (!(yaml.get("api-timeout-millis") instanceof Integer)
        || yaml.getInt("api-timeout-millis") < 500
        || yaml.getInt("api-timeout-millis") > 10000)
      throw new IllegalArgumentException("nicknames.yml: api-timeout-millis must be 500..10000.");
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
