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
          org.bukkit.Material.valueOf(material);
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
  void outdatedCombatHelpIsUpdatedWithoutReplacingCustomText() {
    var custom = defaults();
    custom.set(
        "dynamic.controls.actor-act.lore",
        List.of(
            "<gray>Take its position, identity and costume.",
            "<gray>Record movement, equipment and animations.",
            "<gray>Damage and knockback cannot interrupt you."));
    custom.set("dynamic.controls.actor-hittable.lore", List.of("My custom combat instructions"));
    var loaded = GuiSchema.prepare(custom, defaults());
    assertTrue(
        loaded.getStringList("dynamic.controls.actor-act.lore").stream()
            .anyMatch(s -> s.contains("falls and projectiles")));
    assertEquals(
        List.of("My custom combat instructions"),
        loaded.getStringList("dynamic.controls.actor-hittable.lore"));
    validate(loaded);
  }

  @Test
  void duplicateContentAndNavigationAreRejected() {
    var yaml = defaults();
    yaml.set("layout.content-slots", List.of(10, 10, 49));
    assertThrows(IllegalArgumentException.class, () -> validate(yaml));
  }

  @Test
  void chatRenameAndNewControlsMigrateWithoutReplacingCustomLabels() {
    var old = defaults();
    old.set("menus.production.buttons.mute.action", "command chat mute on");
    old.set("menus.production.buttons.mute.name", "My quiet set button");
    old.set("dynamic.controls.actor-tablist", null);
    old.set("dynamic.controls.kit-save-apply", null);
    var loaded = GuiSchema.prepare(old, defaults());
    assertEquals("command chat block on", loaded.getString("menus.production.buttons.mute.action"));
    assertEquals("My quiet set button", loaded.getString("menus.production.buttons.mute.name"));
    assertTrue(loaded.contains("dynamic.controls.actor-tablist.slot"));
    assertTrue(loaded.contains("dynamic.controls.kit-save-apply.slot"));
    validate(loaded);
  }

  @Test
  void kitImportAndSaveApplyCannotOverwriteInventoryOrOtherButtons() {
    var yaml = defaults();
    yaml.set("dynamic.controls.kit-save-apply.slot", 39);
    assertThrows(IllegalArgumentException.class, () -> validate(yaml));
    var collision = defaults();
    collision.set(
        "dynamic.controls.kit-capture.slot", collision.getInt("dynamic.controls.kit-edit.slot"));
    assertThrows(IllegalArgumentException.class, () -> validate(collision));
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

  @Test
  void actorControlsCannotHideTabsOrOtherButtons() {
    var yaml = defaults();
    yaml.set(
        "dynamic.controls.actor-autoplay.slot",
        yaml.getInt("dynamic.controls.actor-recording.slot"));
    assertThrows(IllegalArgumentException.class, () -> validate(yaml));
    var invalid = defaults();
    invalid.set(
        "dynamic.controls.actor-name.slot",
        invalid.getInt("dynamic.controls.actor-tab-acting.slot"));
    assertThrows(IllegalArgumentException.class, () -> validate(invalid));
  }

  @Test
  void loadoutSaveCannotOverwriteFooterNavigation() {
    var yaml = defaults();
    yaml.set("dynamic.controls.kit-save.slot", yaml.getInt("layout.home-slot"));
    assertThrows(IllegalArgumentException.class, () -> validate(yaml));
  }

  @Test
  void timelineControlsCannotOverlapActions() {
    var yaml = defaults();
    yaml.set(
        "dynamic.controls.timeline-play.slot",
        yaml.getIntegerList("layout.content-slots").getFirst());
    assertThrows(IllegalArgumentException.class, () -> validate(yaml));
  }

  @Test
  void legacyLayoutGetsCompleteDesignWithoutMutatingItsBackup() {
    var old = new YamlConfiguration();
    old.set("layout.back-slot", 49);
    old.set("menus.main.title", "Old studio");
    var upgraded = GuiSchema.prepare(old, defaults());
    validate(upgraded);
    assertEquals(2, upgraded.getInt("schema"));
    assertEquals(48, upgraded.getInt("layout.back-slot"));
    assertEquals("Old studio", old.getString("menus.main.title"));
    assertFalse(old.contains("schema"));
  }

  @Test
  void customizedCurrentLayoutSurvivesReload() {
    var custom = defaults();
    custom.set("menus.main.title", "My studio");
    custom.set("dynamic.actor-autoplay", "Automatic replay: {autoplay}");
    var loaded = GuiSchema.prepare(custom, defaults());
    validate(loaded);
    assertEquals("My studio", loaded.getString("menus.main.title"));
    assertEquals("Automatic replay: {autoplay}", loaded.getString("dynamic.actor-autoplay"));
    custom.set("schema", 3);
    assertThrows(IllegalArgumentException.class, () -> GuiSchema.prepare(custom, defaults()));
    custom.set("schema", "2");
    assertThrows(IllegalArgumentException.class, () -> GuiSchema.prepare(custom, defaults()));
  }
}
