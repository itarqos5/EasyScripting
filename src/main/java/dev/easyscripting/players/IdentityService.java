package dev.easyscripting.players;

import com.destroystokyo.paper.profile.PlayerProfile;
import dev.easyscripting.config.*;
import dev.easyscripting.core.Checks;
import dev.easyscripting.storage.YamlStore;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class IdentityService implements Listener, AutoCloseable {
  private final JavaPlugin plugin;
  private final Settings settings;
  private final YamlStore store;
  private final Messages messages;
  private YamlConfiguration data;
  private final Map<UUID, PlayerProfile> originals = new HashMap<>();
  private final Map<UUID, UUID> requests = new HashMap<>();
  private java.util.function.Consumer<String> purgeIdentity = name -> {};

  public void onBlacklist(java.util.function.Consumer<String> purge) {
    purgeIdentity = purge;
  }

  public boolean blocked(String name) {
    return blacklist().stream().anyMatch(name::equalsIgnoreCase);
  }

  public IdentityService(JavaPlugin plugin, Settings settings, YamlStore store, Messages messages) {
    this.plugin = plugin;
    this.settings = settings;
    this.store = store;
    this.messages = messages;
  }

  public void load() {
    data = store.read("state", "identities");
    if (!data.contains("profiles.default"))
      data.set(
          "profiles.default",
          List.of("CameraOp", "WanderingHero", "VillageGuard", "QuietTraveler"));
  }

  private void save() {
    store.save("state", "identities", data);
  }

  public void nick(Player p, String name) {
    settings.require("identity");
    validate(name);
    for (Player other : Bukkit.getOnlinePlayers())
      if (!other.getUniqueId().equals(p.getUniqueId()) && other.getName().equalsIgnoreCase(name))
        throw new IllegalArgumentException("Nickname is already in use.");
    remember(p);
    PlayerProfile profile = Bukkit.createProfileExact(p.getUniqueId(), name);
    profile.setProperties(p.getPlayerProfile().getProperties());
    p.setPlayerProfile(profile);
    p.displayName(Component.text(name));
    p.playerListName(Component.text(name));
    data.set("active." + p.getUniqueId(), name);
    List<String> history =
        new ArrayList<>(data.getStringList("history." + name.toLowerCase(Locale.ROOT)));
    history.remove(p.getUniqueId().toString());
    history.addFirst(p.getUniqueId().toString());
    data.set("history." + name.toLowerCase(Locale.ROOT), history.stream().limit(20).toList());
    save();
  }

  private void remember(Player p) {
    originals.putIfAbsent(p.getUniqueId(), p.getPlayerProfile().clone());
  }

  public void validate(String name) {
    if (!name.matches("[A-Za-z0-9_]{1,16}"))
      throw new IllegalArgumentException("Identity must be 1..16 letters, digits or underscores.");
    if (data.getStringList("blacklist").stream().anyMatch(s -> s.equalsIgnoreCase(name)))
      throw new IllegalArgumentException("This name is blacklisted.");
  }

  public String random(String profile) {
    List<String> names =
        data.getStringList("profiles." + Checks.id(profile)).stream()
            .filter(n -> data.getStringList("blacklist").stream().noneMatch(n::equalsIgnoreCase))
            .filter(n -> Bukkit.getPlayerExact(n) == null)
            .toList();
    if (names.isEmpty())
      throw new IllegalArgumentException("No available names in profile '" + profile + "'.");
    return names.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(names.size()));
  }

  public void profile(String name, String nickname, boolean add) {
    Checks.id(name);
    validate(nickname);
    List<String> list = new ArrayList<>(data.getStringList("profiles." + name));
    list.removeIf(nickname::equalsIgnoreCase);
    if (add) list.add(nickname);
    data.set("profiles." + name, list);
    save();
  }

  public List<String> info(String name) {
    return data.getStringList("history." + name.toLowerCase(Locale.ROOT));
  }

  public List<String> blacklist() {
    return data.getStringList("blacklist");
  }

  public void blacklist(String name, boolean add) {
    if (!name.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("Invalid name.");
    List<String> list = new ArrayList<>(blacklist());
    list.removeIf(name::equalsIgnoreCase);
    if (add) list.add(name);
    data.set("blacklist", list);
    if (add) purgeIdentity.accept(name);
    if (add)
      for (Player p : Bukkit.getOnlinePlayers())
        if (name.equalsIgnoreCase(data.getString("active." + p.getUniqueId()))) reset(p);
    save();
  }

  public void reset(Player p) {
    requests.remove(p.getUniqueId());
    PlayerProfile profile = originals.remove(p.getUniqueId());
    if (profile != null) p.setPlayerProfile(profile);
    p.displayName(Component.text(p.getName()));
    p.playerListName(Component.text(p.getName()));
    data.set("active." + p.getUniqueId(), null);
    save();
  }

  public void skin(Player p, String value) {
    skin(p, value, "auto");
  }

  public void skin(Player p, String value, String model) {
    settings.require("identity");
    if (!List.of("auto", "slim", "classic").contains(model))
      throw new IllegalArgumentException("Skin model must be auto, slim or classic.");
    remember(p);
    if (value.startsWith("https://textures.minecraft.net/texture/")) {
      try {
        URI uri = URI.create(value);
        if (!uri.getHost().equals("textures.minecraft.net")
            || uri.getPort() != -1
            || uri.getQuery() != null
            || uri.getFragment() != null
            || !uri.getPath().matches("/texture/[a-fA-F0-9]{32,64}"))
          throw new IllegalArgumentException("Invalid Minecraft texture URL.");
        PlayerProfile profile = p.getPlayerProfile().clone();
        var textures = profile.getTextures();
        textures.setSkin(
            uri.toURL(),
            model.equals("slim")
                ? org.bukkit.profile.PlayerTextures.SkinModel.SLIM
                : org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC);
        profile.setTextures(textures);
        requests.remove(p.getUniqueId());
        p.setPlayerProfile(profile);
      } catch (java.net.MalformedURLException ex) {
        throw new IllegalArgumentException("Invalid texture URL.", ex);
      }
      return;
    }
    validate(value);
    if (requests.size() >= 20 && !requests.containsKey(p.getUniqueId()))
      throw new IllegalArgumentException("Skin lookup queue is full. Try again shortly.");
    UUID id = p.getUniqueId(), token = UUID.randomUUID();
    requests.put(id, token);
    Bukkit.createProfileExact(null, value)
        .update()
        .orTimeout(15, TimeUnit.SECONDS)
        .whenComplete(
            (profile, error) -> {
              if (!plugin.isEnabled()) return;
              Bukkit.getScheduler()
                  .runTask(
                      plugin,
                      () -> {
                        if (!token.equals(requests.get(id))) return;
                        requests.remove(id);
                        Player live = Bukkit.getPlayer(id);
                        if (live == null || !settings.enabled("identity")) return;
                        if (error != null || profile == null || !profile.hasTextures()) {
                          messages.error(
                              live,
                              "Skin lookup failed for '"
                                  + value
                                  + "'. Try a valid player name or Minecraft texture URL.");
                          return;
                        }
                        PlayerProfile changed = live.getPlayerProfile().clone();
                        var textures = profile.getTextures();
                        if (!model.equals("auto") && textures.getSkin() != null)
                          textures.setSkin(
                              textures.getSkin(),
                              model.equals("slim")
                                  ? org.bukkit.profile.PlayerTextures.SkinModel.SLIM
                                  : org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC);
                        changed.setTextures(textures);
                        live.setPlayerProfile(changed);
                        messages.ok(live, "Skin applied.");
                      });
            });
  }

  @EventHandler
  public void join(PlayerJoinEvent e) {
    Player p = e.getPlayer();
    String nick = data.getString("active." + p.getUniqueId());
    if (nick != null && settings.enabled("identity")) {
      try {
        nick(p, nick);
      } catch (IllegalArgumentException ex) {
        plugin.getLogger().warning(ex.getMessage());
      }
    }
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    requests.remove(e.getPlayer().getUniqueId());
    originals.remove(e.getPlayer().getUniqueId());
  }

  @Override
  public void close() {
    requests.clear();
    for (Player p : Bukkit.getOnlinePlayers()) {
      PlayerProfile original = originals.get(p.getUniqueId());
      if (original != null) {
        p.setPlayerProfile(original);
        p.displayName(Component.text(original.getName()));
        p.playerListName(Component.text(original.getName()));
      }
    }
    originals.clear();
  }
}
