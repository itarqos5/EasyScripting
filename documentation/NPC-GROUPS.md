# NPC movement, groups and combat

EasyScripting 0.1.7 adds a configurable combat prototype. Each NPC owns its health, equipment and carried supplies. A group supplies orders and allies; it does not pool health, inventories, hunger or XP, and it does not choose a winner in advance. Paper handles damage, armor, potion effects, knockback and death. PLAYER actors require a Citizens build for your server version.

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
2. Run `/es kits create fighter` to save your inventory, or open `/kits` and create/import/edit a kit.
3. Run `/es kits claim fighter actor:guard` or choose **Give to NPC** in the kit GUI.
4. Enable **Aggressive** in the NPC's Combat & supplies page, or use `/actor set guard aggressive on`.

Kits replace a loadout. They do not give a group one shared inventory. Applying the same kit to several NPCs gives each its own copy. Only operators can create, edit, import or gift kits. See [all kit commands](COMMANDS.md#kits).

Outside scenes/recordings, a carried totem moves to the offhand automatically. The displaced item moves into the vacated inventory slot. After a vanilla totem pop, another carried totem is equipped after `combat.totem-refill-ticks` (default **1 tick**, about 50 ms at 20 TPS). A main-hand totem works too. No replacement is created when supplies run out. Immortal NPCs cannot reach an ordinary lethal hit, so use **Immortal OFF** when testing normal totem pops.

An aggressive NPC retaliates after an uncancelled hit. An intelligent group member can also defend allies or attack an ordered enemy. Before retaliation, it throws up to three **carried beneficial splash potions** straight upward, one at a time. Fewer supplies mean fewer throws. Harmful potions and drinkable potions are not used by this defensive routine. Throws create actual potion projectiles and consume the items; terrain and splash range matter.

Melee has configurable accuracy, cooldown jitter and a chance to jump after a hit. A missed attempt swings but does not deal damage. These are intentionally imperfect prototype reactions, not a trained PvP bot. A carried shield may be raised after a random delay when a non-allied mace holder is overhead. It can notice that threat before the first hit, but a failed reaction roll or exhausted inventory leaves the NPC unprotected. No shield or potion is conjured. Shield effectiveness follows Minecraft's facing/attack rules and the installed Citizens adapter.

Idle aggressive NPCs may still wander when Wander is enabled. A fight takes priority. Group orders take priority over ambient wandering. A scene, actor performance capture or replay has an exclusive reservation; autonomous movement and item reactions yield until it ends.

## Create a group and appoint its leader

As an operator:

```text
/es group create red
/es group add red guard
/es group leader red Alex
/es group gui red
```

Alex must be a real online player who is not acting as an NPC. One player can lead one group. Assigning a leader starts following; use `/es group leader red off` to remove the leader. Group members and their leader cannot damage or knock back one another. Harmful allied splash/cloud effects are filtered too; helpful potion effects can still apply.

Operators manage groups. A leader can open their group's GUI and issue orders without operator status, provided they have the general `easyscripting.use` access. A leader cannot edit membership, intelligence or ownership. The legacy actor administration commands remain staff tools; do not grant `easyscripting.actor` to ordinary leaders.

| Command | Meaning |
| --- | --- |
| `/es group follow red` | Follow the online leader in a spread formation. |
| `/es group move red` | Move the formation to where you are standing. |
| `/es group hold red` | Stop movement and clear attack orders. `stop` is an alias. Intelligence can still defend against a new hit. |
| `/es group attack red Alex` | Attack an eligible nearby real player. Use `actor:enemy_1` to target an NPC. |
| `/es group fight red blue` | Engage another group's members and leader. An intelligent enemy group fights back. |
| `/es group intelligence red off` | Operator: turn off autonomous attacks and defensive combat reactions; movement orders still work. |
| `/es group remove red guard` | Operator: remove a member without deleting the NPC. |
| `/es group delete red` | Operator: delete the group and leave its NPCs unassigned. |

When following or moving, a leader's successful hit adds the victim as an enemy. If the leader hits multiple enemies, NPCs distribute across them and reassess as enemies move, die or leave range. Engaging an enemy group recruits that group's members. There is no fixed damage multiplier, scripted winner or shared resource bar. A group with no surviving NPCs is still a saved group definition and may receive new members.

NPCs need **Hittable ON** to be eligible melee targets. **Immortal OFF** allows deaths to settle a battle. Immortal still permits hits and knockback, but an immortal combatant cannot lose by dying. NPC death permanently deletes that NPC and removes its selected take once no other NPC references it.

## Start a larger faction

```text
/actor pattern red grid 100 2 PLAYER
/es group create red
/es group leader red Alex
```

Pattern creation assigns the `red` group tag; creating the faction adopts existing matching tags. If the faction already exists, creating another matching pattern joins it automatically. To move actors with another tag, use `/es group add red tag:extras`. Actor IDs generated by the example are `red_1` through `red_100`. The global actor limit still applies (default 200). Keep both factions within that limit.

Each NPC receives its own path and attack timing. New paths share a configurable budget, and decisions are staggered. A 100-member formation is supported by the allocation logic; actual server capacity depends on terrain, Citizens and server hardware and has **not been benchmarked** for this release.

## Recording sessions and elytra performances

Studio home has **Record session**, with explicit ON/OFF controls. `/es record on` changes the server-list MOTD and blocks every non-operator join/reconnect. Existing players stay connected. `/es record off` restores ordinary admission/MOTD rules. This is separate from an NPC's selected performance.

Use `/actor act guard flight_take`, fly with an elytra, then `/actor finish`. The take now saves the actual gliding flag as well as the body pose and equipment; replay uses the elytra animation. Old `FALL_FLYING` frames gain the flag on load. An old take that saved only `SWIMMING` cannot reliably distinguish swimming from gliding; re-record that take if needed. Replay clears the flight state when it stops.

Change `/actor mode guard stop`, `repeat` or `reverse` before, after or during playback. `/actor autoplay guard on` automatically starts the selected take; select `repeat` or `reverse` for continuous playback. `/actor stop guard` stops and disables autoplay. Name and skin changes remain available during replay.

`/es player halfheart` protection now lets a held vanilla totem pop, with normal consumption/effects, and stays enabled afterward. When no held totem remains, it prevents a lethal hit and leaves half a heart. It is a player flag, separate from NPC Immortal.

## Configuration and limits

Every setting in `actor-ai.yml` has an explanation and range. Movement distances are blocks; delays are ticks; chances run from 0 to 1. Reload with `/es reload`. Group identity, leader and Follow/Hold persist in `groups/`; active targets, group wars and a Move destination are temporary. A disconnected/dead/acting leader pauses the group and clears current enemies. Cross-world leaders are not followed by teleportation. NPCs outside engagement range disengage.

This version was checked through automated tests, compilation and artifact inspection only. No Minecraft server was started. Verify navigation feel, visible flight, native Citizens melee, potion impacts and shield blocking on your server before a production shoot. See [testing evidence and acceptance cases](TESTING.md).
