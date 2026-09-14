package dev.easyscripting.actors;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

public interface ActorBackend extends AutoCloseable {
  interface Handle {
    LivingEntity entity();

    void move(Location target, double speed);

    void stop();

    void name(String name);

    void skin(String name);

    /** Copies a resolved skin to persistent actor data. Mob backends have no player skin. */
    default boolean captureSkin(ActorDefinition definition) {
      return false;
    }

    void remove();
  }

  Handle spawn(ActorDefinition definition);

  String name();

  @Override
  default void close() {}
}
