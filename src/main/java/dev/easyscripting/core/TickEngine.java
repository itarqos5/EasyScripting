package dev.easyscripting.core;

import java.util.*;
import java.util.logging.Level;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** One server task for active scenes, paths and bounded world jobs; idle means no task. */
public final class TickEngine implements AutoCloseable {
  public interface Job {
    boolean tick();

    default void stopped() {}
  }

  private final JavaPlugin plugin;
  private final Map<UUID, Job> jobs = new LinkedHashMap<>();
  private BukkitTask task;
  private boolean closed;

  public TickEngine(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public UUID add(Job job) {
    if (closed) throw new IllegalStateException("EasyScripting is stopping.");
    UUID id = UUID.randomUUID();
    jobs.put(id, job);
    if (task == null)
      task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::pulse, 1, 1);
    return id;
  }

  public void cancel(UUID id) {
    Job job = jobs.remove(id);
    if (job != null) job.stopped();
  }

  private void pulse() {
    for (var entry : List.copyOf(jobs.entrySet())) {
      if (!jobs.containsKey(entry.getKey())) continue;
      try {
        if (!entry.getValue().tick()) cancel(entry.getKey());
      } catch (RuntimeException ex) {
        plugin.getLogger().log(Level.SEVERE, "Scheduled production job stopped safely", ex);
        cancel(entry.getKey());
      }
    }
    if (jobs.isEmpty() && task != null) {
      task.cancel();
      task = null;
    }
  }

  public int activeJobs() {
    return jobs.size();
  }

  public void later(int ticks, Runnable action) {
    add(
        new Job() {
          int remaining = ticks;

          public boolean tick() {
            if (--remaining > 0) return true;
            action.run();
            return false;
          }
        });
  }

  @Override
  public void close() {
    closed = true;
    for (UUID id : List.copyOf(jobs.keySet())) cancel(id);
    if (task != null) task.cancel();
    task = null;
  }
}
