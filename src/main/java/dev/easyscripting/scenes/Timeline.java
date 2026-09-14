package dev.easyscripting.scenes;

import dev.easyscripting.api.Scene;
import java.util.List;
import java.util.function.Consumer;

/** Pure deterministic state machine. No scheduler or Bukkit state is owned here. */
public final class Timeline {
  public enum State {
    READY,
    RUNNING,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED
  }

  private final List<Scene.Action> actions;
  private State state = State.READY;
  private long tick;
  private int cursor;

  public Timeline(List<Scene.Action> actions) {
    this.actions =
        actions.stream().sorted(java.util.Comparator.comparingLong(Scene.Action::tick)).toList();
  }

  public void start() {
    if (state != State.READY) throw new IllegalStateException("Timeline has already started.");
    state = State.RUNNING;
  }

  public void pause() {
    if (state != State.RUNNING) throw new IllegalStateException("Scene is not running.");
    state = State.PAUSED;
  }

  public void resume() {
    if (state != State.PAUSED) throw new IllegalStateException("Scene is not paused.");
    state = State.RUNNING;
  }

  public void cancel() {
    if (!terminal()) state = State.CANCELLED;
  }

  public void advance(int budget, Consumer<Scene.Action> execute) {
    if (state != State.RUNNING) return;
    int executed = 0;
    try {
      while (cursor < actions.size() && actions.get(cursor).tick() <= tick) {
        if (++executed > budget)
          throw new IllegalStateException(
              "Per-tick action budget exceeded at tick " + tick + ". Spread actions across ticks.");
        execute.accept(actions.get(cursor++));
        if (state != State.RUNNING) return;
      }
      if (cursor == actions.size()) state = State.COMPLETED;
      else tick++;
    } catch (RuntimeException ex) {
      state = State.FAILED;
      throw ex;
    }
  }

  public State state() {
    return state;
  }

  public long tick() {
    return tick;
  }

  public boolean terminal() {
    return state == State.COMPLETED || state == State.CANCELLED || state == State.FAILED;
  }
}
