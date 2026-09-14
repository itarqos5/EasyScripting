package dev.easyscripting.config;

import org.bukkit.configuration.file.YamlConfiguration;

/** One-time creation-default migration; saved actor definitions are not changed. */
public final class ActorDefaults {
  private ActorDefaults() {}

  public static boolean migrate(YamlConfiguration config) {
    if (config.getInt("actors.defaults.version", 1) >= 2) return false;
    config.set("actors.defaults.immortal", false);
    config.set("actors.defaults.version", 2);
    return true;
  }
}
