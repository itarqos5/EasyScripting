package dev.easyscripting.commands;

import dev.easyscripting.actors.ActorService;
import dev.easyscripting.config.*;
import dev.easyscripting.gui.MenuService;
import dev.easyscripting.integration.KitImports;
import dev.easyscripting.items.*;
import java.util.*;

public final class KitCommands {
  private KitCommands() {}

  public static void register(
      CommandRouter router,
      Access access,
      Settings settings,
      Messages messages,
      KitService kits,
      KitClaims claims,
      KitImports imports,
      ActorService actors,
      MenuService menus) {
    router.add(
        "kits",
        "kit",
        "[claim <kit> [player|*|actor:id] | imports | import <provider> <kit> [new-id] | importall"
            + " <provider> | cancelimport | access <kit> <operators|everyone|player> [player] |"
            + " export <id>]",
        (sender, args) -> {
          settings.require("kits");
          if (args.size() == 0) {
            menus.open(Args.player(sender), "kits", 0);
            return;
          }
          if (args.get(0).equalsIgnoreCase("claim")) {
            String[] values = new String[args.size() - 1];
            for (int i = 0; i < values.length; i++) values[i] = args.get(i + 1);
            KitClaimRequest request = KitClaimRequest.parse(values, kits::exists);
            int count = claims.claim(sender, request);
            messages.ok(
                sender,
                "Equipped kit '"
                    + request.kit()
                    + "' for "
                    + count
                    + " recipient"
                    + (count == 1 ? "." : "s."));
            return;
          }
          access.require(sender, "kit.edit");
          switch (args.get(0).toLowerCase(Locale.ROOT)) {
            case "imports" -> menus.kitImportMenu(Args.player(sender));
            case "import" -> {
              if (args.size() > 4)
                throw new IllegalArgumentException(
                    "Use /es kits import <provider> <kit> [new-id].");
              String destination = args.get(3, imports.destination(args.get(1), args.get(2)));
              imports.importKit(args.get(1), args.get(2), destination, Args.player(sender));
              messages.ok(
                  sender,
                  "Imported kit '"
                      + destination
                      + "'. Only items are copied; check its access settings before sharing.");
            }
            case "importall" -> imports.importAll(args.get(1), Args.player(sender));
            case "cancelimport" -> {
              imports.cancel();
              messages.ok(sender, "Stopped the kit import. Completed imports are kept.");
            }
            case "export" -> {
              kits.export(args.get(1));
              messages.ok(
                  sender,
                  "Export queued: plugins/EasyScripting/kit-exports/" + args.get(1) + ".yml");
            }
            case "access" -> {
              String id = args.get(1);
              String mode = args.get(2).toLowerCase(Locale.ROOT);
              if (args.size() != (mode.equals("player") ? 4 : 3))
                throw new IllegalArgumentException(
                    "Use /es kits access <kit> <operators|everyone|player> [online-player].");
              KitAccess policy =
                  switch (mode) {
                    case "operators" -> KitAccess.operators();
                    case "everyone" -> new KitAccess(KitAccess.Mode.EVERYONE, null, null);
                    case "player" -> {
                      var player = claims.player(args.get(3));
                      yield new KitAccess(
                          KitAccess.Mode.PLAYER, player.getUniqueId(), player.getName());
                    }
                    default ->
                        throw new IllegalArgumentException(
                            "Access must be operators, everyone, or player.");
                  };
              kits.access(id, policy);
              messages.ok(
                  sender, "Kit '" + id + "' can be claimed by: " + policy.description() + ".");
            }
            default ->
                throw new IllegalArgumentException(
                    "Use /es kits to browse, or /es help for kit commands.");
          }
        },
        (sender, args) -> {
          if (args.size() == 1)
            return sender.isOp()
                ? List.of(
                    "claim", "imports", "import", "importall", "cancelimport", "access", "export")
                : List.of("claim");
          String operation = args.get(0).toLowerCase(Locale.ROOT);
          if (operation.equals("claim")) {
            List<String> options = new ArrayList<>(kits.ids(sender));
            if (sender.isOp() && args.size() <= 3) {
              options.add("*");
              options.addAll(claims.names());
              claims.names().forEach(name -> options.add("player:" + name));
              actors.ids().forEach(id -> options.add("actor:" + id));
            }
            return args.size() <= 3 ? options : List.of();
          }
          if (!sender.isOp()) return List.of();
          if (args.size() == 2 && List.of("import", "importall").contains(operation))
            return imports.sources();
          if (args.size() == 3 && operation.equals("import")) return imports.names(args.get(1));
          if (args.size() == 2 && List.of("access", "export").contains(operation))
            return kits.ids();
          if (operation.equals("access")) {
            if (args.size() == 3) return List.of("operators", "everyone", "player");
            if (args.size() == 4 && args.get(2).equals("player")) return claims.names();
          }
          return List.of();
        });
  }
}
