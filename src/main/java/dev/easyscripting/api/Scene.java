package dev.easyscripting.api;

import dev.easyscripting.core.Checks;
import java.util.*;

public record Scene(
    String id,
    String description,
    Map<String, String> bindings,
    List<Action> actions,
    boolean restoreOnComplete) {
  public Scene {
    Checks.id(id);
    Objects.requireNonNull(description);
    bindings = Map.copyOf(bindings);
    // Stable sort deliberately preserves YAML order for actions on the same tick.
    actions = actions.stream().sorted(Comparator.comparingLong(Action::tick)).toList();
  }

  public record Action(long tick, String type, String target, Map<String, String> arguments) {
    public Action {
      if (tick < 0 || tick > 720000)
        throw new IllegalArgumentException("Action tick must be 0..720000.");
      Checks.id(type);
      Objects.requireNonNull(target);
      arguments = Map.copyOf(arguments);
    }

    public String arg(String name) {
      String value = arguments.get(name);
      if (value == null)
        throw new IllegalArgumentException(type + ": missing argument '" + name + "'.");
      return value;
    }

    public String arg(String name, String fallback) {
      return arguments.getOrDefault(name, fallback);
    }
  }
}
