package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.config.Settings;
import dev.easyscripting.storage.YamlStore;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationCommentsTest {
  @TempDir Path directory;

  @Test
  void upgradeAddsExplanationsWithoutChangingUserValuesOrComments() throws Exception {
    var user = new YamlConfiguration();
    user.loadFromString("speed: 2\n# My choice\nenabled: false\n");
    var defaults = new YamlConfiguration();
    defaults.loadFromString(
        "# Server settings\n\n# Walking multiplier\nspeed: 1\n# Toggle behavior\nenabled: true\n");
    assertTrue(Settings.inheritComments(user, defaults));
    assertEquals(2, user.getInt("speed"));
    assertFalse(user.getBoolean("enabled"));
    assertEquals(List.of("Walking multiplier"), user.getComments("speed"));
    assertEquals(List.of("My choice"), user.getComments("enabled"));
    assertFalse(Settings.inheritComments(user, defaults));
  }

  @Test
  void asyncSavePreservesDetachedCommentsAndValues() throws Exception {
    var y = new YamlConfiguration();
    y.set("settings.speed", 1);
    y.setComments("settings.speed", Arrays.asList("Speed multiplier", null, "Use 1 normally"));
    y.setInlineComments("settings.speed", List.of("blocks"));
    y.options().setHeader(List.of("Header")).setFooter(List.of("Footer"));
    try (var store = new YamlStore(directory, Logger.getAnonymousLogger())) {
      var saved = store.save("groups", "red", y);
      y.set("settings.speed", 9);
      y.setComments("settings.speed", List.of("Changed"));
      saved.join();
      var actual = store.read("groups", "red");
      assertEquals(1, actual.getInt("settings.speed"));
      assertEquals(
          Arrays.asList("Speed multiplier", null, "Use 1 normally"),
          actual.getComments("settings.speed"));
      assertEquals(List.of("blocks"), actual.getInlineComments("settings.speed"));
      assertEquals(List.of("Header"), actual.options().getHeader());
      assertEquals(List.of("Footer"), actual.options().getFooter());
    }
  }
}
