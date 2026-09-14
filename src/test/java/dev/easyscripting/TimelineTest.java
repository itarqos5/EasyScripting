package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.api.Scene;
import dev.easyscripting.scenes.Timeline;
import java.util.*;
import org.junit.jupiter.api.Test;

class TimelineTest {
  private Scene.Action action(long tick, String text) {
    return new Scene.Action(tick, "message", "self", Map.of("text", text));
  }

  @Test
  void stableOrderAndExactTick() {
    Timeline timeline =
        new Timeline(List.of(action(2, "third"), action(0, "first"), action(0, "second")));
    List<String> observed = new ArrayList<>();
    timeline.start();
    timeline.advance(10, a -> observed.add(a.arg("text")));
    assertEquals(List.of("first", "second"), observed);
    timeline.advance(10, a -> observed.add(a.arg("text")));
    assertEquals(2, observed.size());
    timeline.advance(10, a -> observed.add(a.arg("text")));
    assertEquals(List.of("first", "second", "third"), observed);
    assertEquals(Timeline.State.COMPLETED, timeline.state());
  }

  @Test
  void pauseDoesNotAdvanceClockOrActions() {
    Timeline t = new Timeline(List.of(action(2, "a")));
    t.start();
    t.advance(10, a -> fail());
    t.pause();
    for (int i = 0; i < 10; i++) t.advance(10, a -> fail());
    assertEquals(1, t.tick());
    t.resume();
    t.advance(10, a -> fail());
    t.advance(10, a -> assertEquals("a", a.arg("text")));
  }

  @Test
  void cancellationInsideActionPreventsOtherSameTickActions() {
    Timeline t = new Timeline(List.of(action(0, "a"), action(0, "b")));
    t.start();
    List<String> observed = new ArrayList<>();
    t.advance(
        10,
        a -> {
          observed.add(a.arg("text"));
          t.cancel();
        });
    t.advance(10, a -> fail());
    assertEquals(List.of("a"), observed);
    assertEquals(Timeline.State.CANCELLED, t.state());
  }

  @Test
  void failuresAreTerminalAndNeverReplayAnAction() {
    Timeline t = new Timeline(List.of(action(0, "a")));
    t.start();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            t.advance(
                10,
                a -> {
                  throw new IllegalArgumentException("bad target");
                }));
    assertEquals(Timeline.State.FAILED, t.state());
    t.advance(10, a -> fail());
  }

  @Test
  void budgetFailureDoesNotRunUnboundedWork() {
    Timeline t = new Timeline(List.of(action(0, "a"), action(0, "b")));
    t.start();
    List<String> observed = new ArrayList<>();
    assertThrows(IllegalStateException.class, () -> t.advance(1, a -> observed.add(a.arg("text"))));
    assertEquals(List.of("a"), observed);
    assertEquals(Timeline.State.FAILED, t.state());
  }

  @Test
  void emptyAndIllegalTransitions() {
    Timeline t = new Timeline(List.of());
    assertThrows(IllegalStateException.class, t::pause);
    t.start();
    assertThrows(IllegalStateException.class, t::start);
    t.advance(1, a -> fail());
    assertTrue(t.terminal());
  }
}
