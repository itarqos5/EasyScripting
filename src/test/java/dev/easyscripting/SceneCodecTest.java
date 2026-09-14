package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.api.Scene;
import dev.easyscripting.scenes.SceneCodec;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SceneCodecTest {
  @Test
  void yamlRoundTripRetainsBindingsOrderAndText() throws Exception {
    Scene source =
        new Scene(
            "final_fight",
            "A repeated take",
            Map.of("hero", "actor:hero"),
            List.of(
                new Scene.Action(20, "message", "hero", Map.of("text", "<red>Ready: now!")),
                new Scene.Action(0, "wait", "hero", Map.of())),
            true);
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.loadFromString(SceneCodec.encode(source).saveToString());
    assertEquals(source, SceneCodec.decode("final_fight", yaml, 10));
  }

  @Test
  void malformedTimingNamesSchemasAndShapesFail() throws Exception {
    YamlConfiguration y = new YamlConfiguration();
    y.loadFromString("schema: 2\nactions: []");
    assertThrows(IllegalArgumentException.class, () -> SceneCodec.decode("test", y, 100));
    y.loadFromString("schema: 1\nactions: wrong");
    assertThrows(IllegalArgumentException.class, () -> SceneCodec.decode("test", y, 100));
    y.loadFromString("schema: 1\nactions:\n  - tick: -1\n    type: wait\n    target: self");
    assertThrows(IllegalArgumentException.class, () -> SceneCodec.decode("test", y, 100));
  }

  @Test
  void maximumActionsIsEnforcedBeforeExecution() {
    Scene s =
        new Scene(
            "test",
            "",
            Map.of(),
            List.of(
                new Scene.Action(0, "wait", "self", Map.of()),
                new Scene.Action(1, "wait", "self", Map.of())),
            false);
    assertThrows(
        IllegalArgumentException.class, () -> SceneCodec.decode("test", SceneCodec.encode(s), 1));
  }
}
