package dev.easyscripting.actors;

import dev.easyscripting.core.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class ActorDefinition {
  public final String id;
  public String name;
  public String type;
  public String skin = "";
  public String skinTexture = "", skinSignature = "";
  public String group = "default";
  public String recording = "";
  public boolean autoplay = true;
  public dev.easyscripting.recording.PlaybackMode playbackMode =
      dev.easyscripting.recording.PlaybackMode.STOP;
  public org.bukkit.entity.Pose pose = org.bukkit.entity.Pose.STANDING;
  public boolean glowing, sneaking, sprinting;
  public Location location;
  public boolean hidden,
      immortal,
      hittable = true,
      collidable = true,
      lookNearby,
      wander,
      nametag = true,
      tablist;
  public ItemStack[] equipment = new ItemStack[6];
  public ItemStack[] inventory = new ItemStack[36];
  public int heldSlot;
  public boolean aggressive;

  public ActorDefinition(String id, String type, Location at) {
    this.id = Checks.id(id);
    this.type = type;
    this.name = id;
    this.location = at.clone();
  }

  public YamlConfiguration yaml() {
    YamlConfiguration y = new YamlConfiguration();
    y.set("schema", 1);
    y.set("name", name);
    y.set("type", type);
    y.set("skin", skin);
    if (!skinTexture.isEmpty() && !skinSignature.isEmpty()) {
      y.set("skin-texture", skinTexture);
      y.set("skin-signature", skinSignature);
    }
    y.set("group", group);
    y.set("recording", recording);
    y.set("autoplay", autoplay);
    y.set("playback-mode", playbackMode.name().toLowerCase(Locale.ROOT));
    y.set("pose", pose.name());
    y.set("glowing", glowing);
    y.set("sneaking", sneaking);
    y.set("sprinting", sprinting);
    y.createSection("location", Positions.encode(location));
    y.set("hidden", hidden);
    y.set("immortal", immortal);
    y.set("hittable", hittable);
    y.set("collidable", collidable);
    y.set("look-nearby", lookNearby);
    y.set("wander", wander);
    y.set("nametag", nametag);
    y.set("tablist", tablist);
    y.set("equipment", Arrays.asList(equipment));
    y.set("inventory", Arrays.asList(inventory));
    y.set("held-slot", heldSlot);
    y.set("aggressive", aggressive);
    y.options()
        .setHeader(
            List.of(
                "Saved NPC. Prefer /actor gui or /actor set; edit this file only while the server"
                    + " is stopped."));
    Map.ofEntries(
            Map.entry("schema", "Storage format version; keep 1."),
            Map.entry("name", "Displayed NPC identity; the filename remains its command ID."),
            Map.entry("type", "Bukkit entity type. PLAYER requires Citizens."),
            Map.entry("skin", "Java account name used for the player NPC skin."),
            Map.entry("skin-texture", "Cached signed skin texture; maintained by the plugin."),
            Map.entry(
                "skin-signature", "Signature for the cached texture; maintained by the plugin."),
            Map.entry(
                "group",
                "NPC group ID; default means unassigned. /es group create registers a faction."),
            Map.entry("recording", "Selected saved movement take ID; empty means none."),
            Map.entry(
                "autoplay",
                "Automatically start the selected take after capture/spawn; loop behavior uses"
                    + " playback-mode."),
            Map.entry(
                "playback-mode",
                "stop: play once; repeat: restart from first frame; reverse: bounce"
                    + " backward/forward."),
            Map.entry("pose", "Saved Bukkit body pose, such as STANDING or SNEAKING."),
            Map.entry("glowing", "Enable the glowing outline."),
            Map.entry("sneaking", "Player NPC crouch flag."),
            Map.entry("sprinting", "Player NPC sprint flag."),
            Map.entry(
                "location", "Saved position: world name, x/y/z in blocks, yaw/pitch in degrees."),
            Map.entry("hidden", "Keep the saved definition but do not spawn its entity."),
            Map.entry("immortal", "Prevent death while still allowing hits and knockback."),
            Map.entry(
                "hittable",
                "Allow direct melee hits; falls, projectiles and explosions remain possible when"
                    + " false."),
            Map.entry("collidable", "Allow entity collision."),
            Map.entry(
                "look-nearby",
                "Look at visible nearby players when idle; walking also tracks them."),
            Map.entry(
                "wander",
                "Take short social walks; groups/aggression/scenes/replays own movement when"
                    + " active."),
            Map.entry("nametag", "Display the name above the NPC."),
            Map.entry("tablist", "Include this PLAYER NPC in the player tab list."),
            Map.entry(
                "equipment",
                "Six Bukkit item stacks: main hand, offhand, helmet, chestplate, leggings, boots."),
            Map.entry(
                "inventory",
                "36 personal backpack slots. Player NPCs use live inventory; mobs store reserve kit"
                    + " items here."),
            Map.entry("held-slot", "Selected player hotbar slot, 0 through 8."),
            Map.entry(
                "aggressive",
                "Retaliate when hit while ungrouped; registered groups use group intelligence"
                    + " instead."))
        .forEach(
            (key, description) -> {
              if (y.contains(key)) y.setComments(key, List.of(description));
            });
    return y;
  }

  public static ActorDefinition read(String id, YamlConfiguration y) {
    if (y.getInt("schema") != 1)
      throw new IllegalArgumentException("actors/" + id + ".yml: schema must be 1.");
    ActorDefinition d =
        new ActorDefinition(
            id,
            y.getString("type", "PLAYER"),
            Positions.read(y.getConfigurationSection("location")));
    d.name = y.getString("name", id);
    d.skin = y.getString("skin", "");
    d.skinTexture = y.getString("skin-texture", "");
    d.skinSignature = y.getString("skin-signature", "");
    d.group = Checks.id(y.getString("group", "default"));
    d.recording = y.getString("recording", "");
    d.autoplay = y.getBoolean("autoplay", true);
    if (!d.recording.isBlank()) Checks.id(d.recording);
    d.playbackMode =
        dev.easyscripting.recording.PlaybackMode.parse(y.getString("playback-mode", "stop"));
    d.pose = Checks.choice(org.bukkit.entity.Pose.class, y.getString("pose", "STANDING"));
    d.glowing = y.getBoolean("glowing");
    d.sneaking = y.getBoolean("sneaking");
    d.sprinting = y.getBoolean("sprinting");
    d.hidden = y.getBoolean("hidden");
    d.immortal = y.getBoolean("immortal", true);
    d.hittable = y.getBoolean("hittable", true);
    d.collidable = y.getBoolean("collidable", true);
    d.lookNearby = y.getBoolean("look-nearby");
    d.wander = y.getBoolean("wander");
    d.nametag = y.getBoolean("nametag", true);
    d.tablist = y.getBoolean("tablist", false);
    List<?> items = y.getList("equipment", List.of());
    for (int i = 0; i < Math.min(6, items.size()); i++)
      if (items.get(i) instanceof ItemStack item) d.equipment[i] = item.clone();
    d.inventory =
        dev.easyscripting.players.EntitySnapshot.items(y.getList("inventory", List.of()), 36);
    d.heldSlot = Math.max(0, Math.min(8, y.getInt("held-slot", 0)));
    d.aggressive = y.getBoolean("aggressive", false);
    return d;
  }
}
