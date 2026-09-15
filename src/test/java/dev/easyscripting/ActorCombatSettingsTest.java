package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.config.ActorCombatSettings;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ActorCombatSettingsTest {
  private YamlConfiguration config() {
    return YamlConfiguration.loadConfiguration(
        new InputStreamReader(
            Objects.requireNonNull(getClass().getResourceAsStream("/actor-ai.yml")),
            StandardCharsets.UTF_8));
  }

  @Test
  void defaultsBoundSuppliesAndAvoidInstantPerfectReactions() {
    var c = ActorCombatSettings.read(config());
    assertEquals(1, c.refillTicks());
    assertEquals(3, c.potionCount());
    assertTrue(c.reactionMin() > 0);
    assertTrue(c.accuracy() > 0 && c.accuracy() < 1);
    assertTrue(c.shieldChance() > 0 && c.shieldChance() < 1);
  }

  @Test
  void invalidChanceDelayAndInvertedReactionRangeFailReloadValidation() {
    for (String field : List.of("accuracy", "jump-reset-chance", "shield-chance"))
      for (Object bad : List.of(-.1, 1.1, Double.NaN, "0.5")) {
        var y = config();
        y.set("combat." + field, bad);
        assertThrows(IllegalArgumentException.class, () -> ActorCombatSettings.read(y));
      }
    var delay = config();
    delay.set("combat.totem-refill-ticks", 0);
    assertThrows(IllegalArgumentException.class, () -> ActorCombatSettings.read(delay));
    var inverted = config();
    inverted.set("combat.reaction-min-ticks", 10);
    inverted.set("combat.reaction-max-ticks", 2);
    assertThrows(IllegalArgumentException.class, () -> ActorCombatSettings.read(inverted));
  }
}
