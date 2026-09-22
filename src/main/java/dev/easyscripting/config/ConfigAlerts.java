package dev.easyscripting.config;

import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Tell operators which configuration files are running on the copies bundled in the jar. A broken
 * file is only visible in console, which nobody is watching while they play, so the plugin looks
 * like it is quietly ignoring settings that were in fact never loaded.
 */
public final class ConfigAlerts implements Listener {
  private final Settings settings;
  private final Messages messages;

  public ConfigAlerts(Settings settings, Messages messages) {
    this.settings = settings;
    this.messages = messages;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void join(PlayerJoinEvent event) {
    announce(event.getPlayer());
  }

  /**
   * Name each broken file and point at the console, where the load already logged the real error.
   * Repeating a stack trace in chat would not help anyone fix it.
   */
  public void announce(CommandSender sender) {
    if (!sender.isOp()) return;
    for (String file : settings.broken())
      messages.error(sender, file + ".yml file is broken, please read console.");
  }
}
