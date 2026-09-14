package dev.easyscripting.api;

import java.util.Optional;
import org.bukkit.entity.LivingEntity;

/** Main-thread-only live actor handle. Never retain the entity across ticks. */
public interface Actor {
  String id();

  String group();

  Optional<LivingEntity> entity();
}
