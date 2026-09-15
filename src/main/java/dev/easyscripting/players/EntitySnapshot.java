package dev.easyscripting.players;

import dev.easyscripting.core.Positions;
import java.util.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

/** Detached take state; restore must run on the server thread after the entity is alive. */
public final class EntitySnapshot {
  private final YamlConfiguration data;

  private EntitySnapshot(YamlConfiguration data) {
    this.data = data;
  }

  public static EntitySnapshot capture(LivingEntity e) {
    YamlConfiguration y = new YamlConfiguration();
    y.set("schema", 1);
    y.set("entity", e.getUniqueId().toString());
    y.createSection("location", Positions.encode(e.getLocation()));
    y.set("health", e.getHealth());
    y.set("fire", e.getFireTicks());
    y.set("fall", e.getFallDistance());
    y.set("air", e.getRemainingAir());
    y.set("invulnerable", e.isInvulnerable());
    y.set("glow", e.isGlowing());
    y.set("gravity", e.hasGravity());
    y.set("invisible", e.isInvisible());
    y.set("velocity", e.getVelocity());
    y.set("effects", new ArrayList<>(e.getActivePotionEffects()));
    y.set("pose", e.getPose().name());
    y.set("gliding", e.isGliding());
    if (e instanceof Mob mob) y.set("aware", mob.isAware());
    if (e.getEquipment() != null) {
      y.set("armor", copy(e.getEquipment().getArmorContents()));
      y.set("main", e.getEquipment().getItemInMainHand().clone());
      y.set("off", e.getEquipment().getItemInOffHand().clone());
    }
    if (e instanceof Player p) {
      y.set("inventory", copy(p.getInventory().getContents()));
      y.set("held-slot", p.getInventory().getHeldItemSlot());
      y.set("sneak", p.isSneaking());
      y.set("sprint", p.isSprinting());
      y.set("food", p.getFoodLevel());
      y.set("saturation", p.getSaturation());
      y.set("exhaustion", p.getExhaustion());
      y.set("gamemode", p.getGameMode().name());
      y.set("allow-flight", p.getAllowFlight());
      y.set("flying", p.isFlying());
      y.set("walk-speed", p.getWalkSpeed());
      y.set("fly-speed", p.getFlySpeed());
      y.set("exp", p.getExp());
      y.set("level", p.getLevel());
      y.set("total-exp", p.getTotalExperience());
      y.set("display-name", MiniMessage.miniMessage().serialize(p.displayName()));
      y.set("list-name", MiniMessage.miniMessage().serialize(p.playerListName()));
    }
    return new EntitySnapshot(y);
  }

  public YamlConfiguration yaml() {
    return data;
  }

  /** Acting-only profile snapshot, also readable by deferred login/respawn restoration. */
  public void captureProfile(Player player) {
    var profile = player.getPlayerProfile();
    data.set("acting-profile.name", profile.getName());
    data.set(
        "acting-profile.properties",
        profile.getProperties().stream()
            .map(
                property -> {
                  Map<String, Object> value = new LinkedHashMap<>();
                  value.put("name", property.getName());
                  value.put("value", property.getValue());
                  value.put("signature", property.getSignature());
                  return value;
                })
            .toList());
  }

  public void restoreProfile(Player player) {
    if (!data.contains("acting-profile")) return;
    var profile =
        Bukkit.createProfileExact(player.getUniqueId(), data.getString("acting-profile.name"));
    // Paper may seed this profile from the online player's current (NPC) properties.
    profile.clearProperties();
    for (Map<?, ?> property : data.getMapList("acting-profile.properties"))
      profile.setProperty(
          new com.destroystokyo.paper.profile.ProfileProperty(
              String.valueOf(property.get("name")),
              String.valueOf(property.get("value")),
              property.get("signature") instanceof String signature ? signature : null));
    player.setPlayerProfile(profile);
  }

