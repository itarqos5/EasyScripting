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
  void clearingReleasesEveryNameAtOnceAndSurvivesAReload() {
    try (YamlStore store = new YamlStore(temp, Logger.getAnonymousLogger())) {
      var registry = new DeadUserRegistry(store);
      registry.load();
      // An empty registry reports nothing released rather than a silent success.
      assertEquals(0, registry.clear());
      assertTrue(registry.retire("Scout_7", "actor", "guard-7", "Notch", "value", "sig"));
      assertTrue(registry.retire("Frost_2", "player", "account", "", "", ""));
      assertEquals(2, registry.clear());
      assertTrue(registry.list("").isEmpty());
      assertFalse(registry.contains("scout_7"));
      // A released name is free again, which is the whole point of clearing the list.
      assertTrue(registry.retire("Scout_7", "actor", "guard-7", "Notch", "value", "sig"));
    }
    try (YamlStore store = new YamlStore(temp, Logger.getAnonymousLogger())) {
      var reloaded = new DeadUserRegistry(store);
      reloaded.load();
      assertEquals(List.of("Scout_7"), reloaded.list("").stream().map(e -> e.name()).toList());
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
