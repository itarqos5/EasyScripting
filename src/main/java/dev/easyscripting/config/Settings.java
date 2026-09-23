package dev.easyscripting.config;

import dev.easyscripting.core.Checks;
import dev.easyscripting.players.ObfuscatedNames;
import dev.easyscripting.storage.YamlStore;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
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
  private ActorAiSettings actorAi;
  private ActorCombatSettings actorCombat;

  public ActorCombatSettings actorCombat() {
    return actorCombat;
  }

  public ActorAiSettings actorAi() {
    return actorAi;
  }

  /** Every shipped configuration file, in the order it is loaded. */
  public static final List<String> FILES =
      List.of(
          "config",
          "actor-ai",
          "command-help",
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
          "guis");

  private volatile List<String> broken = List.of();

  /**
   * The files that could not be read or validated and are running on the copies bundled in the
   * jar. The files on disk are left exactly as their owner wrote them, so the list is what an
   * operator has to repair rather than a record of anything this plugin changed.
   */
  public List<String> broken() {
    return broken;
  }

  public boolean broken(String name) {
    return broken.contains(name);
  }

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

  /**
   * Load every configuration file, keeping a failure inside the file that caused it. A file whose
   * YAML cannot be parsed, or whose contents fail validation, is reported to the console in full
   * and answered in memory by the copy bundled in the jar. The file on disk is never rewritten:
   * its owner can correct it and run /es reload without having lost what they wrote, and the rest
   * of the plugin starts normally in the meantime.
   */
  public void load(Consumer<YamlConfiguration> validateMenus) {
    Map<String, YamlConfiguration> next = new HashMap<>();
    Set<String> documented = new HashSet<>();
    List<String> failed = new ArrayList<>();
    boolean upgradeGui = false;
    for (String file : FILES) {
      Path path = plugin.getDataFolder().toPath().resolve(file + ".yml");
      if (!path.toFile().exists()) plugin.saveResource(file + ".yml", false);
      YamlConfiguration defaults = bundled(file);
      try {
        YamlConfiguration disk = YamlStore.read(path);
        boolean legacyMenus = file.equals("guis") && disk.getInt("schema", 1) < 3;
        next.put(file, prepare(file, disk, defaults, validateMenus));
        upgradeGui |= legacyMenus;
      } catch (RuntimeException ex) {
        failed.add(file);
        plugin
            .getLogger()
            .log(
                Level.SEVERE,
                file
                    + ".yml could not be loaded, so EasyScripting is running on the copy bundled"
                    + " in its jar. Your file has not been changed or replaced. Correct the error"
                    + " below and run /es reload.",
                ex);
        // A bundled default that will not load is a packaging fault, and stays fatal.
        next.put(file, prepare(file, bundled(file), defaults, validateMenus));
      }
    }
    for (String file : FILES) {
      // A broken file keeps whatever its owner wrote, comments included; nothing is written back.
      if (failed.contains(file)) continue;
      if (inheritComments(next.get(file), bundled(file))) documented.add(file);
    }
    YamlConfiguration config = next.get("config");
    ActorAiSettings nextAi = ActorAiSettings.read(next.get("actor-ai"));
    ActorCombatSettings nextCombat = ActorCombatSettings.read(next.get("actor-ai"));
    NpcIdentities nextIdentities = NpcIdentities.read(next.get("npc-identities"));
    Map<String, Boolean> toggles = toggles(next.get("features"));
    if (!failed.contains("config") && ActorDefaults.migrate(config)) {
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
      Path backup = path.resolveSibling("guis-before-v3-" + UUID.randomUUID() + ".yml");
      try {
        java.nio.file.Files.copy(path, backup);
        store.write(path, next.get("guis").saveToString()).join();
        plugin
            .getLogger()
            .info("Installed GUI layout v3. Previous layout saved as " + backup.getFileName());
      } catch (java.io.IOException | java.util.concurrent.CompletionException ex) {
        throw new IllegalStateException(
            "Could not upgrade guis.yml. Check the original file and backup path " + backup, ex);
      }
    }
    files = Map.copyOf(next);
    features = Map.copyOf(toggles);
    npcIdentities = nextIdentities;
    actorAi = nextAi;
    actorCombat = nextCombat;
    broken = List.copyOf(failed);
    for (String name : documented) {
      if (name.equals("guis") && upgradeGui) continue;
      store.write(
          plugin.getDataFolder().toPath().resolve(name + ".yml"), next.get(name).saveToString());
    }
    if (!failed.isEmpty())
      plugin
          .getLogger()
          .warning(
              "Using bundled defaults for "
                  + String.join(".yml, ", failed)
                  + ".yml. Operators are told on join; the files on disk were left alone.");
    changeListeners.forEach(Runnable::run);
  }

  /** Read one file's shipped copy. A jar without its own defaults cannot be run at all. */
  private YamlConfiguration bundled(String file) {
    try (var input = plugin.getResource(file + ".yml")) {
      return YamlConfiguration.loadConfiguration(
          new java.io.InputStreamReader(
              Objects.requireNonNull(input, file + ".yml is missing from the plugin jar"),
              java.nio.charset.StandardCharsets.UTF_8));
    } catch (java.io.IOException ex) {
      throw new IllegalStateException("Could not read bundled " + file + " defaults", ex);
    }
  }

  /**
   * Complete and check one file on its own. Everything that can reject a file lives here, so a
   * refusal always names the one file responsible instead of stopping the whole load.
   */
  public static YamlConfiguration prepare(
      String file,
      YamlConfiguration yaml,
      YamlConfiguration defaults,
      Consumer<YamlConfiguration> validateMenus) {
    YamlConfiguration prepared = yaml;
    switch (file) {
      case "messages" -> {
        inheritMissing(prepared, defaults);
        migrateMessages(prepared, defaults);
      }
      case "moderation", "actor-ai", "npc-identities", "items", "nicknames", "death" ->
          inheritMissing(prepared, defaults);
      case "guis" -> prepared = GuiSchema.prepare(prepared, defaults);
      default -> {}
    }
    switch (file) {
      case "config" -> validateConfig(prepared);
      case "actor-ai" -> {
        ActorAiSettings.read(prepared);
        ActorCombatSettings.read(prepared);
      }
      case "features" -> toggles(prepared);
      case "kits" -> validateKits(prepared);
      case "moderation" -> validateModeration(prepared);
      case "nicknames" -> validateNicknames(prepared);
      case "npc-identities" -> {
        validateNpcIdentityProvider(prepared);
        NpcIdentities.read(prepared);
      }
      case "items" -> validateItems(prepared);
      case "recording" -> validateRecording(prepared);
      case "death" -> validateDeath(prepared);
      case "guis" -> validateMenus.accept(prepared);
      default -> {}
    }
    return prepared;
  }

  private static void validateConfig(YamlConfiguration config) {
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
  }

  private static void validateKits(YamlConfiguration kits) {
    if (kits.getInt("schema") != 1
        || !(kits.get("max-provider-kits") instanceof Integer)
        || kits.getInt("max-provider-kits") < 1
        || kits.getInt("max-provider-kits") > 10000)
      throw new IllegalArgumentException(
          "kits.yml: use schema 1 and max-provider-kits from 1..10000.");
    for (String provider : List.of("PlayerKits2", "PlayerKits", "Essentials", "CMI"))
      if (!(kits.get("providers." + provider) instanceof Boolean))
        throw new IllegalArgumentException(
            "kits.yml: providers." + provider + " must be true or false.");
  }

  private static void validateRecording(YamlConfiguration recording) {
    for (String key : List.of("playback.knockback-pause-ticks", "playback.return-to-route-ticks")) {
      if (!recording.contains(key)) continue;
      int value = recording.getInt(key);
      if (!(recording.get(key) instanceof Integer) || value < 1 || value > 100)
        throw new IllegalArgumentException(
            "recording.yml: " + key + " must be an integer from 1 to 100.");
    }
  }

  public static void validateDeath(YamlConfiguration death) {
    if (!(death.get("kick-on-death") instanceof Boolean))
      throw new IllegalArgumentException("death.yml: kick-on-death must be true or false.");
    if (!(death.get("kick-delay-ticks") instanceof Integer)
        || death.getInt("kick-delay-ticks") < 1
        || death.getInt("kick-delay-ticks") > 200)
      throw new IllegalArgumentException(
          "death.yml: kick-delay-ticks must be an integer from 1 to 200.");
    if (!(death.get("rejoin-lockout-seconds") instanceof Integer)
        || death.getInt("rejoin-lockout-seconds") < 0
        || death.getInt("rejoin-lockout-seconds") > 86400)
      throw new IllegalArgumentException(
          "death.yml: rejoin-lockout-seconds must be an integer from 0 to 86400.");
    ObfuscatedNames.Mode.of(death.getString("invisible-obfuscation", "message"));
  }

  private static Map<String, Boolean> toggles(YamlConfiguration features) {
    Map<String, Boolean> toggles = new HashMap<>();
    for (String key : FEATURES) {
      Object value = features.get(key);
      if (!(value instanceof Boolean enabled))
        throw new IllegalArgumentException(
            "features.yml: " + key + " = " + value + "; expected true or false.");
      toggles.put(key, enabled);
    }
    return toggles;
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

  /**
   * Add shipped explanations without changing configured values or an owner's existing comments.
   */
  public static boolean inheritComments(YamlConfiguration target, YamlConfiguration defaults) {
    boolean changed = false;
    if (target.options().getHeader().isEmpty() && !defaults.options().getHeader().isEmpty()) {
      target.options().setHeader(defaults.options().getHeader());
      changed = true;
    }
    for (String key : defaults.getKeys(true)) {
      if (target.contains(key)
          && target.getComments(key).isEmpty()
          && !defaults.getComments(key).isEmpty()) {
        target.setComments(key, defaults.getComments(key));
        changed = true;
      }
    }
    return changed;
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
    for (String key : List.of("api-enabled", "local-fallback", "random-skin"))
      if (!(yaml.get(key) instanceof Boolean))
        throw new IllegalArgumentException("nicknames.yml: " + key + " must be true or false.");
    if (!(yaml.get("api-timeout-millis") instanceof Integer)
        || yaml.getInt("api-timeout-millis") < 500
        || yaml.getInt("api-timeout-millis") > 10000)
      throw new IllegalArgumentException("nicknames.yml: api-timeout-millis must be 500..10000.");
  }

  public static void validateItems(YamlConfiguration yaml) {
    String material = yaml.getString("group-actor-tool.material", "");
    Material parsed = Material.matchMaterial(material);
    if (parsed == null || !parsed.isItem())
      throw new IllegalArgumentException(
          "items.yml: group-actor-tool.material must be a Bukkit item material.");
    String type = yaml.getString("group-actor-tool.actor-type", "");
    try {
      if (!EntityType.valueOf(type.toUpperCase(Locale.ROOT)).isAlive())
        throw new IllegalArgumentException();
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException(
          "items.yml: group-actor-tool.actor-type must be a living Bukkit entity type.");
    }
    Object cooldown = yaml.get("group-actor-tool.cooldown-ticks");
    if (!(cooldown instanceof Integer)
        || yaml.getInt("group-actor-tool.cooldown-ticks") < 1
        || yaml.getInt("group-actor-tool.cooldown-ticks") > 100)
      throw new IllegalArgumentException(
          "items.yml: group-actor-tool.cooldown-ticks must be an integer from 1 to 100.");
    if (!(yaml.get("group-actor-tool.name") instanceof String name) || name.isBlank())
      throw new IllegalArgumentException("items.yml: group-actor-tool.name must be text.");
    if (!(yaml.get("group-actor-tool.lore") instanceof List<?> lore)
        || lore.size() > 20
        || lore.stream().anyMatch(line -> !(line instanceof String)))
      throw new IllegalArgumentException(
          "items.yml: group-actor-tool.lore must be a list of at most 20 text lines.");
  }

  public static void validateNpcIdentityProvider(YamlConfiguration yaml) {
    if (!(yaml.get("api-enabled") instanceof Boolean))
      throw new IllegalArgumentException("npc-identities.yml: api-enabled must be true or false.");
    Object timeout = yaml.get("api-timeout-millis");
    if (!(timeout instanceof Integer)
        || yaml.getInt("api-timeout-millis") < 500
        || yaml.getInt("api-timeout-millis") > 10000)
      throw new IllegalArgumentException(
          "npc-identities.yml: api-timeout-millis must be an integer from 500 to 10000.");
    Object refresh = yaml.get("api-refresh-minutes");
    if (!(refresh instanceof Integer)
        || yaml.getInt("api-refresh-minutes") < 1
        || yaml.getInt("api-refresh-minutes") > 1440)
      throw new IllegalArgumentException(
          "npc-identities.yml: api-refresh-minutes must be an integer from 1 to 1440.");
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
    requireWritable("features");
    Map<String, Boolean> next = new HashMap<>(features);
    next.put(key, !enabled(key));
    features = Map.copyOf(next);
    file("features").set(key, enabled(key));
    persist("features");
    changeListeners.forEach(Runnable::run);
  }

  public void persist(String name) {
    requireWritable(name);
    store.write(plugin.getDataFolder().toPath().resolve(name + ".yml"), file(name).saveToString());
  }

  /**
   * Refuse to save over a file that is only broken. What is in memory for it came from the jar,
   * so writing it out would quietly replace the owner's file with the defaults — exactly what
   * running on defaults is meant to avoid.
   */
  private void requireWritable(String name) {
    if (broken.contains(name))
      throw new IllegalArgumentException(
          name
              + ".yml is broken and is running on the defaults from the jar, so saving now would"
              + " overwrite it. Fix the error shown in console, then run /es reload.");
  }
}
