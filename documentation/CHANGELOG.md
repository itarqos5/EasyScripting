# Changelog

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
