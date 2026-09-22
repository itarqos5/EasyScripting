# Changelog

Latest plugin release: **[0.2.5](https://github.com/itarqos5/EasyScripting/releases/tag/v0.2.5)**. Entries below describe each version at its release; later entries supersede changed behavior.

## Unreleased

Nickname coverage fixes on top of 0.2.5. No version has been published for these changes yet.

* A nickname now also rewrites the name carried by a death or leave message rather than only the name shown in it. Vanilla attaches a hover card and shift-click insertion to every name it puts in those messages, and both still read as the real account, so hovering a rewritten death message handed the account name straight back. Translation arguments — where a death message keeps its victim and killer — are walked for the same data.
* A kicked player's leave message is now rewritten too. It is announced through `PlayerKickEvent`, which never passed through the quit-message rewrite, so a kick published the real account name of a nicknamed player.
* Applying a nickname now also applies a random public skin, drawn from the same cached skin-owner pool generated NPC identities use and falling back to the `npc-identities.yml` skin owners. Blacklisted owners, retired names and the player's own account are never chosen, the lookup is asynchronous, and a failure keeps the current skin without costing the nickname. The new `random-skin` in `nicknames.yml` turns it off; resetting a nickname restores the real skin as before.
* Validation: **184 unit tests**, production, test and smoke compilation. No Minecraft server was started.

## 0.2.5 — 2026-09-22

Group AI, formation and lifecycle repairs, distance-dependent melee accuracy, half-heart protection that keeps the hit, and configuration loading that survives a broken file.

* Fixed NPC melee landing every blow from exactly its maximum reach. Accuracy now falls off with distance: `combat.accuracy` applies in full from half of `groups.melee-reach` and closer, tapering linearly to the new `combat.reach-accuracy` (0.25) at the limit itself. The roll now also covers the fallback melee path that previously struck without one, so no NPC swing connects unconditionally.
* Fixed NPCs raising their shields against a mace carried at their own level, which left them turtling through an ordinary ground fight. Only a mace **overhead** — the falling smash a shield is actually worth raising against — triggers the reaction again, and `combat.shield-ground-radius` has been removed. A leftover key in an existing `actor-ai.yml` is ignored.
* Fixed members walking to a fixed numbered place in the follow formation. Each member now takes the place nearest to where it already stands, preferring the one it already held, so a group that turns or reforms no longer sends members around the leader and through their neighbours to reach a square on the far side.
* Fixed settled followers shuffling on the spot. Citizens and Paper both finish a path a little short of its destination, and the director kept asking for that last fraction of a block back every few ticks; a navigator that has already stopped inside the resume distance now counts as settled, and one repath interval covers a completed path as well as a running one.
* Fixed NPCs stopping dead for `groups.knockback-pause-ticks` on damage that throws nobody anywhere. Fire, fall damage, drowning and cactus no longer pause navigation, so an NPC walks out of the damage instead of standing in it; a real blow still pauses for its knockback.
* Shields now answer a mace carried **anywhere** in a player's inventory, not only one in their hand. Attribute swapping means the mace is in the backpack until the instant it swings, so held-only detection reacted after the smash had landed. The new `combat.shield-inventory-mace` turns the backpack check off.
* Fixed defensive potion bursts re-dosing effects the NPC already had. A potion is thrown only when its effect has run out, been cleared, or is weaker than the one in the bottle; healing counts only while hurt. The check is repeated before every throw, so a burst stops as soon as one bottle has covered it.
* Fixed a retreating NPC keeping its enemy in view while walking away, which read as moonwalking. It now turns and runs the way it is going.
* Tightened the follow formation so its rows and columns visibly line up: `follow-arrival-distance` ships at 0.6 instead of 1.0, and members that have taken their place face the way the formation faces instead of each swivelling to stare at the leader.
* Added `/es group lineup <group> [front|behind] [columns]` and a **Line up on me** button. It teleports every present, free member into block-aligned rows and columns beside you, each NPC on its own whole block, facing the way you face. The direction is snapped to the nearest cardinal so the grid follows the world exactly, columns resolve near your own feet rather than to the world's highest block, and one unusable column refuses the whole line-up rather than stacking two members on one square.
* Fixed falling NPCs being steered mid-air, which made long drops look floaty. An NPC with clear ground more than the new `groups.fall-pause-distance` below it is left to gravity — no navigation, no wandering, no attack steering — and takes its ordinary fall damage.
* Fixed the group GUI's attack picker offering targets the order would then refuse. It now lists only reachable, hittable, non-allied targets, and shows an explicit empty state.
* Fixed every refused command printing four lines of syntax help after its real reason. Syntax help is now shown only when the command itself was malformed, so an ordinary refusal reads as one sentence.
* Added **Delete all NPCs in this group** to the group page and `/es group purge <group>`: deletes every member while keeping the group, its leader and its shared defaults. Added an operator-only **Delete every NPC** button to `/actors` and `/actor deleteall`.
* Dead NPCs now drop their backpack and worn equipment, controlled by the new `actors.death-drops` (default true). The items are the ones the NPC was carrying; nothing is created.
* Fixed the NPC "left the game" announcement being sent before the server's own death message. It is now sent one tick later, so the two read in the order they happened.
* `/es player halfheart` now keeps the lethal hit instead of cancelling it. Cancelling swallowed the whole attack — no knockback, no hurt animation, no mace smash, no hit sound. The blow now lands with its damage taken away, leaving the player on half a heart.
* Configuration loading now isolates failures to the file that caused them. A file whose YAML cannot be parsed, or whose contents fail validation, is logged to console in full and answered from the copy bundled in the jar; every other file still loads, and the broken file on disk is never overwritten or replaced. Operators are told `<file>.yml file is broken, please read console.` when they join, `/es reload` names the affected files, and any command that would save one of them is refused rather than writing the defaults over it. Previously a single bad file disabled the whole plugin.
* Added **Release every dead username** to `/deadusers` and `/es deadusers clear`: releases every retired name at once so all of them can be generated again. The button is operator-only, asks for confirmation, and ignores any active search filter.
* Validation: **183 unit tests**, production, test and smoke compilation. No Minecraft server was started.

## 0.2.0 — 2026-09-20

Group AI repairs and NPC self preservation.

* Fixed group following orienting its rows by where the leader was looking instead of where they were walking. The heading now comes from how far the leader actually moved each tick; `Player#getVelocity` is not populated by walking input, so the old reading was almost always empty and the formation stayed frozen on whatever yaw it first saw.
* Rebuilt the trailing formation as an aligned grid. Every row sits on one shared lateral grid so columns line up, and a partial last row is centred by whole slots. The new `groups.follow-columns` chooses the width; `0` keeps the automatic square block. A Move order now forms the same rows and columns, centred on the destination.
* Fixed formation places being taken from a list position that shifted whenever a member died, was hidden or was reserved for a recording, which re-shuffled the whole group and let two members share one slot. Places are now numbered over the members that are present and free, so a casualty closes the gap, and a member with no place waits instead of piling onto the first one.
* Fixed a formation slot inside a wall or over a drop stopping that member outright; the slot now pulls in toward the leader before the member gives up.
* Added `groups.follow-resume-margin`, so a member that has taken its place waits for a wider distance before walking again. A settled formation no longer stutters in and out of walking while the leader shuffles on the spot.
* Fixed the shared path budget going to whoever happened to be considered first, which starved stragglers in a large group. Path requests are now ranked, stopped members first and then those furthest from their slot.
* Added `combat.weapon-cooldown` (on by default): an NPC waits for its held weapon to recharge before swinging rather than attacking on a fixed timer and landing partly charged hits. `groups.attack-cooldown-ticks` becomes a floor and its shipped default drops from 20 to 10; an existing `actor-ai.yml` keeps its own value.
* Added `combat.crit-jump-chance` and `combat.crit-jump-delay-ticks`, giving a ready attacker a chance to hop and land the blow while falling, which Minecraft scores as a critical hit.
* Fixed melee reach being measured between foot positions, which denied hits on anything standing on a slab or a stair. It is now measured from the attacker's eyes to the nearest point of the target's hitbox.
* Fixed an engaged member only reassessing every five ticks, which quantised its swings into misses; engaged members now think every two ticks.
* Fixed several members sent at one enemy all pathing onto its exact block. They now take separate places around it.
* Fixed a dead enemy staying assigned until the next sweep, leaving a squad swinging at a corpse for up to a second. A death reassigns its attackers at once.
* Generalised the mace shield reaction. An NPC now raises a carried shield against a mace holder standing at its own level within the new `combat.shield-ground-radius`, not only one directly overhead, and keeps the guard up while that threat remains — past `shield-hold-ticks`, up to the new `combat.shield-max-hold-ticks` — instead of dropping it on a fixed timer.
* Added a `survival` section. At or below `heal-health` an NPC breaks off, backs away `retreat-distance` and eats a carried golden apple; at or below `escape-health` it throws a carried ender pearl away from the fight. Eating stows the weapon, plays the real animation and hands the consumption to Paper, so the apple's own effects apply and nothing is invented; a hit interrupts the meal and returns the apple. An NPC that would die to its own pearl's 5 damage keeps fighting instead. Neither reaction creates supplies.
* Added combat movement. NPCs sprint while closing beyond the new `groups.sprint-chase-distance` and walk inside it, circle the target at the distance they already hold while waiting on a weapon (`survival.strafe-chance`, `survival.strafe-interval-ticks`) instead of standing still, and back away while still facing their enemy when healing or escaping.
* Fixed ambient wandering being able to interrupt a shield, a potion burst, a meal or a retreat; all four now hold the NPC until they finish.
* Validation: **171 unit tests**, production, test and smoke compilation. No Minecraft server was started; the live acceptance cases in [TESTING.md](TESTING.md) still apply.

## 0.1.8 — 2026-09-15

* Rebuilt group following around compact trailing rows, a stable movement heading and normal native pathing. Members walk at a normal pace, use a sprint-like catch-up pace only when far behind and use intermediate waypoints instead of teleporting.
* Made group protection directional: NPC members cannot hurt their own leader or teammates through melee, projectiles, splash potions or lingering clouds; the real leader can hit their own NPCs.
* Changed `/actor pattern` to require an existing managed group and saved kit. It can deploy filled discs or squares, or line/circle/grid layouts, in front of or behind the assigned leader (falling back to the creator), with every X/Z column placed on its highest safe surface.
* Added persistent group actor tools. `/es group tool <group> <kit> [type]` binds the chosen group, kit and type to an item; right-clicking creates grounded, equipped members with monotonic IDs such as `red-actor-1`.
* Added shared group Immortal, kit and identity controls in commands and GUI. Values for Immortal and kit persist for later members. Deleting a group now permanently deletes all of its NPCs.
* Added asynchronously cached public username and skin-owner pools with lower-case local fallback. Generated actor names and `/nickname` aliases use 5–16 Minecraft username characters, include at least one letter plus at least one number or underscore, avoid the old CapitalCapital fallback pattern, and exclude current actor/nickname names, the blacklist, current operators, all real accounts remembered as joining the server, and retired names. Actor copies now receive a fresh generated identity instead of duplicating the source username.
* Added `state/dead-users.yml` plus `/deadusers` list/search/remove and a paginated head GUI. Natural actor deaths and nicknamed-player deaths retire the displayed username until an authorized identity user explicitly releases it; manual actor/group deletion does not retire names.
* Updated all GUI/configuration comments, contextual command help and documentation for the new group and identity lifecycle.
* Validation: **160 unit tests**, production/smoke compilation, additional Paper 1.21.11 compilation, documentation links and artifact inspection. No Minecraft server or client was started for this release.

## Documentation follow-up — 2026-09-15

* Audited all Markdown against 0.1.7, corrected kit/spawn examples and current GUI labels, and clarified actor tags versus combat factions and scoreboard teams.
* Replaced old architecture plans with the current service/ownership reference, documented public API boundaries and marked older runtime evidence as historical.
* Updated permission, configuration, release and testing references. Documentation-only change; the published 0.1.7 JAR and its 138-test evidence remain unchanged.

## 0.1.7 — 2026-09-15

* Repaired eye-origin facing and Citizens navigation setup. Social wandering uses short grounded paths around nearby players/NPCs and a fixed home fallback; looking continues while walking.
* Added persistent NPC groups with real player leaders, individual supplies/health, friendly-fire protection, follow/hold/move orders, split target allocation and group battles using native melee. New paths have a shared budget; active wars are temporary and AI yields to recordings/scenes.
* Added standalone aggression, conserved backpack/offhand item exchanges, delayed totem refill, up to three carried beneficial splash potions, imperfect melee timing/accuracy, jump reactions and probabilistic delayed shields against overhead maces.
* Fixed half-heart protection suppressing vanilla held-totem pops. Totems now resurrect normally and the protection remains enabled afterward; cancelled PvP does not lower protected health.
* Fixed elytra replay rendering with a swimming animation by saving/applying actual gliding state, retaining it between replay frames and clearing it on stop. Legacy flight-pose frames remain readable.
* Rebuilt actor navigation around four overview cards, added group/combat controls, `/actors` and `/kits`, and a home recording-session ON/OFF page. Old GUI layouts are backed up before schema-3 migration.
* Added contextual syntax, expected values and examples for command errors. Documented new commands and every AI setting; added comments to existing configuration without replacing owner values/comments. Storage now retains nested comments during asynchronous saves.
* Validation: 138 unit tests, production/smoke compilation, additional Paper 1.21.11 compilation, documentation links and artifact inspection. Remaining live acceptance cases are recorded in [TESTING.md](TESTING.md). No Minecraft server was started for this release.

## 0.1.6 — 2026-09-14

* Added self/player/wildcard/NPC kit claims, with kit-first or player-first syntax, online account/nickname resolution and explicit `player:` / `actor:` targeting. Claims replace the saved inventory/equipment loadout; active reservations and dead players are rejected before giving kits.
* Only actual operators may create/edit/import/export/delete, change access or gift kits. Each kit has Operators only, Everyone, or one selected UUID + operators access. Older/new/external-provider kits default to Operators only; item edits and EasyScripting exports retain policy. Both the GUI and legacy apply command enforce access.
* Added player/NPC gift pickers, selected access controls and Import all kits for every supported provider. Bulk jobs import at most one kit per tick, keep existing kits, suffix duplicate IDs, report failures and support cancellation. Completed imports survive cancellation; de-op/disconnect/shutdown stop further work.
* Replaced the old `/es record` movement command surface with `/es record on|off`. Sessions always show a configurable recording MOTD and block every non-operator login/reconnect, regardless of allow-list or server.bypass. Existing players remain; stopping restores ordinary login/MOTD rules. Actor act/finish/play remain the NPC recording workflow.
* Permanent NPC death/deletion now removes its unshared selected take; shared takes are retained until the final referencing NPC is removed. Replacing a take cleans up its unreferenced predecessor. Hide, unload, feature disable and shutdown retain takes.
* Added descriptive success feedback for previously silent commands, with scoped suppression when a command already replies. Updated GUI actions and migrations for sessions and kit access.
* Rewrote the complete command guide in plain language, covering all 30 groups, help and all 33 scene actions with syntax/examples. README remains at root; other Markdown stays in documentation/.
* Validation: 109 unit tests, production/smoke compilation, Paper 1.21.11 compilation, baseline rebuild and JAR/project checks. No Minecraft test server was started.

## 0.1.5 — 2026-09-14

* Immortal NPCs retain hit feedback and knockback but cannot die from normal damage. New NPCs default to mortal; a backed-up migration changes the creation default once without changing existing actors. Citizens' extra spawn damage immunity is disabled.
* Actual NPC death announces its name leaving the game before permanent deletion. Hittable remains melee-only; acting players still take environmental/projectile damage.
* Name, skin, random identity and mode changes work during recorded playback. Selected modes remain highlighted after capture. Stop holds the current position and disables autoplay/wandering; repeat/reverse can run continuously. Added a saved PLAYER NPC tab-list toggle.
* Added `/nickname <real-player-or-nickname>`, per-player `off` and global `off`. API-generated names preserve skins, reserve real names/aliases, update profile/tab/display/death/quit names and expire on disconnect. Added bounded HTTPS lookup and configurable local fallback in nicknames.yml. No additional protocol plugin is required.
* Renamed `/es chat mute` to `/es chat block [on|off]`. Only current operators can send public messages while blocked, regardless of bypass permissions. Block transitions announce in chat. Simulated death announcements now use vanilla-style white text.
* Added `/es kits`, kit controls, blank creation, inventory import, draft editing, Save & equip and portable YAML export/import. Item adapters support installed PlayerKits 2, legacy PlayerKits, EssentialsX and CMI. Provider commands, costs and cooldowns are not imported. Added kits.yml and configurable GUI controls.
* Added regression/contract tests and updated documentation. Build, unit, compile and artifact checks only; no Minecraft server/client or running kit-provider acceptance test was started.

## 0.1.4 — 2026-09-14

* Fixed shutdown trying to register actor behavior/autoplay tasks after Paper disables the plugin. The scheduler stops accepting work before service cleanup and failed registration no longer retains a phantom job.
* NPC deaths now permanently remove the actor from active storage, cancel its playback and release resources. Scene reset cannot resurrect it or overwrite a newly created actor that reuses its ID. Scripted NPC death also deletes it.
* Hittable OFF blocks direct melee and sweeps only. Falls, projectiles (including wither skulls), explosions and other environmental damage remain eligible; NPC Immortal still prevents lethal damage.
* Acting players now block melee only and otherwise take normal damage instead of being invulnerable. Prior invulnerability is restored after acting.
* Chat broadcasts also display a configurable title. Chat mute/unmute transitions announce the change to everyone, with configurable messages. Existing configurations inherit missing defaults.
* Updated GUI help, documentation and opt-in fixtures. Build/unit/API checks only; no server started.

## 0.1.3 — 2026-09-14

* Rebuilt every studio menu with grouped tools, spaced controls, explanatory lore, empty states, consistent navigation and NPC section tabs. Added recording/NPC/costume pickers, selected-mode indicators and unavailable-action explanations. Chat input returns to its originating page; timeline deletion requires Shift-right click and confirmation.
* Added validated `guis.yml` schema 2. Legacy layouts receive a unique backup before replacement; custom schema-2 values survive reload. Slot collisions are rejected before settings are published.
* Added saved per-NPC autoplay, enabled by default: starts after successful acting, on show/respawn/startup, or when enabled. It respects busy actors and feature switches. Stopping or cancelling does not immediately restart playback.
* Protected acting performers from damage and knockback, restoring their previous state afterward.
* NPC playback now yields to native knockback, then rejoins its recorded route. Damage survives playback reset, death stops the replay, and Hittable/Immortal can be changed during playback. Recovery timings are configurable in `recording.yml`.
* Published the public EasyScripting repository. Kept README.md in the root and moved other Markdown files into documentation/.
* Build and unit checks passed. This version has not been run on a server or visually checked in Minecraft.

## 0.1.2 — 2026-09-13

* Added Appearance, Movement, Acting & Playback, and Combat sections to the YAML-configured actor GUI. Older GUI files inherit missing controls without being overwritten.
* Added temporary acting as an NPC with its position, identity and costume, saved/restored performer state, deferred recovery, cancellation and conflict checks.
* Added persistent per-actor recording selection and stop/repeat/reverse modes. Stop holds the final position; repeat resets to the first frame; reverse alternates direction continuously. Legacy recording commands retain their behavior.
* Extended recording frames with armor, pose, off-hand swings, hurt animation and fire state while retaining legacy frame support.
* Kept hurt/flame playback visual, without replaying damage; synchronized Citizens equipment before immediate rename/skin respawns.
* Added Hittable/Immortal GUI toggles and respawn. Expanded default name endings and avoid recently chosen endings when alternatives exist.

## 0.1.1 — 2026-09-13

* New NPCs receive a generated username and PLAYER actors receive a random account skin. Added validated `npc-identities.yml` pools and an automatic-assignment switch.
* Added `/actor info`, `/actor randomize` and configurable identity buttons in the actor GUI, including defaults for older GUI files.
* Kept actor IDs independent of displayed names and skin owners. Name/skin changes preserve the other field; hidden PLAYER actors can also change skin.
* Persisted resolved signed Citizens textures so saved appearances survive respawn/restart. Existing actors retain their identity; copies preserve appearance.
* Added the step-by-step user guide, identity unit tests and an opt-in real-Citizens restart test.

## 0.1.0 — 2026-09-13

Initial independent implementation, named EasyScripting.

* Added the Gradle wrapper, Java 25 toolchain with Java 21 bytecode, plugin metadata and granular permissions.
* Added bounded timeline execution, equal-tick ordering, pause/resume/stop, preflight checks, actor/world resource locks, repeat/append editing and resettable scene snapshots.
* Added owned mob actors and optional Citizens player actors, equipment, patterns, groups, navigation, combat and cleanup of owned chunk tickets.
* Added movement recording with synchronized capture/playback, reverse/loop playback and spectator camera motion.
* Added player flags, take/session workflows, nicknames/skins, inventory history, kits, warps, items, production locks, environment controls, regions, teams, villagers and combat/effect tools.
* Added optional Simple Voice Chat mute/broadcast and session mute restoration.
* Added YAML menus with pagination, confirmation and a copy-based loadout editor, plus configuration validation and atomic asynchronous persistence.
* Added pure Java tests, a separate real-server smoke-test plugin and an opt-in protocol client test script.
* Added incremental region preflight, container snapshot restoration, actor pose/glow persistence, slim/classic skin controls, Curse of Vanishing behavior and configurable feedback sounds.

The reference parity matrix contains qualified and unfinished behaviors. This initial release should receive the listed multiplayer and visual acceptance tests before use in an important production.
