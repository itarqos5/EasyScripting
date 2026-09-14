package dev.easyscripting.integration;

import static dev.easyscripting.integration.PublicKitApi.*;

import dev.easyscripting.config.Messages;
import dev.easyscripting.config.Settings;
import dev.easyscripting.core.TickEngine;
import dev.easyscripting.items.KitService;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Reads item definitions only; never grants kits, charges money or executes provider actions. */
public final class KitImports implements AutoCloseable {
  private static final List<String> PROVIDERS =
      List.of("PlayerKits2", "PlayerKits", "Essentials", "CMI");
  private final KitService kits;
  private final Settings settings;
  private final TickEngine ticks;
  private final Messages messages;
  private UUID bulkJob;
  private boolean closing;

  public KitImports(KitService kits, Settings settings, TickEngine ticks, Messages messages) {
    this.kits = kits;
    this.settings = settings;
    this.ticks = ticks;
    this.messages = messages;
  }

  public List<String> sources() {
    List<String> result = new ArrayList<>();
    for (String provider : PROVIDERS)
      if (settings.file("kits").getBoolean("providers." + provider, true)
          && Bukkit.getPluginManager().isPluginEnabled(provider)) result.add(provider);
    result.add("EasyScripting");
    return List.copyOf(result);
  }

  private Plugin provider(String source) {
    String name =
        PROVIDERS.stream()
            .filter(source::equalsIgnoreCase)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown kit provider: " + source));
    Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
    if (plugin == null
        || !plugin.isEnabled()
        || !settings.file("kits").getBoolean("providers." + name, true))
      throw new IllegalArgumentException(
          name + " must be installed, enabled and allowed in kits.yml.");
    return plugin;
  }

  public List<String> names(String source) {
    if (source.equalsIgnoreCase("EasyScripting")) {
      List<String> names = kits.exports();
      limit(names.size());
      return names;
    }
    Plugin p = provider(source);
    Collection<?> names =
        switch (p.getName()) {
          case "PlayerKits2" ->
              collection(call(call(p, "getKitsManager"), "getKits"), 10000).stream()
                  .map(k -> call(k, "getName"))
                  .toList();
          case "PlayerKits" -> {
            ConfigurationSection root =
                ((ConfigurationSection) call(p, "getKits")).getConfigurationSection("Kits");
            yield root == null ? List.of() : root.getKeys(false);
          }
          case "Essentials" -> collection(call(call(p, "getKits"), "getKitKeys"), 10000);
          case "CMI" -> ((Map<?, ?>) call(call(p, "getKitsManager"), "getKitMap")).keySet();
          default -> throw new IllegalArgumentException("Unsupported kit provider.");
        };
    limit(names.size());
    return names.stream().map(String::valueOf).sorted().toList();
  }

  public void importKit(String source, String name, String destination, Player player) {
    settings.require("kits");
    if (!player.isOp()) throw new IllegalArgumentException("Only operators can import kits.");
    if (kits.exists(destination))
      throw new IllegalArgumentException(
          "Kit already exists: " + destination + ". Choose another destination ID.");
    if (source.equalsIgnoreCase("EasyScripting")) {
      kits.importExport(name, destination);
      return;
    }
    Plugin provider = provider(source);
    kits.save(destination, read(provider.getName(), provider, name, player));
  }

  private void limit(int count) {
    if (count > settings.file("kits").getInt("max-provider-kits", 1000))
      throw new IllegalArgumentException("Provider exceeds max-provider-kits in kits.yml.");
  }

  public String destination(String source, String name) {
    return KitImportBatch.destination(source, name, kits::exists);
  }

  public boolean importing() {
    return bulkJob != null;
  }

