package dev.easyscripting.smoke;

import dev.easyscripting.actors.ActorService;
import dev.easyscripting.api.EasyScriptingApi;
import java.io.File;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Opt-in real Citizens tests. The prepare fixture intentionally survives one restart. */
final class IdentitySmokeChecks implements CommandExecutor {
  private static final String ID = "identity_fixture";
  private final JavaPlugin plugin;
  private final EasyScriptingApi api;
  private CommandSender sender;
  private int passed;
  private boolean running;

  IdentitySmokeChecks(JavaPlugin plugin, EasyScriptingApi api) {
    this.plugin = plugin;
    this.api = api;
  }

  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (running) {
      sender.sendMessage("Identity test already running.");
      return true;
    }
    this.sender = sender;
    passed = 0;
    running = true;
    try {
      if (args.length == 1 && args[0].equals("prepare")) prepare();
      else if (args.length == 1 && args[0].equals("verify")) verify();
      else
        throw new IllegalArgumentException(
            "Use esidentitytest prepare, restart, then esidentitytest verify.");
    } catch (Throwable error) {
      fail(error);
    }
    return true;
  }

  private ActorService.ManagedActor actor() {
    return (ActorService.ManagedActor) api.actor(ID);
  }

  private String visibleTexture() {
    Player player = (Player) actor().requireEntity();
    return player.getPlayerProfile().getProperties().stream()
        .filter(property -> property.getName().equals("textures"))
        .map(property -> property.getValue())
        .findFirst()
        .orElse("");
  }

  private void prepare() {
    if (!Bukkit.getPluginManager().isPluginEnabled("Citizens"))
      throw new IllegalArgumentException("This test requires compatible Citizens.");
    if (api.actorIds().contains(ID)) api.removeActor(ID);
    var origin = Bukkit.getWorlds().getFirst().getSpawnLocation().add(0, 4, 0);
    api.createActor(ID, org.bukkit.entity.EntityType.PLAYER, origin);
    actor().requireEntity().setGravity(false);
    actor().requireEntity().setInvulnerable(true);
    check(
        "generated username is distinct from actor ID",
        !actor().definition.name.equals(ID)
            && actor().definition.name.matches("[A-Za-z0-9_]{1,16}"));
    check("skin owner chosen on creation", !actor().definition.skin.isBlank());
    awaitSkin(
        () -> {
          String initialSkin = actor().definition.skin,
              initialTexture = actor().definition.skinTexture;
          run("actor set " + ID + " name CustomPerformer");
          check(
              "manual name preserves ID and skin",
              actor().id().equals(ID)
                  && actor().definition.name.equals("CustomPerformer")
                  && actor().definition.skin.equals(initialSkin));
          check("rename preserves resolved skin bytes", visibleTexture().equals(initialTexture));
          String other = initialSkin.equalsIgnoreCase("Notch") ? "jeb_" : "Notch";
          run("actor set " + ID + " skin " + other);
          check(
              "manual skin preserves name",
              actor().definition.name.equals("CustomPerformer")
                  && actor().definition.skin.equals(other));
          awaitSkin(
              () -> {
                check(
                    "new skin replaces old cached texture",
                    !actor().definition.skinTexture.equals(initialTexture));
                run("actor hide " + ID);
                run("actor set " + ID + " skin " + initialSkin);
                check(
                    "hidden actor accepts skin edit and invalidates cache",
                    actor().entity().isEmpty() && actor().definition.skinTexture.isEmpty());
                run("actor show " + ID);
                awaitSkin(
                    () -> {
                      String oldName = actor().definition.name, oldSkin = actor().definition.skin;
                      run("actor randomize " + ID);
                      check(
                          "reroll changes name and skin without changing ID",
                          actor().id().equals(ID)
                              && !actor().definition.name.equals(oldName)
                              && !actor().definition.skin.equalsIgnoreCase(oldSkin));
                      awaitSkin(
                          () -> {
                            YamlConfiguration expected = new YamlConfiguration();
                            expected.set("name", actor().definition.name);
                            expected.set("skin", actor().definition.skin);
                            expected.set("texture", actor().definition.skinTexture);
                            try {
                              expected.save(expectedFile());
                            } catch (java.io.IOException ex) {
                              throw new IllegalStateException(ex);
                            }
                            finish(
                                "IDENTITY PREPARE PASSED: "
                                    + passed
                                    + " checks. Restart the server, then run esidentitytest"
                                    + " verify.");
                          });
                    });
              });
        });
  }

  private File expectedFile() {
    return new File(plugin.getDataFolder(), "identity-expected.yml");
  }

  private void verify() {
    if (!expectedFile().isFile()) throw new IllegalArgumentException("Run prepare before verify.");
    YamlConfiguration expected = YamlConfiguration.loadConfiguration(expectedFile());
    check(
        "restart keeps generated username",
        actor().definition.name.equals(expected.getString("name")));
    check(
        "restart keeps chosen skin owner",
        actor().definition.skin.equals(expected.getString("skin")));
    check(
        "restart keeps exact saved texture",
        actor().definition.skinTexture.equals(expected.getString("texture")));
    check(
        "restart applies saved texture to actual NPC",
        visibleTexture().equals(expected.getString("texture")));
    api.removeActor(ID);
    check("fixture removed", !api.actorIds().contains(ID));
    finish("IDENTITY RESTART PASSED: " + passed + " checks");
  }

  private void awaitSkin(Runnable next) {
    new BukkitRunnable() {
      int remaining = 45;

      public void run() {
        try {
          if (!actor().definition.skinTexture.isBlank()
              && actor().definition.skinTexture.equals(visibleTexture())) {
            cancel();
            check("resolved skin cached and applied to NPC", true);
            next.run();
          } else if (--remaining <= 0) {
            cancel();
            fail(
                new AssertionError(
                    "Skin did not resolve/apply within 45 seconds: " + actor().definition.skin));
          }
        } catch (Throwable error) {
          cancel();
          fail(error);
        }
      }
    }.runTaskTimer(plugin, 20, 20);
  }

  private void run(String command) {
    if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "es " + command))
      throw new AssertionError("Command not handled: " + command);
  }

  private void check(String label, boolean value) {
    if (!value) throw new AssertionError(label);
    passed++;
    plugin.getLogger().info("IDENTITY PASS " + label);
  }

  private void finish(String message) {
    running = false;
    plugin.getLogger().info(message);
    sender.sendMessage(message);
  }

  private void fail(Throwable error) {
    running = false;
    plugin.getLogger().log(java.util.logging.Level.SEVERE, "IDENTITY TEST FAILED", error);
    sender.sendMessage("Identity test failed; see server log.");
  }
}
