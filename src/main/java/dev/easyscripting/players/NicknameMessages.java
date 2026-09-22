package dev.easyscripting.players;

import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.HoverEvent;

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
    return carried(
        message.replaceText(
            b ->
                b.match(names)
                    .replacement(
                        (match, builder) -> builder.content(replacements.get(match.group())))),
        replacements);
  }

  /**
   * Rewrite the account name where a message carries it rather than shows it. Vanilla attaches a
   * hover card and a shift-click insertion to every name it puts in a death or leave message, and
   * a message whose visible name reads as the alias while its hover still reads as the account has
   * not really been rewritten. Translation arguments are walked too, because that is where a
   * death message keeps the victim and the killer.
   */
  private static Component carried(Component component, Map<String, String> replacements) {
    Component result =
        component.children(
            component.children().stream().map(child -> carried(child, replacements)).toList());
    if (result instanceof TranslatableComponent translatable)
      result =
          translatable.arguments(
              translatable.arguments().stream()
                  .map(
                      argument ->
                          argument.value() instanceof Component value
                              ? TranslationArgument.component(carried(value, replacements))
                              : argument)
                  .toList());
    String insertion = result.style().insertion();
    if (insertion != null && replacements.containsKey(insertion))
      result = result.style(result.style().insertion(replacements.get(insertion)));
    HoverEvent<?> hover = result.style().hoverEvent();
    if (hover == null) return result;
    // The entity id is deliberately left alone: it identifies the account, which is still true.
    if (hover.value() instanceof HoverEvent.ShowEntity entity && entity.name() != null)
      return result.style(
          result
              .style()
              .hoverEvent(
                  HoverEvent.showEntity(
                      entity.type(), entity.id(), rewrite(entity.name(), replacements))));
    if (hover.value() instanceof Component text)
      return result.style(result.style().hoverEvent(HoverEvent.showText(rewrite(text, replacements))));
    return result;
  }
}
