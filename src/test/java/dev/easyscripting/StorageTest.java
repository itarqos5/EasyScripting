package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.storage.YamlStore;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageTest {
  @TempDir Path directory;

  @Test
  void atomicReplacementLeavesNoTemporaryFiles() throws Exception {
    Path file = directory.resolve("scene.yml");
    YamlStore.atomicWrite(file, "schema: 1\nname: first");
    YamlStore.atomicWrite(file, "schema: 1\nname: second");
    assertEquals("second", YamlStore.read(file).getString("name"));
    try (var files = Files.list(directory)) {
      assertEquals(1, files.count());
    }
  }

  @Test
  void malformedYamlIsReportedAndPreserved() throws Exception {
    Path file = directory.resolve("bad.yml");
    String text = "actions: [unterminated";
    Files.writeString(file, text);
    assertThrows(IllegalArgumentException.class, () -> YamlStore.read(file));
    assertEquals(text, Files.readString(file));
  }
}
