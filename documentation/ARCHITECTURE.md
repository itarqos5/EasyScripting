# EasyScripting architecture

Current implementation: **0.1.7**. This is the architecture of the shipped code. Version history is in [CHANGELOG.md](CHANGELOG.md); release-specific implementation decisions are in [IMPLEMENTATION-0.1.7.md](IMPLEMENTATION-0.1.7.md).

## Build and supported execution model

One Gradle 9.3.1 project builds `EasyScripting-0.1.7.jar` and its sources companion. The JDK 25 toolchain emits Java 21 bytecode. The default compile API is Paper 1.21.8; `-PpaperVersion=1.21.11-R0.1-SNAPSHOT` selects the additional compatibility gate. `plugin.yml` uses Gradle version expansion and declares the minimum API as 1.21.8. Adventure is provided by the server.

The project targets Paper/Purpur, with Paper 26.2 as its original primary target. Release 0.1.7 has unit/build/API validation against 1.21.8 and 1.21.11 only. Historical 26.x and runtime evidence is separated in [TESTING.md](TESTING.md). There is no Folia or Spigot support claim, NMS adapter, custom server protocol implementation or shaded Paper/Citizens dependency.

`src/smoke` is a separate source set. Its optional `smokeJar` is a test companion, never part of the deployable plugin. The production JAR bundles configuration and integration adapter code; optional plugins remain server installations.

## Service map

| Area | Main responsibilities |
| --- | --- |
| `EasyScriptingPlugin` | Constructor wiring, configuration load, Bukkit registration, service publication and reverse-order shutdown |
| `core` | TickEngine job ownership, timeline cursor, identifiers, bounded values and eye-origin facing |
| `config` / `storage` | Validated settings, feature/access policies, messages, GUI migration and detached asynchronous YAML writes |
| `actors` | Actor definitions/entities, resource reservations, social wandering, group allocation/orders and carried-item combat reactions |
| `integration` | Optional Citizens and voice adapters, fixed public kit-provider adapters and bounded import jobs |
| `recording` | Performer capture/restore, frame playback, mode cursor, gliding state, autoplay, knockback recovery and cameras |
| `scenes` / `players` | Timelines/actions, entity/world reservations, snapshots, player flags, death policies, identities and temporary nicknames |
| `items` / `world` / `utilities` / `effects` | Kits, item tools, warps, region jobs, locks, server sessions/chat, scoreboard teams, villagers and bounded effects |
| `commands` / `gui` | Command authorization/feedback, contextual help, aliases, inventory holders, editors, pickers and confirmation/input flows |
| `api` | Main-thread facade, immutable scene definitions and synchronous scene lifecycle events |

## Lifecycle and thread ownership

Enable constructs owned services, loads validated configuration and saved definitions, registers listeners/commands, then publishes `EasyScriptingApi` through Bukkit ServicesManager. Optional Citizens classes are reached only when its backend is enabled and available. Failed initialization uses the same cleanup path as normal disable.

Every game-world, NPC, inventory, GUI and provider API operation runs on the server thread. TickEngine supplies one shared scheduled pulse and runs only while jobs exist. Timelines preserve equal-tick ordering and fail a take if its action budget is exceeded instead of silently shifting action times.

Nickname HTTP work runs on a bounded executor; completion checks connection/request validity before applying state on the server thread. Async chat/voice paths read appropriate cached or concurrent state. Public chat blocking uses the synchronous chat event to check current operator status. It cannot rely on stale permissions after de-op.

Disable first marks shutdown and makes TickEngine reject new jobs, then unregisters the public service and closes owned resources in reverse order. Services stop producers, release reservations, restore temporary state where possible and remove owned entities. Cleanup continues if one service fails. The YAML writer drains near the end; remaining Bukkit tasks and listeners are cancelled/unregistered. This prevents actor saves or autoplay restoration from registering tasks after the plugin is disabled.

## Actor identity and movement

Actor IDs are stable keys, independent of names and skins. `ActorService` owns definitions, live entity bindings, visibility, leases and reference-counted chunk tickets. Mob actors use Paper navigation; PLAYER actors use Citizens. NPC defaults are mortal, hittable and autoplay enabled. Cached signed skin textures persist without rerolling identity on restart.

Citizens navigation sets default speed before `setTarget` clones the parameters. During walking, its look override tracks player eyes without fighting the path's body rotation. Idle looking uses the rotation trait; mob facing also starts at eye height. Wander destinations are sampled on nearby loaded terrain around a visible player, another nearby actor near home, or a fixed home fallback. A blocked path is retried; it is not resolved by teleporting through terrain. Home is set when the actor is constructed/loaded and resets on explicit teleport or enabling Wander.

Identity refreshes can replace a Citizens entity. The adapter snapshots runtime state and equipment, rebinds the actor, and restores current presentation. Replay pauses up to 100 ticks for that replacement; stop cleanup can wait for the replacement entity. API callers must reacquire `Actor.entity()` rather than retain entity references across ticks.

## Combat groups and supplies

`ActorDefinition.group` is membership authority. A matching `groups/<id>.yml` registers a managed faction with a real-player leader UUID, Intelligence and Follow/Hold order. `default` remains unassigned. Pattern tags, managed factions and scoreboard teams are separate concepts. A player can lead one faction; each NPC keeps its own equipment, backpack and entity stats.

`ActorGroupService` owns Follow/Hold/Move orders, temporary targets and enemy factions. `GroupTactics` allocates members across eligible targets with distance and previous assignments as tie-breakers, and builds spread formation positions. Decisions are staggered; path creation has a shared per-tick budget. Out-of-range/dead targets are dropped, an unavailable leader pauses activity, and battles/Move destinations do not persist across restart.

