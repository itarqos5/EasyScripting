# 0.1.7 engineering notes

## Architecture and compatibility

- Target matrix: Paper/Purpur shared 1.21.8 API, additional 1.21.11 compilation; Java 25 toolchain emitting Java 21 bytecode. Public Bukkit/Paper APIs and server-provided Adventure; no NMS mappings or Folia claim.
- Build/artifact: one Gradle 9.3.1 module, EasyScripting JAR and sources JAR. `plugin.yml` version expansion; no shaded libraries. Smoke source set is separate.
- Plugin model: conventional `plugin.yml`, `EasyScriptingPlugin` entrypoint, optional Citizens and voicechat integrations.
- Lifecycle: entrypoint owns services, reverse-order close, TickEngine rejects new work before cleanup. Preserve partial-enable cleanup.
- Registration: existing CommandRouter and main-thread Bukkit listeners; groups join the same registration path.
- State: existing actor YAML group field remains membership authority. New `groups/<id>.yml` stores leader/order/intelligence; `actor-ai.yml` holds validated movement/combat settings. No shared inventories or health.
- Thread model: game events, AI and navigation stay on the main thread through TickEngine; YamlStore owns detached asynchronous disk writes.
- Integrations: Paper and Citizens remain compile-only; voicechat is unchanged. No new runtime dependency. Citizens is required only for player NPCs.
- Validation: unit tests, build, smoke compilation, 1.21.11 API compilation, artifact inspection. No server launch, as requested. Navigation feel and 100-NPC performance remain runtime acceptance gates.
- Risks: pathfinding depends on loaded terrain and installed Citizens; combat must respect external event cancellation, knockback, replay leases and identity refreshes. Half-heart protection must allow vanilla totem consumption and stasis callbacks.

## Implementation plan

1. Fix eye-origin facing in `Positions`; extend `ActorBackend`, `CitizensBackend`, `MobBackend` and `ActorService` with navigation status and backend-aware looking. Put social wandering and safe local ground sampling in actor movement helpers. Keep a fixed home fallback, throttle retries and do not replace manual paths with wander paths.
2. Add group definitions, deterministic target allocation/formation helpers and `ActorGroupService`. Reuse actors' group IDs and native melee attacks. Hold/follow/move/combat intents own autonomous navigation, yield to recordings/scenes, pause after hits, and stop on unload/disable/leader absence. Live target sets are temporary; groups/leader/intelligence persist, battles do not resume after restart.
3. Register `/es group` and YAML-backed menus in existing command/GUI services. Operators manage membership and leaders can issue orders only to their own groups. Existing bulk actor group commands remain supported.
4. Change `PlayerService` half-heart interception to allow held totems through vanilla resurrection, retaining protection afterward and protecting against failed resurrection.
5. Add focused tests for orientation, safe destination selection, target balancing, group persistence/permissions and half-heart policy. Update all command/use/config documentation and release artifacts after successful checks.

## API evidence (2026-09-15)

The follow-up scope adds persisted actor supplies and an opt-in aggressive state, automatic totem offhand/refill, three beneficial splash potions before retaliation, probabilistic melee/jump/shield reactions, a simpler GUI, `/actors` and `/kits`, a home recording-session toggle and contextual command help. Extend the existing group director for standalone aggression and use a separate ActorCombatService for item reactions. All callbacks remain on TickEngine/the server thread and yield to actor leases. Paper `LivingEntity.startUsingItem` is experimental: isolate shield activation in the combat service, compile on both API targets, and mark live blocking behavior as unverified without a server. One tick (50 ms at 20 TPS) is the minimum safe inventory-switch delay; no asynchronous Bukkit inventory access.

- [Paper 1.21.8 LivingEntity](https://jd.papermc.io/paper/1.21.8/org/bukkit/entity/LivingEntity.html): eye locations and native `attack`, including equipment/attribute damage and knockback.
- [Paper 1.21.8 Pathfinder](https://jd.papermc.io/paper/1.21.8/com/destroystokyo/paper/entity/Pathfinder.html): native mob navigation and path status.
- [Citizens Navigator implementation](https://github.com/CitizensDev/Citizens2/blob/master/main/src/main/java/net/citizensnpcs/npc/ai/CitizensNavigator.java): setTarget clones default parameters; configure defaults before setting destinations.
- [Citizens NavigatorParameters](https://github.com/CitizensDev/CitizensAPI/blob/master/src/main/java/net/citizensnpcs/api/ai/NavigatorParameters.java): speedModifier, distance margins, lookAtFunction, stationary timeout and stuck action. Compile against the repository's pinned 2.0.43-SNAPSHOT API.

## Elytra follow-up and completion

The reported swimming animation exposed a missing client gliding flag in replay frames. ReplayPose now captures/applies actual gliding alongside the body pose. Schema-3 recordings write the flag; legacy FALL_FLYING poses infer it. EntityToggleGlideEvent prevents vanilla's between-frame landing reset only while replay owns that NPC's animation; knockback yields to physics, and stop releases the flag. EntitySnapshot retains gliding across performer/identity restoration. The deprecated no-op swimming setter is deliberately unused; normal swimming still uses the saved pose.

API references: [Pose](https://jd.papermc.io/paper/1.21.8/org/bukkit/entity/Pose.html), [EntityToggleGlideEvent](https://jd.papermc.io/paper/1.21.8/org/bukkit/event/entity/EntityToggleGlideEvent.html) and the LivingEntity reference above. Source changes are confined to the existing command/service/backend architecture; no server internals or added runtime dependency. Unit/API/artifact evidence and unverified client/server acceptance are recorded in TESTING.md.
