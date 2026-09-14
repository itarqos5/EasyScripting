package dev.easyscripting.scenes;

import dev.easyscripting.api.Scene;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SceneCodec {
  private SceneCodec() {}

  public static Scene decode(String id, YamlConfiguration yaml, int limit) {
    if (yaml.getInt("schema", -1) != 1)
      throw new IllegalArgumentException("scenes/" + id + ".yml: schema must be 1.");
    Map<String, String> bindings = new LinkedHashMap<>();
    ConfigurationSection section = yaml.getConfigurationSection("bindings");
    if (section != null)
      for (String key : section.getKeys(false)) bindings.put(key, section.getString(key, ""));
    List<Scene.Action> actions = new ArrayList<>();
    if (yaml.contains("actions") && !yaml.isList("actions"))
      throw new IllegalArgumentException("scenes/" + id + ".yml: actions must be a list.");
    for (Object entry : yaml.getList("actions", List.of())) {
      if (!(entry instanceof Map<?, ?> raw))
        throw new IllegalArgumentException(
            "scenes/"
                + id
                + ".yml: actions["
                + actions.size()
                + "] = "
                + entry
                + "; expected an action mapping.");
      if (actions.size() >= limit)
        throw new IllegalArgumentException("scenes/" + id + ".yml: actions exceeds " + limit);
      Map<String, String> args = new LinkedHashMap<>();
      Object params = raw.get("args");
      if (params != null && !(params instanceof Map<?, ?>))
        throw new IllegalArgumentException(
            "scenes/" + id + ".yml: actions[" + actions.size() + "].args must be a mapping.");
      if (params instanceof Map<?, ?> map)
        map.forEach((k, v) -> args.put(String.valueOf(k), String.valueOf(v)));
      try {
        actions.add(
            new Scene.Action(
                Long.parseLong(String.valueOf(raw.get("tick"))),
                Objects.toString(raw.get("type"), ""),
                Objects.toString(raw.get("target"), ""),
                args));
      } catch (RuntimeException ex) {
        throw new IllegalArgumentException(
            "scenes/" + id + ".yml: actions[" + actions.size() + "]: " + ex.getMessage(), ex);
      }
    }
    if (yaml.contains("actions") && !yaml.isList("actions"))
      throw new IllegalArgumentException("scenes/" + id + ".yml: actions must be a list.");
    return new Scene(
        id,
        yaml.getString("description", id),
        bindings,
        actions,
        yaml.getBoolean("restore-on-complete", false));
  }

  public static YamlConfiguration encode(Scene scene) {
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("schema", 1);
    yaml.set("description", scene.description());
    yaml.createSection("bindings", scene.bindings());
    yaml.set("restore-on-complete", scene.restoreOnComplete());
    yaml.set(
        "actions",
        scene.actions().stream()
            .map(
                a ->
                    Map.of(
                        "tick",
                        a.tick(),
                        "type",
                        a.type(),
                        "target",
                        a.target(),
                        "args",
                        a.arguments()))
            .toList());
    return yaml;
  }
}
