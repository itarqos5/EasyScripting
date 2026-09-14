package dev.easyscripting.world;

import dev.easyscripting.config.*;
import dev.easyscripting.core.*;
import dev.easyscripting.items.ItemService;
import dev.easyscripting.storage.YamlStore;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

public final class RegionService implements Listener, AutoCloseable {
  private final Settings settings;
  private final Messages messages;
  private final ItemService items;
  private final TickEngine ticks;
  private final YamlStore store;
  private final Map<UUID, Location[]> selections = new HashMap<>();
  private final Map<String, YamlConfiguration> regions = new TreeMap<>();
  private final Map<String, UUID> jobs = new HashMap<>();
  private final Set<UUID> busyWorlds = new HashSet<>();

  public RegionService(
      Settings settings, Messages messages, ItemService items, TickEngine ticks, YamlStore store) {
    this.settings = settings;
    this.messages = messages;
    this.items = items;
    this.ticks = ticks;
    this.store = store;
  }

  public void load() {
    regions.putAll(store.load("regions"));
  }

  public List<String> ids() {
    return List.copyOf(regions.keySet());
  }

  public void point(Player p, int index, Location at) {
    selections.computeIfAbsent(p.getUniqueId(), k -> new Location[2])[index] = at.clone();
  }

  public void chunk(Player p) {
    Location at = p.getLocation();
    int x = at.getBlockX() >> 4, z = at.getBlockZ() >> 4;
    point(p, 0, new Location(p.getWorld(), x * 16, p.getWorld().getMinHeight(), z * 16));
    point(
        p,
        1,
        new Location(p.getWorld(), x * 16 + 15, p.getWorld().getMaxHeight() - 1, z * 16 + 15));
  }