  public void importAll(String source, Player player) {
    settings.require("kits");
    if (!player.isOp()) throw new IllegalArgumentException("Only operators can import kits.");
    if (closing || !ticks.acceptingWork())
      throw new IllegalStateException("EasyScripting is stopping.");
    if (importing())
      throw new IllegalArgumentException(
          "An import is already running. Use /es kits cancelimport to stop it.");
    KitImportBatch batch = new KitImportBatch(names(source));
    if (batch.total() == 0)
      throw new IllegalArgumentException("This provider has no kits to import.");
    bulkJob =
        ticks.add(
            new TickEngine.Job() {
              public boolean tick() {
                boolean more =
                    batch.step(
                        () ->
                            !closing
                                && settings.enabled("kits")
                                && player.isOnline()
                                && player.isOp()
                                && Bukkit.getPlayer(player.getUniqueId()) == player,
                        name -> importKit(source, name, destination(source, name), player));
                if (more && batch.processed() % 100 == 0)
                  messages.send(
                      player,
                      "info",
                      "Kit import: " + batch.processed() + "/" + batch.total() + " processed.");
                return more;
              }

              public void stopped() {
                bulkJob = null;
                if (closing || !player.isOnline()) return;
                messages.send(
                    player,
                    batch.failed() == 0 ? "success" : "error",
                    "Kit import "
                        + (batch.processed() == batch.total() ? "finished" : "stopped")
                        + ": "
                        + batch.imported()
                        + " imported, "
                        + batch.failed()
                        + " failed, "
                        + (batch.total() - batch.processed())
                        + " not processed. Completed imports are kept.");
                for (String error : batch.errors()) messages.send(player, "error", error);
              }
            });
    messages.send(
        player,
        "info",
        "Importing "
            + batch.total()
            + " kits from "
            + source
            + ". Existing kits are kept; new IDs receive a suffix when needed.");
  }

  public void cancel() {
    if (bulkJob == null) throw new IllegalArgumentException("No kit import is running.");
    ticks.cancel(bulkJob);
  }

  @Override
  public void close() {
    closing = true;
    if (bulkJob != null) ticks.cancel(bulkJob);
  }

  public static ItemStack[] read(String source, Object provider, String name, Player player) {
    Layout layout = new Layout();
    switch (source) {
      case "PlayerKits2" -> {
        Object kit = require(call(call(provider, "getKitsManager"), "getKitByName", name));
        Object converter = call(provider, "getKitItemManager");
        boolean armor = Boolean.TRUE.equals(call(kit, "isAutoArmor"));
        for (Object item : collection(call(kit, "getItems"))) {
          ItemStack exported = stack(call(converter, "createItemFromKitItem", item, player, kit));
          if (Boolean.TRUE.equals(call(item, "isOffhand"))) layout.put(40, exported);
          else layout.add(exported, armor);
        }
      }
      case "PlayerKits" -> {
        ConfigurationSection config = (ConfigurationSection) call(provider, "getKits");
        ConfigurationSection items = config.getConfigurationSection("Kits." + name + ".Items");
        if (items == null) throw new IllegalArgumentException("Kit has no item definitions.");
        boolean armor =
            Boolean.parseBoolean(config.getString("Kits." + name + ".auto_armor", "false"));
        Class<?> manager = type(provider, "pk.ajneb97.managers.KitManager");
        for (String id : items.getKeys(false)) {
          String path = "Kits." + name + ".Items." + id;
          ItemStack exported =
              stack(call(manager, "getItem", config, path, call(provider, "getConfig"), player));
          if (Boolean.parseBoolean(config.getString(path + ".offhand", "false")))
            layout.put(40, exported);
          else layout.add(exported, armor);
        }
      }
      case "CMI" -> {
        Object kit = require(call(call(provider, "getKitsManager"), "getKit", name));
        int slot = 0;
        for (Object item : collection(call(kit, "getItems", player)))
          layout.put(slot++, stack(item));
        layout.put(36, stack(call(kit, "getBoots")));
        layout.put(37, stack(call(kit, "getLegs")));
        layout.put(38, stack(call(kit, "getChest")));
        layout.put(39, stack(call(kit, "getHelmet")));
        layout.put(40, stack(call(kit, "getOffHand")));
      }
      case "Essentials" -> essentials(provider, name, layout);
      default -> throw new IllegalArgumentException("Unsupported kit provider: " + source);
    }
    return layout.contents();
  }

