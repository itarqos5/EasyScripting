package dev.easyscripting.smoke;

import dev.easyscripting.api.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Real server checks. Run only in a disposable world using /essmoke. */
public final class SmokePlugin extends JavaPlugin implements Listener {
  private EasyScriptingApi api;
  private final Set<String> actors = new HashSet<>(), scenes = new HashSet<>();
  private final List<String> results = new ArrayList<>();
  private final Queue<Runnable> steps = new ArrayDeque<>();
  private int completes, stops;
  private CommandSender sender;
  private boolean running;
  private org.bukkit.permissions.PermissionAttachment destructiveGrant;

  @Override
  public void onEnable() {
    api = Objects.requireNonNull(Bukkit.getServicesManager().load(EasyScriptingApi.class));
    Objects.requireNonNull(getCommand("esidentitytest"))
        .setExecutor(new IdentitySmokeChecks(this, api));
    Objects.requireNonNull(getCommand("esactingtest"))
        .setExecutor(new ActingSmokeChecks(this, api));
    Bukkit.getPluginManager().registerEvents(this, this);
    Objects.requireNonNull(getCommand("essmoke"))
        .setExecutor(
            (source, command, alias, args) -> {
              if (running) {
                source.sendMessage("Integration checks already running.");
                return true;
              }
              sender = source;
              run();
              return true;
            });
  }

  @EventHandler
  public void completed(SceneCompleteEvent event) {
    if (event.sceneId().startsWith("smoke_")) completes++;
  }

  @EventHandler
  public void stopped(SceneStopEvent event) {
    if (event.sceneId().startsWith("smoke_")) stops++;
  }

  private void run() {
    running = true;
    results.clear();
    steps.clear();
    completes = 0;
    stops = 0;
    try {
      cleanup();
      Location origin = Bukkit.getWorlds().getFirst().getSpawnLocation().add(0, 4, 0);
      for (int i = 0; i < 100; i++) {
        String id = "smoke_actor_" + i;
        EntityType type =
            i == 99 && Bukkit.getPluginManager().isPluginEnabled("Citizens")
                ? EntityType.PLAYER
                : EntityType.ZOMBIE;
        Actor actor = api.createActor(id, type, origin.clone().add(i % 10 * 2, 0, i / 10 * 2));
        actor.entity().orElseThrow().setGravity(false);
        actors.add(id);
        actor.entity().orElseThrow().setInvulnerable(true);
        actor.entity().orElseThrow().setCollidable(false);
      }
      check(
          "100 actors created",
          () -> actors.stream().allMatch(id -> api.actor(id).entity().isPresent()));
      if (api.actor("smoke_actor_99").entity().orElseThrow() instanceof Player player) {
        check("Citizens player actor spawned", () -> player.isValid());
      }
      scene("smoke_restore", 0, true, health(0, "smoke_actor_0", "6"));
      api.play("smoke_restore", Bukkit.getConsoleSender());
      steps.add(
          () -> {
            check("completion restores health", () -> health(0) == 20 && completes == 1);
            scene(
                "smoke_order",
                1,
                false,
                health(0, "smoke_actor_1", "6"),
                health(0, "smoke_actor_1", "7"));
            api.play("smoke_order", Bukkit.getConsoleSender());
          });
      steps.add(
          () -> {
            check("stable same-tick order (health=" + health(1) + ")", () -> health(1) == 7);
            api.reset("smoke_order");
            check("reset completed take", () -> health(1) == 20);
            scene("smoke_pause", 2, false, health(0, "smoke_actor_2", "8"));
            api.play("smoke_pause", Bukkit.getConsoleSender());
            api.pause("smoke_pause");
          });
      steps.add(
          () -> {
            check("paused scene does not advance", () -> health(2) == 20);
            api.resume("smoke_pause");
          });
      steps.add(
          () -> {
            check("resume executes action", () -> health(2) == 8);
            api.reset("smoke_pause");
            scene(
                "smoke_cancel",
                3,
                false,
                health(0, "smoke_actor_3", "5"),
                health(100, "smoke_actor_3", "1"));
            api.play("smoke_cancel", Bukkit.getConsoleSender());
          });
      steps.add(
          () -> {
            check("running action applied", () -> health(3) == 5);
            scene("smoke_conflict", 3, true, health(0, "smoke_actor_3", "2"));
            rejects(
                "concurrent entity conflict",
                () -> api.play("smoke_conflict", Bukkit.getConsoleSender()));
            api.stop("smoke_cancel", true);
            check("cancellation restores health", () -> health(3) == 20 && stops == 1);
            scene(
                "smoke_delete",
                4,
                true,
                health(0, "smoke_actor_4", "3"),
                health(100, "smoke_actor_4", "1"));
            api.play("smoke_delete", Bukkit.getConsoleSender());
            api.removeActor("smoke_actor_4");
            actors.remove("smoke_actor_4");
            check("actor deletion cancels scene", () -> stops == 2);
            scene(
                "smoke_death",
                5,
                true,
                new Scene.Action(0, "death", "actor:smoke_actor_5", Map.of()));
            rejects(
                "forced death requires explicit permission",
                () -> api.play("smoke_death", Bukkit.getConsoleSender()));
            destructiveGrant =
                Bukkit.getConsoleSender().addAttachment(this, "easyscripting.destructive", true);
            api.play("smoke_death", Bukkit.getConsoleSender());
          });
      steps.add(
          () -> {
            check(
                "scripted actor death deletes the actor",
                () -> !api.actorIds().contains("smoke_actor_5"));
            int before = completes;
            for (int i = 10; i < 18; i++) {
              String id = "smoke_parallel_" + i;
              scene(
                  id,
                  i,
                  true,
                  health(0, "smoke_actor_" + i, "4"),
                  health(10, "smoke_actor_" + i, "2"));
              api.play(id, Bukkit.getConsoleSender());
            }
            steps.add(
                () ->
                    check(
                        "eight simultaneous scenes finish and restore",
                        () ->
                            completes == before + 8
                                && java.util.stream.IntStream.range(10, 18)
                                    .allMatch(i -> health(i) == 20)));
          });
      next();
    } catch (Throwable ex) {
      fail(ex);
    }
  }

