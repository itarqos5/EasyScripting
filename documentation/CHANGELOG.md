# Changelog

## 0.1.7 — 2026-09-15

* Repaired eye-origin facing and Citizens navigation setup. Social wandering uses short grounded paths around nearby players/NPCs and a fixed home fallback; looking continues while walking.
* Added persistent NPC groups with real player leaders, individual supplies/health, friendly-fire protection, follow/hold/move orders, split target allocation and group battles using native melee. New paths have a shared budget; active wars are temporary and AI yields to recordings/scenes.
* Added standalone aggression, conserved backpack/offhand item exchanges, delayed totem refill, up to three carried beneficial splash potions, imperfect melee timing/accuracy, jump reactions and probabilistic delayed shields against overhead maces.
* Fixed half-heart protection suppressing vanilla held-totem pops. Totems now resurrect normally and the protection remains enabled afterward; cancelled PvP does not lower protected health.
* Fixed elytra replay rendering with a swimming animation by saving/applying actual gliding state, retaining it between replay frames and clearing it on stop. Legacy flight-pose frames remain readable.
* Rebuilt actor navigation around four overview cards, added group/combat controls, `/actors` and `/kits`, and a home recording-session ON/OFF page. Old GUI layouts are backed up before schema-3 migration.
* Added contextual syntax, expected values and examples for command errors. Documented new commands and every AI setting; added comments to existing configuration without replacing owner values/comments. Storage now retains nested comments during asynchronous saves.
* Build-only validation and remaining live acceptance cases are recorded in TESTING.md. No Minecraft server was started for this release.

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
