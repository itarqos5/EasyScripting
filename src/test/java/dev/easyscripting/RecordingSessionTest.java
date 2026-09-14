package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.recording.TakeRetention;
import dev.easyscripting.utilities.RecordingSessionPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordingSessionTest {
  @Test
  void recordingRejectsEveryNonOperatorRegardlessOfPriorConnection() {
    assertFalse(RecordingSessionPolicy.blocksLogin(false, false));
    assertTrue(RecordingSessionPolicy.blocksLogin(true, false));
    assertTrue(RecordingSessionPolicy.blocksLogin(true, false)); // Reconnect has no exception.
    assertFalse(RecordingSessionPolicy.blocksLogin(true, true));
    assertTrue(RecordingSessionPolicy.blocksLogin(true, false)); // Live de-op takes effect.
    assertFalse(RecordingSessionPolicy.blocksLogin(false, false));
  }

  @Test
  void sharedTakesRemainUntilTheirLastNpcIsRemoved() {
    assertFalse(TakeRetention.unused("entrance", List.of("entrance", "other")));
    assertTrue(TakeRetention.unused("entrance", List.of("other")));
    assertTrue(TakeRetention.unused("entrance", List.of()));
    assertFalse(TakeRetention.unused("", List.of()));
    assertFalse(TakeRetention.unused(null, List.of()));
  }
}
