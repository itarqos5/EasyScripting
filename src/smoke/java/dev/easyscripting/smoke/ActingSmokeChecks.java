package dev.easyscripting.smoke;

import dev.easyscripting.actors.ActorService;
import dev.easyscripting.api.EasyScriptingApi;
import dev.easyscripting.core.Positions;
import dev.easyscripting.players.EntitySnapshot;
import java.io.File;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Opt-in protocol-client companion; never packaged in the deployable plugin. */
final class ActingSmokeChecks implements CommandExecutor {
  private static final String ID = "acting_fixture";
  private final JavaPlugin plugin;
  private final EasyScriptingApi api;
  private YamlConfiguration expected;

  ActingSmokeChecks(JavaPlugin plugin, EasyScriptingApi api) {
    this.plugin = plugin;
    this.api = api;
    expected = YamlConfiguration.loadConfiguration(expectedFile());
  }

  private File expectedFile() {
    return new File(plugin.getDataFolder(), "acting-expected.yml");
  }

  private ActorService.ManagedActor actor() {
    return (ActorService.ManagedActor) api.actor(ID);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player) || args.length != 1) return false;
    try {
      switch (args[0]) {
        case "setup" -> {
          if (api.actorIds().contains(ID)) api.removeActor(ID);
          player.setGameMode(GameMode.CREATIVE);
          player.setHealth(18);
          player.setFoodLevel(10);
          player.getInventory().clear();
          player.getInventory().setItem(2, new ItemStack(Material.DIAMOND, 3));
          player.setLevel(7);
          player.setExp(.3f);
          var snapshot = EntitySnapshot.capture(player);
          snapshot.captureProfile(player);
          expected = snapshot.yaml();
          var npc =
              api.createActor(
                  ID,
                  Bukkit.getPluginManager().isPluginEnabled("Citizens")
                      ? EntityType.PLAYER
                      : EntityType.ZOMBIE,
                  player.getLocation().clone().add(4, 0, 0));
          npc.entity()
              .orElseThrow()
              .getEquipment()
              .setItemInMainHand(new ItemStack(Material.GOLDEN_SWORD));
          var helmet = new ItemStack(Material.DIAMOND_HELMET);
          helmet.editMeta(meta -> meta.setUnbreakable(true));
          npc.entity().orElseThrow().getEquipment().setHelmet(helmet);
          player.performCommand("es actor set " + ID + " name StagePerformer");
          check(
              npc.entity().orElseThrow().getEquipment().getHelmet().getType()
                  == Material.DIAMOND_HELMET,
              "same-tick rename preserves NPC armor");
          expected.createSection(
              "test.actor-origin", Positions.encode(npc.entity().orElseThrow().getLocation()));
          expected.save(expectedFile());
        }
        case "active" -> {
          check(actor().entity().isEmpty(), "NPC suspended");
          check(
              player.getPlayerProfile().getName().equals("StagePerformer"),
              "performer uses NPC profile name");
          check(
              player.getLocation().distanceSquared(actor().definition.location) < 1,
              "performer moved to NPC position");
          check(
              player.getInventory().getItemInMainHand().getType() == Material.GOLDEN_SWORD,
              "costume hand copied");
          check(
              player.getInventory().getHelmet().getType() == Material.DIAMOND_HELMET,
              "costume armor copied");
          if (!actor().definition.skinTexture.isEmpty())
            check(
                player.getPlayerProfile().getProperties().stream()
                    .anyMatch(
                        p ->
                            p.getName().equals("textures")
                                && p.getValue().equals(actor().definition.skinTexture)),
                "actual NPC texture applied to performer");
        }
        case "restored" -> {
          check(
              player
                      .getLocation()
                      .distanceSquared(Positions.read(expected.getConfigurationSection("location")))
                  < .05,
              "original location restored");
          check(
              player.getGameMode() == GameMode.CREATIVE && player.getHealth() == 18,
              "original gamemode/health restored");
          check(
              player.getLevel() == 7 && Math.abs(player.getExp() - .3) < .001,
              "original XP restored");
          check(
              player.getInventory().getItem(2) != null
                  && player.getInventory().getItem(2).getType() == Material.DIAMOND
                  && player.getInventory().getItem(2).getAmount() == 3
                  && (player.getInventory().getHelmet() == null
                      || player.getInventory().getHelmet().getType().isAir()),
              "original inventory restored (slot 2="
                  + player.getInventory().getItem(2)
                  + ", helmet="
                  + player.getInventory().getHelmet()
                  + ")");
          check(
              player.getPlayerProfile().getName().equals(expected.getString("acting-profile.name")),
              "original profile name restored");
          var actualProperties = player.getPlayerProfile().getProperties();
          check(
              actualProperties.size() == expected.getMapList("acting-profile.properties").size(),
              "original profile property count restored");
          for (Map<?, ?> property : expected.getMapList("acting-profile.properties"))
            check(
                actualProperties.stream()
                    .anyMatch(
                        p ->
                            p.getName().equals(property.get("name"))
                                && p.getValue().equals(property.get("value"))),
                "original texture restored");
          check(actor().entity().isPresent(), "NPC returned after acting");
        }
        case "saved" -> {
          check(!actor().definition.recording.isBlank(), "recording bound to NPC");
          var data = recording();
          player.sendMessage("ACTING FRAMES " + data.getMapList("frames").size());
          check(
              data.getInt("schema") == 2 && data.getMapList("frames").size() > 10,
              "new recording frames saved");
          check(
              data.getMapList("frames").stream().anyMatch(f -> Boolean.TRUE.equals(f.get("swing"))),
              "swing animation recorded");
          check(
              data.getMapList("frames").stream()
                  .allMatch(f -> f.containsKey("armor") && f.containsKey("pose")),
              "armor and pose captured");
          check(
              data.getMapList("frames").stream().noneMatch(f -> Boolean.TRUE.equals(f.get("hurt")))
                  && data.getMapList("frames").stream()
                      .anyMatch(f -> f.get("fire") instanceof Number n && n.intValue() > 0),
              "protected performer has no damage cues; visual fire captured");
        }
        case "cue" -> {
          double health = player.getHealth();
          player.setNoDamageTicks(0);
          player.damage(1);
          check(
              player.getHealth() == health && player.isInvulnerable(),
              "acting performer is protected from damage");
          player.setFireTicks(80);
        }
        case "end" -> {
          var frames = recording().getMapList("frames");
          var last = new YamlConfiguration();
          frames.getLast().forEach((k, v) -> last.set(String.valueOf(k), v));
          check(
              actor().requireEntity().getLocation().distanceSquared(Positions.read(last)) < .2,
              "STOP stays at final recorded position");
          check(
              actor().requireEntity().getEquipment().getChestplate().getType()
                  == Material.IRON_CHESTPLATE,
              "playback applies recorded armor changes");
          check(
              actor().requireEntity().getHealth() == 20
                  && actor().requireEntity().getFireTicks() <= 0,
              "recorded hurt/fire cues do not damage or ignite NPC");
        }
        case "unhittable" -> {
          check(!actor().definition.hittable, "GUI disables hittable");
          var e = actor().requireEntity();
          e.setInvulnerable(false);
          e.setHealth(20);
          e.setNoDamageTicks(0);
          e.damage(2);
          check(e.getHealth() == 20, "unhittable NPC blocks damage");
        }
        case "hittable" -> {
          check(actor().definition.hittable, "GUI enables hittable");
          var e = actor().requireEntity();
          e.setInvulnerable(false);
          e.setNoDamageTicks(0);
          e.damage(2);
          check(e.getHealth() < 20, "hittable NPC takes actual damage");
          e.setHealth(20);
        }
        case "immortal" -> {
          check(actor().definition.immortal, "GUI has immortal enabled");
          var e = actor().requireEntity();
          e.setNoDamageTicks(0);
          e.damage(1000);
          check(!e.isDead() && e.getHealth() > 0, "immortal NPC survives lethal hit");
        }
        case "mortal" -> {
          check(
              !actor().definition.immortal && actor().definition.hittable,
              "GUI allows mortal damage");
          var e = actor().requireEntity();
          e.setNoDamageTicks(0);
          e.damage(1000);
          Bukkit.getScheduler()
              .runTaskLater(
                  plugin,
                  () -> {
                    try {
                      check(actor().entity().isEmpty(), "mortal NPC remains dead until respawn");
                      pass(player, "mortal");
                    } catch (Throwable error) {
                      fail(player, error);
                    }
                  },
                  30);
          return true;
        }
        case "respawned" -> check(actor().entity().isPresent(), "GUI respawns dead NPC");
        case "cleanup" -> api.removeActor(ID);
        default -> throw new IllegalArgumentException("Unknown acting check");
      }
      pass(player, args[0]);
    } catch (Throwable error) {
      fail(player, error);
    }
    return true;
  }

  private YamlConfiguration recording() {
    return YamlConfiguration.loadConfiguration(
        new File(
            Bukkit.getPluginManager().getPlugin("EasyScripting").getDataFolder(),
            "recordings/" + actor().definition.recording + ".yml"));
  }

  private void check(boolean value, String label) {
    if (!value) throw new AssertionError(label);
    plugin.getLogger().info("ACTING PASS " + label);
  }

  private void pass(Player player, String stage) {
    player.sendMessage("ACTING CHECK PASSED " + stage);
  }

  private void fail(Player player, Throwable error) {
    plugin.getLogger().log(java.util.logging.Level.SEVERE, "ACTING TEST FAILED", error);
    player.sendMessage("ACTING TEST FAILED " + error.getMessage());
  }
}
