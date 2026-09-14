package dev.easyscripting.utilities;

import dev.easyscripting.config.Messages;
import dev.easyscripting.core.Checks;
import dev.easyscripting.storage.YamlStore;
import java.util.*;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scoreboard.*;

public final class TeamService implements Listener, AutoCloseable {
  private final YamlStore store;
  private YamlConfiguration data;
  private final Map<String, Team> teams = new TreeMap<>();
  private final Map<UUID, Boolean> originalGlow = new HashMap<>();

  public TeamService(YamlStore store) {
    this.store = store;
  }

  public void load() {
    data = store.read("state", "teams");
    for (String id : data.getKeys(false)) {
      try {
        createTeam(id);
        configure(id);
      } catch (RuntimeException ex) {
        Bukkit.getLogger().warning("state/teams.yml: " + id + ": " + ex.getMessage());
      }
    }
  }

  public List<String> ids() {
    return List.copyOf(teams.keySet());
  }

  public void create(String id) {
    Checks.id(id);
    if (id.length() > 12)
      throw new IllegalArgumentException("Team id must be at most 12 characters.");
    if (teams.containsKey(id)) throw new IllegalArgumentException("Team already exists.");
    if (Objects.requireNonNull(Bukkit.getScoreboardManager())
            .getMainScoreboard()
            .getTeam("es_" + id)
        != null)
      throw new IllegalArgumentException(
          "Scoreboard team es_" + id + " is owned by another system; use a different identifier.");
    createTeam(id);
    data.set(id + ".color", "aqua");
    data.set(id + ".members", List.of());
    configure(id);
    save();
  }

  private void createTeam(String id) {
    Scoreboard board = Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard();
    String key = "es_" + Checks.id(id);
    Team existing = board.getTeam(key);
    teams.put(id, existing == null ? board.registerNewTeam(key) : existing);
  }

  public Team get(String id) {
    Team team = teams.get(id);
    if (team == null) throw new IllegalArgumentException("Team '" + id + "' does not exist.");
    return team;
  }

  public void member(String id, String name, boolean add) {
    Team team = get(id);
    if (!name.matches("[A-Za-z0-9_]{1,16}"))
      throw new IllegalArgumentException("Invalid team member name.");
    if (add) {
      for (String other : ids())
        if (!other.equals(id)) {
          List<String> names = new ArrayList<>(data.getStringList(other + ".members"));
          if (names.remove(name)) data.set(other + ".members", names);
        }
      team.addEntry(name);
    } else {
      team.removeEntry(name);
      Player player = Bukkit.getPlayerExact(name);
      if (player != null) restoreGlow(player);
    }
    data.set(id + ".members", new ArrayList<>(team.getEntries()));
    configure(id);
    save();
  }

  public void set(String id, String option, String value) {
    get(id);
    switch (option) {
      case "color" -> {
        if (NamedTextColor.NAMES.value(value) == null)
          throw new IllegalArgumentException("Unknown named color.");
        data.set(id + ".color", value);
      }
      case "glow", "friendly-fire", "see-invisible", "nametags", "collision" ->
          data.set(id + "." + option, Checks.bool(value));
      case "prefix" -> data.set(id + ".prefix", value);
      default ->
          throw new IllegalArgumentException(
              "Team setting must be color, glow, friendly-fire, see-invisible, nametags, collision,"
                  + " or prefix.");
    }
    configure(id);
    save();
  }

  private void configure(String id) {
    Team team = get(id);
    team.color(
        Optional.ofNullable(NamedTextColor.NAMES.value(data.getString(id + ".color", "aqua")))
            .orElse(NamedTextColor.AQUA));
    team.prefix(Messages.rich(data.getString(id + ".prefix", "")));
    team.setAllowFriendlyFire(data.getBoolean(id + ".friendly-fire", false));
    team.setCanSeeFriendlyInvisibles(data.getBoolean(id + ".see-invisible", true));
    team.setOption(
        Team.Option.NAME_TAG_VISIBILITY,
        data.getBoolean(id + ".nametags", true)
            ? Team.OptionStatus.ALWAYS
            : Team.OptionStatus.NEVER);
    team.setOption(
        Team.Option.COLLISION_RULE,
        data.getBoolean(id + ".collision", true)
            ? Team.OptionStatus.ALWAYS
            : Team.OptionStatus.NEVER);
    for (String name : data.getStringList(id + ".members")) team.addEntry(name);
    for (Player p : Bukkit.getOnlinePlayers())
      if (team.hasEntry(p.getName())) {
        originalGlow.putIfAbsent(p.getUniqueId(), p.isGlowing());
        p.setGlowing(data.getBoolean(id + ".glow", false));
      }
  }

  public void delete(String id) {
    Team team = get(id);
    for (Player p : Bukkit.getOnlinePlayers()) if (team.hasEntry(p.getName())) restoreGlow(p);
    team.unregister();
    teams.remove(id);
    data.set(id, null);
    save();
  }

  private void save() {
    store.save("state", "teams", data);
  }

  private void restoreGlow(Player p) {
    Boolean original = originalGlow.remove(p.getUniqueId());
    if (original != null) p.setGlowing(original);
  }

  @EventHandler
  public void join(PlayerJoinEvent e) {
    for (String id : ids()) configure(id);
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    restoreGlow(e.getPlayer());
  }

  @Override
  public void close() {
    for (Player p : Bukkit.getOnlinePlayers()) restoreGlow(p);
    for (Team team : teams.values()) team.unregister();
    teams.clear();
  }
}
