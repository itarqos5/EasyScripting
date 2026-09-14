package dev.easyscripting.gui;

import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.*;

final class MenuHolder implements InventoryHolder {
  final UUID owner;
  final Inventory inventory;
  final Map<Integer, Consumer<ClickType>> actions = new HashMap<>();
  String kitId;
  ItemStack selected;

  MenuHolder(Player owner, int size, Component title) {
    this.owner = owner.getUniqueId();
    this.inventory = Bukkit.createInventory(this, size, title);
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }
}
