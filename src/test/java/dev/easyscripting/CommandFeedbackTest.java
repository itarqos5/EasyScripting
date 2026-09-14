package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.commands.*;
import dev.easyscripting.config.CommandResponses;
import java.lang.reflect.Proxy;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

class CommandFeedbackTest {
  private CommandSender sender() {
    return (CommandSender)
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {CommandSender.class},
            (proxy, method, args) -> method.getName().equals("equals") ? proxy == args[0] : null);
  }

  @Test
  void silentSuccessNeedsFeedbackButDirectReplySuppressesIt() {
    var responses = new CommandResponses();
    var alice = sender();
    try (var command = responses.begin(alice)) {
      assertFalse(command.responded());
      responses.sent(alice);
      assertTrue(command.responded());
    }
    try (var next = responses.begin(alice)) {
      assertFalse(next.responded());
    }
  }

  @Test
  void otherRecipientsDoNotSuppressFeedbackAndNestedRepliesReachBothScopes() {
    var responses = new CommandResponses();
    var alice = sender();
    try (var outer = responses.begin(alice)) {
      responses.sent(sender());
      assertFalse(outer.responded());
      try (var inner = responses.begin(alice)) {
        responses.sent(alice);
        assertTrue(inner.responded());
        assertTrue(outer.responded());
      }
    }
  }

  @Test
  void exceptionUnwindsResponseTracking() {
    var responses = new CommandResponses();
    var alice = sender();
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          try (var scope = responses.begin(alice)) {
            responses.sent(alice);
            throw new IllegalArgumentException("failed");
          }
        });
    try (var next = responses.begin(alice)) {
      assertFalse(next.responded());
    }
  }

  @Test
  void destructiveAndToggleFeedbackExplainTheResult() {
    assertEquals(
        "Deleted scene 'opening'.",
        CommandFeedback.describe("scene", new Args(new String[] {"delete", "opening"})));
    assertEquals(
        "NPC 'guard': immortal set to on.",
        CommandFeedback.describe(
            "actor", new Args(new String[] {"set", "guard", "immortal", "on"})));
    assertEquals(
        "Voice mute turned off.",
        CommandFeedback.describe("voice", new Args(new String[] {"mute", "off"})));
    assertTrue(
        CommandFeedback.describe("skin", new Args(new String[] {"Notch"}))
            .startsWith("Requested skin"));
  }
}
