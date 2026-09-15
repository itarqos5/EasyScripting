# 0.1.7 engineering notes

Completed release record for **EasyScripting 0.1.7**, published from `1ea6d202e249a3aba726d58d61e7f0966027060f`. These notes describe implemented work; current service ownership is summarized in [ARCHITECTURE.md](ARCHITECTURE.md).

## Delivered changes

| Concern | Implementation |
| --- | --- |
| Incorrect looking/slow wandering | Eye-origin facing, Citizens default navigation parameters before target creation, path-aware look override, grounded social destinations and bounded home fallback |
| Independent NPC factions | Saved group definitions and leader UUIDs, actor-tag membership, Follow/Hold/Move orders, balanced target allocation, friendly-fire filtering and native melee |
| Carried combat resources | Per-NPC backpack/offhand conservation, delayed totem refill, actual beneficial splash projectiles, imperfect attacks/jumps and delayed shield reactions |
| Half-heart suppressing totems | Held totems reach vanilla resurrection; player protection remains enabled; death-mode actions skip cancelled deaths |
| Elytra showing swimming | Explicit gliding in frame schema 3, old FALL_FLYING inference, scoped glide-state retention and stop cleanup |
| Confusing actor controls | Four overview cards, group/supply controls, selected modes, `/actors` and `/kits`, home Record session ON/OFF |
| Unhelpful command mistakes | Contextual syntax, descriptions, expected values and examples; existing success-feedback tracking retained |
| Undocumented/lost settings comments | Commented YAML defaults and saved actor/group definitions, additive comment inheritance, detached nested/inline-comment persistence |

The release was committed in three focused changes:

1. `015244a` — allow held totems to pop under half-heart protection.
2. `adbb6cd` — preserve elytra gliding in actor performances.
3. `1ea6d20` — NPC group combat, movement, controls, configuration, help and documentation.

The documentation follow-up audits all Markdown without changing the published plugin JAR or moving the release tag.

## Architecture decisions

The Gradle 9.3.1 project retains its conventional `plugin.yml`, Java 25 toolchain/Java 21 bytecode, shared Paper 1.21.8 API and additional 1.21.11 compile gate. Public Bukkit/Paper APIs and server-provided Adventure are used; there is no NMS/Folia support or new runtime dependency. PLAYER actors continue to require compatible Citizens; mob actors do not.

Game state and provider calls stay on the main thread through existing services and TickEngine. Groups/combat are owned services closed before actors/storage. TickEngine rejects new work before cleanup; reverse-order close and partial-enable cleanup remain intact. YamlStore owns detached asynchronous persistence.

Actor tags remain membership authority; groups add orders and allies without shared health or inventories. Active targets, group wars and Move destinations are temporary. AI yields to scene/replay/acting reservations, uses bounded/staggered navigation work and permits real knockback. Standalone aggression is separate from group Intelligence. Automatic carried-totem handling does not require Intelligence.

One server tick (about 50 ms at 20 TPS) is the minimum refill delay. No asynchronous inventory mutation or synthetic spare item is used. Shield activation is isolated in ActorCombatService because Paper marks `LivingEntity.startUsingItem` experimental; API compilation is not proof of a particular Citizens adapter's blocking/rendering behavior.

Recordings capture actual gliding independently of body pose, and ReplayPose applies it during playback. Applying only FALL_FLYING was insufficient for the reported animation. Schema-3 frames save the flag; old FALL_FLYING poses infer it. EntityToggleGlideEvent prevents an unwanted between-frame landing reset only while replay owns the NPC animation. Knockback yields to physics, stop releases the flight flag, and EntitySnapshot retains gliding across performer/identity restoration. The deprecated no-op swimming setter is unused. A legacy take containing only SWIMMING has insufficient information to infer an elytra flight and must be recorded again.

## Validation and known limits

**138 unit tests passed**, with production/smoke compilation, Paper 1.21.11 production compilation, a baseline rebuild, project/JAR inspection and documentation-link checks. The artifact has Java class major 65 and contains the new YAML resources. No server/client was started for 0.1.7.

Tests cover facing, target/formation allocation, group persistence/authorization, AI/combat settings, supply stack conservation, half-heart policy, gliding capture/apply/legacy frames, contextual help, YAML comments and GUI structure. They do not simulate live block pathfinding, totem animation, potion impact, native Citizens melee or shield behavior. Large-group performance, visual animation and interaction with installed plugins remain live acceptance gates in [TESTING.md](TESTING.md).

## API evidence consulted for the implementation (2026-09-15)

- [Paper 1.21.8 LivingEntity](https://jd.papermc.io/paper/1.21.8/org/bukkit/entity/LivingEntity.html): eye locations, native attack, gliding and carried-item APIs.
- [Paper 1.21.8 Pathfinder](https://jd.papermc.io/paper/1.21.8/com/destroystokyo/paper/entity/Pathfinder.html): native mob navigation and path status.
- [Citizens Navigator](https://github.com/CitizensDev/Citizens2/blob/master/main/src/main/java/net/citizensnpcs/npc/ai/CitizensNavigator.java) and [NavigatorParameters](https://github.com/CitizensDev/CitizensAPI/blob/master/src/main/java/net/citizensnpcs/api/ai/NavigatorParameters.java): parameter cloning, look overrides, speed, margins and stuck handling. Compilation used the repository's 2.0.43-SNAPSHOT dependencies.
- [Pose](https://jd.papermc.io/paper/1.21.8/org/bukkit/entity/Pose.html) and [EntityToggleGlideEvent](https://jd.papermc.io/paper/1.21.8/org/bukkit/event/entity/EntityToggleGlideEvent.html): independent body pose and flight-state transitions.

These are implementation references consulted for this release, not claims about future Paper/Citizens versions.