  private static void essentials(Object provider, String name, Layout layout) {
    Object definition = require(call(call(provider, "getKits"), "getKit", name));
    if (!(definition instanceof Map<?, ?> kit))
      throw new IllegalArgumentException("Unsupported Essentials kit definition.");
    Object itemDb = call(provider, "getItemDb");
    Class<?> metadata = type(provider, "com.earth2me.essentials.MetaItemStack");
    for (Object value : collection(kit.get("items"))) {
      String line = String.valueOf(value).strip();
      if (line.startsWith("/") || line.startsWith("$")) continue;
      int slot = -1;
      if (line.startsWith("slot:")) {
        int end = line.indexOf(' ');
        if (end < 0) throw new IllegalArgumentException("Invalid Essentials slot line.");
        slot = Integer.parseInt(line.substring(5, end));
        line = line.substring(end + 1).strip();
      }
      ItemStack item;
      if (line.startsWith("@")) {
        byte[] bytes = Base64.getDecoder().decode(line.substring(1));
        if (bytes.length > 1048576)
          throw new IllegalArgumentException("Serialized kit item is too large.");
        Class<?> serialization = type(provider, "net.ess3.provider.SerializationProvider");
        item =
            stack(
                call(require(call(provider, "provider", serialization)), "deserializeItem", bytes));
      } else {
        String[] parts = line.split(" +");
        ItemStack base =
            stack(call(itemDb, "get", parts[0], parts.length > 1 ? Integer.parseInt(parts[1]) : 1));
        Object meta = construct(metadata, base);
        if (parts.length > 2) call(meta, "parseStringMeta", null, true, parts, 2, provider);
        item = stack(call(meta, "getItemStack"));
      }
      if (slot < 0) layout.add(item, false);
      else layout.put(slot, item);
    }
  }

  private static Object require(Object value) {
    if (value == null) throw new IllegalArgumentException("Kit or provider export was not found.");
    return value;
  }

  private static Collection<?> collection(Object value) {
    return collection(value, 1000);
  }

  private static Collection<?> collection(Object value, int maximum) {
    if (!(value instanceof Collection<?> items) || items.size() > maximum)
      throw new IllegalArgumentException("Unsupported or oversized kit export.");
    return items;
  }

  private static ItemStack stack(Object value) {
    if (value == null) return null;
    if (!(value instanceof ItemStack item))
      throw new IllegalArgumentException("Provider did not export a Bukkit item.");
    return item.clone();
  }

  public static final class Layout {
    private final ItemStack[] items = new ItemStack[41];

    public void put(int slot, ItemStack item) {
      if (slot < 0 || slot >= 41)
        throw new IllegalArgumentException("Kit exceeds 41 slots; nothing was imported.");
      if (empty(item)) return;
      if (items[slot] != null)
        throw new IllegalArgumentException(
            "Two kit items use slot " + slot + ". Nothing was imported.");
      items[slot] = item.clone();
    }

    public void add(ItemStack item, boolean armor) {
      if (empty(item)) return;
      String material = item.getType().name();
      int armorSlot =
          material.endsWith("_BOOTS")
              ? 36
              : material.endsWith("_LEGGINGS")
                  ? 37
                  : material.endsWith("_CHESTPLATE") || material.equals("ELYTRA")
                      ? 38
                      : material.endsWith("_HELMET") ? 39 : -1;
      if (armor && armorSlot >= 0 && items[armorSlot] == null) {
        put(armorSlot, item);
        return;
      }
      for (int i = 0; i < 36; i++)
        if (items[i] == null) {
          put(i, item);
          return;
        }
      throw new IllegalArgumentException("Kit exceeds 36 storage slots; nothing was imported.");
    }

    public ItemStack[] contents() {
      return Arrays.stream(items).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
    }

    private static boolean empty(ItemStack item) {
      return item == null || item.getAmount() <= 0 || item.getType() == Material.AIR;
    }
  }
}