  private void next() {
    Bukkit.getScheduler()
        .runTaskLater(
            this,
            () -> {
              try {
                Runnable step = steps.poll();
                if (step == null) {
                  finish();
                  return;
                }
                step.run();
                next();
              } catch (Throwable ex) {
                fail(ex);
              }
            },
            20);
  }

  private void scene(String id, int actor, boolean restore, Scene.Action... actions) {
    api.saveScene(new Scene(id, "Integration fixture", Map.of(), List.of(actions), restore));
    scenes.add(id);
  }

  private static Scene.Action health(long tick, String actor, String amount) {
    return new Scene.Action(tick, "health", "actor:" + actor, Map.of("value", amount));
  }

  private double health(int index) {
    return api.actor("smoke_actor_" + index).entity().orElseThrow().getHealth();
  }

  private void check(String label, BooleanSupplier condition) {
    if (!condition.getAsBoolean()) throw new AssertionError(label);
    results.add("PASS " + label);
    getLogger().info("PASS " + label);
  }

  private void rejects(String label, Runnable operation) {
    try {
      operation.run();
    } catch (IllegalArgumentException expected) {
      check(label, () -> true);
      return;
    }
    throw new AssertionError(label + " did not reject");
  }

  private void cleanup() {
    for (String id : api.sceneIds())
      if (id.startsWith("smoke_") && !id.equals("smoke_console")) api.deleteScene(id);
    for (String id : api.actorIds()) if (id.startsWith("smoke_actor_")) api.removeActor(id);
    actors.clear();
    scenes.clear();
    if (destructiveGrant != null) {
      Bukkit.getConsoleSender().removeAttachment(destructiveGrant);
      destructiveGrant = null;
    }
  }

  private void finish() {
    cleanup();
    running = false;
    getLogger().info("SMOKE PASSED: " + results.size() + " checks");
    sender.sendMessage("SMOKE PASSED: " + results.size() + " checks");
  }

  private void fail(Throwable ex) {
    getLogger().log(java.util.logging.Level.SEVERE, "SMOKE FAILED", ex);
    steps.clear();
    running = false;
    try {
      cleanup();
    } catch (RuntimeException cleanupFailure) {
      getLogger().log(java.util.logging.Level.SEVERE, "Smoke cleanup failed", cleanupFailure);
    }
  }
}
