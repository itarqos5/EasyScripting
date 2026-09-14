package dev.easyscripting.scenes;

import dev.easyscripting.api.Scene;
import java.util.*;

/** Expands reusable choreography into an ordinary bounded timeline, without extra tasks. */
public final class SceneEdits {
  private SceneEdits() {}

  public static Scene append(Scene destination, Scene source, long offset, int limit) {
    if (offset < 0) throw new IllegalArgumentException("Append offset must be nonnegative.");
    List<Scene.Action> actions = new ArrayList<>(destination.actions());
    for (Scene.Action action : source.actions()) {
      Map<String, String> args = new LinkedHashMap<>(action.arguments());
      for (String key : List.of("at", "victim"))
        if (args.containsKey(key))
          args.put(key, source.bindings().getOrDefault(args.get(key), args.get(key)));
      actions.add(
          new Scene.Action(
              Math.addExact(offset, action.tick()),
              action.type(),
              source.bindings().getOrDefault(action.target(), action.target()),
              args));
    }
    return replace(destination, actions, limit);
  }

  public static Scene repeat(
      Scene scene, long from, long through, int copies, long interval, int limit) {
    if (from < 0 || through < from || copies < 1 || copies > 100 || interval <= through - from)
      throw new IllegalArgumentException(
          "Repeat needs an ordered tick range, 1..100 additional copies and an interval longer than"
              + " the range.");
    List<Scene.Action> selected =
        scene.actions().stream().filter(a -> a.tick() >= from && a.tick() <= through).toList();
    if (selected.isEmpty())
      throw new IllegalArgumentException("No actions in the selected tick range.");
    if ((long) scene.actions().size() + (long) selected.size() * copies > limit)
      throw new IllegalArgumentException("Repeat exceeds the scene action limit of " + limit);
    List<Scene.Action> actions = new ArrayList<>(scene.actions());
    for (int i = 1; i <= copies; i++)
      for (Scene.Action action : selected)
        actions.add(
            new Scene.Action(
                Math.addExact(action.tick(), Math.multiplyExact(interval, i)),
                action.type(),
                action.target(),
                action.arguments()));
    return replace(scene, actions, limit);
  }

  private static Scene replace(Scene scene, List<Scene.Action> actions, int limit) {
    if (actions.size() > limit)
      throw new IllegalArgumentException("Scene action limit is " + limit);
    return new Scene(
        scene.id(), scene.description(), scene.bindings(), actions, scene.restoreOnComplete());
  }
}
