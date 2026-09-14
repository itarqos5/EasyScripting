package dev.easyscripting.gui;

import dev.easyscripting.actors.ActorService;
import dev.easyscripting.config.*;
import dev.easyscripting.items.KitService;
import dev.easyscripting.players.PlayerService;
import dev.easyscripting.recording.ActingService;
import dev.easyscripting.recording.RecordingService;
import dev.easyscripting.scenes.SceneService;
import dev.easyscripting.utilities.*;
import dev.easyscripting.world.WarpService;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class MenuService implements Listener, AutoCloseable {
  private final JavaPlugin plugin;
  private final Settings settings;
  private final Messages messages;
  private final Access access;
  private final SceneService scenes;
  private final ActorService actors;
  private final PlayerService players;
  private final KitService kits;
  private final WarpService warps;
  private final RecordingService recordings;
  private final ActingService acting;
  private final TeamService teams;
  private final VillagerService villagers;
  private final Map<UUID, Pending> inputs = new ConcurrentHashMap<>();
  private final Set<UUID> viewers = new HashSet<>();

  private record Pending(String prefix, long expires) {}

  public MenuService(
      JavaPlugin plugin,
      Settings settings,
      Messages messages,
      Access access,
      SceneService scenes,
      ActorService actors,
      PlayerService players,
      KitService kits,
      WarpService warps,
      RecordingService recordings,
      ActingService acting,
      TeamService teams,
      VillagerService villagers) {
    this.plugin = plugin;
    this.settings = settings;
    this.messages = messages;
    this.access = access;
    this.scenes = scenes;
    this.actors = actors;
    this.players = players;
    this.kits = kits;
    this.warps = warps;
    this.recordings = recordings;
    this.acting = acting;
    this.teams = teams;
    this.villagers = villagers;
    validate();
  }

  public void validate() {
    validate(settings.file("guis"));
  }

  public static void validate(org.bukkit.configuration.file.YamlConfiguration y) {
    GuiSchema.validate(y, PlayerService.FLAGS.size(), MenuService::material);
  }

  private List<Integer> slots() {
    return settings.file("guis").getIntegerList("layout.content-slots");
  }

  private String label(String key) {
    String fallback =
        switch (key) {
          case "actor-randomize" -> "<aqua>Randomize name & skin";
          case "actor-info" -> "<aqua>Show ID, name & skin";
          default -> key;
        };
    return settings.file("guis").getString("dynamic." + key, fallback);
  }

  private int layout(String key) {
    return settings.file("guis").getInt("layout." + key);
  }

  private MenuHolder base(Player p, String menu, String name, int page) {
    access.require(p, "use");
    String title =
        settings
            .file("guis")
            .getString("menus." + menu + ".title", "<dark_gray>EasyScripting")
            .replace("{name}", name)
            .replace("{page}", String.valueOf(page + 1));
    MenuHolder holder = new MenuHolder(p, layout("rows") * 9, Messages.rich(title));
    ItemStack filler =
        icon(
            material(settings.file("guis").getString("layout.filler", "GRAY_STAINED_GLASS_PANE")),
            settings.file("guis").getString("layout.filler-name", " "),
            List.of());
    for (int i = 0; i < holder.inventory.getSize(); i++) holder.inventory.setItem(i, filler);
    button(
        holder,
        layout("back-slot"),
        Material.ARROW,
        settings.file("guis").getString("layout.back-name", "Back"),
        c -> open(p, "main", 0));
    return holder;
  }

  private static Material material(String name) {
    Material m = Material.matchMaterial(name);
    if (m == null || !m.isItem() || m.isAir())
      throw new IllegalArgumentException("guis.yml: invalid icon material '" + name + "'.");
    return m;
  }

  private static ItemStack icon(Material material, String name, List<String> lore) {
    ItemStack item = new ItemStack(material);
    var meta = item.getItemMeta();
    meta.displayName(Messages.rich(name).decoration(TextDecoration.ITALIC, false));
    meta.lore(
        lore.stream().map(s -> Messages.rich(s).decoration(TextDecoration.ITALIC, false)).toList());
    item.setItemMeta(meta);
    return item;
  }

  private void button(
      MenuHolder h, int slot, Material type, String name, Consumer<ClickType> action) {
    h.inventory.setItem(slot, icon(type, name, List.of()));
    h.actions.put(slot, action);
  }

  private int controlSlot(String key, int fallback) {
    return settings.file("guis").getInt("dynamic.controls." + key + ".slot", fallback);
  }

  private void control(
      MenuHolder h, String key, int slot, Material material, Consumer<ClickType> action) {
    String path = "dynamic.controls." + key;
    int configuredSlot = controlSlot(key, slot);
    h.inventory.setItem(
        configuredSlot,
        icon(
            material(settings.file("guis").getString(path + ".material", material.name())),
            label(key),
            settings.file("guis").getStringList(path + ".lore")));
    h.actions.put(configuredSlot, action);
  }

  private void show(Player p, MenuHolder h) {
    p.openInventory(h.inventory);
    viewers.add(p.getUniqueId());
  }

  private void command(Player p, String suffix) {
    p.performCommand("es " + suffix);
  }

  public void open(Player p, String menu, int page) {
    switch (menu) {
      case "scenes" -> {
        access.require(p, "scene.play");
        listing(
            p,
            menu,
            scenes.ids(),
            page,
            Material.WRITABLE_BOOK,
            id -> timeline(p, id, 0),
            id -> "scene delete " + id,
            () -> prompt(p, "scene create", "Enter the new scene identifier."));
      }
      case "actors" -> {
        access.require(p, "actor");
        listing(
            p,
            menu,
            actors.ids(),
            page,
            Material.PLAYER_HEAD,
            id -> actor(p, id),
            id -> "actor delete " + id,
            () -> prompt(p, "actor create", "Enter an actor identifier and optional entity type."));
      }
      case "kits" -> {
        access.require(p, "kit");
        listing(
            p,
            menu,
            kits.ids(),
            page,
            Material.CHEST,
            id -> command(p, "kit apply " + id),
            id -> "kit delete " + id,
            () -> prompt(p, "kit save", "Enter the kit identifier to save your inventory."));
      }
      case "warps" -> {
        access.require(p, "warp");
        listing(
            p,
            menu,
            warps.ids(p),
            page,
            Material.ENDER_PEARL,
            id -> command(p, "warp go " + id),
            id -> "warp delete " + id,
            () -> prompt(p, "warp save", "Enter a warp identifier for your current location."));
      }
      case "recording" -> {
        access.require(p, "record");
        listing(
            p,
            menu,
            recordings.ids(),
            page,
            Material.CLOCK,
            id ->
                prompt(
                    p,
                    "record play " + id,
                    "Enter the actor identifier, optional loop on/off, and reverse on/off."),
            id -> "record delete " + id,
            () ->
                prompt(
                    p,
                    "record start",
                    "Enter a movement recording identifier; use /es record stop to finish."));
      }
      case "teams" -> {
        access.require(p, "team");
        listing(
            p,
            menu,
            teams.ids(),
            page,
            Material.WHITE_BANNER,
            id ->
                prompt(
                    p,
                    "team set " + id,
                    "Enter a setting and value, such as color aqua or glow on."),
            id -> "team delete " + id,
            () -> prompt(p, "team create", "Enter a team identifier, up to 12 characters."));
      }
      case "villagers" -> {
        access.require(p, "villager");
        listing(
            p,
            menu,
            villagers.ids(),
            page,
            Material.EMERALD,
            id -> command(p, "villager spawn " + id),
            id -> "villager delete " + id,
            () -> prompt(p, "villager create", "Enter a villager template identifier."));
      }
      case "features" -> features(p, page);
      case "players" -> playerControls(p);
      case "permissions" -> permissions(p, page);
      default -> configured(p, menu);
    }
  }

  private void configured(Player p, String menu) {
    if (!List.of("main", "item", "production", "world", "effects").contains(menu))
      throw new IllegalArgumentException("Unknown menu '" + menu + "'.");
    MenuHolder h = base(p, menu, "", 0);
    ConfigurationSection buttons =
        settings.file("guis").getConfigurationSection("menus." + menu + ".buttons");
    if (buttons != null)
      for (String id : buttons.getKeys(false)) {
        ConfigurationSection b = Objects.requireNonNull(buttons.getConfigurationSection(id));
        int slot = b.getInt("slot");
        String action = b.getString("action", "");
        h.inventory.setItem(
            slot,
            icon(
                material(b.getString("material", "STONE")),
                b.getString("name", id),
                b.getStringList("lore")));
        h.actions.put(
            slot,
            click -> {
              if (action.startsWith("menu ")) open(p, action.substring(5), 0);
              else if (action.startsWith("command ")) command(p, action.substring(8));
              else if (action.startsWith("input "))
                prompt(p, action.substring(6), b.getString("prompt", "Enter a value."));
            });
      }
    show(p, h);
  }

  private void listing(
      Player p,
      String menu,
      List<String> ids,
      int requested,
      Material icon,
      Consumer<String> select,
      Function<String, String> delete,
      Runnable create) {
    List<Integer> slots = slots();
    int page = Math.max(0, Math.min(requested, Math.max(0, (ids.size() - 1) / slots.size())));
    MenuHolder h = base(p, menu, "", page);
    for (int i = 0; i < slots.size() && page * slots.size() + i < ids.size(); i++) {
      String id = ids.get(page * slots.size() + i);
      int slot = slots.get(i);
      List<String> lore =
          settings.file("guis").getStringList("layout.entry-lore").stream()
              .map(line -> line.replace("{detail}", menu.equals("scenes") ? scenes.status(id) : id))
              .toList();
      h.inventory.setItem(
          slot,
          icon(
              icon,
              settings.file("guis").getString("layout.entry-name", "{name}").replace("{name}", id),
              lore));
      h.actions.put(
          slot,
          click -> {
            if (click == ClickType.SHIFT_RIGHT) confirm(p, () -> command(p, delete.apply(id)));
            else if (menu.equals("kits") && click == ClickType.RIGHT) kitEditor(p, id);
            else select.accept(id);
          });
    }
    button(
        h,
        settings.file("guis").getInt("dynamic.create-slot"),
        Material.LIME_DYE,
        label("create-name"),
        c -> create.run());
    navigation(p, h, menu, page, ids.size());
    show(p, h);
  }

  private void navigation(Player p, MenuHolder h, String menu, int page, int total) {
    if (page > 0)
      button(
          h,
          layout("previous-slot"),
          Material.ARROW,
          settings.file("guis").getString("layout.previous-name", "Previous"),
          c -> open(p, menu, page - 1));
    if ((page + 1) * slots().size() < total)
      button(
          h,
          layout("next-slot"),
          Material.ARROW,
          settings.file("guis").getString("layout.next-name", "Next"),
          c -> open(p, menu, page + 1));
  }

  public void confirm(Player p, Runnable action) {
    MenuHolder h = base(p, "confirm", "", 0);
    button(
        h,
        settings.file("guis").getInt("dynamic.confirm-slot"),
        Material.RED_CONCRETE,
        label("confirm-name"),
        c -> {
          p.closeInventory();
          action.run();
        });
    button(
        h,
        settings.file("guis").getInt("dynamic.cancel-slot"),
        Material.LIME_CONCRETE,
        label("cancel-name"),
        c -> open(p, "main", 0));
    show(p, h);
  }

  private void features(Player p, int page) {
    access.require(p, "admin");
    MenuHolder h = base(p, "features", "", page);
    List<Integer> slots = slots();
    for (int i = 0; i < slots.size() && page * slots.size() + i < Settings.FEATURES.size(); i++) {
      String key = Settings.FEATURES.get(page * slots.size() + i);
      boolean enabled = settings.enabled(key);
      button(
          h,
          slots.get(i),
          enabled ? Material.LIME_DYE : Material.GRAY_DYE,
          "<aqua>"
              + key
              + " <gray>· "
              + settings.file("guis").getString("layout." + (enabled ? "enabled" : "disabled")),
          c -> {
            access.require(p, "admin");
            settings.toggle(key);
            features(p, page);
          });
    }
    navigation(p, h, "features", page, Settings.FEATURES.size());
    show(p, h);
  }

  private void permissions(Player p, int page) {
    access.require(p, "admin");
    MenuHolder h = base(p, "permissions", "", page);
    List<Integer> slots = slots();
    for (int i = 0; i < slots.size() && page * slots.size() + i < Access.EDITABLE.size(); i++) {
      String key = Access.EDITABLE.get(page * slots.size() + i);
      button(
          h,
          slots.get(i),
          Material.TRIPWIRE_HOOK,
          "<aqua>" + key,
          c -> prompt(p, "permissions " + key, "Enter everyone or a custom permission node."));
    }
    navigation(p, h, "permissions", page, Access.EDITABLE.size());
    show(p, h);
  }

  private void playerControls(Player p) {
    access.require(p, "player");
    MenuHolder h = base(p, "players", "", 0);
    List<Integer> slots = slots();
    for (int i = 0; i < PlayerService.FLAGS.size(); i++) {
      String flag = PlayerService.FLAGS.get(i);
      button(
          h,
          slots.get(i),
          players.flag(p.getUniqueId(), flag) ? Material.LIME_DYE : Material.GRAY_DYE,
          "<aqua>" + flag,
          c -> {
            command(p, "player " + flag + " " + !players.flag(p.getUniqueId(), flag));
            playerControls(p);
          });
    }
    control(h, "snapshot", 38, Material.CLOCK, c -> command(p, "take snapshot"));
    control(h, "reset", 39, Material.RECOVERY_COMPASS, c -> command(p, "take reset"));
    control(h, "inventory-save", 40, Material.DIAMOND_ORE, c -> command(p, "inventory save"));
    show(p, h);
  }

  public void actor(Player p, String id) {
    actor(p, id, "overview");
  }

  public void actor(Player p, String id, String section) {
    access.require(p, "actor");
    var definition = actors.get(id).definition;
    if (!List.of("overview", "appearance", "movement", "acting", "combat").contains(section))
      throw new IllegalArgumentException(
          "Actor section must be overview, appearance, movement, acting or combat.");
    MenuHolder h = base(p, section.equals("overview") ? "actor" : "actor-" + section, id, 0);
    button(
        h,
        layout("back-slot"),
        Material.ARROW,
        settings.file("guis").getString("dynamic.actor-back", "<gray>Back"),
        c -> {
          if (section.equals("overview")) open(p, "actors", 0);
          else actor(p, id);
        });
    switch (section) {
      case "overview" -> {
        control(
            h,
            "actor-section-appearance",
            10,
            Material.PLAYER_HEAD,
            c -> actor(p, id, "appearance"));
        control(h, "actor-section-movement", 12, Material.COMPASS, c -> actor(p, id, "movement"));
        control(h, "actor-section-acting", 14, Material.ARMOR_STAND, c -> actor(p, id, "acting"));
        control(h, "actor-section-combat", 16, Material.IRON_SWORD, c -> actor(p, id, "combat"));
        control(h, "actor-info", 21, Material.BOOK, c -> command(p, "actor info " + id));
      }
      case "movement" -> {
        control(h, "actor-here", 10, Material.ENDER_PEARL, c -> command(p, "actor here " + id));
        control(h, "actor-walk", 11, Material.LEATHER_BOOTS, c -> command(p, "actor move " + id));
        control(h, "actor-hide", 12, Material.GRAY_DYE, c -> command(p, "actor hide " + id));
        control(h, "actor-show", 13, Material.LIME_DYE, c -> command(p, "actor respawn " + id));
        control(
            h,
            "actor-setting",
            19,
            Material.LEVER,
            c ->
                prompt(
                    p,
                    "actor set " + id,
                    "Enter a setting and value, such as look on, wander off, or group guards."));
      }
      case "appearance" -> {
        control(
            h,
            "actor-kit",
            14,
            Material.CHEST,
            c -> prompt(p, "actor kit " + id, "Enter a kit identifier."));
        control(
            h,
            "actor-skin",
            15,
            Material.PLAYER_HEAD,
            c -> prompt(p, "actor set " + id + " skin", "Enter a Minecraft skin owner name."));
        control(
            h,
            "actor-name",
            16,
            Material.NAME_TAG,
            c -> prompt(p, "actor set " + id + " name", "Enter the actor name."));
        control(
            h, "actor-randomize", 20, Material.ENDER_EYE, c -> command(p, "actor randomize " + id));
        control(h, "actor-info", 21, Material.BOOK, c -> command(p, "actor info " + id));
      }
      case "combat" -> {
        actorToggle(p, h, id, "hittable", definition.hittable, 10);
        actorToggle(p, h, id, "immortal", definition.immortal, 12);
        control(
            h,
            "actor-combat-respawn",
            16,
            Material.TOTEM_OF_UNDYING,
            c -> {
              command(p, "actor respawn " + id);
              actor(p, id, "combat");
            });
      }
      case "acting" -> {
        control(
            h,
            "actor-act",
            10,
            Material.ARMOR_STAND,
            c -> {
              p.closeInventory();
              command(p, "actor act " + id);
            });
        control(
            h,
            "actor-finish",
            12,
            Material.EMERALD,
            c -> {
              p.closeInventory();
              command(p, "actor finish");
            });
        control(
            h,
            "actor-cancel",
            14,
            Material.BARRIER,
            c -> {
              p.closeInventory();
              command(p, "actor cancel");
            });
        control(
            h,
            "actor-play",
            16,
            Material.LIME_CONCRETE,
            c -> {
              p.closeInventory();
              command(p, "actor play " + id);
            });
        control(h, "actor-stop", 23, Material.RED_CONCRETE, c -> command(p, "actor stop " + id));
        control(
            h,
            "actor-recording",
            25,
            Material.WRITABLE_BOOK,
            c ->
                prompt(
                    p,
                    "actor recording " + id,
                    "Enter an existing recording ID. Current: " + definition.recording));
        for (var mode : dev.easyscripting.recording.PlaybackMode.values()) {
          String key = mode.name().toLowerCase(Locale.ROOT);
          control(
              h,
              "actor-mode-" + key,
              19 + mode.ordinal(),
              Material.REPEATER,
              c -> {
                command(p, "actor mode " + id + " " + key);
                actor(p, id, "acting");
              });
        }
      }
      default -> throw new IllegalStateException("Unvalidated actor section");
    }
    for (int slot : h.actions.keySet()) {
      ItemStack item = h.inventory.getItem(slot);
      if (item == null) continue;
      var meta = item.getItemMeta();
      Map<String, String> values =
          Map.of(
              "{mode}",
              definition.playbackMode.name().toLowerCase(Locale.ROOT),
              "{recording}",
              definition.recording.isBlank() ? "None yet" : definition.recording,
              "{acting}",
              acting.actor(p).orElse("None"));
      java.util.function.UnaryOperator<net.kyori.adventure.text.Component> replace =
          component -> {
            var result = component;
            for (var entry : values.entrySet())
              result =
                  result.replaceText(
                      b -> b.matchLiteral(entry.getKey()).replacement(entry.getValue()));
            return result;
          };
      if (meta.hasDisplayName()) meta.displayName(replace.apply(meta.displayName()));
      if (meta.hasLore()) meta.lore(meta.lore().stream().map(replace).toList());
      item.setItemMeta(meta);
    }
    show(p, h);
  }

  private void actorToggle(
      Player player, MenuHolder holder, String id, String setting, boolean enabled, int slot) {
    String key = "actor-" + setting;
    control(
        holder,
        key,
        slot,
        enabled ? Material.LIME_DYE : Material.GRAY_DYE,
        c -> {
          command(player, "actor set " + id + " " + setting + " " + !enabled);
          actor(player, id, "combat");
        });
    ItemStack item = holder.inventory.getItem(controlSlot(key, slot));
    var meta = item.getItemMeta();
    meta.displayName(
        Messages.rich(
            label(key)
                .replace(
                    "{state}",
                    settings
                        .file("guis")
                        .getString("layout." + (enabled ? "enabled" : "disabled")))));
    item.setItemMeta(meta);
  }

  public void timeline(Player p, String id, int requested) {
    access.require(p, "scene.play");
    var scene = scenes.get(id);
    List<Integer> slots = slots().subList(0, Math.max(1, slots().size() - 7));
    int page =
        Math.max(0, Math.min(requested, Math.max(0, (scene.actions().size() - 1) / slots.size())));
    MenuHolder h = base(p, "timeline", id, page);
    for (int i = 0; i < slots.size() && page * slots.size() + i < scene.actions().size(); i++) {
      int index = page * slots.size() + i;
      var action = scene.actions().get(index);
      button(
          h,
          slots.get(i),
          Material.PAPER,
          "<aqua>" + action.tick() + "t <white>" + action.type() + " <gray>" + action.target(),
          c -> confirm(p, () -> command(p, "scene remove " + id + " " + (index + 1))));
    }
    control(h, "timeline-play", 37, Material.LIME_CONCRETE, c -> command(p, "scene play " + id));
    control(
        h,
        "timeline-pause",
        38,
        Material.YELLOW_CONCRETE,
        c ->
            command(
                p,
                "scene " + (scenes.status(id).startsWith("PAUSED") ? "resume " : "pause ") + id));
    control(h, "timeline-stop", 39, Material.RED_CONCRETE, c -> command(p, "scene stop " + id));
    control(
        h,
        "timeline-add",
        40,
        Material.WRITABLE_BOOK,
        c ->
            prompt(
                p,
                "scene add " + id,
                "Enter tick, type, target and optional key=value;key=value arguments."));
    control(
        h,
        "timeline-bind",
        41,
        Material.LEAD,
        c ->
            prompt(
                p, "scene bind " + id, "Enter binding name followed by actor:id or player:name."));
    if (page > 0)
      button(
          h,
          layout("previous-slot"),
          Material.ARROW,
          "<aqua>Previous",
          c -> timeline(p, id, page - 1));
    if ((page + 1) * slots.size() < scene.actions().size())
      button(h, layout("next-slot"), Material.ARROW, "<aqua>Next", c -> timeline(p, id, page + 1));
    show(p, h);
  }

  public void viewInventory(Player viewer, Player target, boolean ender) {
    access.require(viewer, "inventory");
    MenuHolder h = base(viewer, "inventory", target.getName(), 0);
    h.inventory.clear();
    ItemStack[] contents = (ender ? target.getEnderChest() : target.getInventory()).getContents();
    for (int i = 0; i < contents.length && i < 45; i++)
      h.inventory.setItem(i, contents[i] == null ? null : contents[i].clone());
    show(viewer, h);
  }

  public void kitEditor(Player p, String id) {
    access.require(p, "kit.edit");
    MenuHolder h = base(p, "kit-editor", id, 0);
    h.kitId = id;
    h.inventory.clear();
    ItemStack[] contents = kits.contents(id);
    for (int i = 0; i < contents.length; i++) h.inventory.setItem(i, contents[i]);
    control(
        h,
        "kit-save",
        49,
        Material.EMERALD,
        c -> {
          access.require(p, "kit.edit");
          kits.save(id, Arrays.copyOf(h.inventory.getContents(), 41));
          open(p, "kits", 0);
        });
    messages.send(
        p,
        "info",
        PlainTextComponentSerializer.plainText().serialize(Messages.rich(label("kit-help"))));
    show(p, h);
  }

  public void prompt(Player p, String prefix, String question) {
    p.closeInventory();
    inputs.put(p.getUniqueId(), new Pending(prefix, System.currentTimeMillis() + 60000));
    messages.send(p, "input", question);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void chat(AsyncChatEvent e) {
    Pending pending = inputs.remove(e.getPlayer().getUniqueId());
    if (pending == null) return;
    e.setCancelled(true);
    String text = PlainTextComponentSerializer.plainText().serialize(e.message()).strip();
    if (pending.expires < System.currentTimeMillis() || text.equalsIgnoreCase("cancel")) return;
    if (text.length() > 512 || text.contains("\n") || text.contains("\r")) return;
    if (!plugin.isEnabled()) return;
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (e.getPlayer().isOnline()) command(e.getPlayer(), pending.prefix + " " + text);
            });
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void click(InventoryClickEvent e) {
    if (!(e.getView().getTopInventory().getHolder() instanceof MenuHolder h)) return;
    e.setCancelled(true);
    if (!(e.getWhoClicked() instanceof Player p) || !h.owner.equals(p.getUniqueId())) return;
    int slot = e.getRawSlot();
    ClickType click = e.getClick();
    ItemStack clicked = e.getCurrentItem() == null ? null : e.getCurrentItem().clone();
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!p.isOnline() || p.getOpenInventory().getTopInventory() != h.inventory) return;
              try {
                if (h.kitId != null && slot >= h.inventory.getSize()) {
                  access.require(p, "kit.edit");
                  h.selected = clicked;
                  return;
                }
                if (h.kitId != null && slot >= 0 && slot < 41) {
                  access.require(p, "kit.edit");
                  h.inventory.setItem(
                      slot,
                      click.isRightClick() ? null : h.selected == null ? null : h.selected.clone());
                  return;
                }
                Consumer<ClickType> action = h.actions.get(slot);
                if (action != null) action.accept(click);
              } catch (IllegalArgumentException | IllegalStateException ex) {
                messages.error(p, ex.getMessage());
              }
            });
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void drag(InventoryDragEvent e) {
    if (e.getView().getTopInventory().getHolder() instanceof MenuHolder) e.setCancelled(true);
  }

  @EventHandler
  public void close(InventoryCloseEvent e) {
    if (e.getInventory().getHolder() instanceof MenuHolder)
      viewers.remove(e.getPlayer().getUniqueId());
  }

  @EventHandler
  public void quit(PlayerQuitEvent e) {
    inputs.remove(e.getPlayer().getUniqueId());
    viewers.remove(e.getPlayer().getUniqueId());
  }

  @Override
  public void close() {
    for (UUID id : List.copyOf(viewers)) {
      Player p = Bukkit.getPlayer(id);
      if (p != null && p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder)
        p.closeInventory();
    }
    viewers.clear();
    inputs.clear();
  }
}
