package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.players.*;
import java.util.*;
import net.kyori.adventure.text.*;
import org.junit.jupiter.api.Test;

class NicknameTest {
  @Test
  void restoringAnOldTakeCannotBringBackAResetNickname() {
    var identity = new IdentityService(null, null, null, null);
    UUID id = UUID.randomUUID();
    identity.directory().join(id, "Alice");
    identity.directory().assign(id, "QuietFox");
    var display = new java.util.concurrent.atomic.AtomicReference<>(Component.text("QuietFox"));
    var tab = new java.util.concurrent.atomic.AtomicReference<>(Component.text("QuietFox"));
    var profile =
        java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {com.destroystokyo.paper.profile.PlayerProfile.class},
            (proxy, method, args) -> {
              if (method.getName().equals("getName")) return "Alice";
              throw new AssertionError("Unexpected profile call: " + method);
            });
    var player =
        (org.bukkit.entity.Player)
            java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {org.bukkit.entity.Player.class},
                (proxy, method, args) -> {
                  return switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "isOnline" -> true;
                    case "hasMetadata" -> false;
                    case "getPlayerProfile" -> profile;
                    case "displayName" -> {
                      display.set((TextComponent) args[0]);
                      yield null;
                    }
                    case "playerListName" -> {
                      tab.set((TextComponent) args[0]);
                      yield null;
                    }
                    default -> throw new AssertionError("Unexpected player call: " + method);
                  };
                });
    var yaml = new org.bukkit.configuration.file.YamlConfiguration();
    yaml.set("schema", 1);
    var take = EntitySnapshot.read(yaml);
    identity.captureIdentity(player, take);
    identity.directory().reset(id);
    identity.afterRestore(player, take);
    assertEquals(Component.text("Alice"), display.get());
    assertEquals(Component.text("Alice"), tab.get());
    // A take without any nickname must keep unrelated custom display components.
    yaml.set("nickname", null);
    display.set(Component.text("Director"));
    identity.afterRestore(player, take);
    assertEquals(Component.text("Director"), display.get());
  }

  @Test
  void newlyJoinedAccountReclaimsItsNameFromAnExistingAlias() {
    var directory = new NicknameDirectory();
    UUID alice = UUID.randomUUID(), fox = UUID.randomUUID();
    directory.join(alice, "Alice");
    directory.assign(alice, "QuietFox");
    assertEquals(Set.of(alice), directory.join(fox, "QuietFox"));
    assertEquals("Alice", directory.get(alice).visible());
    assertEquals(Optional.of(fox), directory.resolve("quietfox"));
  }

  @Test
  void accountAndAliasBothResolveWithoutReleasingTheRealName() {
    var directory = new NicknameDirectory();
    UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
    directory.join(alice, "Alice");
    directory.join(bob, "Bob");
    directory.assign(alice, "QuietFox");
    assertEquals(Optional.of(alice), directory.resolve("ALICE"));
    assertEquals(Optional.of(alice), directory.resolve("quietfox"));
    assertFalse(directory.available(bob, "Alice"));
    assertThrows(IllegalArgumentException.class, () -> directory.assign(bob, "QUIETFOX"));
    directory.assign(alice, "BraveOtter");
    assertTrue(directory.resolve("QuietFox").isEmpty());
  }

  @Test
  void disconnectAndResetNeverReapplyAnOldAlias() {
    var directory = new NicknameDirectory();
    UUID id = UUID.randomUUID();
    directory.join(id, "Alice");
    directory.assign(id, "QuietFox");
    directory.reset(id);
    assertFalse(directory.nicknamed(id));
    assertTrue(directory.resolve("QuietFox").isEmpty());
    directory.assign(id, "BraveOtter");
    directory.leave(id);
    directory.join(id, "Alice");
    assertEquals("Alice", directory.get(id).visible());
    assertTrue(directory.resolve("BraveOtter").isEmpty());
  }

  @Test
  void responseKeepsOnlyValidMinecraftUsernames() {
    assertEquals(
        List.of("quietfox123"),
        UsernameClient.parse(
            """
            {"results":[{"login":{"username":"quietfox123"}},
            {"login":{"username":"name_with_more_than_16_characters"}},
            {"login":{"username":"<red>Injected"}}, {"login":{"username":12}}]}
            """));
    assertThrows(RuntimeException.class, () -> UsernameClient.parse("<html>Unavailable</html>"));
    assertThrows(RuntimeException.class, () -> UsernameClient.parse("{\"error\":\"failed\"}"));
    assertThrows(RuntimeException.class, () -> UsernameClient.parse(" ".repeat(16385)));
  }

  @Test
  void deathMessageRewritesVictimAndKillerWithoutChangingItemNames() {
    Component input =
        Component.translatable(
            "death.attack.player.item",
            Component.text("Alice"),
            Component.text("Bob"),
            Component.text("Alice's Sword"));
    Component output =
        NicknameMessages.rewrite(input, Map.of("Alice", "QuietFox", "Bob", "BraveOtter"));
    assertEquals(
        Component.translatable(
            "death.attack.player.item",
            Component.text("QuietFox"),
            Component.text("BraveOtter"),
            Component.text("Alice's Sword")),
        output);
    assertNull(NicknameMessages.rewrite(null, Map.of("Alice", "QuietFox")));
  }

  @Test
  void replacementIsSimultaneousAndNamesAreNotRegex() {
    Component message = Component.translatable("multiplayer.player.left", Component.text("Alice"));
    assertEquals(
        Component.translatable("multiplayer.player.left", Component.text("Bob")),
        NicknameMessages.rewrite(message, Map.of("Alice", "Bob", "Bob", "Charlie")));
  }
}
