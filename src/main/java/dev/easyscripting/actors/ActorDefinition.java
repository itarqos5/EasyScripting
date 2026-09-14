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
    return d;
  }
}
