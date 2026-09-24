package dev.easyscripting.players;

import dev.easyscripting.config.Settings;
import java.util.LinkedHashSet;
import java.util.Set;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffectType;

/**
 * Obfuscates the death, kill and leave messages of a player nobody can see. Somebody who is
 * invisible has already left the shot; naming them in chat puts them back in it.
 */
public final class InvisibleNameService implements Listener {
  private static final PotionEffectType INVISIBILITY =
      Registry.EFFECT.get(NamespacedKey.minecraft("invisibility"));
  private final Settings settings;
  private final NicknameDirectory directory;

  public InvisibleNameService(Settings settings, NicknameDirectory directory) {
    this.settings = settings;
    this.directory = directory;
  }

  private ObfuscatedNames.Mode mode() {
    if (!settings.enabled("death")) return ObfuscatedNames.Mode.OFF;
    return ObfuscatedNames.Mode.of(
        settings.file("death").getString("invisible-obfuscation", "names"));
  }

  public static boolean invisible(Player player) {
    return player.isInvisible() || (INVISIBILITY != null && player.hasPotionEffect(INVISIBILITY));
  }

  /**
   * Every name this player can be printed under. A nickname rewrite may run either side of this
   * one, so the account name and the alias are both hidden and the order stops mattering.
   */
  private Set<String> names(Player player) {
    Set<String> names = new LinkedHashSet<>();
    names.add(player.getName());
    names.add(PlainTextComponentSerializer.plainText().serialize(player.displayName()));
    var entry = directory.get(player.getUniqueId());
    if (entry != null) {
      names.add(entry.account());
      names.add(entry.visible());
    }
    names.removeIf(name -> name == null || name.isBlank());
    return names;
  }

  private Set<String> hidden(Player... candidates) {
    Set<String> names = new LinkedHashSet<>();
    for (Player candidate : candidates)
      if (candidate != null && invisible(candidate)) names.addAll(names(candidate));
    return names;
  }

  /** Runs before the death feature's own message handling, which may narrow the audience. */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void death(PlayerDeathEvent e) {
    ObfuscatedNames.Mode mode = mode();
    if (mode == ObfuscatedNames.Mode.OFF) return;
    Set<String> names = hidden(e.getEntity(), e.getEntity().getKiller());
    if (!names.isEmpty()) e.deathMessage(ObfuscatedNames.hide(e.deathMessage(), mode, names));
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void quit(PlayerQuitEvent e) {
    ObfuscatedNames.Mode mode = mode();
    if (mode == ObfuscatedNames.Mode.OFF) return;
    Set<String> names = hidden(e.getPlayer());
    if (!names.isEmpty()) e.quitMessage(ObfuscatedNames.hide(e.quitMessage(), mode, names));
  }

  /** A kick announces its own leave message, which never passes through the quit message above. */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void kicked(PlayerKickEvent e) {
    ObfuscatedNames.Mode mode = mode();
    if (mode == ObfuscatedNames.Mode.OFF) return;
    Set<String> names = hidden(e.getPlayer());
    if (!names.isEmpty()) e.leaveMessage(ObfuscatedNames.hide(e.leaveMessage(), mode, names));
  }
}
