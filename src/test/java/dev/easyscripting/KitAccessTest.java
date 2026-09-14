package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.config.Access;
import dev.easyscripting.items.*;
import dev.easyscripting.storage.YamlStore;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KitAccessTest {
  @TempDir Path directory;
  private final UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();

  private Player player(UUID id, AtomicBoolean op, List<ItemStack[]> grants) {
    var inventory =
        (PlayerInventory)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {PlayerInventory.class},
                (proxy, method, args) -> {
                  if (method.getName().equals("setContents")) {
                    grants.add((ItemStack[]) args[0]);
                    return null;
                  }
                  throw new AssertionError(method.toString());
                });
    return (Player)
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {Player.class},
            (proxy, method, args) ->
                switch (method.getName()) {
                  case "isOp" -> op.get();
                  case "hasPermission", "isOnline" -> true;
                  case "getUniqueId" -> id;
                  case "isDead", "hasMetadata" -> false;
                  case "getInventory" -> inventory;
                  case "closeInventory" -> null;
                  case "equals" -> proxy == args[0];
                  case "hashCode" -> System.identityHashCode(proxy);
                  default -> throw new AssertionError(method.toString());
                });
  }

  @Test
  void specificPlayerMeansUuidPlusOperatorsOnly() {
    var policy = new KitAccess(KitAccess.Mode.PLAYER, alice, "OldNickname");
    assertTrue(policy.allows(false, alice));
    assertFalse(policy.allows(false, bob));
    assertFalse(policy.allows(false, null));
    assertTrue(policy.allows(true, bob));
    assertTrue(KitAccess.operators().allows(true, alice));
    assertFalse(KitAccess.operators().allows(false, alice));
    assertTrue(new KitAccess(KitAccess.Mode.EVERYONE, null, null).allows(false, bob));
  }

  @Test
  void policyRoundTripAndModeChangesClearTheOldPlayer() throws Exception {
    var yaml = new YamlConfiguration();
    new KitAccess(KitAccess.Mode.PLAYER, alice, "Alex").write(yaml);
    var loaded = new YamlConfiguration();
    loaded.loadFromString(yaml.saveToString());
    assertEquals(alice, KitAccess.read(loaded).player());
    new KitAccess(KitAccess.Mode.EVERYONE, bob, "Ignored").write(loaded);
    assertFalse(loaded.contains("access.player"));
    assertFalse(loaded.contains("access.player-name"));
    KitAccess.operators().write(loaded);
    assertEquals(KitAccess.operators(), KitAccess.read(loaded));
  }

  @Test
  void missingIsPrivateAndMalformedPoliciesAreRejected() {
    var yaml = new YamlConfiguration();
    assertEquals(KitAccess.operators(), KitAccess.read(yaml));
    yaml.set("access.mode", "public_typo");
    assertThrows(IllegalArgumentException.class, () -> KitAccess.read(yaml));
    yaml.set("access.mode", "player");
    yaml.set("access.player", "Alex");
    assertThrows(IllegalArgumentException.class, () -> KitAccess.read(yaml));
  }

  @Test
  void permissionGrantsCannotSurviveDeopForManagement() {
    AtomicBoolean op = new AtomicBoolean(true);
    Player sender = player(alice, op, new ArrayList<>());
    Access access = new Access(null);
    assertTrue(access.allowed(sender, "easyscripting.kit.edit"));
    op.set(false);
    assertFalse(access.allowed(sender, "easyscripting.kit.edit"));
    assertThrows(IllegalArgumentException.class, () -> access.require(sender, "kit.edit"));
    assertTrue(access.allowed(sender, "easyscripting.kit"));
  }

  @Test
  void editsExportImportAndRestartPreservePolicy() {
    try (YamlStore store = new YamlStore(directory, Logger.getAnonymousLogger())) {
      KitService kits = new KitService(store);
      kits.create("starter");
      assertEquals(KitAccess.operators(), kits.access("starter"));
      kits.access("starter", new KitAccess(KitAccess.Mode.PLAYER, alice, "Alex"));
      kits.save("starter", new ItemStack[43]);
      assertEquals(41, kits.contents("starter").length);
      assertEquals(alice, kits.access("starter").player());
      kits.export("starter");
    }
    try (YamlStore store = new YamlStore(directory, Logger.getAnonymousLogger())) {
      KitService kits = new KitService(store);
      kits.load();
      assertEquals(alice, kits.access("starter").player());
      kits.importExport("starter", "imported");
      assertEquals(kits.access("starter"), kits.access("imported"));
      assertThrows(IllegalArgumentException.class, () -> kits.importExport("starter", "imported"));
    }
  }

  @Test
  void bothListingsAndLegacyClaimsRejectUnauthorizedPlayersBeforeInventoryChange() {
    var grants = new ArrayList<ItemStack[]>();
    Player allowed = player(alice, new AtomicBoolean(false), grants);
    Player denied = player(bob, new AtomicBoolean(false), grants);
    try (YamlStore store = new YamlStore(directory, Logger.getAnonymousLogger())) {
      KitService kits = new KitService(store);
      kits.create("personal");
      kits.access("personal", new KitAccess(KitAccess.Mode.PLAYER, alice, "Alex"));
      assertEquals(List.of("personal"), kits.ids(allowed));
      assertEquals(List.of(), kits.ids(denied));
      assertThrows(IllegalArgumentException.class, () -> kits.claim("personal", denied));
      assertTrue(grants.isEmpty());
      kits.claim("personal", allowed);
      assertEquals(1, grants.size());
      kits.access("personal", KitAccess.operators());
      assertThrows(IllegalArgumentException.class, () -> kits.claim("personal", allowed));
      assertEquals(1, grants.size());
    }
  }

  @Test
  void aClosedSaveQueueDoesNotLeavePhantomImportedKitsOrPolicyChanges() {
    YamlStore store = new YamlStore(directory, Logger.getAnonymousLogger());
    KitService kits = new KitService(store);
    kits.create("existing");
    store.close();
    assertThrows(IllegalStateException.class, () -> kits.create("unsaved"));
    assertFalse(kits.exists("unsaved"));
    assertThrows(
        IllegalStateException.class,
        () -> kits.access("existing", new KitAccess(KitAccess.Mode.EVERYONE, null, null)));
    assertEquals(KitAccess.operators(), kits.access("existing"));
  }
}
