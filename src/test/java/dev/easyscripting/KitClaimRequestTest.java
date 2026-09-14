package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.items.KitClaimRequest;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KitClaimRequestTest {
  private KitClaimRequest parse(String... values) {
    return KitClaimRequest.parse(values, Set.of("starter", "alex")::contains);
  }

  @Test
  void acceptsSelfBothOrdersWildcardAndExplicitActor() {
    assertEquals(new KitClaimRequest("starter", null), parse("starter"));
    assertEquals(parse("starter", "Alex"), parse("Alex", "starter"));
    assertEquals(parse("starter", "*"), parse("*", "starter"));
    assertEquals("actor:guard", parse("starter", "actor:guard").target());
    assertEquals("player:alex", parse("starter", "player:alex").target());
  }

  @Test
  void ambiguousNamesUnknownKitsAndExtraArgumentsNeverGuessRecipients() {
    assertThrows(IllegalArgumentException.class, () -> parse("starter", "alex"));
    assertThrows(IllegalArgumentException.class, () -> parse("unknown"));
    assertThrows(IllegalArgumentException.class, () -> parse("Alex", "unknown"));
    assertThrows(IllegalArgumentException.class, () -> parse());
    assertThrows(IllegalArgumentException.class, () -> parse("starter", "Alex", "extra"));
  }
}
