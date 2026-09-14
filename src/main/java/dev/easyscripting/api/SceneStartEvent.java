package dev.easyscripting.api;

import java.util.UUID;
import org.bukkit.event.*;

public final class SceneStartEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Scene scene;
  private final UUID runId;
  private boolean cancelled;

  public SceneStartEvent(Scene scene, UUID runId) {
    this.scene = scene;
    this.runId = runId;
  }

  public Scene scene() {
    return scene;
  }

  public UUID runId() {
    return runId;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  public void setCancelled(boolean value) {
    cancelled = value;
  }

  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
