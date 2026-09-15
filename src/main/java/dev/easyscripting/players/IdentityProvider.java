package dev.easyscripting.players;

import dev.easyscripting.config.Settings;
import dev.easyscripting.core.TickEngine;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Warms bounded public identity pools off-thread. Bukkit state and candidate claims stay on the
 * server thread.
 */
public final class IdentityProvider implements Listener, AutoCloseable {
  private static final int LOW_WATER = 64;
  private static final int MAX_CACHED_NAMES = 512;
  private static final int MAX_CACHED_SKINS = 128;

  private final JavaPlugin plugin;
  private final Settings settings;
  private final TickEngine ticks;
  private final UsernameClient client = new UsernameClient();
  private final LinkedHashMap<String, String> names = new LinkedHashMap<>();
  private final LinkedHashMap<String, String> skins = new LinkedHashMap<>();
  private final Set<String> realNames = new HashSet<>();
  private CompletableFuture<List<String>> nameRequest, skinRequest;
  private UUID pollJob;
  private long nextRefresh;
  private boolean closing;

  public IdentityProvider(JavaPlugin plugin, Settings settings, TickEngine ticks) {
    this.plugin = plugin;
    this.settings = settings;
    this.ticks = ticks;
    for (OfflinePlayer player : Bukkit.getOfflinePlayers())
      if ((player.hasPlayedBefore() || player.isOp()) && player.getName() != null)
        realNames.add(player.getName().toLowerCase(Locale.ROOT));
    for (OfflinePlayer player : Bukkit.getOperators())
      if (player.getName() != null) realNames.add(player.getName().toLowerCase(Locale.ROOT));
    startRefresh(true);
  }

  public List<String> usernames() {
    if (names.size() < LOW_WATER) startRefresh(false);
    return List.copyOf(names.values());
  }

  public List<String> skinOwners() {
    if (skins.isEmpty()) startRefresh(false);
    return List.copyOf(skins.values());
  }

  public void claim(String name) {
    names.remove(name.toLowerCase(Locale.ROOT));
    if (names.size() < LOW_WATER) {
      nextRefresh = 0;
      startRefresh(false);
    }
  }

  public boolean reservedRealName(String name) {
    String normalized = name.toLowerCase(Locale.ROOT);
    if (realNames.contains(normalized)) return true;
    for (OfflinePlayer player : Bukkit.getOperators())
      if (player.getName() != null && player.getName().equalsIgnoreCase(name)) {
        realNames.add(normalized);
        return true;
      }
    return false;
  }

  public CompletableFuture<List<String>> requestUsernames(int timeoutMillis) {
    return client.request(timeoutMillis);
  }

  /** Force a fresh provider fetch after /es reload without discarding usable cached values. */
  public void reload() {
    nextRefresh = 0;
    startRefresh(true);
  }

  private void startRefresh(boolean force) {
    if (closing
        || !settings.file("npc-identities").getBoolean("api-enabled", true)
        || pollJob != null
        || (!force && System.currentTimeMillis() < nextRefresh)
        || !ticks.acceptingWork()) return;
    int timeout = settings.file("npc-identities").getInt("api-timeout-millis", 4000);
    try {
      nameRequest = client.request(timeout);
      skinRequest = client.requestSkinOwners(timeout);
    } catch (RejectedExecutionException queueFull) {
      nextRefresh = System.currentTimeMillis() + 60_000;
      return;
    }
    pollJob =
        ticks.add(
            new TickEngine.Job() {
              public boolean tick() {
                if (!nameRequest.isDone() || !skinRequest.isDone()) return true;
                accept(nameRequest, names, MAX_CACHED_NAMES, "username");
                accept(skinRequest, skins, MAX_CACHED_SKINS, "skin profile");
                int minutes =
                    settings.file("npc-identities").getInt("api-refresh-minutes", 30);
                nextRefresh = System.currentTimeMillis() + minutes * 60_000L;
                return false;
              }

              public void stopped() {
                pollJob = null;
                nameRequest = null;
                skinRequest = null;
              }
            });
  }

  private void accept(
      CompletableFuture<List<String>> request,
      LinkedHashMap<String, String> destination,
      int maximum,
      String label) {
    try {
      for (String value : request.join()) {
        destination.putIfAbsent(value.toLowerCase(Locale.ROOT), value);
        while (destination.size() > maximum)
          destination.remove(destination.keySet().iterator().next());
      }
    } catch (CompletionException | CancellationException error) {
      plugin.getLogger().fine("Public " + label + " pool refresh failed: " + error.getMessage());
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void joined(PlayerJoinEvent event) {
    if (!event.getPlayer().hasMetadata("NPC"))
      realNames.add(event.getPlayer().getName().toLowerCase(Locale.ROOT));
  }

  @Override
  public void close() {
    closing = true;
    if (pollJob != null) ticks.cancel(pollJob);
    if (nameRequest != null) nameRequest.cancel(true);
    if (skinRequest != null) skinRequest.cancel(true);
    client.close();
    names.clear();
    skins.clear();
  }
}
