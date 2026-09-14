package dev.easyscripting.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class TickEngineTest {
  @Test
  void disabledPluginCannotRegisterOrRetainJobs() {
    var schedules = new AtomicInteger();
    var engine =
        new TickEngine(
            () -> false,
            pulse -> {
              schedules.incrementAndGet();
              return new Task();
            },
            Logger.getAnonymousLogger());
    assertFalse(engine.acceptingWork());
    assertThrows(IllegalStateException.class, () -> engine.add(() -> true));
    assertEquals(0, schedules.get());
    assertEquals(0, engine.activeJobs());
    engine.close();
  }

  @Test
  void shutdownStopsProducersBeforeCleanupCallbacks() {
    var task = new Task();
    var stopped = new AtomicInteger();
    var engine = new TickEngine(() -> true, pulse -> task, Logger.getAnonymousLogger());
    engine.add(
        new TickEngine.Job() {
          public boolean tick() {
            return true;
          }

          public void stopped() {
            assertFalse(engine.acceptingWork());
            stopped.incrementAndGet();
          }
        });
    engine.beginShutdown();
    assertThrows(IllegalStateException.class, () -> engine.add(() -> true));
    engine.close();
    engine.close();
    assertEquals(1, stopped.get());
    assertTrue(task.cancelled);
    assertEquals(0, engine.activeJobs());
  }

  @Test
  void disableWhileIdleCannotRestartScheduler() {
    var enabled = new AtomicBoolean(true);
    var schedules = new AtomicInteger();
    var engine =
        new TickEngine(
            enabled::get,
            pulse -> {
              schedules.incrementAndGet();
              return new Task();
            },
            Logger.getAnonymousLogger());
    enabled.set(false);
    assertFalse(engine.acceptingWork());
    assertThrows(IllegalStateException.class, () -> engine.later(1, () -> {}));
    assertEquals(0, schedules.get());
  }

  @Test
  void schedulerFailureLeavesNoPhantomJob() {
    var engine =
        new TickEngine(
            () -> true,
            pulse -> {
              throw new IllegalStateException("disabled by server");
            },
            Logger.getAnonymousLogger());
    assertThrows(IllegalStateException.class, () -> engine.add(() -> true));
    assertEquals(0, engine.activeJobs());
  }

  private static final class Task implements BukkitTask {
    boolean cancelled;

    public int getTaskId() {
      return 1;
    }

    public Plugin getOwner() {
      return null;
    }

    public boolean isSync() {
      return true;
    }

    public boolean isCancelled() {
      return cancelled;
    }

    public void cancel() {
      cancelled = true;
    }
  }
}
