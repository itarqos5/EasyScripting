package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.core.Durations;
import org.junit.jupiter.api.Test;

class DurationsTest {
  @Test
  void waitsAreWrittenTheWayAnOperatorSaysThem() {
    assertEquals(30, Durations.seconds("30s"));
    assertEquals(90, Durations.seconds("90"), "a bare number is seconds");
    assertEquals(300, Durations.seconds("5min"));
    assertEquals(300, Durations.seconds("5m"));
    assertEquals(3600, Durations.seconds("1h"));
    assertEquals(5400, Durations.seconds("1h30m"), "parts written together add up");
    assertEquals(5400, Durations.seconds("1 h 30 min"));
    assertEquals(86400, Durations.seconds("1d"));
    assertEquals(45, Durations.seconds("45 SECONDS"));
  }

  @Test
  void offIsAWaitOfNothing() {
    for (String none : new String[] {"off", "OFF", "none", "no", "disabled", "0"})
      assertEquals(0, Durations.seconds(none));
  }

  @Test
  void anythingThatIsNotALengthOfTimeIsRefusedWithTheFormatsThatWork() {
    for (String invalid :
        new String[] {"soon", "", " ", "5x", "min", "5min5", "-30s", "5min soon", null}) {
      var refusal =
          assertThrows(IllegalArgumentException.class, () -> Durations.seconds(invalid), invalid);
      assertTrue(refusal.getMessage().contains("30s"), refusal.getMessage());
    }
  }

  @Test
  void aWaitLongerThanADayIsRefused() {
    assertThrows(IllegalArgumentException.class, () -> Durations.seconds("2d"));
    assertThrows(IllegalArgumentException.class, () -> Durations.seconds("25h"));
    assertThrows(IllegalArgumentException.class, () -> Durations.seconds("23h61m"));
  }

  @Test
  void theSavedWaitReadsBackAsItWasSet() {
    assertEquals("off", Durations.describe(0));
    assertEquals("1 second", Durations.describe(1));
    assertEquals("30 seconds", Durations.describe(30));
    assertEquals("1 minute", Durations.describe(60));
    assertEquals("5 minutes", Durations.describe(300));
    assertEquals("1 hour 30 minutes", Durations.describe(5400));
    assertEquals("1 day", Durations.describe(86400));
    assertEquals("2 minutes 5 seconds", Durations.describe(125));
  }
}
