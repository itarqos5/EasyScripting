package dev.easyscripting.players;

import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;

public final class NicknameMessages {
  private NicknameMessages() {}

  /** Match complete name components/translation arguments, never words inside item names. */
  public static Component rewrite(Component message, Map<String, String> replacements) {
    if (message == null || replacements.isEmpty()) return message;
    Pattern names =
        Pattern.compile(
            "^(?:"
                + String.join("|", replacements.keySet().stream().map(Pattern::quote).toList())
                + ")$");
    return message.replaceText(
        b ->
            b.match(names)
                .replacement((match, builder) -> builder.content(replacements.get(match.group()))));
  }
}
