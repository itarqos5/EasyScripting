package dev.easyscripting.scenes;

import dev.easyscripting.actors.ActorService;
import dev.easyscripting.api.Scene;
import dev.easyscripting.config.*;
import dev.easyscripting.core.*;
import dev.easyscripting.players.PlayerService;
import java.time.Duration;
import java.util.*;
import java.util.function.BiConsumer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

public final class ActionRegistry {
  public record Context(
      CommandSender sender,
      Map<String, String> bindings,
      ActorService actors,
      PlayerService players) {
    public String reference(String target) {
      return bindings.getOrDefault(target, target);
    }

    public LivingEntity entity(String target) {
      String ref = reference(target);
      if (ref.startsWith("actor:")) return actors.get(ref.substring(6)).requireEntity();
      if (ref.equals("self") || ref.equals("player:self")) {
        if (sender instanceof Player p) return p;
        throw new IllegalArgumentException(
            "This scene requires a player director or an explicit binding.");
      }
      if (ref.startsWith("player:")) return players.player(ref.substring(7));
      throw new IllegalArgumentException(
          "Target '" + target + "' must be a scene binding, actor:<id>, player:<name>, or self.");
    }

    public Player player(String target) {
      if (entity(target) instanceof Player p) return p;
      throw new IllegalArgumentException("Action requires a player target.");
    }

    public String actorId(String target) {
      String ref = reference(target);
      if (!ref.startsWith("actor:"))
        throw new IllegalArgumentException("Action requires an actor: target.");
      return ref.substring(6);
    }
  }

  public record Spec(
      String permission, List<String> required, BiConsumer<Context, Scene.Action> executor) {}

  private final Map<String, Spec> actions = new TreeMap<>();
  private final Settings settings;
  private final Access access;