Friendly damage/knockback is filtered for members and leaders, including supported projectile/explosion owners and harmful allied splash/cloud effects. Intelligence controls group attacks and reactions; an ungrouped actor can opt into standalone aggression. Idle aggressive actors may still wander. Native `LivingEntity.attack` uses individual equipment/attributes and normal damage handling; no winner or shared resource total is calculated.

`ActorCombatService` coordinates one-tick-or-longer carried-totem refill, up to three actual beneficial splash projectiles, accuracy/cooldown jitter, delayed jumps and probabilistic shields against overhead mace threats. `ActorSupplies` conserves stacks and the item displaced from offhand. Player NPCs use live storage; mobs use their saved 36-slot reserve backpack. The shield call uses Paper's experimental `startUsingItem` API and remains a live compatibility gate.

Scenes, performer capture and replays reserve actors. Group/ambient navigation and autonomous item use yield to those reservations; recordings remain movement/equipment/animation capture, not a replay of real attacks or block edits. Damage pauses navigation so knockback can occur. Group battles and graphical combat behavior are a prototype with automated logic checks, not a measured 100-NPC performance guarantee.

## Recordings, sessions and player restoration

`ActingService` checkpoints the real performer before applying the NPC profile/costume, reserves the player/NPC and suspends the NPC. Finish/cancel restores the performer and actor, immediately or through `pending/` after reconnect/respawn. Acting blocks direct melee and its knockback; falls, projectiles and other environmental damage remain possible. The actor's Immortal setting does not make its performer invulnerable.

`RecordingService` stores schema-3 frames containing transforms, equipment, poses, explicit gliding and animation/fire cues. Legacy frames remain readable; FALL_FLYING implies gliding when no flag exists. `ReplayPose` sets gliding after equipment/teleport changes. A scoped glide-event guard prevents between-frame landing resets while replay owns the animation. Knockback temporarily yields to physics; stop restores normal ownership/gravity and clears replay flight state.

STOP holds the last position with normal physics restored, REPEAT restarts at the first frame, and REVERSE bounces along the path. Mode and identity can change during playback. Autoplay is a saved eligibility switch, separate from loop mode; manual Stop disables it. Received damage survives restoration. A permanently dead/deleted NPC cannot be resurrected by replay or scene reset. Its selected take is removed only after its last referencing NPC is removed; hide/unload/disable retain takes.

Server recording sessions are owned by `ModerationService`: `/es record on|off` changes MOTD/admission and configured chat/voice behavior. Non-operators cannot join or reconnect while active. `/es take start/stop` shares this session state and adds participant snapshots. Ordinary login locks remain separate. Session state and active takes are memory-only; deferred restoration is persisted. Player half-heart protection allows held vanilla totems through resurrection and stays enabled after a pop.

## Commands, menus and access

`CommandRouter` owns `/es` (aliases `/easyscripting`, `/script`), `/actor`, `/actors`, `/scene`, `/kits` and `/nickname`. Bare `/actors` opens the NPC library; `/kits` routes to plural `/es kits`. Singular `/es kit` remains the create/save/edit/apply/delete command family. Every route checks general access and its feature policy. A successful operation that did not reply receives fallback feedback; errors get documented syntax, descriptions and examples from `command-help.yml`.

Actual operator checks guard kit management/gifting, faction management and speaking while public chat is blocked. Group leaders can order only their assigned group with general use access. Legacy actor editing remains a staff capability. Kit self-claims follow operators/everyone/one UUID plus operators policy. Nicknames and connection changes cannot transfer UUID-based access.

`MenuService` binds actions to its own inventory holders, rejects unintended transfers and rechecks authorization on clicks. NPC overview cards open Identity & clothing, Movement, Record & replay and Combat & supplies. Studio Record session opens server-session ON/OFF. Editors use detached drafts with explicit Save; input belongs to the initiating UUID and expires after 60 seconds. Old GUI schemas are backed up before the full schema-3 layout is installed.

## Persistence and bounded operations

`YamlStore` snapshots Bukkit values on the server thread, then serializes/writes on a single bounded writer. Save comments, headers and inline comments are detached too. Writes use same-directory atomic replacement where supported. Queue rejection is reported; an accepted asynchronous save is not a durability barrier. Deleted definitions move to `trash/`.

Settings reload validates a candidate before replacing active settings. Missing configuration files/default leaves and shipped comments are inherited according to the file's migration rules; owner values/comments are preserved except documented backed-up migrations. Hand-edited actor/group/scene/kit/recording definitions reload on restart, not `/es reload`.

Region jobs allow one operation per world and a bounded number overall. Capture, validation and application use per-tick budgets and loaded-chunk checks; malformed records are rejected before the first restore mutation. Cancelling a multi-tick restore leaves completed block changes in place. Only supported block/container/sign state is stored.

Kit imports use fixed adapters for installed PlayerKits2, PlayerKits, EssentialsX and CMI, plus EasyScripting YAML. They import item definitions without invoking rewards/claims. Bulk import handles at most one kit per tick, keeps completed imports on cancellation, avoids overwriting IDs and stops on disconnect/de-op/disable. Reflection is isolated to provider compatibility; no server-internal reflection is used.

Snapshots do not cover arbitrary third-party state, scoreboard membership, personal borders or whole worlds. Hard crashes cannot guarantee recovery of in-memory active takes. See [configuration/storage](CONFIGURATION.md), [public API](API.md), [permissions](PERMISSIONS.md) and [test evidence](TESTING.md) for the detailed contracts and remaining runtime checks.
