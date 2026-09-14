package dev.easyscripting.items;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;

/** Self-claim policy. Management and gifts always require live operator status. */
public record KitAccess(Mode mode, UUID player, String playerName) {
  public enum Mode {
    OPERATORS,
    EVERYONE,
    PLAYER
  }

  public KitAccess {
    Objects.requireNonNull(mode);
    if (mode == Mode.PLAYER) {
      Objects.requireNonNull(player, "A specific-player kit requires a UUID.");
      playerName = playerName == null || playerName.isBlank() ? player.toString() : playerName;
    } else {
      player = null;
      playerName = null;
    }
  }

  public static KitAccess operators() {
    return new KitAccess(Mode.OPERATORS, null, null);
  }

  public boolean allows(boolean operator, UUID claimant) {
    return operator || mode == Mode.EVERYONE || mode == Mode.PLAYER && player.equals(claimant);
  }

  public String description() {
    return switch (mode) {
      case OPERATORS -> "Operators only";
      case EVERYONE -> "Everyone";
      case PLAYER -> playerName + " + operators";
    };
  }

  public static KitAccess read(ConfigurationSection yaml) {
    if (!yaml.contains("access")) return operators();
    try {
      Mode mode = Mode.valueOf(yaml.getString("access.mode", "").toUpperCase(Locale.ROOT));
      return new KitAccess(
          mode,
          mode == Mode.PLAYER ? UUID.fromString(yaml.getString("access.player", "")) : null,
          yaml.getString("access.player-name"));
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new IllegalArgumentException(
          "Invalid kit access data. An operator must reset its access mode.", ex);
    }
  }

  public void write(ConfigurationSection yaml) {
    yaml.set("access", null);
    yaml.set("access.mode", mode.name().toLowerCase(Locale.ROOT));
    yaml.set("access.player", player == null ? null : player.toString());
    yaml.set("access.player-name", playerName);
  }
}
