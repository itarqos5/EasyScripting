# Java integration API

This describes the supported facade in **EasyScripting 0.2.0**. Compile against that release's plugin JAR; the sources JAR is for inspection.

The public API is in `dev.easyscripting.api`. Declare `depend: [EasyScripting]` in your plugin metadata, or `softdepend` and explicitly handle absence. Compile against the EasyScripting JAR; do not shade its classes into your plugin.

```java
import dev.easyscripting.api.Actor;
import dev.easyscripting.api.EasyScriptingApi;
import dev.easyscripting.api.Scene;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;

// Inside your plugin method: spawnLocation is a loaded-world Location,
// and director is the CommandSender whose scene/action permissions will be checked.
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
| `removeActor(id)` | Cancel dependent scenes/playback, despawn and soft-delete actor; remove its selected take when no other actor uses it |

`Actor` exposes `id()`, `group()` and `Optional<LivingEntity> entity()`. `group()` is the saved actor tag; it represents a combat faction only when that ID is registered in the group service. Do not retain an entity handle across ticks: respawn and name/skin refresh may replace it. Entity changes must follow Paper's thread rules. Prefer scene actions for coordinated playback so resource conflicts and restoration are managed.

Since 0.1.4, an uncancelled NPC death deletes its active actor definition. Its ID immediately disappears from `actorIds()`, an already retained actor view returns an empty `entity()`, and scene restoration cannot respawn it. A cancelled Paper death event leaves the actor registered. Death deletion is independent of whether the damage came from gameplay or a scripted kill. Since 0.1.8, natural actor death also retires its displayed username in `state/dead-users.yml`; an explicit `removeActor` or group deletion does not.

The actor ID remains stable independently of its generated/displayed name or skin. Creation follows `npc-identities.yml`: automatic random names and PLAYER skin owners are enabled by default. In 0.1.8 generated names are 5–16 Minecraft username characters, include at least one letter plus at least one digit or underscore, and exclude current actor/nickname names, blacklisted/retired names, current operators, and every real account remembered as having joined the server. Public username and skin-owner pools refresh asynchronously; a readable local fallback is used without blocking actor creation. Skin resolution may complete after `createActor` returns. Existing definitions retain their saved identity across restarts.

## Scope and ownership

The facade has no public group, kit, nickname, dead-user-registry, recording-session or replay-controller methods. Those workflows are exposed through the documented commands/GUI; the internal services and mutable definitions are not stable integration APIs. The integrating plugin must authorize its own create/remove/save operations: those methods have no sender whose permissions could be checked. Only `play` receives a director and checks that director's permissions.

Scene and replay reservations prevent competing controllers. Group AI and supply reactions yield to reserved actors, then may resume after release; callers that manipulate entities directly must coordinate that themselves. Defaults still apply to API-created actors: randomized identity when enabled, Immortal OFF, Hittable ON, and no implicit managed faction. A deleted actor ID may be reused for a new actor; reacquire the view instead of reusing a stale reference.

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
