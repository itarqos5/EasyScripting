package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.core.Checks;
import dev.easyscripting.integration.KitImportBatch;
import java.util.*;
import org.junit.jupiter.api.Test;

class KitImportBatchTest {
  @Test
  void oneKitPerStepAndFailedKitDoesNotDiscardTheOthers() {
    var batch = new KitImportBatch(List.of("first", "broken", "last"));
    var imported = new ArrayList<String>();
    assertTrue(batch.step(() -> true, imported::add));
    assertEquals(List.of("first"), imported);
    assertTrue(
        batch.step(
            () -> true,
            name -> {
              throw new IllegalArgumentException("Bad definition");
            }));
    assertFalse(batch.step(() -> true, imported::add));
    assertEquals(List.of("first", "last"), imported);
    assertEquals(2, batch.imported());
    assertEquals(1, batch.failed());
    assertEquals(List.of("broken: Bad definition"), batch.errors());
    assertFalse(batch.step(() -> true, name -> fail("Finished batch ran again")));
  }

  @Test
  void deopOrDisconnectStopsBatchWithoutUndoingCompletedKits() {
    var batch = new KitImportBatch(List.of("first", "second"));
    assertTrue(batch.step(() -> true, name -> {}));
    assertFalse(batch.step(() -> false, name -> fail("Must not import after access is lost")));
    assertEquals(1, batch.imported());
    assertFalse(batch.step(() -> true, name -> fail("A stopped batch must not restart")));
  }

  @Test
  void destinationsNeverOverwriteExistingAndStayValidForLongProviderNames() {
    Set<String> ids = new HashSet<>(Set.of("playerkits2_starter"));
    String first = KitImportBatch.destination("PlayerKits2", "starter", ids::contains);
    assertEquals("playerkits2_starter_2", first);
    ids.add(first);
    assertEquals(
        "playerkits2_starter_3",
        KitImportBatch.destination("PlayerKits2", "starter", ids::contains));
    String longId =
        KitImportBatch.destination("! provider", "Long name ".repeat(20), ids::contains);
    assertDoesNotThrow(() -> Checks.id(longId));
    assertTrue(ids.contains("playerkits2_starter"));
  }

  @Test
  void failureDetailsAreBounded() {
    var batch = new KitImportBatch(Collections.nCopies(20, "kit"));
    while (batch.step(
        () -> true,
        name -> {
          throw new IllegalArgumentException("x".repeat(1000));
        })) {}
    assertEquals(20, batch.failed());
    assertEquals(5, batch.errors().size());
    assertTrue(batch.errors().stream().allMatch(error -> error.length() <= 240));
  }
}
