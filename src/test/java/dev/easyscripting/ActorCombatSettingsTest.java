package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.config.ActorAiSettings;
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
  void defaultsPaceMeleeByTheWeaponAndLeaveRoomForCriticals() {
    var c = ActorCombatSettings.read(config());
    assertTrue(c.weaponCooldown());
    assertTrue(c.critJumpChance() > 0 && c.critJumpChance() < 1);
    // The hop must finish rising before the strike, or the blow is not a critical.
    assertTrue(c.critJumpDelay() >= 4);
    // The configured interval is only a floor; the weapon's own recharge sets the real pace.
    assertEquals(10, ActorAiSettings.read(config()).attackCooldown());
  }

  @Test
  void followFormationDefaultsToAnAutomaticBlockWithASettlingMargin() {
    var ai = ActorAiSettings.read(config());
    assertEquals(0, ai.followColumns()); // Automatic rows and columns.
    assertTrue(ai.followResumeDistance() > ai.followArrivalDistance());
    assertTrue(ai.followResumeDistance() <= ai.followCatchUpDistance());
  }

  @Test
  void theResumeMarginCannotInvertOrOutgrowTheCatchUpDistance() {
    var narrow = config();
    narrow.set("groups.follow-arrival-distance", 2.9);
    narrow.set("groups.follow-catch-up-distance", 3.0);
    narrow.set("groups.follow-resume-margin", 4.0);
    var ai = ActorAiSettings.read(narrow);
    assertEquals(3.0, ai.followResumeDistance(), 0.0001);
    assertTrue(ai.followResumeDistance() > ai.followArrivalDistance());
    var bad = config();
    bad.set("groups.follow-resume-margin", 0);
    assertThrows(IllegalArgumentException.class, () -> ActorAiSettings.read(bad));
  }

  @Test
  void survivalDefaultsGapBeforeRunningAndLeaveTheShieldUsable() {
    var c = ActorCombatSettings.read(config());
    assertTrue(c.healHealth() > 0 && c.healHealth() < 1);
    // An NPC reaches for food before it reaches for a pearl.
    assertTrue(c.escapeHealth() <= c.healHealth());
    // The default escape threshold must leave more health than the pearl's own 5 damage.
    assertTrue(c.escapeHealth() * 20 > 5);
    assertTrue(c.shieldGroundRadius() > 0); // A mace alongside counts, not only one overhead.
    assertTrue(c.shieldMaxHold() >= c.shieldHold());
    assertTrue(c.strafeChance() > 0 && c.strafeChance() < 1);
    assertTrue(ActorAiSettings.read(config()).sprintChaseDistance() > 0);
  }

  @Test
  void aShorterMaximumShieldHoldNeverFallsBelowASingleHold() {
    var y = config();
    y.set("combat.shield-hold-ticks", 60);
    y.set("combat.shield-max-hold-ticks", 20);
    assertEquals(60, ActorCombatSettings.read(y).shieldMaxHold());
  }

  @Test
  void anEscapeThresholdAboveTheHealingThresholdFailsReloadValidation() {
    var y = config();
    y.set("survival.heal-health", 0.3);
    y.set("survival.escape-health", 0.6);
    assertThrows(IllegalArgumentException.class, () -> ActorCombatSettings.read(y));
    // Healing turned off leaves escaping free to use any threshold.
    y.set("survival.heal-health", 0);
    assertEquals(0.6, ActorCombatSettings.read(y).escapeHealth(), 0.0001);
  }

  @Test
  void invalidChanceDelayAndInvertedReactionRangeFailReloadValidation() {
    for (String field :
        List.of(
            "combat.accuracy",
            "combat.jump-reset-chance",
            "combat.shield-chance",
            "combat.crit-jump-chance",
            "survival.heal-health",
            "survival.escape-health",
            "survival.strafe-chance"))
      for (Object bad : List.of(-.1, 1.1, Double.NaN, "0.5")) {
        var y = config();
        y.set(field, bad);
        assertThrows(IllegalArgumentException.class, () -> ActorCombatSettings.read(y));
      }
    var delay = config();
    delay.set("combat.totem-refill-ticks", 0);
    assertThrows(IllegalArgumentException.class, () -> ActorCombatSettings.read(delay));
    var inverted = config();
    inverted.set("combat.reaction-min-ticks", 10);
    inverted.set("combat.reaction-max-ticks", 2);
    assertThrows(IllegalArgumentException.class, () -> ActorCombatSettings.read(inverted));
    var switched = config();
    switched.set("combat.weapon-cooldown", "yes");
    assertThrows(IllegalArgumentException.class, () -> ActorCombatSettings.read(switched));
    var columns = config();
    columns.set("groups.follow-columns", 33);
    assertThrows(IllegalArgumentException.class, () -> ActorAiSettings.read(columns));
    var retreat = config();
    retreat.set("survival.retreat-distance", 2);
    assertThrows(IllegalArgumentException.class, () -> ActorCombatSettings.read(retreat));
    var sprint = config();
    sprint.set("groups.sprint-chase-distance", 0);
    assertThrows(IllegalArgumentException.class, () -> ActorAiSettings.read(sprint));
  }
}
