# EasyScripting architecture

The repository was empty on 2026-09-13. No pre-existing code, build or repository instructions were present.

* Target matrix: Paper/Purpur 26.2 primary; 26.1, 1.21.11, 1.21.8 compatibility candidates. JDK 25 toolchain, Java 21 bytecode, shared public Paper APIs, server-provided Adventure. No NMS or Folia claim.
* Build/artifact: Gradle Kotlin DSL, single deployable plugin and sources JAR, no bundled Paper/Citizens/voice chat classes. Version expansion in source plugin.yml.
* Plugin model: conventional plugin.yml; EasyScriptingPlugin owns constructor wiring and enable/disable.
* Lifecycle: validate and load files during enable; register listeners, commands and public service after initialization. Disable stops producers, restores temporary state, removes owned actors, and drains writes.
* Registration: /es namespace, /scene and /actor aliases through dedicated routers; event listeners own gameplay restrictions and inventory protection.
* State: schema-versioned YAML definitions and runtime stores, validated identifiers, atomic replacement, bounded single-writer executor. Invalid source files are preserved and diagnosed.
* Thread model: synchronous entity/world access; Bukkit values are detached on the owning thread, then YAML encoding and atomic disk replacement run on a bounded writer. Large region captures create immutable plain records incrementally. Chat/voice callbacks read immutable/concurrent snapshots. Definition reads happen during startup or explicit administrative reload.
* Integrations: optional Citizens for real player NPCs; optional Simple Voice Chat for microphone moderation. Their classes are loaded only when dependencies are present. Core mob actors have no external runtime dependency.
* Validation: unit tests for pure scheduling/validation/serialization, build and artifact checks, exact-version compilation, isolated server startup and manual multiplayer acceptance.
* Risks: packet-sensitive NPC and skin behavior depends on the matching Citizens version. Server-side camera motion is bounded by vanilla client interpolation. Public screenshots show feature availability, not all hidden options.

## Implemented modules

1. `core`, `config`, `storage`, `api`: validated settings, feature registry, message rendering, atomic YAML, immutable scene definitions and deterministic tick cursor.
2. `actors`, `integration`: owned actor registry and backend interface, mob and Citizens adapters, transforms/equipment/navigation, lifecycle cleanup, recording paths.
3. `scenes`, `players`: action registry, resource locking, timelines, snapshot/reset, recording sessions and disconnect/world-unload cancellation.
4. `items`, `world`, `utilities`: kits, warps, rollback, environment and production controls, opt-in moderation/protection.
5. `commands`, `gui`: permission-gated modules and configurable inventory holders with cancellation, pagination, confirmations and editing.
6. Tests and documentation follow each coherent group. API events run synchronously; saves capture state before dispatch. Failed writes are reported; disabling invalidates late callbacks.

## Ownership and bounded work

0.1.2 extends RecordingService with explicit end modes and equipment/pose frames while reading legacy recordings. ActingService owns temporary performer possession using PlayerService snapshots, actor reservations and the shared tick engine. ActorDefinition persists the selected recording/mode. MenuService provides actor sections and direct combat toggles backed by guis.yml. All entity/profile changes and event cleanup stay on the main thread; detached saves keep using YamlStore. Acting restores on finish/cancel/death/quit/disable and rejects conflicting scenes/cameras. No new production dependency, NMS or Folia support is introduced.

Acting profile changes use Paper's public `setPlayerProfile` API, which keeps the real player's UUID and changes the visible name/properties ([1.21.8 contract](https://jd.papermc.io/paper/1.21.8/org/bukkit/entity/Player.html#setPlayerProfile(com.destroystokyo.paper.profile.PlayerProfile)), [26.2 contract](https://jd.papermc.io/paper/26.2/org/bukkit/entity/Player.html#setPlayerProfile(com.destroystokyo.paper.profile.PlayerProfile))). The original profile is serialized only for acting checkpoints; ordinary scene/take snapshots retain their existing identity contract. The actor is suspended without persisting a temporary hidden flag, so a restart loads its saved definition. Stop playback releases ownership and retains the final frame; explicit cancellation restores the starting snapshot. A pure PlaybackCursor defines endpoint order, including single-frame recordings.

Citizens equipment is initialized through its Equipment trait, and immediate name/skin edits capture current equipment before Citizens can respawn the entity ([Equipment implementation](https://github.com/CitizensDev/CitizensAPI/blob/master/src/main/java/net/citizensnpcs/api/trait/trait/Equipment.java)). Recording playback uses Paper's visual-fire state for flame cues, restoring its previous value on completion; it never converts recorded flames into actual fire damage ([Entity API](https://jd.papermc.io/paper/1.21.8/org/bukkit/entity/Entity.html#setVisualFire(net.kyori.adventure.util.TriState))).

Scenes reserve entity/world targets. Movement playback shares actor reservations, preventing competing timelines. Spawned actors hold reference-counted plugin chunk tickets; cleanup releases them. Temporary projectiles and boats belong to their creating services. A single tick engine runs only while jobs exist.

NPC identity pools are immutable validated configuration. Creation and explicit randomization choose from a bounded set after excluding occupied/blacklisted names; IDs remain stable. Name and skin owner are separate persisted fields. Only the Citizens adapter touches `SkinTrait`: Citizens resolves profiles asynchronously, while EasyScripting captures completed texture/signature data on the existing actor behavior pulse every 20 ticks. Changed caches use the bounded YAML writer; no per-NPC timer or additional network executor is created. Saved signed textures are reapplied on spawn, and an explicit skin edit invalidates the cache. Legacy definitions without cached textures remain readable.

Region jobs allow one operation per world and use the concurrent-scene count as their global cap. Capture, full validation and block application have per-tick budgets. Coordinates, block data, container items and sign text are validated before the first mutation. Loaded-chunk checks prevent implicit synchronous loads. Cancellation/unload stops further writes; a multi-tick restore is not a transaction and does not undo already applied blocks. Containers use snapshot inventories before block-state updates.

Reload validates a candidate configuration before publication. Deferred player snapshots survive graceful disconnect/death; active take snapshots remain memory-only until deferral. A process crash cannot guarantee recovery of active takes. Test fixtures are separate artifacts and never included in the production JAR.
