package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.recording.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlaybackCursorTest {
  private List<Integer> frames(int size, PlaybackMode mode, boolean backwards, int limit) {
    var cursor = new PlaybackCursor(size, mode, backwards);
    List<Integer> result = new ArrayList<>();
    do {
      result.add(cursor.index());
    } while (result.size() < limit && cursor.advance());
    return result;
  }

  @Test
  void stopLeavesFinalFrame() {
    assertEquals(List.of(0, 1, 2), frames(3, PlaybackMode.STOP, false, 10));
  }

  @Test
  void repeatReturnsDirectlyToStart() {
    assertEquals(List.of(0, 1, 2, 0, 1, 2, 0), frames(3, PlaybackMode.REPEAT, false, 7));
  }

  @Test
  void reverseBouncesWithoutTeleportOrDuplicateEndpoints() {
    assertEquals(List.of(0, 1, 2, 1, 0, 1, 2, 1), frames(3, PlaybackMode.REVERSE, false, 8));
  }

  @Test
  void legacyReversePlaysOnceBackwards() {
    assertEquals(List.of(2, 1, 0), frames(3, PlaybackMode.STOP, true, 10));
  }

  @Test
  void legacyReverseLoopIsPreserved() {
    assertEquals(List.of(2, 1, 0, 2, 1, 0), frames(3, PlaybackMode.REPEAT, true, 6));
  }

  @Test
  void singleFrameStopAndLoopsAreBounded() {
    assertEquals(List.of(0), frames(1, PlaybackMode.STOP, false, 10));
    assertEquals(List.of(0, 0, 0), frames(1, PlaybackMode.REVERSE, false, 3));
    assertEquals(List.of(0, 0, 0), frames(1, PlaybackMode.REPEAT, false, 3));
  }

  @Test
  void noEmptyRecording() {
    assertThrows(
        IllegalArgumentException.class, () -> new PlaybackCursor(0, PlaybackMode.STOP, false));
  }

  @Test
  void modeNamesAreValidated() {
    assertEquals(PlaybackMode.REVERSE, PlaybackMode.parse("reverse"));
    assertThrows(IllegalArgumentException.class, () -> PlaybackMode.parse("random"));
  }

  @Test
  void modeChangesDoNotRestartTheCurrentFrame() {
    var cursor = new PlaybackCursor(4, PlaybackMode.STOP, false);
    cursor.advance();
    cursor.advance();
    cursor.mode(PlaybackMode.REPEAT);
    assertEquals(2, cursor.index());
    assertTrue(cursor.advance());
    assertTrue(cursor.advance());
    assertEquals(0, cursor.index());
    cursor.mode(PlaybackMode.STOP);
    assertTrue(cursor.advance());
    assertTrue(cursor.advance());
    assertTrue(cursor.advance());
    assertFalse(cursor.advance());
  }

  @Test
  void switchingFromReverseToStopContinuesForwardToTheLastFrame() {
    var cursor = new PlaybackCursor(3, PlaybackMode.REVERSE, false);
    cursor.advance();
    cursor.advance();
    cursor.advance();
    assertEquals(1, cursor.index());
    cursor.mode(PlaybackMode.STOP);
    assertTrue(cursor.advance());
    assertEquals(2, cursor.index());
    assertFalse(cursor.advance());
  }
}
