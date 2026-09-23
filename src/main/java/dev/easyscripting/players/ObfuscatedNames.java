package dev.easyscripting.players;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Hide an invisible player's identity in the messages the server prints about them: their death,
 * a kill they scored and their leave announcement. The text keeps its shape and the scrambling is
 * the vanilla obfuscated style, so the message still reads as an event that happened to somebody.
 */
public final class ObfuscatedNames {
  private ObfuscatedNames() {}

  /** Characters a Minecraft name can never contain, so a scrambled insertion cannot read as one. */
  private static final char[] NOISE = "#$%&*+=?@".toCharArray();

  public enum Mode {
    /** Leave every message exactly as the server wrote it. */
    OFF,
    /** Scramble the invisible player's name where it appears; the rest stays readable. */
    NAMES,
    /** Scramble the whole message, so not even its shape identifies who it was about. */
    MESSAGE;

    public static Mode of(String configured) {
      return switch (configured == null ? "" : configured.toLowerCase(Locale.ROOT)) {
        case "off", "none", "false" -> OFF;
        case "names", "name" -> NAMES;
        case "message", "full", "all", "true" -> MESSAGE;
        default ->
            throw new IllegalArgumentException(
                "death.yml: invisible-obfuscation must be message, names or off.");
      };
    }
  }

  /**
   * Obfuscate {@code message} for the given hidden names. A name is also carried by a message
   * rather than only shown in it: vanilla attaches a hover card and a shift-click insertion to
   * every name it puts in a death or leave message, and both are cleared here, because a scrambled
   * name that can be hovered or shift-clicked for the real one has not been hidden at all.
   */
  public static Component hide(Component message, Mode mode, Set<String> names) {
    if (message == null || mode == Mode.OFF || names.isEmpty()) return message;
    Component stripped = carried(message, names);
    if (mode == Mode.MESSAGE) return stripped.decorate(TextDecoration.OBFUSCATED);
    Pattern pattern =
        Pattern.compile(
            "^(?:" + String.join("|", names.stream().map(Pattern::quote).toList()) + ")$");
    return stripped.replaceText(
        b ->
            b.match(pattern)
                .replacement((match, builder) -> builder.decorate(TextDecoration.OBFUSCATED)));
  }

  /** Random characters of the same length, for the shift-click insertion behind a hidden name. */
  public static String scramble(int length) {
    var random = ThreadLocalRandom.current();
    StringBuilder text = new StringBuilder(length);
    for (int i = 0; i < length; i++) text.append(NOISE[random.nextInt(NOISE.length)]);
    return text.toString();
  }

  /**
   * Clear what a message carries about a hidden player. Translation arguments are walked too,
   * because that is where a death message keeps its victim and its killer.
   */
  private static Component carried(Component component, Set<String> names) {
    Component result =
        component.children(
            component.children().stream().map(child -> carried(child, names)).toList());
    if (result instanceof TranslatableComponent translatable)
      result =
          translatable.arguments(
              translatable.arguments().stream()
                  .map(
                      argument ->
                          argument.value() instanceof Component value
                              ? TranslationArgument.component(carried(value, names))
                              : argument)
                  .toList());
    String insertion = result.style().insertion();
    if (insertion != null && names.contains(insertion))
      result = result.style(result.style().insertion(scramble(insertion.length())));
    HoverEvent<?> hover = result.style().hoverEvent();
    // The entity hover also carries the account UUID, so a naming hover is dropped whole.
    if (hover != null && mentions(hover, names))
      result = result.style(result.style().toBuilder().hoverEvent(null).build());
    return result;
  }

  private static boolean mentions(HoverEvent<?> hover, Set<String> names) {
    Component shown = null;
    if (hover.value() instanceof HoverEvent.ShowEntity entity) shown = entity.name();
    else if (hover.value() instanceof Component text) shown = text;
    if (shown == null) return false;
    String plain = PlainTextComponentSerializer.plainText().serialize(shown);
    return names.stream().anyMatch(plain::contains);
  }
}
