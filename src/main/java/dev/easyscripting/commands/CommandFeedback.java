package dev.easyscripting.commands;

import java.util.Set;

/** Used only for successful handlers that have not already explained their result. */
public final class CommandFeedback {
  private CommandFeedback() {}

  public static String describe(String command, Args args) {
    String operation = args.get(0, "");
    String id = args.get(1, "");
    String subject =
        switch (command) {
          case "actor" -> "NPC";
          case "kit", "kits" -> "Kit";
          default -> Character.toUpperCase(command.charAt(0)) + command.substring(1);
        };
    if (command.equals("menu"))
      return "Opened " + (operation.isEmpty() ? "studio" : operation) + " menu.";
    if (command.equals("skin"))
      return "Requested skin '" + operation + "'; the result will appear after lookup.";
    if (command.equals("nick") && operation.equals("random")) return "Random nickname selected.";
    if (command.equals("player"))
      return "Player "
          + operation.replace('-', ' ')
          + (id.isEmpty() ? " updated" : " set to " + id)
          + (args.size() > 2 ? " for " + args.get(2) : "")
          + ".";
    if (command.equals("chat"))
      return switch (operation) {
        case "block" -> "Chat block setting applied" + (id.isEmpty() ? "." : ": " + id + ".");
        case "clear" -> "Cleared " + (id.equals("self") ? "your" : "everyone's") + " chat view.";
        case "broadcast" -> "Broadcast sent.";
        default -> "Sent the simulated " + operation + " message for " + id + ".";
      };
    if (operation.equals("set"))
      return subject
          + " '"
          + id
          + "': "
          + args.get(2, "setting")
          + " set to "
          + args.get(3, "updated")
          + ".";
    if (Set.of("on", "off").contains(id))
      return subject + " " + operation.replace('-', ' ') + " turned " + id + ".";
    if (Set.of(
            "create", "delete", "save", "copy", "spawn", "hide", "show", "respawn", "play", "stop",
            "pause", "resume", "reset")
        .contains(operation)) {
      String verb =
          switch (operation) {
            case "create" -> "Created";
            case "delete" -> "Deleted";
            case "save" -> "Saved";
            case "copy" -> "Copied";
            case "spawn" -> "Spawned";
            case "hide" -> "Hid";
            case "show" -> "Showed";
            case "respawn" -> "Respawned";
            case "play" -> "Started";
            case "stop" -> "Stopped";
            case "pause" -> "Paused";
            case "resume" -> "Resumed";
            default -> "Reset";
          };
      return verb
          + " "
          + subject.toLowerCase(java.util.Locale.ROOT)
          + (id.isEmpty() ? "" : " '" + id + "'")
          + ".";
    }
    String detail = args.size() == 0 ? "" : args.rest(0);
    if (detail.length() > 160) detail = detail.substring(0, 157) + "...";
    return subject + (detail.isEmpty() ? "" : " " + detail) + " completed.";
  }
}
