package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.actors.ActorGroup;
import dev.easyscripting.config.ActorAiSettings;
import dev.easyscripting.storage.YamlStore;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActorGroupTest {
  @TempDir Path temp;

  private YamlConfiguration config() {
    return YamlConfiguration.loadConfiguration(
        new InputStreamReader(
            Objects.requireNonNull(getClass().getResourceAsStream("/actor-ai.yml")),
            StandardCharsets.UTF_8));
  }

  @Test
  void groupDefinitionRoundTripsThroughDiskWithoutSharedPlayerState() {
    ActorGroup group = new ActorGroup("red");
    group.leader = UUID.randomUUID();
    group.intelligence = false;
    group.memberImmortal = true;
    group.memberKit = "guards";
    group.nextActorIndex = 37;
    group.order = ActorGroup.Order.FOLLOW;
    try (YamlStore store = new YamlStore(temp, Logger.getAnonymousLogger())) {
      store.save("groups", group.id, group.yaml()).join();
      var loaded = ActorGroup.read("red", store.load("groups").get("red"));
      assertEquals(group.leader, loaded.leader);
      assertFalse(loaded.intelligence);
      assertEquals(Boolean.TRUE, loaded.memberImmortal);
      assertEquals("guards", loaded.memberKit);
      assertEquals(37, loaded.nextActorIndex);
      assertEquals(ActorGroup.Order.FOLLOW, loaded.order);
      assertEquals(
          Set.of("schema", "leader", "intelligence", "shared", "next-actor-index", "order"),
          loaded.yaml().getKeys(false));
    }
  }

  @Test
  void absentSharedSettingsKeepExistingMemberChoicesAndStartToolAtOne() {
    var yaml = new ActorGroup("red").yaml();
    yaml.set("shared", null);
    yaml.set("next-actor-index", null);
    var loaded = ActorGroup.read("red", yaml);
    assertNull(loaded.memberImmortal);
    assertEquals("", loaded.memberKit);
    assertEquals(1, loaded.nextActorIndex);
  }

  @Test
  void malformedOrExhaustedToolCountersAreRejected() {
    for (Object invalid : List.of(0, -1, Integer.MAX_VALUE, "2", 2L, 1.5)) {
      var yaml = new ActorGroup("red").yaml();
      yaml.set("next-actor-index", invalid);
      var error =
          assertThrows(IllegalArgumentException.class, () -> ActorGroup.read("red", yaml));
      assertTrue(error.getMessage().contains("next-actor-index"));
    }
    var largestAllowed = new ActorGroup("red").yaml();
    largestAllowed.set("next-actor-index", Integer.MAX_VALUE - 1);
    assertEquals(
        Integer.MAX_VALUE - 1, ActorGroup.read("red", largestAllowed).nextActorIndex);
  }

  @Test
  void temporaryMovementDoesNotResumeOnRestart() {
    var group = new ActorGroup("red");
    group.order = ActorGroup.Order.MOVE;
    assertEquals(ActorGroup.Order.HOLD, ActorGroup.read("red", group.yaml()).order);
  }

  @Test
  void onlyAssignedLeaderOrOperatorCanIssueOrders() {
    var group = new ActorGroup("red");
    UUID leader = UUID.randomUUID();
    assertFalse(group.canOrder(false, leader));
    group.leader = leader;
    assertTrue(group.canOrder(false, leader));
    assertFalse(group.canOrder(false, UUID.randomUUID()));
    assertFalse(group.canOrder(false, null));
    assertTrue(group.canOrder(true, null));
    group.leader = UUID.randomUUID();
    assertFalse(group.canOrder(false, leader));
  }

  @Test
  void reservedGroupAndMalformedIdentityAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new ActorGroup("default"));
    assertThrows(IllegalArgumentException.class, () -> new ActorGroup("../escape"));
    var y = new ActorGroup("red").yaml();
    y.set("leader", "not-a-uuid");
    assertThrows(IllegalArgumentException.class, () -> ActorGroup.read("red", y));
  }

  @Test
  void boundedAiSettingsRejectDangerousOrMalformedBudgets() {
    var defaults = config();
    var ai = ActorAiSettings.read(defaults);
    assertEquals(8, ai.pathsPerTick());
    assertEquals(3, ai.meleeReach());
    assertEquals(5, ai.followRepathTicks());
    assertEquals(0.5, ai.followGoalChange());
    assertEquals(1.0, ai.followArrivalDistance());
    assertEquals(1.0, ai.followSpeed());
    assertEquals(1.3, ai.followCatchUpSpeed());
    assertEquals(32, ai.followWaypointDistance());
    for (Object bad : List.of(0, 33, 1.2, "8", Double.NaN)) {
      var y = config();
      y.set("groups.paths-per-tick", bad);
      assertThrows(IllegalArgumentException.class, () -> ActorAiSettings.read(y));
    }
    defaults.set("groups.melee-reach", 8);
    assertThrows(IllegalArgumentException.class, () -> ActorAiSettings.read(defaults));
    var crossed = config();
    crossed.set("groups.follow-arrival-distance", 10);
    assertThrows(IllegalArgumentException.class, () -> ActorAiSettings.read(crossed));
    var slowCatchUp = config();
    slowCatchUp.set("groups.follow-catch-up-speed", 0.5);
    assertThrows(IllegalArgumentException.class, () -> ActorAiSettings.read(slowCatchUp));
  }
}
