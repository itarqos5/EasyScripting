package dev.easyscripting.actors;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.config.NpcIdentities;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

class ActorIdentityReservationTest {
  @Test
  void automaticCopiesReceiveAFreshIdentityBeforeSpawn() {
    var copy = new ActorDefinition("copy_2", "PLAYER", new Location(null, 0, 64, 0));
    copy.name = "Source_1";
    copy.skin = "Notch";

    ActorService.prepareCopyIdentity(
        copy,
        true,
        definition -> {
          definition.name = "Fresh_9";
          definition.skin = "jeb_";
        });

    assertEquals("Fresh_9", copy.name);
    assertEquals("jeb_", copy.skin);
    assertNotEquals("Source_1", copy.name);
  }

  @Test
  void copiesUseTheirOwnIdWhenAutomaticIdentitiesAreDisabled() {
    var copy = new ActorDefinition("copy_2", "ZOMBIE", new Location(null, 0, 64, 0));
    copy.name = "Source_1";
    var allocatorCalled = new AtomicBoolean();

    ActorService.prepareCopyIdentity(copy, false, definition -> allocatorCalled.set(true));

    assertEquals("copy_2", copy.name);
    assertFalse(allocatorCalled.get());
  }

  @Test
  void hiddenActiveNicknameIsReservedIndependentlyOfTheCurrentProfileName() {
    List<String> reserved =
        ActorService.identityReservations(
            List.of("Actor_1"),
            List.of("Alice", "Costume_3"),
            List.of("HiddenNick_4", "ALICE"),
            "copy_2");
    var identities =
        new NpcIdentities(
            true, List.of("HiddenNick_4", "Fresh_5"), List.of("Notch"));

    var selected =
        identities.choose(reserved, name -> false, false, "", new Random(1));

    assertEquals("Fresh_5", selected.name());
    assertEquals(5, reserved.size());
    assertTrue(reserved.stream().anyMatch("HiddenNick_4"::equalsIgnoreCase));
  }
}
