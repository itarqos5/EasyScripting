package dev.easyscripting.utilities;

import dev.easyscripting.config.*;
import dev.easyscripting.core.*;
import dev.easyscripting.players.*;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ModerationService implements Listener, AutoCloseable {
  private final JavaPlugin plugin;
  private final Settings settings;
  private final Messages messages;
  private final PlayerService players;
  private final TickEngine ticks;
  private volatile boolean chatMuted, recording;
  private volatile String recordingName = "";
  private volatile List<String> filters = List.of();
  private volatile boolean allowRecordingChat;
  private volatile Component blockedMessage = Component.empty();
  private final Set<UUID> chatBypass = ConcurrentHashMap.newKeySet();
  private final Set<UUID> participants = new HashSet<>();
  private dev.easyscripting.integration.VoiceBridge voice =
      dev.easyscripting.integration.VoiceBridge.absent();
  private Boolean previousVoiceMute;

  public void voice(dev.easyscripting.integration.VoiceBridge bridge) {
    voice = bridge;
  }

  public ModerationService(
      JavaPlugin plugin,
      Settings settings,
      Messages messages,
      PlayerService players,
      TickEngine ticks) {
    this.plugin = plugin;
    this.settings = settings;
    this.messages = messages;
    this.players = players;
    this.ticks = ticks;
    reload();
  }

  public void reload() {
    filters =
        settings.file("moderation").getStringList("blocked-phrases").stream()
            .map(s -> s.toLowerCase(Locale.ROOT))
            .filter(s -> !s.isBlank())
            .toList();
    allowRecordingChat = settings.file("recording").getBoolean("allow-chat");
    blockedMessage = messages.text("chat-blocked", Map.of("detail", ""));
    for (Player p : Bukkit.getOnlinePlayers()) updateBypass(p);
  }

  private void updateBypass(Player p) {
    if (p.hasPermission("easyscripting.chat.bypass")) chatBypass.add(p.getUniqueId());
    else chatBypass.remove(p.getUniqueId());
  }

  public boolean recording() {
    return recording;
  }

  public void recordingStart(String name, Collection<? extends Player> cast) {
    settings.require("recording");
    Checks.id(name);
    if (recording) throw new IllegalArgumentException("A recording session is already active.");
    List<Player> captured = new ArrayList<>();
    try {
      for (Player p : cast) {
        players.snapshot(p);
        captured.add(p);
      }
    } catch (RuntimeException ex) {
      captured.forEach(players::discard);
      throw ex;
    }
    for (Player p : captured) {
      participants.add(p.getUniqueId());
      updateBypass(p);
    }
    recordingName = name;
    recording = true;
    broadcast("recording-start", name);
    if (settings.enabled("voice")
        && voice.available()
        && settings.file("recording").getBoolean("voice.mute-on-session")) {
      previousVoiceMute = voice.muted();
      voice.mute(true);
    }
  }

  public void recordingStop(boolean restore) {
    if (!recording) throw new IllegalArgumentException("No recording session is active.");
    recording = false;
    for (UUID id : List.copyOf(participants)) {
      Player p = Bukkit.getPlayer(id);
      if (p != null) {
        if (restore) players.reset(p);
        players.discard(p);
      }
    }
    participants.clear();
    broadcast("recording-stop", recordingName);
    recordingName = "";
    if (previousVoiceMute != null) {
      if (voice.available()) voice.mute(previousVoiceMute);
      previousVoiceMute = null;
    }
  }

  public void mute(boolean value) {
    if (chatMuted == value) return;
    chatMuted = value;
    broadcast(value ? "chat-muted" : "chat-unmuted", "");
  }

  public boolean muted() {
    return chatMuted;
  }

  public void broadcast(String key, String detail) {
    Bukkit.broadcast(messages.text(key, Map.of("detail", detail)));
  }

  public void announce(String detail) {
    broadcast("broadcast", detail);
    var config = settings.file("moderation");
    if (!config.getBoolean("broadcast-title.enabled", true)) return;
    var title =
        net.kyori.adventure.title.Title.title(
            messages.text("broadcast-title", Map.of("detail", detail)),
            messages.text("broadcast-subtitle", Map.of("detail", detail)),
            net.kyori.adventure.title.Title.Times.times(
                java.time.Duration.ofMillis(
                    config.getInt("broadcast-title.fade-in-ticks", 10) * 50L),
                java.time.Duration.ofMillis(config.getInt("broadcast-title.stay-ticks", 60) * 50L),
                java.time.Duration.ofMillis(
                    config.getInt("broadcast-title.fade-out-ticks", 10) * 50L)));
    for (Player player : Bukkit.getOnlinePlayers()) player.showTitle(title);
  }

  public void fake(String kind, String name) {
    if (!name.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Invalid name.");
    broadcast("fake-" + kind, name);
  }

  public void clear(Player only) {
    Component empty = Component.text("\n".repeat(100));
    if (only != null) only.sendMessage(empty);
    else Bukkit.broadcast(empty);
  }

  public void lock(boolean value) {
    settings.file("moderation").set("server-lock.enabled", value);
    settings.persist("moderation");
  }

  public void allow(String name, boolean allow) {
    if (!name.matches("[A-Za-z0-9_]{1,16}"))
      throw new IllegalArgumentException("Invalid player name.");
    List<String> names =
        new ArrayList<>(settings.file("moderation").getStringList("server-lock.allowed"));
    names.removeIf(name::equalsIgnoreCase);
    if (allow) names.add(name);
    settings.file("moderation").set("server-lock.allowed", names);
    settings.persist("moderation");
  }

  public void dimension(String world, boolean locked) {
    if (Bukkit.getWorld(world) == null) throw new IllegalArgumentException("World is not loaded.");
    List<String> worlds =
        new ArrayList<>(settings.file("moderation").getStringList("locked-worlds"));
    worlds.remove(world);
    if (locked) worlds.add(world);
    settings.file("moderation").set("locked-worlds", worlds);
    settings.persist("moderation");
  }

  public void dimensionAllow(String world, String name, boolean allow) {
    Checks.id(world);
    if (!name.matches("[A-Za-z0-9_]{1,16}"))
      throw new IllegalArgumentException("Invalid player name.");
    String key = "dimension-whitelist." + world;
    List<String> list = new ArrayList<>(settings.file("moderation").getStringList(key));
    list.removeIf(name::equalsIgnoreCase);
    if (allow) list.add(name);
    settings.file("moderation").set(key, list);
    settings.persist("moderation");
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void chat(AsyncChatEvent e) {
    if (!settings.enabled("chat")) return;
    String text =
        PlainTextComponentSerializer.plainText().serialize(e.message()).toLowerCase(Locale.ROOT);
    boolean bypass = chatBypass.contains(e.getPlayer().getUniqueId());
    if ((!bypass && (chatMuted || (recording && !allowRecordingChat)))
        || filters.stream().anyMatch(text::contains)) {
      e.setCancelled(true);
      e.getPlayer().sendMessage(blockedMessage);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void login(PlayerLoginEvent e) {
    if (!settings.file("moderation").getBoolean("server-lock.enabled")
        || e.getPlayer().hasPermission("easyscripting.server.bypass")) return;
    if (settings.file("moderation").getStringList("server-lock.allowed").stream()
        .noneMatch(e.getPlayer().getName()::equalsIgnoreCase))
      e.disallow(
          PlayerLoginEvent.Result.KICK_WHITELIST,
          messages.text("server-locked", Map.of("detail", "")));
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void teleport(PlayerTeleportEvent e) {
    if (e.getTo() == null || e.getPlayer().hasPermission("easyscripting.world.bypass")) return;
    String world = e.getTo().getWorld().getName();
    if (settings.file("moderation").getStringList("locked-worlds").contains(world)
        && !e.getFrom().getWorld().equals(e.getTo().getWorld())
        && settings.file("moderation").getStringList("dimension-whitelist." + world).stream()
            .noneMatch(e.getPlayer().getName()::equalsIgnoreCase)) {
      e.setCancelled(true);
      messages.error(e.getPlayer(), "That dimension is locked for this production.");
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void command(PlayerCommandPreprocessEvent e) {
    if (e.getPlayer().hasPermission("easyscripting.commands.bypass")) return;
    String root = commandRoot(e.getMessage());
    if (settings.file("moderation").getStringList("blocked-commands").stream()
        .map(ModerationService::commandRoot)
        .anyMatch(root::equals)) {
      e.setCancelled(true);
      messages.error(e.getPlayer(), "This command is blocked during production.");
    }
  }

  public static String commandRoot(String input) {
    String root = input.strip().toLowerCase(Locale.ROOT).replaceFirst("^/", "").split("\\s+", 2)[0];
    int colon = root.indexOf(':');
    return colon >= 0 ? root.substring(colon + 1) : root;
  }

  @EventHandler
  public void sign(SignChangeEvent e) {
    if (!settings.enabled("chat")) return;
    boolean blocked =
        e.lines().stream()
            .map(
                c -> PlainTextComponentSerializer.plainText().serialize(c).toLowerCase(Locale.ROOT))
            .anyMatch(line -> filters.stream().anyMatch(line::contains));
    if (blocked) {
      e.setCancelled(true);
      messages.error(e.getPlayer(), "Sign text contains a blocked phrase.");
    }
    if (settings.file("moderation").getBoolean("sign-alerts"))
      for (Player p : Bukkit.getOnlinePlayers())
        if (p.hasPermission("easyscripting.moderation"))
          messages.send(
              p,
              "sign-alert",
              e.getPlayer().getName()
                  + " at "
                  + e.getBlock().getX()
                  + ", "
                  + e.getBlock().getY()
                  + ", "
                  + e.getBlock().getZ());
  }

  @EventHandler
  public void ping(ServerListPingEvent e) {
    if (recording && settings.file("recording").getBoolean("change-motd"))
      e.motd(
          Messages.rich(
              settings.file("recording").getString("motd", "<red>Recording in progress")));
  }

  @EventHandler
  public void join(PlayerJoinEvent e) {
    updateBypass(e.getPlayer());
    if (!settings.file("moderation").getBoolean("join-messages", true)) e.joinMessage(null);
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    chatBypass.remove(e.getPlayer().getUniqueId());
    participants.remove(e.getPlayer().getUniqueId());
    if (!settings.file("moderation").getBoolean("leave-messages", true)) e.quitMessage(null);
  }

  @Override
  public void close() {
    if (recording) recordingStop(true);
    chatBypass.clear();
  }
}
