package dev.easyscripting.actors;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

public interface ActorBackend extends AutoCloseable {
  interface Handle {
    LivingEntity entity();

    void move(Location target, double speed);

    void stop();

    boolean navigating();

    default void look(Location target) {
      LivingEntity entity = entity();
      if (entity != null && target != null) dev.easyscripting.core.Positions.face(entity, target);
    }

    void name(String name);

    void skin(String name);

    default void tablist(boolean listed) {}

    default void appearance(boolean nametag, boolean collidable) {}

    /** Citizens can replace its entity while refreshing a name or skin. */
    default void onEntityChanged(java.util.function.Consumer<LivingEntity> listener) {}

    /** A temporary backend identity respawn, distinct from removal/death. */
    default boolean refreshing() {
      return false;
    }

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
