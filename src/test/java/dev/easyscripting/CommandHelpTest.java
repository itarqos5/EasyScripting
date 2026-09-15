package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.commands.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class CommandHelpTest {
  private YamlConfiguration catalogue() {
    return YamlConfiguration.loadConfiguration(
        new InputStreamReader(
            Objects.requireNonNull(getClass().getResourceAsStream("/command-help.yml")),
            StandardCharsets.UTF_8));
  }

  @Test
  void incompleteActorSettingExplainsExpectedValues() {
    String help =
        String.join(
            "\n",
            CommandHelp.explain(
                catalogue(), "actor", new Args(new String[] {"set", "guard", "immortal"}), ""));
    assertTrue(help.contains("/es actor set <id> <setting> <value>"));
    assertTrue(help.contains("Expected immortal value: on or off"));
    assertTrue(help.contains("Example: /actor set guard immortal on"));
  }

  @Test
  void groupAndKitErrorsUseRelevantExamples() {
    for (String route : List.of("kits", "kit")) {
      String help =
          String.join(
              "\n", CommandHelp.explain(catalogue(), route, new Args(new String[] {"claim"}), ""));
      assertTrue(help.contains("/es kits claim"));
      assertTrue(help.contains("Example:"));
    }
    var help = CommandHelp.explain(catalogue(), "group", new Args(new String[] {"leader"}), "");
    assertTrue(help.stream().anyMatch(line -> line.contains("/es group leader")));
    assertFalse(help.stream().anyMatch(line -> line.contains("/es group delete")));
  }

  @Test
  void shortcutsExpandWithoutChangingArguments() {
    assertArrayEquals(
        new String[] {"menu", "actors"}, CommandRouter.expand("actors", new String[0]));
    assertArrayEquals(
        new String[] {"actor", "gui", "guard"},
        CommandRouter.expand("actors", new String[] {"gui", "guard"}));
    assertArrayEquals(
        new String[] {"kits", "claim", "starter", "*"},
        CommandRouter.expand("kits", new String[] {"claim", "starter", "*"}));
  }

  @Test
  void unknownRecordingOperationStillExplainsTheSessionCommands() {
    String help =
        String.join(
            "\n",
            CommandHelp.explain(
                catalogue(), "record", new Args(new String[] {"delete"}), "<on|off>"));
    assertTrue(help.contains("Use: /es record <on|off>"));
    assertTrue(help.contains("Example: /es record on"));
    assertTrue(help.contains("MOTD"));
  }
}
