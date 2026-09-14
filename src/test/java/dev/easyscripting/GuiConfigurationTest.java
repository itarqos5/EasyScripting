package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.config.GuiSchema;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class GuiConfigurationTest {
  private void validate(YamlConfiguration yaml) {
    GuiSchema.validate(
        yaml,
        13,
        material -> {
          if (!material.matches("[A-Z_0-9]+"))
            throw new IllegalArgumentException("Invalid material token");
        });
  }

  private YamlConfiguration defaults() {
    return YamlConfiguration.loadConfiguration(
        new InputStreamReader(getClass().getResourceAsStream("/guis.yml"), StandardCharsets.UTF_8));
  }

  @Test
  void bundledMenusAreValid() {
    assertDoesNotThrow(() -> validate(defaults()));
  }

  @Test
  void duplicateContentAndNavigationAreRejected() {
    var yaml = defaults();
    yaml.set("layout.content-slots", List.of(10, 10, 49));
    assertThrows(IllegalArgumentException.class, () -> validate(yaml));
  }

  @Test
  void invalidSizeHasFileAndValue() {
    var yaml = defaults();
    yaml.set("layout.rows", 9);
    var error = assertThrows(IllegalArgumentException.class, () -> validate(yaml));
    assertTrue(error.getMessage().contains("guis.yml"));
    assertTrue(error.getMessage().contains("9"));
  }

  @Test
  void unknownButtonActionIsRejected() {
    var yaml = defaults();
    yaml.set("menus.main.buttons.scenes.action", "console op Someone");
    assertThrows(IllegalArgumentException.class, () -> validate(yaml));
  }
}
