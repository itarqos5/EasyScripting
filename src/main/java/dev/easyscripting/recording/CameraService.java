package dev.easyscripting.recording;

import dev.easyscripting.core.TickEngine;
import dev.easyscripting.players.*;
import dev.easyscripting.players.EntitySnapshot;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;

public final class CameraService implements Listener, AutoCloseable {
  private final TickEngine ticks;
  private final PlayerService players;
  private final Map<UUID, UUID> running = new HashMap<>();

  public CameraService(TickEngine ticks, PlayerService players) {
    this.ticks = ticks;
    this.players = players;
  }

  public void move(Player viewer, Location end, int duration) {
    players.available(viewer.getUniqueId());
    if (running.containsKey(viewer.getUniqueId()))
      throw new IllegalArgumentException("Stop the current camera first.");
    if (duration < 1 || duration > 72000 || !viewer.getWorld().equals(end.getWorld()))
      throw new IllegalArgumentException(
          "Camera duration must be 1..72000 ticks, with both points in the same world.");
    EntitySnapshot original = EntitySnapshot.capture(viewer);
    Location start = viewer.getLocation();
    ArmorStand camera =
        viewer
            .getWorld()
            .spawn(
                start,
                ArmorStand.class,
                stand -> {
                  stand.setInvisible(true);
                  stand.setMarker(true);
                  stand.setGravity(false);
                  stand.setPersistent(false);
                });
    viewer.setGameMode(GameMode.SPECTATOR);
    players.reserve(viewer.getUniqueId(), "camera");
    viewer.setSpectatorTarget(camera);
    UUID job =
        ticks.add(
            new TickEngine.Job() {
              int tick;

              public boolean tick() {
                if (!viewer.isOnline() || !camera.isValid()) return false;
                double t = Math.min(1, ++tick / (double) duration);
                double smooth = t * t * (3 - 2 * t);
                Location at =
                    start.clone().add(end.toVector().subtract(start.toVector()).multiply(smooth));
                float yawDelta = (float) (((end.getYaw() - start.getYaw() + 540) % 360) - 180);
                at.setYaw(start.getYaw() + yawDelta * (float) smooth);
                at.setPitch(
                    start.getPitch() + (end.getPitch() - start.getPitch()) * (float) smooth);
                camera.teleport(at);
                return tick < duration;
              }

              public void stopped() {
                running.remove(viewer.getUniqueId());
                players.release(viewer.getUniqueId(), "camera");
                if (viewer.isOnline() && !viewer.isDead()) {
                  if (viewer.getGameMode() == GameMode.SPECTATOR) viewer.setSpectatorTarget(null);
                  original.restore(viewer);
                } else players.defer(viewer.getUniqueId(), original);
                camera.remove();
              }
            });
    running.put(viewer.getUniqueId(), job);
  }

  public void stop(Player viewer) {
    UUID job = running.get(viewer.getUniqueId());
    if (job == null) throw new IllegalArgumentException("No camera is active.");
    ticks.cancel(job);
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    if (running.containsKey(e.getPlayer().getUniqueId())) stop(e.getPlayer());
  }

  @Override
  public void close() {
    for (UUID job : List.copyOf(running.values())) ticks.cancel(job);
  }
}