  public void save(Player p, String id) {
    settings.require("regions");
    Checks.id(id);
    if (jobs.containsKey(id))
      throw new IllegalArgumentException("Region already has an active operation.");
    Location[] corners = selections.get(p.getUniqueId());
    if (corners == null
        || corners[0] == null
        || corners[1] == null
        || !corners[0].getWorld().equals(corners[1].getWorld()))
      throw new IllegalArgumentException("Select both corners in one world with the region wand.");
    int minX = Math.min(corners[0].getBlockX(), corners[1].getBlockX()),
        minY = Math.min(corners[0].getBlockY(), corners[1].getBlockY()),
        minZ = Math.min(corners[0].getBlockZ(), corners[1].getBlockZ());
    int sx = Math.abs(corners[0].getBlockX() - corners[1].getBlockX()) + 1,
        sy = Math.abs(corners[0].getBlockY() - corners[1].getBlockY()) + 1,
        sz = Math.abs(corners[0].getBlockZ() - corners[1].getBlockZ()) + 1;
    long total = (long) sx * sy * sz;
    if (total > settings.limit("region-blocks"))
      throw new IllegalArgumentException(
          "Selection has "
              + total
              + " blocks; configured limit is "
              + settings.limit("region-blocks")
              + ".");
    World world = corners[0].getWorld();
    if (minY < world.getMinHeight() || minY + sy > world.getMaxHeight())
      throw new IllegalArgumentException("Selection lies outside the world's build height.");
    available(id, world);
    List<Map<String, Object>> blocks = new ArrayList<>((int) total);
    UUID job =
        submit(
            p,
            id,
            world,
            new TickEngine.Job() {
              int cursor;

              public boolean tick() {
                int budget = settings.file("config").getInt("limits.region-blocks-per-tick", 1024);
                while (budget-- > 0 && cursor < total) {
                  int x = minX + cursor % sx,
                      z = minZ + cursor / sx % sz,
                      y = minY + cursor / (sx * sz);
                  cursor++;
                  if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    messages.error(
                        p,
                        "Region save stopped: a selected chunk is not loaded. No saved region was"
                            + " replaced.");
                    return false;
                  }
                  Block block = world.getBlockAt(x, y, z);
                  Map<String, Object> record = new LinkedHashMap<>();
                  record.put("x", x);
                  record.put("y", y);
                  record.put("z", z);
                  record.put("data", block.getBlockData().getAsString());
                  if (block.getState() instanceof Container container)
                    record.put(
                        "inventory",
                        Arrays.stream(container.getSnapshotInventory().getContents())
                            .map(
                                i ->
                                    i == null
                                        ? ""
                                        : Base64.getEncoder().encodeToString(i.serializeAsBytes()))
                            .toList());
                  if (block.getState() instanceof Sign sign) {
                    record.put(
                        "front",
                        sign.getSide(org.bukkit.block.sign.Side.FRONT).lines().stream()
                            .map(
                                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                    ::serialize)
                            .toList());
                    record.put(
                        "back",
                        sign.getSide(org.bukkit.block.sign.Side.BACK).lines().stream()
                            .map(
                                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                                    ::serialize)
                            .toList());
                  }
                  blocks.add(Collections.unmodifiableMap(record));
                }
                if (cursor < total) return true;
                YamlConfiguration y = new YamlConfiguration();
                y.set("schema", 1);
                y.set("world", world.getName());
                List<Map<String, Object>> snapshot = List.copyOf(blocks);
                y.set("blocks", snapshot);
                store.saveDetached(
                    "regions",
                    id,
                    Map.of("schema", 1, "world", world.getName(), "blocks", snapshot));
                regions.put(id, y);
                messages.ok(p, "Saved region '" + id + "' (" + total + " blocks).");
                return false;
              }

              public void stopped() {
                jobs.remove(id);
              }
            });
    jobs.put(id, job);
  }

  public void restore(Player p, String id) {
    settings.require("regions");
    if (jobs.containsKey(id)) throw new IllegalArgumentException("Region has an active operation.");
    YamlConfiguration y = regions.get(id);
    if (y == null) throw new IllegalArgumentException("Region '" + id + "' does not exist.");
    World world = Bukkit.getWorld(y.getString("world", ""));
    if (world == null) throw new IllegalArgumentException("Region world is not loaded.");
    available(id, world);
    if (y.getInt("schema") != 1)
      throw new IllegalArgumentException("regions/" + id + ".yml: schema must be 1.");
    if (!(y.get("blocks") instanceof List<?> records) || records.isEmpty())
      throw new IllegalArgumentException(
          "regions/" + id + ".yml: blocks must be a non-empty list.");
    if (records.size() > settings.limit("region-blocks"))
      throw new IllegalArgumentException("Region exceeds configured block limit.");
    // Validate every record before the first mutation; both phases share the tick budget.
    List<PreparedBlock> parsed = new ArrayList<>(records.size());
    Map<String, BlockData> palette = new HashMap<>();
    UUID job =
        submit(
            p,
            id,
            world,
            new TickEngine.Job() {
              int cursor, checked;

              public boolean tick() {
                int budget = settings.file("config").getInt("limits.region-blocks-per-tick", 1024);
                while (budget-- > 0 && checked < records.size()) {
                  try {
                    if (!(records.get(checked) instanceof Map<?, ?> record))
                      throw new IllegalArgumentException("expected a block map");
                    parsed.add(prepare(world, record, palette));
                    checked++;
                  } catch (RuntimeException ex) {
                    throw new IllegalArgumentException(
                        "regions/" + id + ".yml: blocks[" + checked + "]: " + ex.getMessage(), ex);
                  }
                }
                if (checked < records.size()) return true;
                while (budget-- > 0 && cursor < records.size()) {
                  int index = cursor++;
                  PreparedBlock record = parsed.get(index);
                  requireLoaded(world, record.x, record.z);
                  Block block = world.getBlockAt(record.x, record.y, record.z);
                  block.setBlockData(record.data, false);
                  if (block.getState() instanceof Container container && record.inventory != null) {
                    container.getSnapshotInventory().setContents(record.inventory);
                    container.update(true, false);
                  }
                  if (block.getState() instanceof Sign sign) {
                    for (org.bukkit.block.sign.Side side : org.bukkit.block.sign.Side.values())
                      if (record.lines.get(side) != null)
                        for (int i = 0; i < record.lines.get(side).size(); i++)
                          sign.getSide(side).line(i, record.lines.get(side).get(i));
                    sign.update(true, false);
                  }
                }
                if (cursor < records.size()) return true;
                messages.ok(p, "Restored region '" + id + "'.");
                return false;
              }

              public void stopped() {
                jobs.remove(id);
              }
            });
    jobs.put(id, job);
  }

  private record PreparedBlock(
      int x,
      int y,
      int z,
      BlockData data,
      ItemStack[] inventory,
      Map<org.bukkit.block.sign.Side, List<net.kyori.adventure.text.Component>> lines) {}

  private PreparedBlock prepare(World world, Map<?, ?> record, Map<String, BlockData> palette) {
    int x = integer(record, "x"), y = integer(record, "y"), z = integer(record, "z");
    if (y < world.getMinHeight() || y >= world.getMaxHeight())
      throw new IllegalArgumentException("y = " + y + "; outside world build height");
    requireLoaded(world, x, z);
    BlockData data =
        palette.computeIfAbsent(String.valueOf(record.get("data")), Bukkit::createBlockData);
    ItemStack[] inventory = null;
    Map<org.bukkit.block.sign.Side, List<net.kyori.adventure.text.Component>> lines =
        new EnumMap<>(org.bukkit.block.sign.Side.class);
    if (record.containsKey("inventory")) {
      if (!(data.createBlockState() instanceof Container container)
          || !(record.get("inventory") instanceof List<?> contents)
          || contents.size() != container.getSnapshotInventory().getSize())
        throw new IllegalArgumentException("inventory must match this container's slot count");
      inventory = new ItemStack[contents.size()];
      for (int i = 0; i < contents.size(); i++) {
        if (!(contents.get(i) instanceof String value) || value.length() > 1048576)
          throw new IllegalArgumentException("inventory[" + i + "] must be a bounded Base64 item");
        if (!value.isBlank())
          inventory[i] = ItemStack.deserializeBytes(Base64.getDecoder().decode(value));
      }
    }
    for (org.bukkit.block.sign.Side side : org.bukkit.block.sign.Side.values()) {
      String key = side.name().toLowerCase(Locale.ROOT);
      if (!record.containsKey(key)) continue;
      if (!(data.createBlockState() instanceof Sign)
          || !(record.get(key) instanceof List<?> text)
          || text.size() != 4)
        throw new IllegalArgumentException(key + " must contain four sign lines");
      List<net.kyori.adventure.text.Component> rendered = new ArrayList<>();
      for (Object value : text) {
        if (!(value instanceof String line) || line.length() > 8192)
          throw new IllegalArgumentException(key + " contains an invalid sign line");
        rendered.add(Messages.rich(line));
      }
      lines.put(side, List.copyOf(rendered));
    }
    return new PreparedBlock(x, y, z, data, inventory, lines);
  }

  private static void requireLoaded(World world, int x, int z) {
    if (!world.isChunkLoaded(x >> 4, z >> 4))
      throw new IllegalArgumentException(
          "Chunk " + (x >> 4) + "," + (z >> 4) + " is not loaded. Load it before restoring.");
  }

  private void available(String id, World world) {
    if (jobs.containsKey(id) || busyWorlds.contains(world.getUID()))
      throw new IllegalArgumentException(
          "A region job already owns this region or world. Wait or cancel it first.");
    if (jobs.size() >= settings.limit("active-scenes"))
      throw new IllegalArgumentException(
          "The configured concurrent region job limit has been reached.");
  }

  private UUID submit(Player player, String id, World world, TickEngine.Job work) {
    UUID job =
        ticks.add(
            new TickEngine.Job() {
              public boolean tick() {
                if (!player.isOnline()
                    || !settings.enabled("regions")
                    || Bukkit.getWorld(world.getUID()) != world) return false;
                try {
                  return work.tick();
                } catch (RuntimeException ex) {
                  messages.error(player, "Region '" + id + "' stopped: " + ex.getMessage());
                  throw ex;
                }
              }

              public void stopped() {
                jobs.remove(id);
                busyWorlds.remove(world.getUID());
                work.stopped();
              }
            });
    busyWorlds.add(world.getUID());
    return job;
  }

  private static int integer(Map<?, ?> map, String key) {
    return Checks.integer(String.valueOf(map.get(key)), -30000000, 30000000);
  }

  public void cancel(String id) {
    UUID job = jobs.get(id);
    if (job == null) throw new IllegalArgumentException("No active region operation.");
    ticks.cancel(job);
  }

  public void delete(String id) {
    if (jobs.containsKey(id))
      throw new IllegalArgumentException("Cancel the active region job first.");
    if (!regions.containsKey(id))
      throw new IllegalArgumentException("Region '" + id + "' does not exist.");
    store.delete("regions", id);
    regions.remove(id);
  }

  @EventHandler(ignoreCancelled = true)
  public void select(PlayerInteractEvent e) {
    if (e.getClickedBlock() == null
        || !items.isTool(e.getItem(), "regionwand")
        || !e.getPlayer().hasPermission("easyscripting.world.edit")) return;
    if (!settings.enabled("regions")) return;
    e.setCancelled(true);
    int index = e.getAction().isLeftClick() ? 0 : 1;
    point(e.getPlayer(), index, e.getClickedBlock().getLocation());
    messages.ok(e.getPlayer(), "Selected corner " + (index + 1));
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    selections.remove(e.getPlayer().getUniqueId());
  }

  @Override
  public void close() {
    for (UUID job : List.copyOf(jobs.values())) ticks.cancel(job);
    selections.clear();
  }
}