  public static EntitySnapshot read(YamlConfiguration y) {
    if (y.getInt("schema") != 1) throw new IllegalArgumentException("Snapshot schema must be 1.");
    return new EntitySnapshot(y);
  }

  public Location location() {
    return Positions.read(data.getConfigurationSection("location"));
  }

  public void restore(LivingEntity e) {
    if (e.isDead())
      throw new IllegalArgumentException("Cannot restore dead entity; respawn it first.");
    if (!e.teleport(location()))
      throw new IllegalArgumentException("Snapshot teleport was cancelled.");
    if (e instanceof Player p && data.contains("inventory")) {
      restoreProfile(p);
      p.closeInventory();
      p.getInventory().setContents(items(data.getList("inventory", List.of()), 41));
      p.getInventory().setHeldItemSlot(data.getInt("held-slot"));
      p.setGameMode(GameMode.valueOf(data.getString("gamemode", "SURVIVAL")));
      p.setAllowFlight(data.getBoolean("allow-flight"));
      p.setFlying(data.getBoolean("flying") && p.getAllowFlight());
      p.setWalkSpeed((float) data.getDouble("walk-speed", .2));
      p.setFlySpeed((float) data.getDouble("fly-speed", .1));
      p.setSneaking(data.getBoolean("sneak"));
      p.setSprinting(data.getBoolean("sprint"));
      p.setFoodLevel(data.getInt("food"));
      p.setSaturation((float) data.getDouble("saturation"));
      p.setExhaustion((float) data.getDouble("exhaustion"));
      p.setTotalExperience(data.getInt("total-exp"));
      p.setLevel(data.getInt("level"));
      p.setExp((float) data.getDouble("exp"));
      p.displayName(
          MiniMessage.miniMessage().deserialize(data.getString("display-name", p.getName())));
      p.playerListName(
          MiniMessage.miniMessage().deserialize(data.getString("list-name", p.getName())));
    } else if (e.getEquipment() != null) {
      e.getEquipment().setArmorContents(items(data.getList("armor", List.of()), 4));
      e.getEquipment().setItemInMainHand(data.getItemStack("main"));
      e.getEquipment().setItemInOffHand(data.getItemStack("off"));
    }
    e.setHealth(
        Math.max(
            .01,
            Math.min(
                data.getDouble("health", 20),
                Objects.requireNonNull(e.getAttribute(Attribute.MAX_HEALTH)).getValue())));
    for (PotionEffect effect : e.getActivePotionEffects()) e.removePotionEffect(effect.getType());
    for (Object effect : data.getList("effects", List.of()))
      if (effect instanceof PotionEffect p) e.addPotionEffect(p);
    e.setFireTicks(data.getInt("fire"));
    e.setFallDistance((float) data.getDouble("fall"));
    e.setRemainingAir(data.getInt("air", 300));
    e.setInvulnerable(data.getBoolean("invulnerable"));
    e.setGlowing(data.getBoolean("glow"));
    e.setGravity(data.getBoolean("gravity", true));
    e.setInvisible(data.getBoolean("invisible"));
    e.setVelocity(data.getVector("velocity", new Vector()));
    e.setGliding(data.getBoolean("gliding"));
    if (data.contains("pose")) e.setPose(Pose.valueOf(data.getString("pose", "STANDING")), false);
    if (e instanceof Mob mob && data.contains("aware")) mob.setAware(data.getBoolean("aware"));
  }

  private static List<ItemStack> copy(ItemStack[] items) {
    return Arrays.stream(items).map(item -> item == null ? null : item.clone()).toList();
  }

  public void restoreInventory(Player player) {
    player.closeInventory();
    player.getInventory().setContents(items(data.getList("inventory", List.of()), 41));
  }

  public static ItemStack[] items(List<?> source, int size) {
    ItemStack[] items = new ItemStack[size];
    for (int i = 0; i < Math.min(source.size(), size); i++)
      if (source.get(i) instanceof ItemStack item) items[i] = item.clone();
    return items;
  }
}
