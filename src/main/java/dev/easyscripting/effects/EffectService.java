package dev.easyscripting.effects;

import dev.easyscripting.config.*;
import dev.easyscripting.core.*;
import java.util.*;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;

public final class EffectService implements Listener, AutoCloseable {
  private final Settings settings;
  private final TickEngine ticks;
  private final Map<UUID, Entity> owned = new HashMap<>();
  private final Map<UUID, BossBar> bars = new HashMap<>();
  private final Set<UUID> jobs = new HashSet<>();

  public EffectService(Settings settings, TickEngine ticks) {
    this.settings = settings;
    this.ticks = ticks;
  }

  private Location target(Player p) {
    var hit = p.rayTraceBlocks(64);
    return hit == null
        ? p.getLocation().add(p.getLocation().getDirection().multiply(10))
        : hit.getHitPosition().toLocation(p.getWorld());
  }

  public void run(Player p, String kind, int amount) {
    settings.require("effects");
    Location at = target(p);
    switch (kind) {
      case "lightning" -> p.getWorld().strikeLightningEffect(at);
      case "explosion" -> explosion(at);
      case "totem" -> p.playEffect(EntityEffect.PROTECTED_FROM_DEATH);
      case "arrows", "snowballs", "rod" -> {
        if (amount < 1 || amount > settings.file("effects").getInt("projectile-limit", 40))
          throw new IllegalArgumentException("Projectile count exceeds configured limit.");
        for (int i = 0; i < amount; i++) {
          Projectile projectile =
              kind.equals("arrows")
                  ? p.launchProjectile(Arrow.class)
                  : p.launchProjectile(Snowball.class);
          projectile.setVelocity(
              p.getEyeLocation().getDirection().multiply(kind.equals("rod") ? 5 : 2));
          if (projectile instanceof Arrow arrow)
            arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
          own(projectile);
        }
      }
      case "railgun" -> {
        double range = Math.min(128, settings.file("effects").getDouble("railgun-range", 64));
        var hit =
            p.getWorld()
                .rayTrace(
                    p.getEyeLocation(),
                    p.getEyeLocation().getDirection(),
                    range,
                    FluidCollisionMode.NEVER,
                    true,
                    .3,
                    e -> e != p && e instanceof LivingEntity);
        double distance =
            hit == null ? range : hit.getHitPosition().distance(p.getEyeLocation().toVector());
        for (double d = 0; d < distance; d += .5)
          p.getWorld()
              .spawnParticle(
                  Particle.END_ROD,
                  p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(d)),
                  1,
                  0,
                  0,
                  0,
                  0);
        if (hit != null && hit.getHitEntity() instanceof LivingEntity living)
          living.damage(
              Math.max(0, Math.min(100, settings.file("effects").getDouble("railgun-damage", 8))),
              p);
        p.getWorld().playSound(p.getLocation(), "entity.firework_rocket.blast", 1, .5f);
      }
      case "orbital" -> {
        int height =
            Math.max(5, Math.min(100, settings.file("effects").getInt("orbital-height", 30)));
        if (jobs.size() >= 100)
          throw new IllegalArgumentException("Active orbital effect limit reached.");
        UUID[] jobId = new UUID[1];
        jobId[0] =
            ticks.add(
                new TickEngine.Job() {
                  int remaining = height;

                  public boolean tick() {
                    at.getWorld()
                        .spawnParticle(
                            Particle.FLAME, at.clone().add(0, remaining--, 0), 12, .2, .3, .2, .01);
                    if (remaining > 0) return true;
                    explosion(at);
                    return false;
                  }

                  public void stopped() {
                    jobs.remove(jobId[0]);
                  }
                });
        jobs.add(jobId[0]);
      }
      case "wolves" -> {
        if (amount < 1 || amount > settings.file("effects").getInt("wolf-limit", 12))
          throw new IllegalArgumentException("Wolf count exceeds configured limit.");
        for (int i = 0; i < amount; i++) {
          Wolf wolf =
              p.getWorld().spawn(p.getLocation().add((i % 3) - 1, 0, i / 3 + 1), Wolf.class);
          wolf.setOwner(p);
          wolf.setPersistent(false);
          own(wolf);
        }
      }
      case "destructive-explosion" -> {
        if (!p.hasPermission("easyscripting.destructive")
            || !settings.file("config").getBoolean("security.destructive-effects"))
          throw new IllegalArgumentException(
              "Destructive effects need explicit configuration and permission.");
        p.getWorld().createExplosion(at, 3, false, true, p);
      }
      default -> throw new IllegalArgumentException("Unknown effect '" + kind + "'.");
    }
  }

  private void explosion(Location at) {
    at.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, at, 1);
    at.getWorld().playSound(at, "entity.generic.explode", 1, 1);
  }

  private void own(Entity entity) {
    if (owned.size() >= 500) {
      entity.remove();
      throw new IllegalArgumentException("Active effect entity limit reached.");
    }
    entity.setPersistent(false);
    owned.put(entity.getUniqueId(), entity);
    ticks.later(
        Math.max(
            20, Math.min(1200, settings.file("effects").getInt("projectile-lifetime-ticks", 200))),
        () -> {
          Entity e = owned.remove(entity.getUniqueId());
          if (e != null) e.remove();
        });
  }

  public void bossbar(Player p, String text) {
    BossBar old = bars.remove(p.getUniqueId());
    if (old != null) p.hideBossBar(old);
    if (text.equals("off")) return;
    BossBar bar =
        BossBar.bossBar(
            Messages.rich(text),
            1,
            Checks.choice(
                BossBar.Color.class, settings.file("effects").getString("bossbar.color", "BLUE")),
            Checks.choice(
                BossBar.Overlay.class,
                settings.file("effects").getString("bossbar.overlay", "PROGRESS")));
    bars.put(p.getUniqueId(), bar);
    p.showBossBar(bar);
  }

  public void stop() {
    owned.values().forEach(Entity::remove);
    owned.clear();
    for (UUID id : List.copyOf(jobs)) ticks.cancel(id);
    jobs.clear();
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    bossbar(e.getPlayer(), "off");
  }

  @Override
  public void close() {
    stop();
    for (Player p : Bukkit.getOnlinePlayers()) bossbar(p, "off");
    bars.clear();
  }
}
