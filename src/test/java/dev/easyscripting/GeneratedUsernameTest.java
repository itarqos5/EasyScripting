package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.players.GeneratedUsername;
import org.junit.jupiter.api.Test;

class GeneratedUsernameTest {
  @Test
  void acceptsFiveToSixteenMinecraftCharactersWithDigitOrUnderscore() {
    assertTrue(GeneratedUsername.valid("Abe_1"));
    assertTrue(GeneratedUsername.valid("name5"));
    assertTrue(GeneratedUsername.valid("abcdefghijklmno1"));
    assertTrue(GeneratedUsername.valid("under_score"));
  }

  @Test
  void rejectsMissingMarkerWrongLengthOrNonMinecraftCharacters() {
    assertFalse(GeneratedUsername.valid(null));
    assertFalse(GeneratedUsername.valid("abc1"));
    assertFalse(GeneratedUsername.valid("abcdefghijklmnop1"));
    assertFalse(GeneratedUsername.valid("lettersOnly"));
    assertFalse(GeneratedUsername.valid("_____"));
    assertFalse(GeneratedUsername.valid("12345"));
    assertFalse(GeneratedUsername.valid("name-1"));
    assertFalse(GeneratedUsername.valid("name 1"));
  }
}
