package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.actors.ImmortalDamage;
import dev.easyscripting.config.ActorDefaults;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ImmortalDamageTest {
  @Test
  void repeatedLethalHitsStayPositiveAndNeverCrossTheHealthFloor() {
    double health = 20;
    for (int hit = 0; hit < 100; hit++) {
      double before = ImmortalDamage.healthBeforeHit(health, 20);
      var finalDamage = new AtomicReference<>(0.0);
      ImmortalDamage.limit(
          1000,
          before - ImmortalDamage.floor(20),
          raw -> {
            finalDamage.set(raw);
            return raw;
          });
      assertTrue(finalDamage.get() > 0, "Native hurt/knockback path must retain positive damage");
      health = before - finalDamage.get();
      assertTrue(health >= 1);
    }
  }

  @Test
  void customMaximumHealthBelowOneHeartIsSupported() {
    double health = ImmortalDamage.healthBeforeHit(.25, .5);
    assertEquals(.5, health);
    assertEquals(.25, ImmortalDamage.floor(.5));
  }

  @Test
  void modifiersAreRecalculatedUntilDamageFits() {
    var damage = new AtomicReference<>(0.0);
    ImmortalDamage.limit(
        100,
        1,
        raw -> {
          damage.set(raw * 3);
          return raw * 3;
        });
    assertTrue(damage.get() > 0 && damage.get() <= 1);
  }

  @Test
  void creationDefaultsMigrateOnceAndRemainConfigurable() {
    var config = new YamlConfiguration();
    config.set("actors.defaults.immortal", true);
    assertTrue(ActorDefaults.migrate(config));
    assertFalse(config.getBoolean("actors.defaults.immortal"));
    config.set("actors.defaults.immortal", true);
    assertFalse(ActorDefaults.migrate(config));
    assertTrue(config.getBoolean("actors.defaults.immortal"));
  }
}
