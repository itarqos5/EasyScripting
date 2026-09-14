package dev.easyscripting.gui;

import dev.easyscripting.actors.ActorService;
import dev.easyscripting.config.*;
import dev.easyscripting.items.KitAccess;
import dev.easyscripting.items.KitService;
import dev.easyscripting.players.IdentityService;
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
  private dev.easyscripting.integration.KitImports kitImports;

  public void kitImports(dev.easyscripting.integration.KitImports imports) {
    this.kitImports = imports;
  }

  private record Pending(String prefix, long expires, Runnable back) {}

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
    for (int i = 0; i < holder.inventory.getSize(); i++)
      if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) holder.inventory.setItem(i, filler);
    String path = "menus." + menu;
    holder.inventory.setItem(
        layout("header-slot"),
        icon(
            material(settings.file("guis").getString(path + ".material", "BOOK")),
            settings
                .file("guis")
                .getString(path + ".heading", "<white><bold>" + name)
                .replace("{name}", name)
                .replace("{page}", String.valueOf(page + 1)),
            settings.file("guis").getStringList(path + ".description")));
    button(
        holder,
        layout("back-slot"),
        Material.ARROW,
        settings.file("guis").getString("layout.back-name", "Back"),
        c -> open(p, "main", 0));
    button(
        holder,
        layout("home-slot"),
        Material.NETHER_STAR,
        settings.file("guis").getString("layout.home-name"),
        c -> open(p, "main", 0));
    button(
        holder,
        layout("close-slot"),
        Material.BARRIER,
        settings.file("guis").getString("layout.close-name"),
        c -> p.closeInventory());
    holder.inventory.setItem(
        layout("help-slot"),
        icon(
            Material.BOOK,
            settings.file("guis").getString("layout.help-name"),
            settings.file("guis").getStringList(path + ".description")));
    holder.refresh = () -> open(p, menu, page);
    String parent = settings.file("guis").getString(path + ".parent", "main");
    holder.actions.put(layout("back-slot"), c -> open(p, parent, 0));
    if (menu.equals("main")) {
      holder.actions.remove(layout("back-slot"));
      holder.inventory.setItem(layout("back-slot"), filler);
    }
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
    button(h, slot, type, name, settings.file("guis").getStringList("layout.click-lore"), action);
  }

  private void button(
      MenuHolder h,
      int slot,
      Material type,
      String name,
      List<String> lore,
      Consumer<ClickType> action) {
    h.inventory.setItem(slot, icon(type, name, lore));
    h.actions.put(slot, action);
  }

  private void selected(MenuHolder h, int slot, boolean selected) {
    ItemStack item = h.inventory.getItem(slot);
    if (item == null) return;
    var meta = item.getItemMeta();
    meta.setEnchantmentGlintOverride(selected);
    item.setItemMeta(meta);
  }

  private void disabled(MenuHolder h, String key, String reason) {
    int slot = controlSlot(key, -1);
    if (slot < 0 || h.inventory.getItem(slot) == null) return;
    ItemStack item = h.inventory.getItem(slot);
    var meta = item.getItemMeta();
    var lore =
        new ArrayList<>(
            meta.hasLore() ? meta.lore() : List.<net.kyori.adventure.text.Component>of());
    lore.add(Messages.rich("<gray>" + reason).decoration(TextDecoration.ITALIC, false));
    meta.lore(lore);
    meta.setEnchantmentGlintOverride(false);
    item.setItemMeta(meta);
    h.actions.remove(slot);
  }

  private String state(boolean enabled) {
    return settings.file("guis").getString("layout." + (enabled ? "enabled" : "disabled"));
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
            kits.ids(p),
            page,
            Material.CHEST,
            id -> kitDetails(p, id),
            id -> "kit delete " + id,
            () ->
                prompt(
                    p,
                    "kit create",
                    "Enter a new kit ID. Then import your inventory or edit its slots."));
        if (p.isOp() && p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder h)
          control(h, "kits-import", 40, Material.HOPPER, c -> kitImportMenu(p));
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
        settings.require("recording");
        picker(
            p,
            "recording",
            "NPC performances",
            recordings.ids(),
            page,
            Material.CLOCK,
            id ->
                picker(
                    p,
                    "Choose an NPC",
                    actors.ids(),
                    0,
                    Material.PLAYER_HEAD,
                    actorId -> {
                      command(p, "actor recording " + actorId + " " + id);
                      actor(p, actorId, "acting");
                    },
                    () -> open(p, "recording", page)),
            () -> open(p, "main", 0),
            h -> {});
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
    if (!List.of(
            "main",
            "item",
            "production",
            "world",
            "effects",
            "wardrobe",
            "stage",
            "organization",
            "settings")
        .contains(menu)) throw new IllegalArgumentException("Unknown menu '" + menu + "'.");
    MenuHolder h = base(p, menu, "", 0);
    ConfigurationSection buttons =
        settings.file("guis").getConfigurationSection("menus." + menu + ".buttons");
    if (buttons != null)
      for (String id : buttons.getKeys(false)) {
        ConfigurationSection b = Objects.requireNonNull(buttons.getConfigurationSection(id));
        int slot = b.getInt("slot");
        String action = b.getString("action", "");
        String permission = b.getString("permission", "use");
        boolean allowed = access.allowed(p, "easyscripting." + permission);
        List<String> lore = new ArrayList<>(b.getStringList("lore"));
        if (!allowed) lore.add(settings.file("guis").getString("layout.locked-name"));
        h.inventory.setItem(
            slot, icon(material(b.getString("material", "STONE")), b.getString("name", id), lore));
        h.actions.put(
            slot,
            click -> {
              access.require(p, permission);
              if (action.startsWith("menu ")) open(p, action.substring(5), 0);
              else if (action.startsWith("command ")) {
                command(p, action.substring(8));
                configured(p, menu);
              } else if (action.startsWith("input "))
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
      String entryPath = "entries." + menu;
      String detail = menu.equals("scenes") ? scenes.status(id) : id;
      if (menu.equals("kits")) detail = kitAccessDescription(id);
      String display = id;
      if (menu.equals("actors")) {
        var a = actors.get(id);
        display = a.definition.name;
        detail =
            a.definition.type
                + " · "
                + (recordings.playing(id)
                    ? "Playing"
                    : a.entity().isPresent() ? "Ready" : "Hidden / dead");
      }
      String entryDetail = detail;
      List<String> lore =
          settings
              .file("guis")
              .getStringList(
                  entryPath + (menu.equals("kits") && !p.isOp() ? ".claim-lore" : ".lore"))
              .stream()
              .map(line -> line.replace("{detail}", entryDetail).replace("{id}", id))
              .toList();
      h.inventory.setItem(
          slot,
          icon(
              icon,
              settings
                  .file("guis")
                  .getString("layout.entry-name", "{name}")
                  .replace("{name}", display),
              lore));
      h.actions.put(
          slot,
          click -> {
            if (menu.equals("kits") && !p.isOp()) select.accept(id);
            else if (click == ClickType.SHIFT_RIGHT)
              confirm(
                  p,
                  id,
                  () -> {
                    command(p, delete.apply(id));
                    open(p, menu, page);
                  },
                  () -> open(p, menu, page));
            else if (menu.equals("kits") && click == ClickType.RIGHT) kitEditor(p, id);
            else select.accept(id);
          });
    }
    if (!menu.equals("kits") || p.isOp())
      button(
          h,
          settings.file("guis").getInt("dynamic.create-slot"),
          Material.LIME_DYE,
          settings.file("guis").getString("entries." + menu + ".create", label("create-name")),
          settings.file("guis").getStringList("dynamic.create-lore"),
          c -> create.run());
    if (ids.isEmpty())
      h.inventory.setItem(
          layout("empty-slot"),
          icon(
              Material.WRITABLE_BOOK,
              settings
                  .file("guis")
                  .getString("entries." + menu + ".empty", "<white>Nothing saved yet"),
              settings
                  .file("guis")
                  .getStringList(
                      menu.equals("kits") && !p.isOp()
                          ? "entries.kits.claim-empty-lore"
                          : "layout.empty-lore")));
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
    var previous = p.getOpenInventory().getTopInventory();
    Runnable back =
        previous.getHolder() instanceof MenuHolder old ? old.refresh : () -> open(p, "main", 0);
    confirm(p, "selected item", action, back);
  }

  private void confirm(Player p, String name, Runnable action, Runnable back) {
    confirm(p, "confirm", name, action, back);
  }

  private void confirm(Player p, String menu, String name, Runnable action, Runnable back) {
    MenuHolder h = base(p, menu, name, 0);
    h.refresh = back;
    h.actions.put(layout("back-slot"), c -> back.run());
    button(
        h,
        settings.file("guis").getInt("dynamic.confirm-slot"),
        Material.RED_CONCRETE,
        label(menu.equals("confirm") ? "confirm-name" : "confirm-import-name"),
        c -> {
          p.closeInventory();
          action.run();
        });
    button(
        h,
        settings.file("guis").getInt("dynamic.cancel-slot"),
        Material.LIME_CONCRETE,
        label(menu.equals("confirm") ? "cancel-name" : "cancel-import-name"),
        c -> back.run());
    show(p, h);
  }

  private void picker(
      Player p,
      String title,
      List<String> ids,
      int requested,
      Material material,
      Consumer<String> select,
      Runnable back) {
    picker(p, "picker", title, ids, requested, material, select, back, h -> {});
  }

  private void picker(
      Player p,
      String menu,
      String title,
      List<String> ids,
      int requested,
      Material material,
      Consumer<String> select,
      Runnable back,
      Consumer<MenuHolder> decorate) {
    int page = Math.max(0, Math.min(requested, Math.max(0, (ids.size() - 1) / slots().size())));
    MenuHolder h = base(p, menu, title, page);
    h.refresh = () -> picker(p, menu, title, ids, page, material, select, back, decorate);
    h.actions.put(layout("back-slot"), c -> back.run());
    for (int i = 0; i < slots().size() && page * slots().size() + i < ids.size(); i++) {
      String id = ids.get(page * slots().size() + i);
      button(
          h,
          slots().get(i),
          material,
          "<white>" + id,
          settings.file("guis").getStringList("layout.select-lore"),
          c -> select.accept(id));
    }
    if (ids.isEmpty())
      h.inventory.setItem(
          layout("empty-slot"),
          icon(
              Material.PAPER,
              settings.file("guis").getString("layout.picker-empty-name"),
              settings.file("guis").getStringList("layout.picker-empty-lore")));
    if (page > 0)
      h.actions.put(
          layout("previous-slot"),
          c -> picker(p, menu, title, ids, page - 1, material, select, back, decorate));
    if ((page + 1) * slots().size() < ids.size())
      h.actions.put(
          layout("next-slot"),
          c -> picker(p, menu, title, ids, page + 1, material, select, back, decorate));
    for (String key : List.of("previous", "next"))
      if (h.actions.containsKey(layout(key + "-slot")))
        h.inventory.setItem(
            layout(key + "-slot"),
            icon(
                Material.ARROW,
                settings.file("guis").getString("layout." + key + "-name"),
                List.of()));
    decorate.accept(h);
    show(p, h);
  }

  private void features(Player p, int page) {
    access.require(p, "admin");
    MenuHolder h = base(p, "features", "", page);
    h.actions.put(layout("back-slot"), c -> open(p, "settings", 0));
    List<Integer> slots = slots();
    for (int i = 0; i < slots.size() && page * slots.size() + i < Settings.FEATURES.size(); i++) {
      String key = Settings.FEATURES.get(page * slots.size() + i);
      boolean enabled = settings.enabled(key);
      button(
          h,
          slots.get(i),
          enabled ? Material.LIME_DYE : Material.GRAY_DYE,
          settings.file("guis").getString("features." + key + ".name", key)
              + " <gray>· "
              + state(enabled),
          settings.file("guis").getStringList("features." + key + ".lore"),
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
    h.actions.put(layout("back-slot"), c -> open(p, "settings", 0));
    List<Integer> slots = slots();
    for (int i = 0; i < slots.size() && page * slots.size() + i < Access.EDITABLE.size(); i++) {
      String key = Access.EDITABLE.get(page * slots.size() + i);
      button(
          h,
          slots.get(i),
          Material.TRIPWIRE_HOOK,
          settings.file("guis").getString("permissions." + key + ".name", "<white>" + key),
          List.of(
              "<gray>Current: <white>"
                  + settings
                      .file("permissions")
                      .getString("overrides." + key, "easyscripting." + key),
              "<gray>Click to change the permission node.",
              "<gray>Use everyone to allow all players."),
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
      boolean enabled = players.flag(p.getUniqueId(), flag);
      button(
          h,
          slots.get(i),
          enabled ? Material.LIME_DYE : Material.GRAY_DYE,
          settings.file("guis").getString("player-flags." + flag + ".name", flag)
              + " <gray>· "
              + state(enabled),
          settings.file("guis").getStringList("player-flags." + flag + ".lore"),
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
    var managed = actors.get(id);
    var definition = managed.definition;
    if (!List.of("overview", "appearance", "movement", "acting", "combat").contains(section))
      throw new IllegalArgumentException(
          "Actor section must be overview, appearance, movement, acting or combat.");
    MenuHolder h = base(p, section.equals("overview") ? "actor" : "actor-" + section, id, 0);
    h.refresh = () -> actor(p, id, section);
    for (String tab : List.of("overview", "appearance", "movement", "acting", "combat")) {
      String key = "actor-tab-" + tab;
      control(h, key, 0, Material.PAPER, c -> actor(p, id, tab));
      selected(h, controlSlot(key, 0), tab.equals(section));
    }
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
        control(
            h,
            "actor-delete",
            43,
            Material.BARRIER,
            c ->
                confirm(
                    p,
                    id,
                    () -> {
                      command(p, "actor delete " + id);
                      open(p, "actors", 0);
                    },
                    h.refresh));
      }
      case "movement" -> {
        control(
            h,
            "actor-here",
            11,
            Material.ENDER_PEARL,
            c -> {
              command(p, "actor here " + id);
              h.refresh.run();
            });
        control(
            h,
            "actor-walk",
            13,
            Material.LEATHER_BOOTS,
            c -> {
              command(p, "actor move " + id);
              p.closeInventory();
            });
        control(
            h,
            "actor-hide",
            20,
            Material.GRAY_DYE,
            c -> {
              command(p, "actor hide " + id);
              h.refresh.run();
            });
        control(
            h,
            "actor-show",
            24,
            Material.LIME_DYE,
            c -> {
              command(p, "actor show " + id);
              h.refresh.run();
            });
        control(
            h,
            "actor-respawn",
            15,
            Material.RECOVERY_COMPASS,
            c -> {
              command(p, "actor respawn " + id);
              h.refresh.run();
            });
        actorToggle(p, h, id, "look", definition.lookNearby, 29, section);
        actorToggle(p, h, id, "wander", definition.wander, 31, section);
        actorToggle(p, h, id, "collidable", definition.collidable, 33, section);
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
            c ->
                picker(
                    p,
                    "Choose a costume",
                    kits.ids(),
                    0,
                    Material.CHEST,
                    kit -> {
                      command(p, "actor kit " + id + " " + kit);
                      h.refresh.run();
                    },
                    h.refresh));
        if (!p.isOp()) disabled(h, "actor-kit", "Only operators can give kits to NPCs.");
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
            h,
            "actor-randomize",
            31,
            Material.ENDER_EYE,
            c -> {
              command(p, "actor randomize " + id);
              h.refresh.run();
            });
        actorToggle(p, h, id, "glow", definition.glowing, 29, section);
        actorToggle(p, h, id, "nametag", definition.nametag, 33, section);
        actorToggle(p, h, id, "tablist", definition.tablist, 40, section);
        if (!definition.type.equals("PLAYER"))
          disabled(h, "actor-tablist", "Only player NPCs appear in the tab list.");
        if (!definition.type.equals("PLAYER"))
          disabled(h, "actor-skin", "Skins are available for player NPCs.");
      }
      case "combat" -> {
        actorToggle(p, h, id, "hittable", definition.hittable, 20, section);
        actorToggle(p, h, id, "immortal", definition.immortal, 24, section);
        control(h, "actor-health", 13, Material.APPLE, c -> command(p, "actor info " + id));
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
        control(
            h,
            "actor-stop",
            33,
            Material.RED_CONCRETE,
            c -> {
              command(p, "actor stop " + id);
              h.refresh.run();
            });
        control(
            h,
            "actor-recording",
            25,
            Material.WRITABLE_BOOK,
            c ->
                picker(
                    p,
                    "Choose a recording",
                    recordings.ids(),
                    0,
                    Material.CLOCK,
                    recording -> {
                      command(p, "actor recording " + id + " " + recording);
                      h.refresh.run();
                    },
                    h.refresh));
        control(
            h,
            "actor-autoplay",
            40,
            Material.REDSTONE_TORCH,
            c -> {
              command(p, "actor autoplay " + id + " " + !definition.autoplay);
              h.refresh.run();
            });
        selected(h, controlSlot("actor-autoplay", 40), definition.autoplay);
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
          selected(
              h,
              controlSlot("actor-mode-" + key, 19 + mode.ordinal()),
              definition.playbackMode == mode);
        }
        boolean performing = acting.actor(p).filter(id::equals).isPresent();
        if (!performing) {
          disabled(h, "actor-finish", "Start acting as this NPC first.");
          disabled(h, "actor-cancel", "No active performance for this NPC.");
        }
        if (acting.actor(p).isPresent() || recordings.capturing(p))
          disabled(h, "actor-act", "Finish your current recording first.");
        if (definition.recording.isBlank() || !recordings.ids().contains(definition.recording))
          disabled(h, "actor-play", "Record a performance or choose a saved one.");
        if (recordings.playing(id) || performing) {
          for (String key : List.of("actor-act", "actor-play", "actor-recording"))
            disabled(h, key, "Finish acting or stop playback first.");
        }
        if (managed.entity().isEmpty()) {
          disabled(h, "actor-act", "Show or respawn this NPC first.");
          disabled(h, "actor-play", "Show or respawn this NPC first.");
        }
      }
      default -> throw new IllegalStateException("Unvalidated actor section");
    }
    for (int slot = 0; slot < h.inventory.getSize(); slot++) {
      ItemStack item = h.inventory.getItem(slot);
      if (item == null) continue;
      var meta = item.getItemMeta();
      Map<String, String> values =
          Map.ofEntries(
              Map.entry("{id}", id),
              Map.entry("{name}", definition.name),
              Map.entry("{skin}", definition.skin.isBlank() ? "Default" : definition.skin),
              Map.entry("{autoplay}", definition.autoplay ? "ON" : "OFF"),
              Map.entry(
                  "{health}",
                  managed
                      .entity()
                      .map(e -> String.format(Locale.ROOT, "%.1f", e.getHealth()))
                      .orElse("Not spawned")),
              Map.entry("{status}", recordings.playing(id) ? "Playing" : performingStatus(p, id)),
              Map.entry("{mode}", definition.playbackMode.name().toLowerCase(Locale.ROOT)),
              Map.entry(
                  "{recording}",
                  definition.recording.isBlank() ? "None yet" : definition.recording),
              Map.entry("{acting}", acting.actor(p).orElse("None")));
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
      Player player,
      MenuHolder holder,
      String id,
      String setting,
      boolean enabled,
      int slot,
      String section) {
    String key = "actor-" + setting;
    control(
        holder,
        key,
        slot,
        enabled ? Material.LIME_DYE : Material.GRAY_DYE,
        c -> {
          command(player, "actor set " + id + " " + setting + " " + !enabled);
          actor(player, id, section);
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
    selected(holder, controlSlot(key, slot), enabled);
  }

  private String performingStatus(Player player, String id) {
    if (acting.actor(player).filter(id::equals).isPresent()) return "Recording your performance";
    try {
      actors.available(id);
    } catch (IllegalArgumentException busy) {
      return "Busy";
    }
    return actors.get(id).entity().isPresent() ? "Ready" : "Hidden / dead";
  }

  public void timeline(Player p, String id, int requested) {
    access.require(p, "scene.play");
    var scene = scenes.get(id);
    List<Integer> slots = slots();
    int page =
        Math.max(0, Math.min(requested, Math.max(0, (scene.actions().size() - 1) / slots.size())));
    MenuHolder h = base(p, "timeline", id, page);
    h.refresh = () -> timeline(p, id, page);
    h.actions.put(layout("back-slot"), c -> open(p, "scenes", 0));
    for (int i = 0; i < slots.size() && page * slots.size() + i < scene.actions().size(); i++) {
      int index = page * slots.size() + i;
      var action = scene.actions().get(index);
      button(
          h,
          slots.get(i),
          Material.PAPER,
          "<aqua>" + action.tick() + "t <white>" + action.type() + " <gray>" + action.target(),
          List.of(
              "<gray>Action #" + (index + 1),
              "<gray>Arguments: <white>" + action.arguments(),
              "",
              "<red>Shift-right click to delete this action."),
          c -> {
            if (c == ClickType.SHIFT_RIGHT)
              confirm(
                  p,
                  "action #" + (index + 1),
                  () -> {
                    command(p, "scene remove " + id + " " + (index + 1));
                    h.refresh.run();
                  },
                  h.refresh);
          });
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
    for (int i = 0; i < 45; i++) h.inventory.setItem(i, null);
    h.actions.put(layout("back-slot"), c -> open(viewer, "players", 0));
    h.refresh = () -> viewInventory(viewer, target, ender);
    ItemStack[] contents = (ender ? target.getEnderChest() : target.getInventory()).getContents();
    for (int i = 0; i < contents.length && i < 45; i++)
      h.inventory.setItem(i, contents[i] == null ? null : contents[i].clone());
    show(viewer, h);
  }

  public void kitEditor(Player p, String id) {
    settings.require("kits");
    access.require(p, "kit.edit");
    MenuHolder h = base(p, "kit-editor", id, 0);
    h.kitId = id;
    for (int i = 0; i < 45; i++) h.inventory.setItem(i, null);
    h.actions.put(layout("back-slot"), c -> open(p, "kits", 0));
    h.refresh = () -> kitEditor(p, id);
    ItemStack[] contents = kits.contents(id);
    for (int i = 0; i < contents.length; i++) h.inventory.setItem(i, contents[i]);
    control(
        h,
        "kit-save",
        49,
        Material.EMERALD,
        c -> {
          access.require(p, "kit.edit");
          settings.require("kits");
          kits.save(id, Arrays.copyOf(h.inventory.getContents(), 41));
          open(p, "kits", 0);
        });
    control(
        h,
        "kit-import-inventory",
        46,
        Material.CHEST,
        c -> {
          access.require(p, "kit.edit");
          confirm(
              p,
              "confirm-import",
              "the draft slots",
              () -> {
                access.require(p, "kit.edit");
                settings.require("kits");
                ItemStack[] inventory = p.getInventory().getContents();
                for (int i = 0; i < 41; i++)
                  h.inventory.setItem(i, inventory[i] == null ? null : inventory[i].clone());
                show(p, h);
              },
              () -> show(p, h));
        });
    control(
        h,
        "kit-save-apply",
        52,
        Material.LIME_CONCRETE,
        c -> {
          access.require(p, "kit.edit");
          access.require(p, "kit");
          settings.require("kits");
          players.available(p.getUniqueId());
          kits.save(id, Arrays.copyOf(h.inventory.getContents(), 41));
          kits.apply(id, p);
          messages.ok(p, "Saved and equipped kit '" + id + "'.");
        });
    messages.send(
        p,
        "info",
        PlainTextComponentSerializer.plainText().serialize(Messages.rich(label("kit-help"))));
    show(p, h);
  }

  public void kitDetails(Player p, String id) {
    settings.require("kits");
    access.require(p, "kit");
    kits.requireClaim(id, p);
    MenuHolder h = base(p, "kit-details", id, 0);
    h.refresh = () -> kitDetails(p, id);
    h.actions.put(layout("back-slot"), c -> open(p, "kits", 0));
    control(h, "kit-apply", 11, Material.LIME_CONCRETE, c -> command(p, "kits claim " + id));
    if (!p.isOp()) {
      kitPlaceholders(h, id);
      show(p, h);
      return;
    }
    control(h, "kit-edit", 13, Material.ANVIL, c -> kitEditor(p, id));
    control(
        h,
        "kit-capture",
        15,
        Material.CHEST,
        c -> {
          access.require(p, "kit.edit");
          confirm(
              p,
              "confirm-import",
              "kit '" + id + "'",
              () -> {
                access.require(p, "kit.edit");
                settings.require("kits");
                kits.save(id, p);
                kitEditor(p, id);
              },
              h.refresh);
        });
    control(h, "kit-export", 29, Material.PAPER, c -> command(p, "kits export " + id));
    control(
        h,
        "kit-delete",
        33,
        Material.BARRIER,
        c -> {
          access.require(p, "kit.edit");
          confirm(
              p,
              id,
              () -> {
                access.require(p, "kit.edit");
                settings.require("kits");
                kits.delete(id);
                open(p, "kits", 0);
              },
              h.refresh);
        });
    control(h, "kit-access", 31, Material.TRIPWIRE_HOOK, c -> kitAccessMenu(p, id));
    control(h, "kit-give-player", 20, Material.PLAYER_HEAD, c -> kitRecipientMenu(p, id, false));
    control(h, "kit-give-actor", 24, Material.ARMOR_STAND, c -> kitRecipientMenu(p, id, true));
    kitPlaceholders(h, id);
    show(p, h);
  }

  public void kitImportMenu(Player p) {
    settings.require("kits");
    access.require(p, "kit.edit");
    picker(
        p,
        "Choose a kit provider",
        kitImports.sources(),
        0,
        Material.HOPPER,
        source -> kitProviderMenu(p, source),
        () -> open(p, "kits", 0));
  }

  private void kitProviderMenu(Player p, String source) {
    access.require(p, "kit.edit");
    settings.require("kits");
    picker(
        p,
        "kit-provider",
        source,
        kitImports.names(source),
        0,
        Material.CHEST,
        name -> {
          access.require(p, "kit.edit");
          String destination = kitImports.destination(source, name);
          kitImports.importKit(source, name, destination, p);
          messages.ok(
              p,
              "Imported items as '"
                  + destination
                  + "'. Review its access settings before sharing.");
          kitEditor(p, destination);
        },
        () -> kitImportMenu(p),
        h -> {
          control(
              h,
              "kits-import-all",
              40,
              Material.CHEST_MINECART,
              c -> {
                access.require(p, "kit.edit");
                kitImports.importAll(source, p);
                kitProviderMenu(p, source);
              });
          control(
              h,
              "kits-import-cancel",
              42,
              Material.RED_CONCRETE,
              c -> {
                access.require(p, "kit.edit");
                kitImports.cancel();
                kitProviderMenu(p, source);
              });
          if (kitImports.importing())
            disabled(h, "kits-import-all", "An import is running. Reopen this page to refresh.");
          else disabled(h, "kits-import-cancel", "No import is running.");
        });
  }

  private void kitRecipientMenu(Player p, String id, boolean npc) {
    settings.require("kits");
    access.require(p, "kit.edit");
    List<String> ids = new ArrayList<>();
    Map<String, UUID> recipients = new HashMap<>();
    if (npc) ids.addAll(actors.ids());
    else {
      ids.add("* (all online players)");
      Bukkit.getOnlinePlayers().stream()
          .filter(IdentityService::realPlayer)
          .forEach(
              player -> {
                ids.add(player.getName());
                recipients.put(player.getName(), player.getUniqueId());
              });
    }
    picker(
        p,
        "kit-recipients",
        id,
        ids,
        0,
        npc ? Material.ARMOR_STAND : Material.PLAYER_HEAD,
        recipient -> {
          String target =
              npc
                  ? "actor:" + recipient
                  : recipient.startsWith("*") ? "*" : "player:" + recipients.get(recipient);
          command(p, "kits claim " + id + " " + target);
        },
        () -> kitDetails(p, id),
        h -> {});
  }

  public void kitAccessMenu(Player p, String id) {
    settings.require("kits");
    access.require(p, "kit.edit");
    kits.contents(id);
    MenuHolder h = base(p, "kit-access", id, 0);
    h.refresh = () -> kitAccessMenu(p, id);
    h.actions.put(layout("back-slot"), c -> kitDetails(p, id));
    control(h, "kit-access-info", 13, Material.PAPER, c -> {});
    control(
        h,
        "kit-access-operators",
        20,
        Material.REDSTONE_TORCH,
        c -> {
          command(p, "kits access " + id + " operators");
          h.refresh.run();
        });
    control(
        h,
        "kit-access-everyone",
        22,
        Material.GRASS_BLOCK,
        c -> {
          command(p, "kits access " + id + " everyone");
          h.refresh.run();
        });
    control(
        h,
        "kit-access-player",
        24,
        Material.PLAYER_HEAD,
        c -> {
          access.require(p, "kit.edit");
          Map<String, UUID> online = new TreeMap<>();
          Bukkit.getOnlinePlayers().stream()
              .filter(IdentityService::realPlayer)
              .forEach(player -> online.put(player.getName(), player.getUniqueId()));
          picker(
              p,
              "Choose the allowed player",
              List.copyOf(online.keySet()),
              0,
              Material.PLAYER_HEAD,
              name -> {
                command(p, "kits access " + id + " player " + online.get(name));
                h.refresh.run();
              },
              h.refresh);
        });
    try {
      KitAccess.Mode mode = kits.access(id).mode();
      selected(h, controlSlot("kit-access-operators", 20), mode == KitAccess.Mode.OPERATORS);
      selected(h, controlSlot("kit-access-everyone", 22), mode == KitAccess.Mode.EVERYONE);
      selected(h, controlSlot("kit-access-player", 24), mode == KitAccess.Mode.PLAYER);
    } catch (IllegalArgumentException ignored) {
      /* Operators can repair malformed access here. */
    }
    kitPlaceholders(h, id);
    show(p, h);
  }

  private String kitAccessDescription(String id) {
    try {
      return kits.access(id).description();
    } catch (IllegalArgumentException ex) {
      return "Invalid access; operator repair required";
    }
  }

  private void kitPlaceholders(MenuHolder h, String id) {
    var replacement =
        net.kyori.adventure.text.TextReplacementConfig.builder()
            .matchLiteral("{access}")
            .replacement(kitAccessDescription(id))
            .build();
    for (ItemStack item : h.inventory.getContents()) {
      if (item == null || !item.hasItemMeta()) continue;
      var meta = item.getItemMeta();
      if (meta.hasDisplayName()) meta.displayName(meta.displayName().replaceText(replacement));
      if (meta.hasLore())
        meta.lore(meta.lore().stream().map(line -> line.replaceText(replacement)).toList());
      item.setItemMeta(meta);
    }
  }

  public void prompt(Player p, String prefix, String question) {
    Runnable back =
        p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder old
            ? old.refresh
            : () -> open(p, "main", 0);
    p.closeInventory();
    inputs.put(p.getUniqueId(), new Pending(prefix, System.currentTimeMillis() + 60000, back));
    messages.send(p, "input", question);
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void chat(AsyncChatEvent e) {
    Pending pending = inputs.remove(e.getPlayer().getUniqueId());
    if (pending == null) return;
    e.setCancelled(true);
    String text = PlainTextComponentSerializer.plainText().serialize(e.message()).strip();
    boolean cancelled =
        pending.expires < System.currentTimeMillis()
            || text.equalsIgnoreCase("cancel")
            || text.length() > 512
            || text.contains("\n")
            || text.contains("\r");
    if (!plugin.isEnabled()) return;
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (!e.getPlayer().isOnline()) return;
              if (!cancelled) command(e.getPlayer(), pending.prefix + " " + text);
              try {
                pending.back.run();
              } catch (IllegalArgumentException | IllegalStateException ex) {
                messages.error(e.getPlayer(), ex.getMessage());
              }
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
                  h.inventory.setItem(
                      layout("previous-slot"),
                      clicked == null
                          ? icon(
                              Material.PAPER,
                              "<gray>No item selected",
                              List.of("<gray>Click an item in your inventory to copy it."))
                          : clicked.clone());
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
