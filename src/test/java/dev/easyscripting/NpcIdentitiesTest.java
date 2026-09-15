package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.config.NpcIdentities;
import dev.easyscripting.players.GeneratedUsername;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class NpcIdentitiesTest {
  @Test
  void avoidsTheLastEightEndingsWhenOtherEndingsAreAvailable() {
    var pool = NpcIdentities.read(defaults());
    Deque<String> recent = new ArrayDeque<>();
    Set<String> names = new HashSet<>();
    Random random = new Random(412);
    for (int i = 0; i < 100; i++) {
      var selection = pool.choose(names, n -> false, false, "", random, recent);
      for (String suffix : pool.suffixes())
        if (selection.name().matches(".*" + suffix + "(?:[0-9]|$)"))
          assertTrue(
              recent.stream().noneMatch(n -> n.matches(".*" + suffix + "(?:[0-9]|$)")),
              selection.name());
      names.add(selection.name());
      recent.addLast(selection.name());
      if (recent.size() > 8) recent.removeFirst();
    }
  }

  @Test
  void tinyCustomSuffixPoolFallsBackWithoutHanging() {
    var pool =
        new NpcIdentities(
            true, List.of("AmberCrow1", "RiverCrow2"), List.of("Notch"), List.of("Crow"));
    assertEquals(
        "RiverCrow2",
        pool.choose(
                List.of("AmberCrow1"),
                n -> false,
                false,
                "",
                new Random(1),
                List.of("AmberCrow1"))
            .name());
  }

  private YamlConfiguration defaults() {
    return YamlConfiguration.loadConfiguration(
        new InputStreamReader(
            getClass().getResourceAsStream("/npc-identities.yml"), StandardCharsets.UTF_8));
  }

  @Test
  void defaultsSupportAFullCastWithoutDuplicateNames() {
    var pool = NpcIdentities.read(defaults());
    Set<String> names = new HashSet<>();
    var random = new Random(421);
    for (int i = 0; i < 200; i++) {
      var chosen = pool.choose(names, value -> false, true, "", random);
      assertTrue(GeneratedUsername.valid(chosen.name()), chosen.name());
      assertTrue(names.add(chosen.name()));
      assertTrue(pool.skins().contains(chosen.skin()));
    }
    assertEquals(200, names.size());
  }

  @Test
  void skipsOccupiedAndBlacklistedNamesAndSkins() {
    var pool =
        new NpcIdentities(
            true,
            List.of("RiverFox1", "CedarHawk2", "AmberWolf3"),
            List.of("Notch", "jeb_"));
    var chosen =
        pool.choose(
            List.of("RIVERFOX1"),
            value -> value.equals("CedarHawk2") || value.equals("Notch"),
            true,
            "",
            new Random(1));
    assertEquals("AmberWolf3", chosen.name());
    assertEquals("jeb_", chosen.skin());
  }

  @Test
  void rerollUsesAnotherSkinWhenOneIsAvailable() {
    var pool = new NpcIdentities(true, List.of("RiverFox1"), List.of("Notch", "jeb_"));
    assertEquals(
        "jeb_", pool.choose(List.of(), name -> false, true, "NOTCH", new Random(1)).skin());
  }

  @Test
  void mobNamesDoNotPretendToHavePlayerSkins() {
    var pool = NpcIdentities.read(defaults());
    assertEquals("", pool.choose(List.of(), name -> false, false, "", new Random(1)).skin());
  }

  @Test
  void exhaustionIsBoundedAndHasActionableError() {
    var pool = new NpcIdentities(true, List.of("RiverFox1"), List.of("Notch"));
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () -> pool.choose(List.of("riverfox1"), name -> false, true, "", new Random(1)));
    assertTrue(error.getMessage().contains("npc-identities.yml"));
  }

  @Test
  void allBlockedSkinOwnersRejectInsteadOfSilentlyUsingDefaultSkin() {
    var pool = new NpcIdentities(true, List.of("RiverFox1"), List.of("Notch"));
    assertThrows(
        IllegalArgumentException.class,
        () -> pool.choose(List.of(), name -> name.equals("Notch"), true, "", new Random(1)));
  }

  @Test
  void rejectsLongCombinedUsernameAndIdentifiesTheFile() {
    var yaml = defaults();
    yaml.set("name-prefixes", List.of("AReallyLongName"));
    var error = assertThrows(IllegalArgumentException.class, () -> NpcIdentities.read(yaml));
    assertTrue(error.getMessage().contains("npc-identities.yml"));
    assertTrue(error.getMessage().contains("15 characters"));
  }

  @Test
  void fifteenCharacterBaseFitsItsRequiredFinalDigit() {
    var yaml = defaults();
    yaml.set("name-prefixes", List.of("abcdefghij"));
    yaml.set("name-suffixes", List.of("klmno"));
    var pool = NpcIdentities.read(yaml);
    assertEquals(11, pool.usernames().size());
    assertTrue(pool.usernames().stream().allMatch(GeneratedUsername::valid));
    assertTrue(pool.usernames().stream().allMatch(name -> name.length() == 16));
  }

  @Test
  void localFallbacksAlwaysContainADigitOrUnderscore() {
    var yaml = defaults();
    yaml.set("name-prefixes", List.of("Sun"));
    yaml.set("name-suffixes", List.of("Vale"));
    var pool = NpcIdentities.read(yaml);
    assertEquals(11, pool.usernames().size());
    assertTrue(pool.usernames().contains("sun_vale"));
    assertTrue(pool.usernames().stream().allMatch(GeneratedUsername::valid));
  }

  @Test
  void rejectsInvalidAndDuplicateOwners() {
    var yaml = defaults();
    yaml.set("skin-owners", List.of("Notch", "NOTCH"));
    assertThrows(IllegalArgumentException.class, () -> NpcIdentities.read(yaml));
    yaml.set("skin-owners", List.of("https://namemc.com/skin/example"));
    assertThrows(IllegalArgumentException.class, () -> NpcIdentities.read(yaml));
  }

  @Test
  void requiresRealYamlBooleanAndNonemptyPool() {
    var yaml = defaults();
    yaml.set("enabled", "false");
    assertThrows(IllegalArgumentException.class, () -> NpcIdentities.read(yaml));
    yaml.set("enabled", false);
    assertFalse(NpcIdentities.read(yaml).enabled());
    yaml.set("skin-owners", List.of());
    assertThrows(IllegalArgumentException.class, () -> NpcIdentities.read(yaml));
  }
}
