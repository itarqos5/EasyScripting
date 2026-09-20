# NPC movement, groups and combat

EasyScripting 0.1.8 provides configurable group movement and combat. Each NPC owns its health, equipment and carried supplies. A group supplies orders, allies and optional shared defaults; it does not pool health, inventories, hunger or XP, and it does not choose a winner in advance. Paper handles damage, armor, potion effects, knockback and death. PLAYER actors require a Citizens build for your server version.

## Start with one NPC

```text
/actor create guard
/actors
/actor set guard wander on
/actor set guard look on
```

`/actors` opens the NPC library. Select the NPC, then choose one of four large cards:

| Card | Use it for |
| --- | --- |
| Identity & clothing | Name, skin, random identity, costume, nametag and tab-list visibility |
| Movement | Walk to you, teleport, looking and short social wandering |
| Record & replay | Act as the NPC, finish/cancel, choose a take, change end mode, play/stop and autoplay |
| Combat & supplies | Health, Hittable, Immortal, Aggressive, kit and group |

Back returns to that NPC's overview. Home returns to the studio. Green/enchantment highlighting marks selected modes and enabled switches. The group card opens membership/orders. Edit labels, slots, materials and lore in `guis.yml`.

Wandering chooses short grounded paths around a nearby visible player. With no nearby player, it stays near other NPCs around its starting position, or near that starting position itself. It does not repeatedly move its home farther away. Teleporting the NPC or enabling Wander again sets a new home. Home is reset to the saved location on restart. Looking uses the player's eyes and continues while walking. Navigation depends on a walkable route: blocked terrain, unloaded chunks and steep drops are not bypassed with teleports.

## Give the NPC real supplies

1. Put a weapon, armor, extra totems, beneficial splash potions and optionally a shield in your inventory. Put your intended weapon in hotbar slot 0 for a mob kit.
2. Run `/es kit save fighter` to save your inventory, or open `/kits` and create/import/edit a kit.
3. Run `/es kits claim fighter actor:guard` or choose **Give to NPC** in the kit GUI.
4. Enable **Aggressive** in the NPC's Combat & supplies page, or use `/actor set guard aggressive on`.

