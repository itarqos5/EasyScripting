package dev.easyscripting.actors;

import dev.easyscripting.config.Messages;
import dev.easyscripting.core.Checks;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MobBackend implements ActorBackend {
  private final JavaPlugin plugin;

  public MobBackend(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public String name() {
    return "Paper mob";
  }

  @Override
  public Handle spawn(ActorDefinition d) {
    EntityType type = Checks.choice(EntityType.class, d.type);
    if (!type.isAlive() || !type.isSpawnable() || type == EntityType.PLAYER)
      throw new IllegalArgumentException(
          "Actor type "
              + type
              + " needs an installed, compatible Citizens plugin; use ZOMBIE or another living mob"
              + " without it.");
    LivingEntity entity = (LivingEntity) d.location.getWorld().spawnEntity(d.location, type);
    entity.setPersistent(false);
    entity.setRemoveWhenFarAway(false);
    entity
        .getPersistentDataContainer()
        .set(new NamespacedKey(plugin, "actor"), PersistentDataType.STRING, d.id);
    if (entity instanceof Mob mob) {
      Bukkit.getMobGoals().removeAllGoals(mob);
      mob.setAware(false);
    }
    if (entity instanceof Zombie zombie) zombie.setShouldBurnInDay(false);
    return new Handle() {
      public LivingEntity entity() {
        return entity.isValid() ? entity : null;
      }

      public void move(Location target, double speed) {
        if (!(entity instanceof Mob mob))
          throw new IllegalArgumentException(
              "This actor type cannot navigate; use teleport or a recorded path.");
        mob.setAware(true);
        if (!mob.getPathfinder().moveTo(target, speed))
          throw new IllegalArgumentException("No walkable path to destination.");
      }

      public void stop() {
        if (entity instanceof Mob mob) {
          mob.getPathfinder().stopPathfinding();
          mob.setAware(false);
        }
      }

      public void name(String name) {
        entity.customName(Messages.rich(name));
      }

      public void skin(String name) {
        if (!name.isEmpty())
          throw new IllegalArgumentException("Player skins require a PLAYER actor and Citizens.");
      }

      public void remove() {
        entity.remove();
      }
    };
  }
}
