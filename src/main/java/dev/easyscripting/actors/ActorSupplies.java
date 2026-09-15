package dev.easyscripting.actors;

import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Item exchanges conserve stacks. Player backpacks are live; mob backpacks are actor-owned. */
public final class ActorSupplies {
  private ActorSupplies() {}

  public static ItemStack[] inventory(ActorService.ManagedActor actor) {
    return actor.requireEntity() instanceof Player player
        ? player.getInventory().getStorageContents()
        : actor.definition.inventory.clone();
  }

  public static void inventory(ActorService.ManagedActor actor, ItemStack[] items) {
    if (actor.requireEntity() instanceof Player player)
      player.getInventory().setStorageContents(items);
    else actor.definition.inventory = items.clone();
  }

  public static int find(ItemStack[] items, Predicate<ItemStack> predicate) {
    for (int i = 0; i < items.length; i++)
      if (items[i] != null && items[i].getAmount() > 0 && predicate.test(items[i])) return i;
    return -1;
  }

  public static boolean offhand(ActorService.ManagedActor actor, Material material) {
    var equipment = actor.requireEntity().getEquipment();
    if (equipment == null || equipment.getItemInOffHand().getType() == material) return false;
    if (!(actor.requireEntity() instanceof Player)
        && equipment.getItemInMainHand().getType() == material) {
      ItemStack main = equipment.getItemInMainHand();
      equipment.setItemInMainHand(equipment.getItemInOffHand());
      equipment.setItemInOffHand(main);
      return true;
    }
    var items = inventory(actor);
    int slot = find(items, item -> item.getType() == material);
    if (slot < 0) return false;
    ItemStack next = items[slot];
    items[slot] = equipment.getItemInOffHand();
    inventory(actor, items);
    equipment.setItemInOffHand(next);
    return true;
  }

  public static ItemStack takeOne(ItemStack[] items, int slot) {
    if (slot < 0 || slot >= items.length || items[slot] == null || items[slot].getAmount() < 1)
      return null;
    ItemStack result = items[slot].clone();
    result.setAmount(1);
    if (items[slot].getAmount() == 1) items[slot] = null;
    else {
      items[slot] = items[slot].clone();
      items[slot].setAmount(items[slot].getAmount() - 1);
    }
    return result;
  }
}
