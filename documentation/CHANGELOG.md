# Changelog

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
