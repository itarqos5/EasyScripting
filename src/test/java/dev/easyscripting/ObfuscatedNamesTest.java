package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.players.ObfuscatedNames;
import dev.easyscripting.players.ObfuscatedNames.Mode;
import java.util.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class ObfuscatedNamesTest {
  private static final UUID ACCOUNT = UUID.randomUUID();

  /** A death message as the server writes it: a translation whose arguments are the two names. */
  private static Component deathMessage() {
    return Component.translatable(
        "death.attack.player",
        TranslationArgument.component(named("Alice")),
        TranslationArgument.component(named("Bob")));
  }

  private static Component named(String name) {
    return Component.text(name)
        .hoverEvent(HoverEvent.showEntity(Key.key("minecraft:player"), ACCOUNT, Component.text(name)))
        .insertion(name);
  }

  private static void walk(Component component, List<Component> collected) {
    collected.add(component);
    component.children().forEach(child -> walk(child, collected));
    if (component instanceof TranslatableComponent translatable)
      translatable.arguments().stream()
          .map(TranslationArgument::value)
          .filter(Component.class::isInstance)
          .forEach(value -> walk((Component) value, collected));
  }

  private static List<Component> parts(Component component) {
    List<Component> collected = new ArrayList<>();
    walk(component, collected);
    return collected;
  }

  private static String plain(Component component) {
    return PlainTextComponentSerializer.plainText().serialize(component);
  }

  private static boolean carries(Component message, String name) {
    return parts(message).stream()
        .anyMatch(
            part -> {
              if (name.equals(part.style().insertion())) return true;
              HoverEvent<?> hover = part.style().hoverEvent();
              if (hover == null) return false;
              if (hover.value() instanceof HoverEvent.ShowEntity entity)
                return entity.name() != null && plain(entity.name()).contains(name);
              return hover.value() instanceof Component text && plain(text).contains(name);
            });
  }

  @Test
  void anInvisibleVictimIsNotNamedByAnythingTheMessageCarries() {
    Component hidden = ObfuscatedNames.hide(deathMessage(), Mode.NAMES, Set.of("Alice"));
    assertFalse(carries(hidden, "Alice"), "hovering or shift-clicking must not give the name back");
    assertTrue(carries(hidden, "Bob"), "the visible killer keeps their ordinary name card");
    assertTrue(
        parts(hidden).stream()
            .anyMatch(
                part ->
                    plain(part).contains("Alice")
                        && part.decoration(TextDecoration.OBFUSCATED) == TextDecoration.State.TRUE),
        "the hidden name itself is scrambled");
    assertTrue(
        parts(hidden).stream()
            .anyMatch(
                part ->
                    plain(part).equals("Bob")
                        && part.decoration(TextDecoration.OBFUSCATED)
                            != TextDecoration.State.TRUE),
        "a name that is not hidden stays readable");
  }

  @Test
  void theWholeMessageIsScrambledWhenTheModeSaysSo() {
    Component hidden = ObfuscatedNames.hide(deathMessage(), Mode.MESSAGE, Set.of("Alice"));
    assertEquals(TextDecoration.State.TRUE, hidden.decoration(TextDecoration.OBFUSCATED));
    assertFalse(carries(hidden, "Alice"));
  }

  @Test
  void aLeaveMessageIsHiddenTheSameWayAndOffChangesNothing() {
    Component leaving =
        Component.translatable(
            "multiplayer.player.left", TranslationArgument.component(named("Alice")));
    assertFalse(carries(ObfuscatedNames.hide(leaving, Mode.NAMES, Set.of("Alice")), "Alice"));
    assertSame(leaving, ObfuscatedNames.hide(leaving, Mode.OFF, Set.of("Alice")));
    assertSame(leaving, ObfuscatedNames.hide(leaving, Mode.MESSAGE, Set.of()));
    assertNull(ObfuscatedNames.hide(null, Mode.MESSAGE, Set.of("Alice")));
  }

  @Test
  void bothTheAccountNameAndItsNicknameCanBeHiddenAtOnce() {
    Component message =
        Component.translatable(
            "death.attack.player",
            TranslationArgument.component(named("QuietFox")),
            TranslationArgument.component(named("Bob")));
    Component hidden =
        ObfuscatedNames.hide(message, Mode.NAMES, new LinkedHashSet<>(List.of("Alice", "QuietFox")));
    assertFalse(carries(hidden, "QuietFox"));
  }

  @Test
  void aScrambledInsertionCannotBeReadBackAsAMinecraftName() {
    String scrambled = ObfuscatedNames.scramble(5);
    assertEquals(5, scrambled.length());
    assertFalse(scrambled.matches("[A-Za-z0-9_]+"), "it must not look like an account name");
  }

  @Test
  void theConfiguredModeIsCheckedWhenTheFileIsLoaded() {
    assertEquals(Mode.MESSAGE, Mode.of("message"));
    assertEquals(Mode.NAMES, Mode.of("NAMES"));
    assertEquals(Mode.OFF, Mode.of("off"));
    assertThrows(IllegalArgumentException.class, () -> Mode.of("scrambled"));
  }
}
