package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.actors.ActorSupplies;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class ActorSuppliesTest {
  @Test
  void consumingOnePreservesRemainingCountAndDetachesSource() {
    var source = new TestItem(Material.TOTEM_OF_UNDYING, 3);
    ItemStack[] items = {source};
    var taken = ActorSupplies.takeOne(items, 0);
    assertEquals(1, taken.getAmount());
    assertEquals(2, items[0].getAmount());
    assertEquals(3, source.getAmount());
    assertNotSame(source, items[0]);
    ActorSupplies.takeOne(items, 0);
    ActorSupplies.takeOne(items, 0);
    assertNull(items[0]);
    assertNull(ActorSupplies.takeOne(items, 0));
    assertNull(ActorSupplies.takeOne(items, -1));
  }

  @Test
  void findsOnlyCarriedNonemptyMatchingItems() {
    ItemStack[] items = {
      null,
      new TestItem(Material.TOTEM_OF_UNDYING, 0),
      new TestItem(Material.SHIELD, 1),
      new TestItem(Material.TOTEM_OF_UNDYING, 1)
    };
    assertEquals(3, ActorSupplies.find(items, item -> item.getType() == Material.TOTEM_OF_UNDYING));
    assertEquals(-1, ActorSupplies.find(items, item -> item.getType() == Material.SPLASH_POTION));
  }

  private static final class TestItem extends ItemStack {
    final Material type;
    int count;

    TestItem(Material type, int count) {
      super();
      this.type = type;
      this.count = count;
    }

    @Override
    public Material getType() {
      return type;
    }

    @Override
    public int getAmount() {
      return count;
    }

    @Override
    public void setAmount(int count) {
      this.count = count;
    }

    @Override
    public TestItem clone() {
      return new TestItem(type, count);
    }
  }
}
