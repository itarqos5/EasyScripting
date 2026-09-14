package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.api.Scene;
import dev.easyscripting.scenes.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SceneEditsTest {
  private final Scene source =
      new Scene(
          "source",
          "",
          Map.of("hero", "actor:hero"),
          List.of(
              new Scene.Action(5, "look", "hero", Map.of("at", "hero")),
              new Scene.Action(10, "wait", "hero", Map.of())),
          false);

  @Test
  void appendResolvesBindingsWithoutChangingDestination() {
    Scene dest = new Scene("dest", "", Map.of("hero", "actor:different"), List.of(), true);
    Scene result = SceneEdits.append(dest, source, 20, 10);
    assertEquals("actor:hero", result.actions().getFirst().target());
    assertEquals("actor:hero", result.actions().getFirst().arg("at"));
    assertEquals(25, result.actions().getFirst().tick());
    assertEquals(dest.bindings(), result.bindings());
    assertTrue(result.restoreOnComplete());
  }

  @Test
  void repeatPreservesOrderingAndEnforcesBudget() {
    Scene repeated = SceneEdits.repeat(source, 5, 10, 2, 20, 10);
    assertEquals(
        List.of(5L, 10L, 25L, 30L, 45L, 50L),
        repeated.actions().stream().map(Scene.Action::tick).toList());
    assertThrows(IllegalArgumentException.class, () -> SceneEdits.repeat(source, 5, 10, 2, 20, 4));
    assertThrows(IllegalArgumentException.class, () -> SceneEdits.repeat(source, 5, 10, 2, 5, 10));
  }

  @Test
  void inMemoryCodecRoundTripRetainsBindings() {
    assertEquals(source, SceneCodec.decode("source", SceneCodec.encode(source), 10));
  }

  @Test
  void malformedListEntriesAndArgumentsAreRejected() throws Exception {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.loadFromString("schema: 1\nactions: [oops]");
    assertThrows(IllegalArgumentException.class, () -> SceneCodec.decode("bad", yaml, 10));
    yaml.loadFromString("schema: 1\nactions: [{tick: 0, type: wait, target: self, args: oops}]");
    assertThrows(IllegalArgumentException.class, () -> SceneCodec.decode("bad", yaml, 10));
  }
}
