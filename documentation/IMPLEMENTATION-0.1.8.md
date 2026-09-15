# 0.1.8 engineering notes

Release record for **EasyScripting 0.1.8**, implementation commit `b943d64e92b39bc668ee548c011ba989c483bbdc`. These notes describe the group movement, mass creation and identity-lifecycle work. Current service ownership is summarized in [ARCHITECTURE.md](ARCHITECTURE.md).

## Delivered changes

| Concern | Implementation |
| --- | --- |
| Followers circling, snapping or moving unnaturally | Compact trailing rows derived from a smoothed leader movement heading, normal walking speed, distance-gated sprint catch-up, goal-change thresholds and intermediate path waypoints without teleport recovery |
| Leader unable to hit their own NPCs | Directional friendly policy: member sources cannot hurt their own leader/team; the real leader remains an ordinary damage source |
| Unsafe or unequipped mass spawns | Existing-group and required-kit preflight, front/behind oriented line/circle/filled-disc/grid/filled-square layout, distinct X/Z checks and highest-safe-surface projection before any entity is created |
| Repetitive manual group creation | Persistent PDC-bound group actor tool with required kit/type, current-op checks, safe terrain placement and a monotonic group-owned `<group>-actor-N` counter |
| Repeating changes across a group | Saved shared Immortal/kit values and batch identity randomization through commands and GUI; group deletion now deletes every member actor |
| Patterned or reused generated usernames | Bounded asynchronous Random User username and Craftdex skin-owner caches, local readable fallback and one shared case-insensitive reservation policy |
| Reusing a dead character's identity | Persistent `state/dead-users.yml`, natural actor/nicknamed-player death hooks, list/search/remove commands and a paginated profile-head GUI |

## Group movement and combat policy

`GroupTactics.movementHeading` favors real horizontal leader velocity and smooths changes across ticks. When the leader stops, it retains the previous heading instead of rotating the group whenever the leader looks around. `trailingFormation` assigns deterministic compact rows behind that heading. Followers stop within the configured arrival radius and look at the leader only after reaching their slot, avoiding a manual look call fighting active path rotation.

FOLLOW uses ordinary Citizens/Paper paths. Its own short refresh interval still respects the global new-path budget. Goal movement, arrival, catch-up distance, normal/catch-up speed and maximum intermediate waypoint length are validated settings in `actor-ai.yml`. There is no catch-up teleport. A missing, dead, spectator or acting leader stops the members and clears current enemy state.

Friendly filtering identifies the attacking **member** group separately from the victim group. A member NPC or a real player acting as that NPC cannot damage another member or its real leader. Projectile shooters, TNT sources, area-cloud sources and evoker-fang owners are resolved; harmful allied potion effects are removed while beneficial effects remain. A real leader has no member-source group, so their attacks can damage and knock back their own actors without turning the leader into a retaliation target.

## Formations, safe terrain and tools

`/actor pattern` requires a registered group and saved kit before layout work. Line, circle, filled disc, grid and filled square layouts use the leader/creator yaw and shift their complete footprint in front of or behind the anchor. Disc cells are selected nearest-first from a grid; square/grid cells use compact filled rows. Actor IDs retain the established `<group>_<n>` form.

The preflight validates count, prefix length, global actor capacity, entity type, loaded chunks, world border, unique X/Z columns and every standing surface. Surface resolution uses `HeightMap.MOTION_BLOCKING_NO_LEAVES`, a solid nonhazardous floor and three clear blocks. A failure before spawn creates nothing; a later initialization failure removes all actors made by that batch.

`GroupActorTool` stores a schema marker, group, kit and living entity type in item persistent data. Main-hand block clicks revalidate current operator status, group, kit and safe terrain, then create one member with the explicit kit and group shared Immortal value. The group persists `next-actor-index`; collisions are skipped and copied tools share one monotonic sequence. The item material, default type, 1–100 tick cooldown, MiniMessage name and lore live in `items.yml`.

