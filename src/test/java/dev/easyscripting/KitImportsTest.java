package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.integration.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class KitImportsTest {
  @Test
  void itemExportsAreClonedAndArmorGetsItsOwnSlots() {
    var sword = new TestItem(Material.IRON_SWORD);
    var boots = new TestItem(Material.IRON_BOOTS);
    var layout = new KitImports.Layout();
    layout.add(sword, true);
    layout.add(boots, true);
    assertEquals(Material.IRON_SWORD, layout.contents()[0].getType());
    assertEquals(Material.IRON_BOOTS, layout.contents()[36].getType());
    sword.setAmount(2);
    assertEquals(1, layout.contents()[0].getAmount());
    var view = layout.contents();
    view[0].setAmount(4);
    assertEquals(1, layout.contents()[0].getAmount());
  }

  @Test
  void overflowAndSlotCollisionsFailBeforeAnyKitIsSaved() {
    var layout = new KitImports.Layout();
    var stone = new TestItem(Material.STONE);
    for (int i = 0; i < 36; i++) layout.add(stone, false);
    assertThrows(IllegalArgumentException.class, () -> layout.add(stone, false));
    assertThrows(IllegalArgumentException.class, () -> layout.put(0, stone));
    assertThrows(IllegalArgumentException.class, () -> layout.put(41, stone));
  }

  @Test
  void playerKits2AdapterReadsItemsWithoutClaimingTheKit() {
    ItemStack[] items = KitImports.read("PlayerKits2", new PlayerKits2Fixture(), "starter", null);
    assertEquals(Material.STONE, items[0].getType());
    assertEquals(Material.IRON_BOOTS, items[36].getType());
    assertEquals(Material.SHIELD, items[40].getType());
    assertThrows(
        IllegalArgumentException.class,
        () -> KitImports.read("PlayerKits2", new PlayerKits2Fixture(), "missing", null));
  }

  @Test
  void cmiAdapterKeepsStorageHolesAndOffhand() {
    ItemStack[] items = KitImports.read("CMI", new CmiFixture(), "starter", null);
    assertNull(items[0]);
    assertEquals(Material.STONE, items[1].getType());
    assertEquals(Material.SHIELD, items[40].getType());
  }

  @Test
  void unsupportedApisFailWithInventoryImportFallback() {
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () -> PublicKitApi.call(new Object(), "getKitsManager"));
    assertTrue(error.getMessage().contains("Import inventory"));
  }

  public static final class PlayerKits2Fixture {
    public PlayerKits2Fixture getKitsManager() {
      return this;
    }

    public PlayerKits2Fixture getKitItemManager() {
      return this;
    }

    public PkKit getKitByName(String name) {
      return name.equals("starter") ? new PkKit() : null;
    }

    public ItemStack createItemFromKitItem(PkItem item, Player player, PkKit kit) {
      return item.stack();
    }

    public void giveKit() {
      fail("Imports must never claim a kit");
    }
  }

  public static final class PkKit {
    public boolean isAutoArmor() {
      return true;
    }

    public List<PkItem> getItems() {
      return List.of(
          new PkItem(new TestItem(Material.STONE), false),
          new PkItem(new TestItem(Material.IRON_BOOTS), false),
          new PkItem(new TestItem(Material.SHIELD), true));
    }
  }

  public record PkItem(ItemStack stack, boolean offhand) {
    public boolean isOffhand() {
      return offhand;
    }
  }

  public static final class CmiFixture {
    public CmiFixture getKitsManager() {
      return this;
    }

    public CmiKit getKit(String name) {
      return new CmiKit();
    }
  }

  public static final class CmiKit {
    public List<ItemStack> getItems(Player player) {
      return Arrays.asList(null, new TestItem(Material.STONE));
    }

    public ItemStack getBoots() {
      return null;
    }

    public ItemStack getLegs() {
      return null;
    }

    public ItemStack getChest() {
      return null;
    }

    public ItemStack getHelmet() {
      return null;
    }

    public ItemStack getOffHand() {
      return new TestItem(Material.SHIELD);
    }
  }

  /** Layout/adapter fixture; actual item metadata is supplied by each installed provider. */
  private static final class TestItem extends ItemStack {
    private final Material material;
    private int amount = 1;

    TestItem(Material material) {
      super();
      this.material = material;
    }

    @Override
    public Material getType() {
      return material;
    }

    @Override
    public int getAmount() {
      return amount;
    }

    @Override
    public void setAmount(int amount) {
      this.amount = amount;
    }

    @Override
    public TestItem clone() {
      TestItem copy = new TestItem(material);
      copy.amount = amount;
      return copy;
    }
  }
}
