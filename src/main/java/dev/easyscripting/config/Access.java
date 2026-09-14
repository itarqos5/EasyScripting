package dev.easyscripting.config;

import java.util.List;
import org.bukkit.command.CommandSender;

public final class Access {
  private final Settings settings;
  public static final List<String> EDITABLE =
      List.of(
          "actor",
          "scene.play",
          "scene.edit",
          "record",
          "player",
          "identity",
          "kit",
          "kit.edit",
          "warp",
          "warp.edit",
          "items",
          "inventory",
          "locks",
          "death",
          "chat",
          "world",
          "world.edit",
          "team",
          "villager",
          "effects",
          "voice");

  public Access(Settings settings) {
    this.settings = settings;
  }

  public boolean allowed(CommandSender sender, String node) {
    String suffix = node.startsWith("easyscripting.") ? node.substring(14) : node;
    String rule =
        EDITABLE.contains(suffix)
            ? settings.file("permissions").getString("overrides." + suffix, node)
            : node;
    return rule.equals("everyone") || sender.hasPermission(rule);
  }

  public void require(CommandSender sender, String suffix) {
    String node = "easyscripting." + suffix;
    if (!allowed(sender, node)) throw new IllegalArgumentException("Missing permission: " + node);
  }

  public void set(String suffix, String rule) {
    if (!EDITABLE.contains(suffix) || !rule.matches("[a-zA-Z0-9_.-]{1,100}"))
      throw new IllegalArgumentException("Invalid editable feature or permission rule.");
    settings.file("permissions").set("overrides." + suffix, rule);
    settings.persist("permissions");
  }
}