Kits replace a loadout. They do not give a group one shared inventory. Applying the same kit to several NPCs gives each its own copy. Only operators can create, edit, import or gift kits. See [all kit commands](COMMANDS.md#kits).

Outside scenes/recordings, a carried totem moves to the offhand automatically. The displaced item moves into the vacated inventory slot. After a vanilla totem pop, another carried totem is equipped after `combat.totem-refill-ticks` (default **1 tick**, about 50 ms at 20 TPS). A main-hand totem works too. No replacement is created when supplies run out. Immortal NPCs cannot reach an ordinary lethal hit, so use **Immortal OFF** when testing normal totem pops.

An aggressive NPC retaliates after an uncancelled hit. An intelligent group member can also defend allies or attack an ordered enemy. Before retaliation, it throws up to three **carried beneficial splash potions** straight upward, one at a time. Fewer supplies mean fewer throws. Harmful potions and drinkable potions are not used by this defensive routine. Throws create actual potion projectiles and consume the items; terrain and splash range matter.

Melee has configurable accuracy, cooldown jitter and a chance to jump after a hit. A missed attempt swings but does not deal damage. These are intentionally imperfect prototype reactions, not a trained PvP bot.

With `weapon-cooldown: true` an NPC waits for its held weapon to finish recharging before swinging, the way a player does, instead of attacking on a fixed timer and landing partly charged hits for a fraction of the weapon's damage. `attack-cooldown-ticks` then acts only as a floor; raise it to deliberately slow NPCs below what their weapon allows. The shipped default is **10**, low enough for the weapon to set the pace. An `actor-ai.yml` carried over from an earlier version keeps its own value, so lower it to 10 to get weapon-paced attacks on an existing server. `crit-jump-chance` gives a ready attacker a chance to hop first and land the blow while falling, which Minecraft scores as a critical hit. Reach is measured from the attacker's eyes to the nearest point of the target's hitbox, so an enemy standing on a slab or a stair can be hit.

An engaged member reassesses every two ticks rather than every five, so its swings are not quantised into misses. Several members sent at one enemy take separate places around it instead of stacking on its block, and an enemy that dies is reassigned immediately rather than at the next sweep, so a squad does not keep swinging at a corpse.

### Shields, apples and pearls

A carried shield may be raised after a random delay against a non-allied mace holder. The NPC reacts to a mace directly overhead, which is the falling smash, and — within `shield-ground-radius` — to one simply walking in with a mace at its own level. It can notice either threat before the first hit. While the mace holder is still a threat the guard stays up past `shield-hold-ticks`, up to `shield-max-hold-ticks`, after which the shield drops so the NPC can fight back instead of blocking forever. A failed reaction roll or an exhausted inventory leaves it unprotected. Shield effectiveness follows Minecraft's facing/attack rules and the installed Citizens adapter.

The `survival` section adds two graded reactions, both of which need the NPC to actually carry the item:

| At or below | Reaction |
| --- | --- |
| `heal-health` (default 0.45) | Break off, back away `retreat-distance`, then eat a carried golden apple. |
| `escape-health` (default 0.35) | Throw a carried ender pearl away from the fight and keep running. |

An NPC gaps before it runs, so `escape-health` may not be set above `heal-health`. Eating stows the weapon, holds the apple, plays the real eating animation and hands the consumption to Paper, so the apple's own effects apply exactly as they would for a player and nothing is invented. A hit taken mid-meal interrupts it and returns the apple, the way it does for a player. A cornered NPC that cannot open a gap eats where it stands rather than backing into a wall forever, and an NPC that finishes a fight badly hurt still patches itself up once its cooldown allows. If Paper does not simulate item use for a particular entity the apple comes back untouched rather than being destroyed.

A landing ender pearl hurts whoever threw it for 5 health, so an NPC that would die to its own pearl keeps fighting instead. Vanilla only teleports PLAYER actors; a mob actor throws the pearl and stays where it is. Neither reaction creates supplies: an NPC with no apples does not heal and an NPC with no pearls does not escape.

### How a fight moves

An NPC sprints while closing on an enemy further away than `sprint-chase-distance` and walks inside it, the way a player drops sprint before a hit to keep their knockback. In reach and waiting on its weapon it circles the target at the distance it already holds — `strafe-chance` and `strafe-interval-ticks` set how often — rather than standing perfectly still, and a sidestep in progress is allowed to finish instead of being cancelled two ticks later. While healing or escaping it backs away at chase speed, still facing its enemy, so a retreat reads as a retreat rather than as the NPC losing interest. A meal or a raised shield pins it in place until it is done.

Idle aggressive NPCs may still wander when Wander is enabled. A fight takes priority, and so does a shield, a meal, a potion burst or a retreat, so an NPC is never pulled into a wander partway through one. Group orders take priority over ambient wandering. A scene, actor performance capture or replay has an exclusive reservation; autonomous movement and item reactions yield until it ends.

## Create a group and appoint its leader

As an operator:

```text
/es group create red
/es group add red guard
/es group leader red Alex
/es group gui red
```

Alex must be a real online player who is not acting as an NPC. One player can lead one group. Assigning a leader starts following; use `/es group leader red off` to remove the leader.

Friendly protection is directional. An NPC member, or a real player currently acting as that NPC, cannot damage or knock back another member or the real leader through melee, projectiles, owned TNT/fangs, splash potions or lingering clouds. Helpful potion effects can still apply. The real leader may hit and knock back their own NPCs; doing so does not cause those members to retaliate against the leader.

Operators manage groups. A leader can open their group's GUI and issue orders without operator status, provided they have the general `easyscripting.use` access. A leader cannot edit membership, intelligence or ownership. The legacy actor administration commands remain staff tools; do not grant `easyscripting.actor` to ordinary leaders.

| Command | Meaning |
| --- | --- |
| `/es group follow red` | Follow the online leader in compact rows behind their movement direction. |
| `/es group move red` | Move the formation to where you are standing. |
| `/es group hold red` | Stop movement and clear attack orders. `stop` is an alias. Intelligence can still defend against a new hit. |
| `/es group attack red Steve` | Attack an eligible nearby real player. Use `actor:enemy_1` to target an NPC. |
| `/es group fight red blue` | Engage another group's members and leader. An intelligent enemy group fights back. |
| `/es group intelligence red off` | Operator: turn off autonomous attacks and defensive combat reactions; movement orders still work. |
| `/es group immortal red on` | Operator: set every current member Immortal and apply that shared value to later members. |
| `/es group kit red fighter` | Operator: give every available current member a copy of the kit and remember it for actors later added to the group. |
| `/es group identities red` | Operator: randomize every member's username and skin; normal following or replay may continue. |
| `/es group tool red fighter PLAYER` | Operator: receive a persistent item that creates grounded, equipped members of this group. |
| `/es group remove red guard` | Operator: remove a member without deleting the NPC. |
| `/es group delete red` | Operator: permanently delete the group and every member NPC. |

In this example Alex is the leader and Steve is a different online player; the group refuses to attack Alex or its own members. Automatic totem handling also works when group Intelligence is OFF; that switch controls attacks and combat reactions.

When Intelligence is ON and the order is not Hold, a leader's successful hit adds the victim as an enemy. If the leader hits multiple enemies, NPCs distribute across them and reassess as enemies move, die or leave range. Engaging an enemy group recruits that group's members. There is no fixed damage multiplier, scripted winner or shared resource bar. A group with no surviving NPCs is still a saved group definition and may receive new members.

NPCs need **Hittable ON** to be eligible melee targets. **Immortal OFF** allows deaths to settle a battle. Immortal still permits hits and knockback, but an immortal combatant cannot lose by dying. A natural NPC death permanently deletes that NPC, retires its displayed username, and removes its selected take once no other NPC references it. Manual group deletion removes members without adding their names to Dead Users.

### How following moves

Follow slots form aligned rows and columns behind the leader. Every row is centred on the same lateral grid, so columns line up from row to row, and a partial last row is centred by whole slots rather than sitting half a space off. `follow-columns` chooses the width: `0` picks the squarest block that fits the group, so nine members form 3x3 and a hundred form 10x10, while `5` gives a narrow file and `20` a wide battle line. `follow-spacing` sets the distance between neighbouring rows and columns.

The rows are oriented by the direction the leader is actually travelling, sampled from how far they moved during each tick. The heading is heavily smoothed and retained while the leader is stopped, so looking around does not rotate the formation or make members cross through one another.

Members are numbered over those that are present and free, in a stable order. A casualty closes the gap instead of shuffling everyone into a neighbour's place, and a member with no numbered slot waits rather than piling onto the first one. A slot inside a wall or over a drop pulls in toward the leader instead of freezing that member where it stands.

Followers use ordinary Citizens/native paths at `follow-speed: 1.0`, stop within `follow-arrival-distance` of their slot and look toward the leader after arriving. A member that has taken its place only walks again once its slot has drifted a further `follow-resume-margin`; that gap keeps a settled formation from stuttering in and out of walking while the leader shuffles on the spot. At least eight blocks behind, members may use the configured 1.3 sprint-like catch-up pace. Very long routes are split into intermediate 32-block path targets; group following never teleports a lagging NPC.

The default follower goal refreshes every five ticks only after the slot moves at least half a block. The shared path budget is spent on whoever is worst off — members that are stopped, then those furthest from their slot — instead of on whoever happened to be considered first, so stragglers in a large group are no longer starved of paths. These thresholds, speeds, spacing, column count and the budget are all in `actor-ai.yml`. An unavailable/dead/spectator/acting leader stops the members and clears active enemies.

A **Move** order uses the same rows and columns, centred on the destination and facing the way the commander was looking when they issued it.

## Deploy a larger equipped faction

```text
/es group create red
/es group leader red Alex
/actor pattern red square behind 100 fighter 2 PLAYER
```

Create the `fighter` kit before running this example. Pattern creation now requires an existing managed group and kit. The assigned online leader is the formation anchor; otherwise the player running the command is used. Choose `front` or `behind`; supported shapes are `line`, `circle`, filled `disc`, `grid` and filled `square`. The entire footprint is moved at least three blocks to the selected side. Actor IDs generated by the example are `red_1` through `red_100`, skipping occupied IDs.

Every X/Z column is resolved independently using the world's highest motion-blocking surface while ignoring leaves. The floor must be solid and nonhazardous, the chunk must already be loaded and three passable blocks must be clear above it. Water, lava, fire, magma, cactus, campfires, powder snow, berry bushes and wither roses are rejected. The command validates all columns, the world border, actor limit and kit before creating the first entity; a creation failure rolls back the new actors. The global actor limit still applies (default 200).

Each NPC receives its own path and attack timing. New paths share a configurable budget, are ranked by need and are staggered across ticks. A 100-member formation is supported by the allocation and layout logic; actual server capacity depends on terrain, Citizens and server hardware and has **not been benchmarked** for this release.

## Use the bound actor tool

Run `/es group tool red fighter PLAYER` as an operator. The item stores the group, kit and entity type in persistent item data. Hold it in your main hand and right-click a block; EasyScripting uses that block's X/Z column, finds its highest safe surface, and creates one equipped member named `red-actor-1`. Later uses increment to `red-actor-2`, `red-actor-3`, and so on. The counter belongs to the group, so copied tools and server restarts do not reuse a number.

The group and kit are checked again on every use. A tool pointing to a deleted group or kit reports an error and creates nothing. Only current operators may issue or use it. Customize its material, default actor type, 1–100 tick cooldown, MiniMessage name and lore in `items.yml → group-actor-tool`.

Shared **Immortal** and **kit** settings are stored in the group. Immortal applies to current members and future additions. A shared kit applies to currently available members and actors later transferred with `/es group add`; every pattern/tool creation still needs its own explicit kit. **Randomize all identities** is an immediate batch action rather than a persistent template. The same controls appear in the group GUI.

## Recording sessions and elytra performances

Studio home has **Record session**, with explicit ON/OFF controls. `/es record on` changes the server-list MOTD and blocks every non-operator join/reconnect. Existing players stay connected. `/es record off` restores ordinary admission/MOTD rules. This is separate from an NPC's selected performance.

Use `/actor act guard flight_take`, fly with an elytra, then `/actor finish`. The take now saves the actual gliding flag as well as the body pose and equipment; replay uses the elytra animation. Old `FALL_FLYING` frames gain the flag on load. An old take that saved only `SWIMMING` cannot reliably distinguish swimming from gliding; re-record that take if needed. Replay clears the flight state when it stops.

Change `/actor mode guard stop`, `repeat` or `reverse` before, after or during playback. `/actor autoplay guard on` automatically starts the selected take; select `repeat` or `reverse` for continuous playback. `/actor stop guard` stops and disables autoplay. Name and skin changes remain available during replay.

`/es player halfheart on` protection now lets a held vanilla totem pop, with normal consumption/effects, and stays enabled afterward. When no held totem remains, it prevents a lethal hit and leaves half a heart. It is a player flag, separate from NPC Immortal.

## Configuration and limits

Every setting in `actor-ai.yml` has an explanation and range. Movement distances are blocks; delays are ticks; chances run from 0 to 1. Reload with `/es reload`. Group identity, leader, Follow/Hold, optional shared Immortal/kit and the next tool actor number persist in `groups/`; active targets, group wars and a Move destination are temporary. Follow is saved; a Move order reloads as Hold. A disconnected/dead/acting leader pauses the group and clears current enemies. Cross-world leaders are not followed by teleportation. NPCs outside engagement range disengage.

This version was checked through automated tests, compilation and artifact inspection only. No Minecraft server was started. Verify navigation feel, formation spacing, safe terrain placement, visible flight, native Citizens melee, potion impacts and shield blocking on your server before a production shoot. See [testing evidence and acceptance cases](TESTING.md).