  public ActionRegistry(Settings settings) {
    this.settings = settings;
    this.access = new Access(settings);
    add(
        "teleport",
        "player",
        "world x y z",
        (c, a) -> {
          if (!c.entity(a.target()).teleport(location(a)))
            throw new IllegalArgumentException("Teleport was cancelled.");
        });
    add(
        "move",
        "actor",
        "world x y z",
        (c, a) ->
            c.actors.get(c.actorId(a.target())).move(location(a), number(a, "speed", "1", .1, 5)));
    add(
        "look",
        "player",
        "at",
        (c, a) -> Positions.face(c.entity(a.target()), c.entity(a.arg("at")).getEyeLocation()));
    add(
        "rotation",
        "player",
        "yaw pitch",
        (c, a) ->
            c.entity(a.target())
                .setRotation(
                    (float) number(a, "yaw", "0", -360, 360),
                    (float) number(a, "pitch", "0", -90, 90)));
    add(
        "velocity",
        "player",
        "x y z",
        (c, a) ->
            c.entity(a.target())
                .setVelocity(
                    new Vector(
                        number(a, "x", "0", -10, 10),
                        number(a, "y", "0", -10, 10),
                        number(a, "z", "0", -10, 10))));
    add(
        "health",
        "player",
        "value",
        (c, a) -> {
          LivingEntity e = c.entity(a.target());
          e.setHealth(
              number(
                  a,
                  "value",
                  "20",
                  .01,
                  Objects.requireNonNull(e.getAttribute(Attribute.MAX_HEALTH)).getValue()));
        });
    add(
        "damage",
        "effects",
        "value",
        (c, a) -> c.entity(a.target()).damage(number(a, "value", "1", 0, 1000)));
    add(
        "attack",
        "actor",
        "victim",
        (c, a) ->
            c.actors.attack(
                c.actorId(a.target()),
                c.entity(a.arg("victim")),
                number(a, "damage", "1", 0, 1000)));
    add("swing", "effects", "", (c, a) -> c.entity(a.target()).swingMainHand());
    add("hurt", "effects", "", (c, a) -> c.entity(a.target()).playHurtAnimation(0));
    add(
        "critical",
        "effects",
        "",
        (c, a) -> {
          LivingEntity e = c.entity(a.target());
          e.getWorld()
              .spawnParticle(Particle.CRIT, e.getLocation().add(0, 1, 0), 16, .3, .5, .3, .05);
        });
    add(
        "jump",
        "actor",
        "",
        (c, a) -> {
          LivingEntity e = c.entity(a.target());
          e.setVelocity(e.getVelocity().setY(number(a, "height", "0.42", .1, 3)));
        });
    add(
        "pose",
        "actor",
        "value",
        (c, a) -> c.entity(a.target()).setPose(Checks.choice(Pose.class, a.arg("value")), true));
    add(
        "sneak",
        "actor",
        "value",
        (c, a) -> c.actors.set(c.actorId(a.target()), "sneak", a.arg("value")));
    add(
        "sprint",
        "actor",
        "value",
        (c, a) -> c.actors.set(c.actorId(a.target()), "sprint", a.arg("value")));
    add(
        "equip",
        "items",
        "slot material",
        (c, a) -> {
          LivingEntity e = c.entity(a.target());
          EntityEquipment eq = e.getEquipment();
          if (eq == null) throw new IllegalArgumentException("Target has no equipment slots.");
          eq.setItem(
              Checks.choice(EquipmentSlot.class, a.arg("slot")),
              new ItemStack(material(a.arg("material")), (int) number(a, "amount", "1", 1, 64)));
        });
    add(
        "potion",
        "player",
        "effect",
        (c, a) ->
            c.entity(a.target())
                .addPotionEffect(
                    new PotionEffect(
                        effect(a.arg("effect")),
                        (int) number(a, "ticks", "200", 1, 72000),
                        (int) number(a, "amplifier", "0", 0, 10))));
    add(
        "flag",
        "player",
        "name value",
        (c, a) -> c.players.control(c.player(a.target()), a.arg("name"), a.arg("value")));
    add(
        "fire",
        "effects",
        "ticks",
        (c, a) -> c.entity(a.target()).setFireTicks((int) number(a, "ticks", "20", 0, 12000)));
    add(
        "death",
        "destructive",
        "",
        (c, a) -> {
          if (c.reference(a.target()).startsWith("actor:")) c.actors.kill(c.actorId(a.target()));
          else c.entity(a.target()).setHealth(0);
        });
    add(
        "fake-death",
        "effects",
        "",
        (c, a) -> {
          LivingEntity e = c.entity(a.target());
          e.playHurtAnimation(0);
          e.getWorld().playSound(e.getLocation(), "entity.player.death", 1, 1);
          e.getWorld().spawnParticle(Particle.POOF, e.getLocation(), 25, .3, 1, .3, .02);
        });
    add(
        "title",
        "effects",
        "text",
        (c, a) ->
            c.player(a.target())
                .showTitle(
                    Title.title(
                        Messages.rich(a.arg("text")),
                        Messages.rich(a.arg("subtitle", "")),
                        Title.Times.times(
                            Duration.ofMillis(250),
                            Duration.ofMillis((long) number(a, "ticks", "40", 1, 1200) * 50),
                            Duration.ofMillis(250)))));
    add(
        "actionbar",
        "effects",
        "text",
        (c, a) -> c.player(a.target()).sendActionBar(Messages.rich(a.arg("text"))));
    add(
        "message",
        "chat",
        "text",
        (c, a) -> c.player(a.target()).sendMessage(Messages.rich(a.arg("text"))));
    add(
        "sound",
        "effects",
        "sound",
        (c, a) -> {
          LivingEntity e = c.entity(a.target());
          e.getWorld()
              .playSound(
                  e.getLocation(),
                  a.arg("sound"),
                  (float) number(a, "volume", "1", 0, 4),
                  (float) number(a, "pitch", "1", .1, 2));
        });
    add(
        "particle",
        "effects",
        "particle",
        (c, a) -> {
          LivingEntity e = c.entity(a.target());
          Particle p = Checks.choice(Particle.class, a.arg("particle"));
          if (p.getDataType() != Void.class)
            throw new IllegalArgumentException(
                "This particle requires data; use a data-free particle such as POOF.");
          e.getWorld()
              .spawnParticle(
                  p,
                  e.getLocation().add(0, 1, 0),
                  (int) number(a, "count", "20", 1, 500),
                  .5,
                  .5,
                  .5,
                  .02);
        });
    add(
        "lightning",
        "effects",
        "",
        (c, a) -> {
          LivingEntity e = c.entity(a.target());
          e.getWorld().strikeLightningEffect(e.getLocation());
        });
    add(
        "explosion",
        "effects",
        "",
        (c, a) -> {
          LivingEntity e = c.entity(a.target());
          e.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, e.getLocation(), 1);
          e.getWorld().playSound(e.getLocation(), "entity.generic.explode", 1, 1);
        });
    add(
        "time",
        "world",
        "value",
        (c, a) ->
            c.entity(a.target()).getWorld().setTime((long) number(a, "value", "6000", 0, 24000)));
    add(
        "weather",
        "world",
        "value",
        (c, a) -> {
          World world = c.entity(a.target()).getWorld();
          String weather = a.arg("value");
          if (!List.of("clear", "rain", "thunder").contains(weather))
            throw new IllegalArgumentException("Weather must be clear, rain or thunder.");
          world.setStorm(!weather.equals("clear"));
          world.setThundering(weather.equals("thunder"));
        });
    add(
        "wait",
        "scene.play",
        "",
        (c, a) -> {
          /* An explicit final timeline marker holds the take until this tick. */
        });
    add(
        "player-command",
        "commands.player",
        "command",
        (c, a) -> {
          requireCommands();
          c.player(a.target()).performCommand(a.arg("command"));
        });
    add(
        "console-command",
        "commands.console",
        "command",
        (c, a) -> {
          requireCommands();
          Bukkit.dispatchCommand(Bukkit.getConsoleSender(), a.arg("command"));
        });
  }

  private void add(
      String type, String permission, String required, BiConsumer<Context, Scene.Action> execute) {
    actions.put(
        type,
        new Spec(
            "easyscripting." + permission,
            required.isBlank() ? List.of() : List.of(required.split(" ")),
            execute));
  }

  public Set<String> types() {
    return Collections.unmodifiableSet(actions.keySet());
  }

  public Spec spec(String type) {
    Spec spec = actions.get(type);
    if (spec == null) throw new IllegalArgumentException("Unknown scene action '" + type + "'.");
    return spec;
  }

  public void validate(Context c, Scene.Action a) {
    Spec spec = spec(a.type());
    if (!access.allowed(c.sender, spec.permission))
      throw new IllegalArgumentException(
          "Missing permission " + spec.permission + " for action " + a.type());
    String feature =
        switch (spec.permission.substring(14)) {
          case "actor" -> "actors";
          case "player" -> "players";
          case "scene.play" -> "scenes";
          case "destructive" -> "death";
          case "commands.player", "commands.console" -> "scenes";
          default -> spec.permission.substring(14);
        };
    settings.require(feature);
    for (String key : spec.required) a.arg(key);
    LivingEntity target = c.entity(a.target());
    if (target instanceof Player player
        && !c.reference(a.target()).startsWith("actor:")
        && !player.equals(c.sender)) access.require(c.sender, "player.others");
    for (String reference : List.of("at", "victim")) {
      if (!a.arguments().containsKey(reference)) continue;
      LivingEntity other = c.entity(a.arg(reference));
      // Secondary targets are included in the take snapshot and restored on completion too.
      if (other instanceof Player player
          && !c.reference(a.arg(reference)).startsWith("actor:")
          && !player.equals(c.sender)) access.require(c.sender, "player.others");
    }
    if (a.type().equals("teleport") || a.type().equals("move")) location(a);
    if (a.type().endsWith("-command")) requireCommands();
  }

  public void execute(Context c, Scene.Action a) {
    validate(c, a);
    spec(a.type()).executor.accept(c, a);
  }

  private static Location location(Scene.Action a) {
    return Positions.parse(
        a.arg("world"), a.arg("x"), a.arg("y"), a.arg("z"), a.arg("yaw", "0"), a.arg("pitch", "0"));
  }

  private static double number(
      Scene.Action a, String key, String fallback, double min, double max) {
    return Checks.decimal(a.arg(key, fallback), min, max);
  }

  private static Material material(String name) {
    Material m = Material.matchMaterial(name);
    if (m == null || !m.isItem())
      throw new IllegalArgumentException("Unknown item material '" + name + "'.");
    return m;
  }

  private static PotionEffectType effect(String name) {
    PotionEffectType e =
        Registry.EFFECT.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
    if (e == null) throw new IllegalArgumentException("Unknown potion effect '" + name + "'.");
    return e;
  }

  private void requireCommands() {
    if (!settings.file("config").getBoolean("security.allow-command-actions"))
      throw new IllegalArgumentException(
          "Command actions are disabled in config.yml security.allow-command-actions.");
  }
}
