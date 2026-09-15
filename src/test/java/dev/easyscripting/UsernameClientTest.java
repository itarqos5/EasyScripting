package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.players.UsernameClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class UsernameClientTest {
  @Test
  void usernameResponseFiltersAndDeduplicatesIgnoringCase() {
    assertEquals(
        List.of("quiet_1", "Nova7"),
        UsernameClient.parse(
            """
            {"results":[
              {"login":{"username":"quiet_1"}},
              {"login":{"username":"QUIET_1"}},
              {"login":{"username":"Nova7"}},
              {"login":{"username":"LettersOnly"}},
              {"login":{"username":"abc1"}},
              {"login":{"username":"bad-name9"}},
              {"login":{"username":9}},
              {"other":true}
            ]}
            """));
  }

  @Test
  void usernameResponseHasBoundedBytesAndEntryCount() {
    var oversized = " ".repeat(262_145);
    var error =
        assertThrows(IllegalArgumentException.class, () -> UsernameClient.parse(oversized));
    assertTrue(error.getMessage().contains("too large"));

    StringBuilder entries = new StringBuilder();
    for (int i = 0; i < 257; i++) {
      if (i > 0) entries.append(',');
      entries.append("{\"login\":{\"username\":\"name_").append(i).append("\"}}");
    }
    var tooMany =
        assertThrows(
            IllegalArgumentException.class,
            () -> UsernameClient.parse("{\"results\":[" + entries + "]}"));
    assertTrue(tooMany.getMessage().contains("Invalid username response"));
  }

  @Test
  void skinResponseCombinesPlayersAndFirstOwnersWithCaseInsensitiveDeduplication() {
    assertEquals(
        List.of("Notch", "JEB_", "Dinnerbone"),
        UsernameClient.parseSkinOwners(
            """
            {"top":{
              "players":[
                {"username":"Notch"},
                {"username":"JEB_"},
                {"username":"bad-name"},
                {"username":7}
              ],
              "skins":[
                {"first_player":{"username":"notch"}},
                {"first_player":{"username":"Dinnerbone"}},
                {"first_player":null},
                {}
              ]
            }}
            """));
  }

  @Test
  void skinResponseHasBoundedPayloadAndPerSectionCounts() {
    var oversized = " ".repeat(131_073);
    var error =
        assertThrows(
            IllegalArgumentException.class, () -> UsernameClient.parseSkinOwners(oversized));
    assertTrue(error.getMessage().contains("too large"));

    StringBuilder players = new StringBuilder();
    for (int i = 0; i < 101; i++) {
      if (i > 0) players.append(',');
      players.append("{\"username\":\"player_").append(i).append("\"}");
    }
    var tooMany =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                UsernameClient.parseSkinOwners(
                    "{\"top\":{\"players\":[" + players + "],\"skins\":[]}}"));
    assertTrue(tooMany.getMessage().contains("Too many skin profiles"));
  }
}
