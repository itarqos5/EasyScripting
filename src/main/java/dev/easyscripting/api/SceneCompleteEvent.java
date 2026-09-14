package dev.easyscripting.api;

import java.util.UUID;
import org.bukkit.event.*;

public final class SceneCompleteEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final String sceneId;
  private final UUID runId;

  public SceneCompleteEvent(String sceneId, UUID runId) {
    this.sceneId = sceneId;
    this.runId = runId;
  }

  public String sceneId() {
    return sceneId;
  }

  public UUID runId() {
    return runId;
  }

  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
