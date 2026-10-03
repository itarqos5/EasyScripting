package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.config.GuiSchema;
import dev.easyscripting.config.Settings;
import dev.easyscripting.players.PlayerService;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** A broken file must fail on its own, and the jar's own copy must always be able to replace it. */
class ConfigFallbackTest {
  private YamlConfiguration bundled(String file) {
    return YamlConfiguration.loadConfiguration(
        new InputStreamReader(
            Objects.requireNonNull(getClass().getResourceAsStream("/" + file + ".yml"), file),
            StandardCharsets.UTF_8));
  }

  /** items.yml is checked against the live material and entity registries, which need a server. */
  private static final String NEEDS_SERVER = "items";

  /** The menu check the plugin runs, with the one material lookup that needs a live registry. */
  private static final Consumer<YamlConfiguration> MENUS =
      yaml -> GuiSchema.validate(yaml, PlayerService.FLAGS.size(), Material::valueOf);

  @Test
  void everyBundledDefaultLoadsSoTheFallbackIsAlwaysUsable() {
    // Falling back to a default that does not itself validate would disable the plugin outright.
    for (String file : Settings.FILES)
      if (!file.equals(NEEDS_SERVER))
        assertDoesNotThrow(
            () -> Settings.prepare(file, bundled(file), bundled(file), MENUS),
            file + ".yml");
  }

  @Test
  void eachFileIsAcceptedOrRefusedOnItsOwn() {
    var kits = bundled("kits");
    kits.set("max-provider-kits", 0);
    var refusal =
        assertThrows(
            IllegalArgumentException.class,
            () -> Settings.prepare("kits", kits, bundled("kits"), yaml -> {}));
    assertTrue(refusal.getMessage().startsWith("kits.yml:"));
    // Its neighbours are untouched by that refusal, which is what keeps the failure isolated.
    for (String neighbour : Settings.FILES)
      if (!neighbour.equals("kits") && !neighbour.equals(NEEDS_SERVER))
        assertDoesNotThrow(
            () ->
                Settings.prepare(
                    neighbour, bundled(neighbour), bundled(neighbour), MENUS),
            neighbour + ".yml");
  }

  @Test
  void aFileIsRefusedForItsOwnContentRatherThanAnotherFilesDefaults() {
    var features = bundled("features");
    features.set("actors", "yes");
    assertThrows(
        IllegalArgumentException.class,
        () -> Settings.prepare("features", features, bundled("features"), yaml -> {}));
    var ai = bundled("actor-ai");
    ai.set("combat.reach-accuracy", 4);
    assertThrows(
        IllegalArgumentException.class,
        () -> Settings.prepare("actor-ai", ai, bundled("actor-ai"), yaml -> {}));
    var menus = bundled("guis");
    menus.set("layout.rows", 9);
    assertThrows(
        IllegalArgumentException.class,
        () -> Settings.prepare("guis", menus, bundled("guis"), MENUS));
  }

  @Test
  void walkBackAndHittableSettingsAreCheckedWithinTheirOwnFile() {
    var recording = bundled("recording");
    recording.set("playback.walk-back-speed", 5);
    var refusal =
        assertThrows(
            IllegalArgumentException.class,
            () -> Settings.prepare("recording", recording, bundled("recording"), yaml -> {}));
    assertTrue(refusal.getMessage().startsWith("recording.yml:"));
    recording.set("playback.walk-back-speed", 1.0);
    recording.set("playback.walk-back-timeout-ticks", 5);
    assertThrows(
        IllegalArgumentException.class,
        () -> Settings.prepare("recording", recording, bundled("recording"), yaml -> {}));
    recording.set("playback.walk-back-timeout-ticks", 100);
    recording.set("playback.walk-back", "sometimes");
    assertThrows(
        IllegalArgumentException.class,
        () -> Settings.prepare("recording", recording, bundled("recording"), yaml -> {}));
    var config = bundled("config");
    config.set("actors.hittable-ignores-world-pvp", "yes");
    assertThrows(
        IllegalArgumentException.class,
        () -> Settings.prepare("config", config, bundled("config"), yaml -> {}));
  }

  @Test
  void anOlderRecordingFileWithoutTheWalkBackKeysIsStillAccepted() {
    var recording = bundled("recording");
    for (String key :
        List.of(
            "playback.walk-back",
            "playback.walk-back-speed",
            "playback.walk-back-arrival-distance",
            "playback.walk-back-timeout-ticks")) recording.set(key, null);
    assertDoesNotThrow(
        () -> Settings.prepare("recording", recording, bundled("recording"), yaml -> {}));
  }

  @Test
  void missingKeysAreCompletedFromTheDefaultsBeforeTheFileIsJudged() {
    var ai = bundled("actor-ai");
    ai.set("combat.reach-accuracy", null);
    // An older file that predates a setting is completed, not condemned for lacking it.
    var prepared = Settings.prepare("actor-ai", ai, bundled("actor-ai"), yaml -> {});
    assertTrue(prepared.contains("combat.reach-accuracy"));
  }
}
