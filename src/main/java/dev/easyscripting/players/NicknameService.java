package dev.easyscripting.players;

import dev.easyscripting.actors.ActorService;
import dev.easyscripting.config.*;
import dev.easyscripting.core.TickEngine;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Requests are polled by the existing main-thread engine; workers never schedule Bukkit tasks. */
public final class NicknameService implements AutoCloseable {
  private final Settings settings;
  private final Messages messages;
  private final IdentityService identities;
  private final ActorService actors;
  private final TickEngine ticks;
  private final IdentityProvider provider;
  private final DeadUserRegistry deadUsers;
  private final Map<UUID, UUID> jobs = new HashMap<>();
  private boolean closing;

  public NicknameService(
      Settings settings,
      Messages messages,
      IdentityService identities,
      ActorService actors,
      TickEngine ticks,
      IdentityProvider provider,
      DeadUserRegistry deadUsers) {
    this.settings = settings;
    this.messages = messages;
    this.identities = identities;
    this.actors = actors;
    this.ticks = ticks;
    this.provider = provider;
    this.deadUsers = deadUsers;
    identities.onNicknameChange(this::cancel);
  }

  public Player target(String name) {
    UUID id =
        identities
            .directory()
            .resolve(name)
            .orElseThrow(
                () -> new IllegalArgumentException("Choose an online real username or nickname."));
    Player player = Bukkit.getPlayer(id);
    if (player == null || !IdentityService.realPlayer(player))
      throw new IllegalArgumentException("Choose an online real player, not an NPC.");
    return player;
  }

  public List<String> names() {
    return identities.directory().names();
  }

  public void random(Player player, CommandSender sender) {
    settings.require("identity");
    identities.requireAvailable(player);
    if (closing || !ticks.acceptingWork())
      throw new IllegalStateException("EasyScripting is stopping.");
    if (jobs.size() >= 20 && !jobs.containsKey(player.getUniqueId()))
      throw new IllegalArgumentException(
          "Twenty nickname requests are already active. Try again shortly.");
    cancel(player.getUniqueId());
    var config = settings.file("nicknames");
    boolean fallback = config.getBoolean("local-fallback", true);
    boolean api = config.getBoolean("api-enabled", true);
    CompletableFuture<List<String>> response;
    try {
      response =
          api
              ? provider.requestUsernames(config.getInt("api-timeout-millis", 4000))
              : CompletableFuture.completedFuture(List.of());
    } catch (RejectedExecutionException full) {
      throw new IllegalArgumentException("Username lookup queue is full. Try again shortly.");
    }
    var future = response;
    String account = identities.accountName(player);
    sender.sendMessage(messages.text("nickname-pending", Map.of("name", account)));
    UUID job =
        ticks.add(
            new TickEngine.Job() {
              public boolean tick() {
                if (!future.isDone()) return true;
                jobs.remove(player.getUniqueId());
                if (closing
                    || Bukkit.getPlayer(player.getUniqueId()) != player
                    || !IdentityService.realPlayer(player)
                    || !settings.enabled("identity")) return false;
                try {
                  identities.requireAvailable(player);
                  List<String> choices;
                  try {
                    choices = new ArrayList<>(future.join());
                  } catch (CompletionException | CancellationException error) {
                    choices = new ArrayList<>();
                  }
                  Collections.shuffle(choices);
                  String name =
                      choices.stream().filter(n -> usable(player, n)).findFirst().orElse(null);
                  if (name == null && (fallback || !api)) {
                    List<String> occupied = new ArrayList<>(identities.directory().names());
                    actors.list().forEach(a -> occupied.add(a.definition.name));
                    name =
                        settings
                            .npcIdentities()
                            .choose(
                                occupied,
                                candidate ->
                                    identities.blocked(candidate)
                                        || deadUsers.contains(candidate)
                                        || provider.reservedRealName(candidate),
                                identities::blocked,
                                false,
                                "",
                                ThreadLocalRandom.current(),
                                List.of(),
                                List.of(),
                                List.of())
                            .name();
                  }
                  if (name == null)
                    throw new IllegalArgumentException(
                        "Username API unavailable or no unused name returned. Try again shortly.");
                  identities.nick(player, name);
                  provider.claim(name);
                  disguise(player);
                  if (!(sender instanceof Player p) || p.isOnline())
                    sender.sendMessage(
                        messages.text(
                            "nickname-applied", Map.of("name", account, "nickname", name)));
                } catch (IllegalArgumentException | IllegalStateException error) {
                  if (!(sender instanceof Player p) || p.isOnline())
                    messages.error(sender, error.getMessage());
                }
                return false;
              }

              public void stopped() {
                jobs.remove(player.getUniqueId());
                future.cancel(true);
              }
            });
    jobs.put(player.getUniqueId(), job);
  }

  /**
   * Give the alias a face as well as a name. A nickname worn over the account's own skin is
   * recognisable on sight, which defeats the point of a temporary identity, so a public skin
   * owner is drawn from the same cached pool actor identities use. The skin is applied
   * asynchronously and is never allowed to cost the player the nickname they just received:
   * an empty pool, a blacklisted owner or a failed lookup simply leaves the current skin alone.
   */
  private void disguise(Player player) {
    var config = settings.file("nicknames");
    if (!config.getBoolean("random-skin", true)) return;
    List<String> owners = new ArrayList<>(provider.skinOwners());
    if (owners.isEmpty() && config.getBoolean("local-fallback", true))
      owners.addAll(settings.npcIdentities().skins());
    String account = identities.accountName(player);
    owners.removeIf(
        owner ->
            owner == null
                || owner.isBlank()
                || owner.equalsIgnoreCase(account)
                || identities.blocked(owner)
                || deadUsers.contains(owner));
    if (owners.isEmpty()) return;
    try {
      identities.skin(player, owners.get(ThreadLocalRandom.current().nextInt(owners.size())));
    } catch (IllegalArgumentException | IllegalStateException ignored) {
      // A full lookup queue or a disabled feature costs a skin, never the name.
    }
  }

  private boolean usable(Player player, String name) {
    return GeneratedUsername.valid(name)
        && identities.directory().available(player.getUniqueId(), name)
        && !identities.blocked(name)
        && !deadUsers.contains(name)
        && !provider.reservedRealName(name)
        && !name.equalsIgnoreCase(identities.accountName(player))
        && actors.list().stream().noneMatch(a -> a.definition.name.equalsIgnoreCase(name));
  }

  public void reset(Player player, CommandSender sender) {
    cancel(player.getUniqueId());
    if (identities.directory().nicknamed(player.getUniqueId())) identities.reset(player);
    sender.sendMessage(
        messages.text("nickname-reset", Map.of("name", identities.accountName(player))));
  }

  public void resetAll(CommandSender sender) {
    for (UUID id : List.copyOf(jobs.keySet())) cancel(id);
    for (UUID id : identities.directory().ids())
      if (identities.directory().nicknamed(id)) {
        Player player = Bukkit.getPlayer(id);
        if (player != null) identities.reset(player);
      }
    sender.sendMessage(messages.text("nicknames-reset", Map.of()));
  }

  private void cancel(UUID id) {
    UUID job = jobs.remove(id);
    if (job != null) ticks.cancel(job);
  }

  public void close() {
    closing = true;
    for (UUID id : List.copyOf(jobs.keySet())) cancel(id);
  }
}
