package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.utilities.ChatPolicy;
import org.junit.jupiter.api.Test;

class ChatPolicyTest {
  @Test
  void deoppingImmediatelyBlocksEvenAPermissionBypassHolder() {
    assertFalse(ChatPolicy.blocked(true, true, false, true, true));
    assertTrue(ChatPolicy.blocked(true, false, false, true, true));
  }

  @Test
  void unblockingLetsOrdinaryPlayersSpeak() {
    assertFalse(ChatPolicy.blocked(false, false, false, true, false));
  }

  @Test
  void recordingRestrictionRemainsIndependent() {
    assertTrue(ChatPolicy.blocked(false, false, true, false, false));
    assertFalse(ChatPolicy.blocked(false, false, true, false, true));
  }
}
