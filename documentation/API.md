# Java integration API

The public API is in `dev.easyscripting.api`. Declare `depend: [EasyScripting]` in your plugin metadata, or `softdepend` and explicitly handle absence. Compile against the EasyScripting JAR; do not shade its classes into your plugin.

```java
EasyScriptingApi api = Bukkit.getServicesManager().load(EasyScriptingApi.class);
if (api == null) throw new IllegalStateException("EasyScripting is unavailable");

Actor actor = api.createActor("guard", EntityType.ZOMBIE, spawnLocation);
Scene scene = new Scene("opening", "Opening cue",
    Map.of("guard", "actor:guard"),
    List.of(new Scene.Action(0, "swing", "guard", Map.of()),
            new Scene.Action(40, "wait", "guard", Map.of())),
    true);
api.saveScene(scene);
UUID runId = api.play("opening", director);
```

Call every method on the active server thread. The facade rejects asynchronous access and calls after disable. `play` checks the director's scene and action permissions. A plugin choosing the console as director is explicitly exercising server authority. There is no remote API, HTTP service, telemetry or license check.

| Method | Contract |
| --- | --- |
| `sceneIds()` / `actorIds()` | Immutable current ID list |
| `scene(id)` | Immutable scene definition; unknown ID throws |
| `saveScene(scene)` | Validate action types/limit and queue persistence; reject edits while running |
| `play(id, director)` | Preflight permissions and targets, acquire resources, fire start event, return run UUID |
| `pause(id)` / `resume(id)` | Transition a running/paused timeline; invalid state throws |
| `stop(id, restore)` | Cancel active run, optionally restore; false retains a resettable take |
| `reset(id)` | Stop/restore active run or restore last completed take |
| `deleteScene(id)` | Cancel/restore active run and soft-delete definition |
| `actor(id)` | Actor view; unknown or deleted ID throws; `entity()` may be empty when hidden/unavailable |
| `createActor(id, type, location)` | Create owned actor using the configured random identity policy; PLAYER requires Citizens |
| `removeActor(id)` | Cancel dependent scenes, despawn and soft-delete actor |

`Actor` exposes `id()`, `group()` and `Optional<LivingEntity> entity()`. Do not retain an entity handle across respawn. Entity changes must follow Paper's thread rules. Prefer scene actions for coordinated playback so resource conflicts and restoration are managed.

Since 0.1.4, an uncancelled NPC death deletes its active actor definition. Its ID immediately disappears from `actorIds()`, an already retained actor view returns an empty `entity()`, and scene restoration cannot respawn it. A cancelled Paper death event leaves the actor registered. Death deletion is independent of whether the damage came from gameplay or a scripted kill.

The actor ID remains stable independently of its generated/displayed name or skin. Since 0.1.1, creation follows `npc-identities.yml`: automatic random names and PLAYER skin owners are enabled by default. Skin resolution may complete after `createActor` returns. Existing definitions retain their saved identity across restarts.

## Events

Register ordinary Bukkit listeners:

```java
@EventHandler
public void beforeScene(SceneStartEvent event) {
    if (productionIsLocked()) event.setCancelled(true);
}

@EventHandler
public void finished(SceneCompleteEvent event) {
    getLogger().info("Finished " + event.sceneId() + " run " + event.runId());
}
```

`SceneStartEvent` has `scene()`, `runId()` and cancellation. It fires synchronously after preflight and before execution; resource availability is checked again after listeners return. `SceneStopEvent` exposes scene ID, run ID and reason. `SceneCompleteEvent` fires after timeline completion and automatic restoration when enabled. Cancellation does not fire a completion event.

Persistence is queued; `saveScene` is not a filesystem durability barrier. Definition creation is immediately visible through this API. Internal services, mutable actor definitions and storage classes are not supported integration contracts. The initial API version is 0.1; binary compatibility is only promised within matching published versions.
