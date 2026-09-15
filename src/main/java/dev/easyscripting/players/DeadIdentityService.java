package dev.easyscripting.players;

import dev.easyscripting.actors.ActorService;
import dev.easyscripting.core.TickEngine;
import java.util.Objects;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Connects natural deaths to the persistent username retirement registry. */
public final class DeadIdentityService implements Listener {
  private final IdentityService identities;
  private final DeadUserRegistry deadUsers;
  private final TickEngine ticks;

  public DeadIdentityService(
      ActorService actors,
      IdentityService identities,
      DeadUserRegistry deadUsers,
      TickEngine ticks) {
    this.identities = identities;
    this.deadUsers = deadUsers;
    this.ticks = ticks;
    actors.onDied(
        definition -> {
          if (!definition.name.isBlank())
            deadUsers.retire(
                definition.name,
                "actor",
                definition.id,
                definition.skin,
                definition.skinTexture,
                definition.skinSignature);
        });
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void playerDeath(PlayerDeathEvent event) {
    if (!IdentityService.realPlayer(event.getPlayer())) return;
    var directory = identities.directory().get(event.getPlayer().getUniqueId());
    if (directory == null || directory.nickname() == null) return;
    String nickname = directory.nickname();
    var texture =
        event.getPlayer().getPlayerProfile().getProperties().stream()
            .filter(property -> property.getName().equals("textures"))
            .findFirst()
            .orElse(null);
    deadUsers.retire(
        nickname,
        "player",
        event.getPlayer().getUniqueId().toString(),
        identities.accountName(event.getPlayer()),
        texture == null ? "" : texture.getValue(),
        texture == null ? "" : Objects.requireNonNullElse(texture.getSignature(), ""));
    if (ticks.acceptingWork())
      ticks.later(
          1,
          () -> {
            var current = identities.directory().get(event.getPlayer().getUniqueId());
            if (event.getPlayer().isOnline()
                && current != null
                && nickname.equalsIgnoreCase(current.nickname())) identities.reset(event.getPlayer());
          });
  }
}
