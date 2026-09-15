package dev.easyscripting.items;

import dev.easyscripting.actors.ActorService;
import dev.easyscripting.commands.Args;
import dev.easyscripting.config.Access;
import dev.easyscripting.players.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** All recipients are validated on the main thread before any loadout is replaced. */
public final class KitClaims {
  private final KitService kits;
  private final PlayerService players;
  private final ActorService actors;
  private final NicknameService nicknames;
  private final Access access;

  public KitClaims(
      KitService kits,
      PlayerService players,
      ActorService actors,
      NicknameService nicknames,
      Access access) {
    this.kits = kits;
    this.players = players;
    this.actors = actors;
    this.nicknames = nicknames;
    this.access = access;
  }

  public Player player(String name) {
    if (name.startsWith("player:")) name = name.substring(7);
    Player found;
    try {
      found = Bukkit.getPlayer(UUID.fromString(name));
    } catch (IllegalArgumentException ex) {
      found = nicknames.target(name);
    }
    if (found == null || !found.isOnline() || !IdentityService.realPlayer(found))
      throw new IllegalArgumentException("Choose an online real player or nickname, not an NPC.");
    return found;
  }

  public List<String> names() {
    return nicknames.names();
  }

  public int claim(CommandSender sender, KitClaimRequest request) {
    String id = request.kit();
    kits.contents(id);
    String target = request.target();
    if (target == null) {
      Player self = Args.player(sender);
      available(self);
      kits.claim(id, self);
      return 1;
    }
    if (target.startsWith("actor:")) {
      access.require(sender, "kit.edit");
      String actorId = target.substring(6);
      actors.available(actorId);
      var actor = actors.get(actorId);
      kits.apply(id, actor.requireEntity());
      actors.rememberKit(actor.id(), kits.contents(id));
      actors.save(actor);
      return 1;
    }
    List<? extends Player> recipients;
    if (target.equals("*")) {
      access.require(sender, "kit.edit");
      recipients = Bukkit.getOnlinePlayers().stream().filter(IdentityService::realPlayer).toList();
      if (recipients.isEmpty()) throw new IllegalArgumentException("No real players are online.");
    } else {
      Player recipient = player(target);
      if (!recipient.equals(sender)) access.require(sender, "kit.edit");
      else kits.requireClaim(id, sender);
      recipients = List.of(recipient);
    }
    recipients.forEach(this::available);
    recipients.forEach(p -> kits.apply(id, p));
    return recipients.size();
  }

  private void available(Player player) {
    if (!player.isOnline() || !IdentityService.realPlayer(player) || player.isDead())
      throw new IllegalArgumentException("Player must be online and alive: " + player.getName());
    players.available(player.getUniqueId());
  }
}
