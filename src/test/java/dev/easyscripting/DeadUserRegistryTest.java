package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.players.DeadUserRegistry;
import dev.easyscripting.storage.YamlStore;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeadUserRegistryTest {
  @TempDir Path temp;

  @Test
  void retirementIsCaseInsensitiveIdempotentSearchableAndRemovable() {
    try (YamlStore store = new YamlStore(temp, Logger.getAnonymousLogger())) {
      var registry = new DeadUserRegistry(store);
      registry.load();
      assertTrue(registry.retire("Scout_7", "actor", "guard-7", "Notch", "value", "sig"));
      var original = registry.get("scout_7").orElseThrow();
      assertFalse(registry.retire("SCOUT_7", "player", "someone", "jeb_", "new", "new"));
      assertEquals(original, registry.get("SCOUT_7").orElseThrow());
      assertTrue(registry.contains("sCoUt_7"));
      assertEquals(List.of(original), registry.list("OUT_"));
      assertEquals(original, registry.remove("SCOUT_7"));
      assertFalse(registry.contains("scout_7"));
      assertThrows(IllegalArgumentException.class, () -> registry.remove("scout_7"));
    }
  }

  @Test
  void entriesAndIdentityDetailsRoundTripThroughDisk() {
    try (YamlStore store = new YamlStore(temp, Logger.getAnonymousLogger())) {
      var registry = new DeadUserRegistry(store);
      registry.load();
      assertTrue(registry.retire("Actor_12", "actor", "guard-12", "Notch", "texture", "signature"));
      assertTrue(registry.retire("Player_9", "player", "account-id", "", "", ""));
    }

    try (YamlStore store = new YamlStore(temp, Logger.getAnonymousLogger())) {
      var loaded = new DeadUserRegistry(store);
      loaded.load();
      assertEquals(2, loaded.list("").size());
      var actor = loaded.get("ACTOR_12").orElseThrow();
      assertEquals("actor", actor.kind());
      assertEquals("guard-12", actor.owner());
      assertEquals("Notch", actor.skinOwner());
      assertEquals("texture", actor.texture());
      assertEquals("signature", actor.signature());
      assertTrue(actor.diedAt() > 0);
      assertEquals(List.of(actor), loaded.list("actor"));
    }
  }

  @Test
  void rejectsInvalidNamesAndKindsWithoutPersistingThem() {
    try (YamlStore store = new YamlStore(temp, Logger.getAnonymousLogger())) {
      var registry = new DeadUserRegistry(store);
      registry.load();
      assertThrows(
          IllegalArgumentException.class,
          () -> registry.retire("", "actor", "", "", "", ""));
      assertThrows(
          IllegalArgumentException.class,
          () -> registry.retire("Alive_1", "mob", "", "", "", ""));
      assertTrue(registry.list("").isEmpty());
    }
  }
}
