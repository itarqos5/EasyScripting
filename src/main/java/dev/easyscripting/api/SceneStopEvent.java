package dev.easyscripting.api;

import java.util.UUID;
import org.bukkit.event.*;

public final class SceneStopEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final String sceneId, reason;
  private final UUID runId;

  public SceneStopEvent(String sceneId, UUID runId, String reason) {
    this.sceneId = sceneId;
    this.runId = runId;
    this.reason = reason;
  }

  public String sceneId() {
    return sceneId;
  }

  public UUID runId() {
    return runId;
  }

  public String reason() {
    return reason;
  }

  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
