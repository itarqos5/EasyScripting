package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.commands.Args;
import dev.easyscripting.core.Checks;
import dev.easyscripting.utilities.ModerationService;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValidationTest {
  @Test
  void identifiersCannotEscapeStorage() {
    for (String invalid :
        new String[] {
          "../secret", "..", "C:\\secrets", "/tmp/file", "name.yml", "bad name", "", "x".repeat(49)
        }) assertThrows(IllegalArgumentException.class, () -> Checks.id(invalid));
    assertEquals("final_fight-2", Checks.id("final_fight-2"));
  }

  @Test
  void rejectsNonFiniteAndOutOfRangeValues() {
    for (String value : new String[] {"NaN", "Infinity", "-Infinity", "1e100", "-1"})
      assertThrows(IllegalArgumentException.class, () -> Checks.decimal(value, 0, 10));
    assertEquals(1.5, Checks.decimal("1.5", 0, 10));
  }

  @Test
  void actionArgumentsPreserveSpacesAndRejectDuplicates() {
    assertEquals(
        Map.of("text", "Act one starts now", "sound", "minecraft:test"),
        Args.pairs("text=Act one starts now;sound=minecraft:test"));
    assertThrows(IllegalArgumentException.class, () -> Args.pairs("text=a;text=b"));
    assertThrows(IllegalArgumentException.class, () -> Args.pairs("broken"));
  }

  @Test
  void commandBlockerNormalizesNamespacedAndMixedCaseAliases() {
    assertEquals("op", ModerationService.commandRoot(" /Minecraft:OP SomePlayer"));
    assertEquals("stop", ModerationService.commandRoot("/STOP"));
  }
}
