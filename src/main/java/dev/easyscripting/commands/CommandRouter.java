package dev.easyscripting.commands;

import dev.easyscripting.config.*;
import java.util.*;
import java.util.function.*;
import java.util.logging.Level;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommandRouter implements CommandExecutor, TabCompleter {
  public record Route(
      String permission,
      String usage,
      BiConsumer<CommandSender, Args> handler,
      BiFunction<CommandSender, Args, List<String>> suggestions) {}

  private final Map<String, Route> routes = new LinkedHashMap<>();
  private final Access access;
  private final Messages messages;
  private final JavaPlugin plugin;
  private final Settings settings;

  public CommandRouter(JavaPlugin plugin, Access access, Messages messages, Settings settings) {
    this.plugin = plugin;
    this.access = access;
    this.messages = messages;
    this.settings = settings;
  }

  public void add(
      String name,
      String permission,
      String usage,
      BiConsumer<CommandSender, Args> handler,
      BiFunction<CommandSender, Args, List<String>> suggestions) {
    routes.put(name, new Route(permission, usage, handler, suggestions));
  }

  public void add(
      String name,
      String permission,
      String usage,
      BiConsumer<CommandSender, Args> handler,
      String... suggestions) {
    add(
        name,
        permission,
        usage,
        handler,
        (s, a) -> a.size() <= 1 ? List.of(suggestions) : List.of());
  }

  public List<String> names() {
    return List.copyOf(routes.keySet());
  }

  public static String[] expand(String command, String[] original) {
    if (command.equals("actors")
        && (original.length == 0 || (original.length == 1 && original[0].isBlank())))
      return new String[] {"menu", "actors"};
    if (Set.of("scene", "actor", "actors", "kits", "nickname").contains(command)) {
      String[] args = new String[original.length + 1];
      args[0] = command.equals("actors") ? "actor" : command;
      System.arraycopy(original, 0, args, 1, original.length);
      return args;
    }
    return original;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] original) {
    String[] args = expand(command.getName(), original);
    try {
      access.require(sender, "use");
      String name = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
      if (name.equals("help")) {
        for (var entry : routes.entrySet())
          if (access.allowed(sender, "easyscripting." + entry.getValue().permission))
            messages.send(sender, "info", "/es " + entry.getKey() + " " + entry.getValue().usage);
        return true;
      }
      Route route = routes.get(name);
      if (route == null)
        throw new IllegalArgumentException("Unknown command '" + name + "'. Use /es help.");
      access.require(sender, route.permission);
      Args values =
          new Args(args.length == 0 ? new String[0] : Arrays.copyOfRange(args, 1, args.length));
      try (var response = messages.track(sender)) {
        route.handler.accept(sender, values);
        if (!response.responded()) messages.ok(sender, CommandFeedback.describe(name, values));
      }
      return true;
    } catch (IllegalArgumentException | IllegalStateException ex) {
      String name = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
      Route route = routes.get(name);
      if (route != null && access.allowed(sender, "easyscripting." + route.permission)) {
        if (!CommandHelp.syntaxProblem(ex.getMessage())) messages.error(sender, ex.getMessage());
        Args values = new Args(Arrays.copyOfRange(args, Math.min(1, args.length), args.length));
        for (String line :
            CommandHelp.explain(settings.file("command-help"), name, values, route.usage))
          messages.send(sender, "info", line);
      } else if (route == null) {
        messages.send(
            sender,
            "info",
            "Use /es <command>. Available commands: "
                + String.join(
                    ", ",
                    routes.entrySet().stream()
                        .filter(
                            entry ->
                                access.allowed(
                                    sender, "easyscripting." + entry.getValue().permission))
                        .map(Map.Entry::getKey)
                        .toList()));
        messages.send(
            sender,
            "info",
            "Example: /actors opens NPCs; /kits opens kits; /es help shows command syntax.");
      } else messages.error(sender, ex.getMessage());
      return true;
    } catch (RuntimeException ex) {
      plugin.getLogger().log(Level.SEVERE, "Command failed: /" + label, ex);
      messages.error(
          sender,
          "The operation failed. See the server log for the cause; your command was not retried.");
      return true;
    }
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String label, String[] original) {
    if (!access.allowed(sender, "easyscripting.use")) return List.of();
    String[] args = expand(command.getName(), original);
    String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
    if (args.length <= 1)
      return routes.entrySet().stream()
          .filter(e -> access.allowed(sender, "easyscripting." + e.getValue().permission))
          .map(Map.Entry::getKey)
          .filter(n -> n.startsWith(prefix))
          .toList();
    Route route = routes.get(args[0]);
    if (route == null || !access.allowed(sender, "easyscripting." + route.permission))
      return List.of();
    try {
      return route
          .suggestions
          .apply(sender, new Args(Arrays.copyOfRange(args, 1, args.length)))
          .stream()
          .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
          .distinct()
          .sorted()
          .toList();
    } catch (IllegalArgumentException ex) {
      return List.of();
    }
  }
}
