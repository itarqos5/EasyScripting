package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.config.Settings;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ModerationConfigurationTest {
  @Test
  void existingMessagesGetAnnouncementsWithoutOverwritingCustomization() {
    var defaults =
        YamlConfiguration.loadConfiguration(
            new InputStreamReader(
                getClass().getResourceAsStream("/messages.yml"), StandardCharsets.UTF_8));
    var current = new YamlConfiguration();
    current.set("broadcast", "My broadcast: <detail>");
    Settings.inheritMissing(current, defaults);
    assertEquals("My broadcast: <detail>", current.getString("broadcast"));
    assertTrue(current.getString("chat-muted").contains("muted"));
    assertTrue(current.getString("chat-unmuted").contains("unmuted"));
    assertEquals("<gold><detail>", current.getString("broadcast-title"));
  }

  @Test
  void invalidTitleTimingAndSwitchAreRejected() {
    var config = new YamlConfiguration();
    Settings.validateModeration(config);
    for (Object value : new Object[] {-1, 0, 1201, "60", .5}) {
      config.set("broadcast-title.stay-ticks", value);
      assertThrows(IllegalArgumentException.class, () -> Settings.validateModeration(config));
    }
    config.set("broadcast-title.stay-ticks", 60);
    config.set("broadcast-title.fade-in-ticks", 0);
    config.set("broadcast-title.enabled", true);
    Settings.validateModeration(config);
    config.set("broadcast-title.enabled", "true");
    assertThrows(IllegalArgumentException.class, () -> Settings.validateModeration(config));
  }
}
