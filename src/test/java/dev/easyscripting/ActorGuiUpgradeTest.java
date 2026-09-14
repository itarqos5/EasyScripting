package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.config.Settings;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ActorGuiUpgradeTest {
  @Test
  void olderGuiGetsConcreteActorDefaultsWithoutReplacingCustomValues() {
    var defaults =
        YamlConfiguration.loadConfiguration(
            new InputStreamReader(
                getClass().getResourceAsStream("/guis.yml"), StandardCharsets.UTF_8));
    var old = new YamlConfiguration();
    old.set("dynamic.actor-name", "My rename button");
    old.set("dynamic.controls.actor-name.slot", 24);
    Settings.inheritActorMenus(old, defaults);
    assertEquals("TARGET", old.getString("dynamic.controls.actor-hittable.material", "LIME_DYE"));
    assertEquals("My rename button", old.getString("dynamic.actor-name"));
    assertEquals(24, old.getInt("dynamic.controls.actor-name.slot"));
    assertTrue(old.getString("menus.actor-combat.title").contains("Combat"));
    assertTrue(old.getString("dynamic.actor-immortal").contains("{state}"));
    assertFalse(old.contains("menus.main"));
  }
}
