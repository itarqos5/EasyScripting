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
  /** Null keeps each member's existing value until an operator chooses a shared value. */
  public Boolean memberImmortal;
  /** Empty keeps individual kits; a value becomes the default for current and future members. */
  public String memberKit = "";
  /** Monotonic suffix shared by every copy of this group's bound actor tool. */
  public int nextActorIndex = 1;
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
    if (memberImmortal != null) y.set("shared.immortal", memberImmortal);
    y.set("shared.kit", memberKit);
    y.set("next-actor-index", nextActorIndex);
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
    if (memberImmortal != null)
      y.setComments(
          "shared.immortal",
          List.of(
              "Shared Immortal value applied to every current member and to actors added later."));
    y.setComments(
        "shared.kit",
        List.of(
            "Saved EasyScripting kit applied to every current member and to actors added later;"
                + " empty keeps individual kits."));
    y.setComments(
        "next-actor-index",
        List.of(
            "Next suffix for bound tools: <group>-actor-<number>. Maintained automatically."));
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
    Object immortal = y.get("shared.immortal");
    if (immortal != null && !(immortal instanceof Boolean))
      throw new IllegalArgumentException(
          "groups/" + id + ".yml: shared.immortal must be true or false.");
    group.memberImmortal = (Boolean) immortal;
    group.memberKit = y.getString("shared.kit", "");
    if (!group.memberKit.isBlank()) Checks.id(group.memberKit);
    Object next = y.get("next-actor-index");
    if (next != null
        && (!(next instanceof Integer value) || value < 1 || value == Integer.MAX_VALUE))
      throw new IllegalArgumentException(
          "groups/" + id + ".yml: next-actor-index must be an integer from 1 to 2147483646.");
    group.nextActorIndex = next == null ? 1 : (Integer) next;
    group.order = Checks.choice(Order.class, y.getString("order", "hold"));
    if (group.order == Order.MOVE) group.order = Order.HOLD;
    return group;
  }
}
