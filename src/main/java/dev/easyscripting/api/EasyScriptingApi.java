package dev.easyscripting.api;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;

/** Obtain through Bukkit ServicesManager. Every method must be called on the main thread. */
public interface EasyScriptingApi {
  List<String> sceneIds();

  Scene scene(String id);

  void saveScene(Scene scene);

  UUID play(String id, CommandSender director);

  void stop(String id, boolean restore);

  void pause(String id);

  void resume(String id);

  void reset(String id);

  void deleteScene(String id);

  List<String> actorIds();

  Actor actor(String id);

  Actor createActor(String id, EntityType type, Location location);

  void removeActor(String id);
}