`ActorGroup` schema 1 gained optional `shared.immortal`, `shared.kit` and `next-actor-index`. Missing shared values mean members retain individual values. Shared Immortal and kit apply to current members and actors later transferred into the group. Pattern/tool creation always requires and applies its explicit kit. Identity randomization gives each member a fresh identity and works during ordinary following or replay; scene/acting reservations still prevent conflicting changes. Group deletion snapshots and deletes its members before removing the group definition.

## Identity generation and retirement

Public identity fetching is separate from Minecraft profile resolution. `UsernameClient` uses a two-worker bounded executor, strict timeouts, response-size/candidate caps and fixed HTTPS endpoints. Random User v1.4 contributes login usernames; Craftdex contributes current Minecraft account names used only as skin owners. `IdentityProvider` keeps at most 512 username and 128 skin-owner candidates, refreshes on a configured interval or low water, and never blocks actor creation while waiting.

Every generated actor or `/nickname` name must use 5–16 letters, digits or underscores, contain at least one letter, and contain at least one digit or underscore. Case-insensitive exclusion covers active actor/nickname names, including aliases temporarily hidden while acting, the identity blacklist, every real account known to have joined the server, current operators and the dead-user registry. Actor copies allocate a fresh identity before spawning instead of inheriting a duplicate username. The readable fallback lowercases configured fragments and adds a digit or underscore, avoiding the old CapitalCapital pattern. Skin-owner names are a separate reusable pool.

`DeadUserRegistry` stores case-insensitive, idempotent entries with displayed name, actor/player kind, owner ID, death time and skin details. `ActorService.onDied` fires only for natural actor death; manual actor deletion and group deletion use the ordinary deletion callback without retirement. A real player who dies while temporarily nicknamed retires the alias and restores their normal identity on the next tick when still online.

The `/deadusers` command uses the existing `identity` access policy. Its list/search form opens a 21-entry page with stored heads where possible; Previous/Next preserve the query. Shift-right-click or `remove <username>` explicitly releases a case-insensitive entry. This makes the name eligible for future generation but does not restore a deleted actor.

## Lifecycle and compatibility

Provider HTTP work is asynchronous, but candidate selection, reservations, Bukkit state and GUI work stay on the server thread. Refresh uses TickEngine polling and is cancelled before executor shutdown. `/es reload` refreshes providers without discarding usable cached candidates. Missing `actor-ai.yml`, `items.yml`, `npc-identities.yml` and `guis.yml` leaves inherit shipped comments/defaults while retaining owner values.

No new runtime dependency is bundled. PLAYER actors still require a compatible Citizens installation. Random User and Craftdex failures fall back locally; they do not disable actor creation. The public Java facade is unchanged, so 0.1.8 remains source-compatible with the documented 0.1 API.

## Validation and known limits

Validation totals: **160 unit tests**, production/smoke compilation, Paper 1.21.11 production compilation, baseline rebuild, project/JAR inspection and documentation-link checks. Artifact SHA-256: `401043CA19B4B37C61586AA5376A9AABD15790142FA5629930C10B8C21189D07`. No Minecraft server or client was started for 0.1.8.

Tests cover deterministic layout/math, directional damage policy, group schema/counter state, generated-name rules, bounded provider parsing, dead-user persistence and command/GUI/config structure. They do not reproduce live Citizens path appearance, uneven-world heightmap behavior, profile-provider uptime, graphical heads/skins or large-group tick cost. The exact multiplayer acceptance cases remain in [TESTING.md](TESTING.md).

## Public references consulted

- [Paper World API](https://jd.papermc.io/paper/1.21.8/org/bukkit/World.html): highest-block queries and loaded-world access.
- [Bukkit HeightMap](https://jd.papermc.io/paper/1.21.8/org/bukkit/HeightMap.html): `MOTION_BLOCKING_NO_LEAVES` semantics.
- [Paper PersistentDataContainer](https://jd.papermc.io/paper/1.21.8/org/bukkit/persistence/PersistentDataContainer.html): durable item bindings.
- [Random User documentation](https://randomuser.me/documentation): pinned API version, result count and login-field filtering.
- [Craftdex data API](https://craftdex.net/data): public top-profile/skin-owner data.

These sources document platform/provider contracts used by this implementation. They do not establish future availability or runtime behavior of a particular server/Citizens build.
