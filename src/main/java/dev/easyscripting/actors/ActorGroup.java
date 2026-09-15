package dev.easyscripting.actors;

import dev.easyscripting.core.Checks;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ActorGroup {
  public enum Order {
    HOLD,
    FOLLOW,
    MOVE
  }

  public final String id;
  public UUID leader;
  public boolean intelligence = true;
  public Order order = Order.HOLD;
  public Location destination;
  final Set<UUID> targets = new LinkedHashSet<>();
  final Set<String> enemies = new LinkedHashSet<>();

  public ActorGroup(String id) {
    this.id = Checks.id(id);
    if (id.equals("default"))
      throw new IllegalArgumentException(
          "'default' is reserved for unassigned actors. Choose another group ID.");
  }

  public boolean canOrder(boolean operator, UUID player) {
    return operator || (leader != null && leader.equals(player));
  }

  public YamlConfiguration yaml() {
    var y = new YamlConfiguration();
    y.set("schema", 1);
    y.set("leader", leader == null ? "" : leader.toString());
    y.set("intelligence", intelligence);
    // Follow survives restarts; active battles and one-off movement orders do not.
    y.set("order", order == Order.FOLLOW ? "follow" : "hold");
    y.options()
        .setHeader(
            List.of("Saved NPC group. Use /es group or /actors to manage it. Edit while stopped."));
    y.setComments("schema", List.of("Storage format version; leave at 1."));
    y.setComments(
        "leader", List.of("Real player UUID; empty means no leader. A player may lead one group."));
    y.setComments(
        "intelligence",
        List.of("true: defend allies and attack ordered enemies; false: only follow/move/hold."));
    y.setComments(
        "order",
        List.of(
            "follow resumes when the leader is online; hold stays put. Battles and move"
                + " destinations are temporary."));
    return y;
  }

  public static ActorGroup read(String id, YamlConfiguration y) {
    if (y.getInt("schema") != 1 || !(y.get("intelligence") instanceof Boolean))
      throw new IllegalArgumentException(
          "groups/" + id + ".yml: expected schema 1 and boolean intelligence.");
    ActorGroup group = new ActorGroup(id);
    String leader = y.getString("leader", "");
    if (!leader.isBlank()) group.leader = UUID.fromString(leader);
    group.intelligence = y.getBoolean("intelligence");
    group.order = Checks.choice(Order.class, y.getString("order", "hold"));
    if (group.order == Order.MOVE) group.order = Order.HOLD;
    return group;
  }
}
