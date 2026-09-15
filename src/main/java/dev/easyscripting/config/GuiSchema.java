package dev.easyscripting.config;

import java.util.*;
import java.util.function.Consumer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Structural validation is independent of a running Paper registry. */
public final class GuiSchema {
  private GuiSchema() {}

  /** Legacy layouts are replaced as a whole; mixing slot schemes would hide controls. */
  public static YamlConfiguration prepare(YamlConfiguration current, YamlConfiguration defaults) {
    if (current.contains("schema") && !(current.get("schema") instanceof Integer))
      throw new IllegalArgumentException("guis.yml: schema must be an integer.");
    int version = current.getInt("schema", 1);
    if (version < 1 || version > 3)
      throw new IllegalArgumentException("guis.yml: unsupported schema " + version);
    YamlConfiguration result = new YamlConfiguration();
    try {
      result.loadFromString((version < 3 ? defaults : current).saveToString());
    } catch (org.bukkit.configuration.InvalidConfigurationException ex) {
      throw new IllegalArgumentException("guis.yml: cannot prepare layout", ex);
    }
    for (String key : defaults.getKeys(true))
      if (!defaults.isConfigurationSection(key) && !result.contains(key))
        result.set(key, defaults.get(key));
    // Update shipped help text only; preserve custom wording and slot layouts.
    Map<String, Object> oldHelp =
        Map.of(
            "menus.actor-combat.description",
                List.of(
                    "<gray>Hittable ON: receive damage and knockback.",
                    "<gray>Immortal ON: prevent lethal damage.",
                    "<gray>To allow death, turn Immortal OFF."),
            "dynamic.controls.actor-health.lore",
                List.of(
                    "<gray>Status: {status}",
                    "<gray>Recording performers are always protected.",
                    "<gray>Playback uses the combat switches below."),
            "dynamic.controls.actor-combat-respawn.lore",
                List.of(
                    "<gray>Bring this NPC back after death.",
                    "<gray>Autoplay starts when enabled."),
            "dynamic.controls.actor-act.lore",
                List.of(
                    "<gray>Take its position, identity and costume.",
                    "<gray>Record movement, equipment and animations.",
                    "<gray>Damage and knockback cannot interrupt you."),
            "dynamic.controls.actor-hittable.lore",
                List.of(
                    "<gray>ON: incoming hits cause damage and knockback.",
                    "<gray>Click to toggle."),
            "dynamic.actor-respawn", "<green>Respawn NPC",
            "dynamic.actor-combat-respawn", "<green>Respawn NPC");
    oldHelp.forEach(
        (key, old) -> {
          if (Objects.equals(result.get(key), old)) result.set(key, defaults.get(key));
        });
    Map<String, Object> old015 =
        Map.of(
            "dynamic.controls.actor-stop.lore",
                List.of(
                    "<gray>Return the NPC to its starting state.", "<gray>Combat damage is kept."),
            "dynamic.controls.actor-autoplay.lore",
                List.of(
                    "<gray>ON: start after saving a performance.",
                    "<gray>Also starts when shown, respawned or loaded.",
                    "<gray>OFF does not stop an active replay.",
                    "<gray>Click to toggle."),
            "dynamic.controls.actor-immortal.lore",
                List.of(
                    "<gray>ON: prevent lethal hits. OFF: this NPC can die.",
                    "<gray>Click to toggle."),
            "entries.kits.create", "<green>Save my inventory as a kit",
            "entries.kits.lore",
                List.of(
                    "<gray>ID: <white>{id}",
                    "<gray>{detail}",
                    "",
                    "<yellow>Click to apply · Right click to edit",
                    "<red>Shift-right click to delete"),
            "menus.production.buttons.mute.name", "<white>Mute public chat",
            "menus.production.buttons.mute.lore",
                List.of("<gray>Pause public chat for the shoot.", "", "<yellow>Click to select"));
    old015.forEach(
        (key, old) -> {
          if (Objects.equals(result.get(key), old)) result.set(key, defaults.get(key));
        });
    Map<String, Object> old016 =
        Map.of(
            "menus.kits.description",
                List.of(
                    "<gray>Click to apply; right click to edit a copy.",
                    "<gray>Use the green button below to create one.",
                    "<gray>Shift-right click an entry to delete it."),
            "menus.kit-details.description",
                List.of(
                    "<gray>Equip the kit, edit its slots, or import inventory.",
                    "<gray>Export copies a portable YAML file."),
            "dynamic.kit-apply", "<green>Equip kit",
            "dynamic.controls.kit-apply.lore",
                List.of("<gray>Equip this kit, replacing your inventory."),
            "entries.kits.empty", "<white>No costumes saved yet");
    old016.forEach(
        (key, old) -> {
          if (Objects.equals(result.get(key), old)) result.set(key, defaults.get(key));
        });
    if (result.getString("menus.production.buttons.start.action", "").equals("input take start")) {
      for (String leaf : List.of("action", "lore", "prompt"))
        result.set(
            "menus.production.buttons.start." + leaf,
            defaults.get("menus.production.buttons.start." + leaf));
    }
    if (result
        .getString("menus.production.buttons.stop.action", "")
        .equals("command take stop reset")) {
      for (String leaf : List.of("action", "lore", "name"))
        result.set(
            "menus.production.buttons.stop." + leaf,
            defaults.get("menus.production.buttons.stop." + leaf));
    }
    if (result
        .getStringList("menus.recording.description")
        .equals(
            List.of(
                "<gray>Choose a saved take, then select an NPC.",
                "<gray>Use the green button below to create one.",
                "<gray>Shift-right click an entry to delete it.")))
      result.set("menus.recording.description", defaults.get("menus.recording.description"));
    for (String key : result.getKeys(true))
      if (key.endsWith(".action")
          && result.get(key) instanceof String action
          && action.startsWith("command chat mute "))
        result.set(key, action.replace("command chat mute ", "command chat block "));
    return result;
  }

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
            "layout.home-slot",
            "layout.close-slot",
            "layout.help-slot",
            "layout.next-slot",
            "dynamic.create-slot")) {
      int slot = y.getInt(key);
      if (!(y.get(key) instanceof Integer) || slot < 45 || slot >= size || !taken.add(slot))
        throw new IllegalArgumentException("guis.yml: " + key + " must be a distinct valid slot.");
    }
    int header = y.getInt("layout.header-slot", -1);
    if (header < 0 || header > 8)
      throw new IllegalArgumentException("guis.yml: header-slot must be in the top row.");
    taken.add(header);
    Set<Integer> navigation = Set.copyOf(taken);
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
      if (slot < 9 || slot >= 45 || navigation.contains(slot))
        throw new IllegalArgumentException(
            "guis.yml: " + key + " = " + slot + "; expected 0 through " + (size - 1));
    }
    if (y.getInt("dynamic.confirm-slot") == y.getInt("dynamic.cancel-slot"))
      throw new IllegalArgumentException("guis.yml: confirm and cancel must use different slots.");
    if (!y.getIntegerList("layout.content-slots").contains(y.getInt("layout.empty-slot", -1)))
      throw new IllegalArgumentException("guis.yml: empty-slot must be a content slot.");
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
    for (List<String> page :
        List.of(
            List.of(
                "actor-section-appearance",
                "actor-section-movement",
                "actor-section-acting",
                "actor-section-combat",
                "actor-info",
                "actor-group",
                "actor-delete"),
            List.of(
                "actor-name",
                "actor-skin",
                "actor-kit",
                "actor-randomize",
                "actor-glow",
                "actor-nametag",
                "actor-tablist"),
            List.of(
                "actor-here",
                "actor-walk",
                "actor-respawn",
                "actor-hide",
                "actor-show",
                "actor-look",
                "actor-wander",
                "actor-collidable",
                "actor-setting"),
            List.of(
                "actor-health",
                "actor-hittable",
                "actor-immortal",
                "actor-combat-respawn",
                "actor-aggressive",
                "actor-combat-kit",
                "actor-combat-group"),
            List.of(
                "actor-act",
                "actor-finish",
                "actor-cancel",
                "actor-play",
                "actor-stop",
                "actor-mode-stop",
                "actor-mode-repeat",
                "actor-mode-reverse",
                "actor-recording",
                "actor-autoplay"))) {
      Set<Integer> used = new HashSet<>(navigation);
      for (String key : page) takeControl(y, key, used);
    }
    for (List<String> page :
        List.of(
            List.of("snapshot", "reset", "inventory-save"),
            List.of(
                "timeline-play",
                "timeline-pause",
                "timeline-stop",
                "timeline-add",
                "timeline-bind"))) {
      Set<Integer> used = new HashSet<>(taken);
      for (String key : page) takeControl(y, key, used);
    }
    Set<Integer> kit = new HashSet<>(navigation);
    Set<Integer> groupPage = new HashSet<>(navigation);
    for (String key :
        List.of(
            "group-members",
            "group-leader",
            "group-add",
            "group-remove",
            "group-follow",
            "group-hold",
            "group-move",
            "group-attack",
            "group-fight",
            "group-intelligence",
            "group-info",
            "group-clear-leader",
            "group-delete")) takeControl(y, key, groupPage);
    Set<Integer> groupLibrary = new HashSet<>(taken);
    groupLibrary.remove(y.getInt("dynamic.create-slot"));
    takeControl(y, "group-create", groupLibrary);
    kit.remove(y.getInt("dynamic.create-slot"));
    for (int i = 0; i < 45; i++) kit.add(i);
    takeControl(y, "kit-save", kit);
    takeControl(y, "kit-import-inventory", kit);
    takeControl(y, "kit-save-apply", kit);
    Set<Integer> kitLibrary = new HashSet<>(taken);
    takeControl(y, "kits-import", kitLibrary);
    Set<Integer> kitDetails = new HashSet<>(navigation);
    for (String key :
        List.of(
            "kit-apply",
            "kit-edit",
            "kit-capture",
            "kit-export",
            "kit-delete",
            "kit-access",
            "kit-give-player",
            "kit-give-actor")) takeControl(y, key, kitDetails);
    Set<Integer> kitAccess = new HashSet<>(navigation);
    for (String key :
        List.of(
            "kit-access-info", "kit-access-operators", "kit-access-everyone", "kit-access-player"))
      takeControl(y, key, kitAccess);
    Set<Integer> kitProvider = new HashSet<>(taken);
    for (String key : List.of("kits-import-all", "kits-import-cancel"))
      takeControl(y, key, kitProvider);
    validateMaterial.accept(y.getString("layout.filler", "BLACK_STAINED_GLASS_PANE"));
    ConfigurationSection menus = y.getConfigurationSection("menus");
    if (menus == null) throw new IllegalArgumentException("guis.yml: menus is required.");
    for (String menu : menus.getKeys(false)) {
      validateMaterial.accept(menus.getString(menu + ".material", "BOOK"));
      ConfigurationSection buttons = menus.getConfigurationSection(menu + ".buttons");
      if (buttons == null) continue;
      Set<Integer> used = new HashSet<>(navigation);
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
        if (action.startsWith("menu ") && !menus.isConfigurationSection(action.substring(5)))
          throw new IllegalArgumentException(
              "guis.yml: unknown menu target '" + action.substring(5) + "'.");
      }
    }
  }

  private static void takeControl(YamlConfiguration y, String key, Set<Integer> used) {
    String path = "dynamic.controls." + key + ".slot";
    int slot = y.getInt(path, -1);
    if (slot < 0 || slot >= 54 || !used.add(slot))
      throw new IllegalArgumentException(
          "guis.yml: " + path + " overlaps another control or is invalid.");
  }
}
