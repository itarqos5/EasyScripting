package dev.easyscripting.config;

import java.util.*;
import java.util.function.Consumer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Structural validation is independent of a running Paper registry. */
public final class GuiSchema {
  private GuiSchema() {}

  public static void validate(
      YamlConfiguration y, int minimumContentSlots, Consumer<String> validateMaterial) {
    int rows = y.getInt("layout.rows");
    if (rows != 6)
      throw new IllegalArgumentException(
          "guis.yml: layout.rows = " + rows + "; expected 6 to fit the 41-slot loadout editor.");
    int size = rows * 9;
    Set<Integer> taken = new HashSet<>();
    for (String key :
        List.of(
            "layout.previous-slot",
            "layout.back-slot",
            "layout.next-slot",
            "dynamic.create-slot")) {
      int slot = y.getInt(key);
      if (slot < 0 || slot >= size || !taken.add(slot))
        throw new IllegalArgumentException("guis.yml: " + key + " must be a distinct valid slot.");
    }
    for (int slot : y.getIntegerList("layout.content-slots"))
      if (slot < 0 || slot >= size || !taken.add(slot))
        throw new IllegalArgumentException(
            "guis.yml: layout.content-slots has overlap or invalid slot " + slot);
    if (y.getIntegerList("layout.content-slots").size() < minimumContentSlots)
      throw new IllegalArgumentException(
          "guis.yml: content-slots needs at least "
              + minimumContentSlots
              + " entries for player controls.");
    for (String key : List.of("dynamic.confirm-slot", "dynamic.cancel-slot")) {
      int slot = y.getInt(key);
      if (slot < 0 || slot >= size)
        throw new IllegalArgumentException(
            "guis.yml: " + key + " = " + slot + "; expected 0 through " + (size - 1));
    }
    ConfigurationSection controls = y.getConfigurationSection("dynamic.controls");
    if (y.getInt("dynamic.controls.kit-save.slot", 49) < 41)
      throw new IllegalArgumentException(
          "guis.yml: dynamic.controls.kit-save.slot must be 41..53, outside the kit item slots.");
    if (controls != null)
      for (String key : controls.getKeys(false)) {
        int slot = controls.getInt(key + ".slot");
        if (slot < 0 || slot >= size)
          throw new IllegalArgumentException(
              "guis.yml: dynamic.controls."
                  + key
                  + ".slot = "
                  + slot
                  + "; expected 0 through "
                  + (size - 1));
        validateMaterial.accept(controls.getString(key + ".material", "STONE"));
      }
    ConfigurationSection menus = y.getConfigurationSection("menus");
    if (menus == null) throw new IllegalArgumentException("guis.yml: menus is required.");
    for (String menu : menus.getKeys(false)) {
      ConfigurationSection buttons = menus.getConfigurationSection(menu + ".buttons");
      if (buttons == null) continue;
      Set<Integer> used = new HashSet<>();
      for (String name : buttons.getKeys(false)) {
        int slot = buttons.getInt(name + ".slot");
        if (slot < 0 || slot >= size || !used.add(slot))
          throw new IllegalArgumentException(
              "guis.yml: menus." + menu + ".buttons." + name + ".slot is invalid or duplicated.");
        validateMaterial.accept(buttons.getString(name + ".material", "STONE"));
        String action = buttons.getString(name + ".action", "");
        if (!action.startsWith("menu ")
            && !action.startsWith("input ")
            && !action.startsWith("command "))
          throw new IllegalArgumentException("guis.yml: unknown button action '" + action + "'.");
      }
    }
  }
}
