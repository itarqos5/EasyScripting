package dev.easyscripting.config;

import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;

public final class Messages {
  private final Settings settings;
  private final CommandResponses responses = new CommandResponses();

  public CommandResponses.Scope track(CommandSender sender) {
    return responses.begin(sender);
  }

  public Messages(Settings settings) {
    this.settings = settings;
  }

  public Component text(String key, Map<String, String> values) {
    return rich(settings.file("messages").getString(key, "<gray><detail>"), values);
  }

  public void send(CommandSender sender, String key, String detail) {
    sender.sendMessage(text(key, Map.of("detail", detail)));
    responses.sent(sender);
  }

  public void ok(CommandSender sender, String detail) {
    send(sender, "success", detail);
    feedback(sender, "success");
  }

  public void error(CommandSender sender, String detail) {
    send(sender, "error", detail);
    feedback(sender, "error");
  }

  private void feedback(CommandSender sender, String kind) {
    if (!(sender instanceof org.bukkit.entity.Player player)
        || !org.bukkit.Bukkit.isPrimaryThread()) return;
    var config = settings.file("messages");
    if (!config.getBoolean("feedback.enabled", false)) return;
    String sound = config.getString("feedback." + kind, "ui.button.click");
    player.playSound(player.getLocation(), sound, .35f, 1f);
  }

  public static Component rich(String input) {
    return MiniMessage.miniMessage().deserialize(input);
  }

  /** Fill a configured MiniMessage template whose placeholders live outside messages.yml. */
  public static Component rich(String input, Map<String, String> values) {
    return MiniMessage.miniMessage()
        .deserialize(
            input,
            values.entrySet().stream()
                .map(e -> Placeholder.unparsed(e.getKey(), e.getValue()))
                .toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new));
  }
}
