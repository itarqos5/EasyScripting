package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.storage.YamlStore;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class DetachedYamlTest {
  @Test
  void snapshotsAreDetachedAndRetainBukkitTypeAliases() throws Exception {
    YamlConfiguration original = new YamlConfiguration();
    original.createSection("location", Map.of("x", 12, "world", "world"));
    Vector velocity = new Vector(1, 2, 3);
    original.set("velocity", velocity);
    List<String> names = new ArrayList<>(List.of("hero"));
    original.set("names", names);
    Map<?, ?> detached = (Map<?, ?>) YamlStore.detach(original);
    names.add("other");
    velocity.setX(99);
    original.set("location.x", 123);
    YamlConfiguration encoded = new YamlConfiguration();
    detached.forEach((key, value) -> encoded.set(key.toString(), value));
    YamlConfiguration read = new YamlConfiguration();
    read.loadFromString(encoded.saveToString());
    assertEquals(12, read.getInt("location.x"));
    assertEquals(List.of("hero"), read.getStringList("names"));
    assertEquals(new Vector(1, 2, 3), read.getVector("velocity"));
  }
}
