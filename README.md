# EasyScripting

Paper/Purpur tools for scripted SMP productions: actors, timed scenes, movement recordings, repeatable takes, kits, inventory tools, world controls and configurable inventory menus.

This is an independent clean-room implementation based on public feature descriptions and gallery images. It does not include ScriptedEssentials code or assets. **Full reference parity is not claimed.** See [PARITY.md](documentation/PARITY.md) for the implemented behaviors, acceptance criteria and outstanding differences.

## Build and install

Use JDK 25 and the included Gradle wrapper:

```sh
./gradlew build
```

On Windows, use `.\gradlew.bat build`.

Copy `build/libs/EasyScripting-0.1.3.jar` into your server's `plugins/` directory and restart. Do not install the sources JAR or the optional smoke-test JAR. Configuration files are created under `plugins/EasyScripting/`. Open the studio with `/es` or `/es menu`. Follow [USERGUIDE.md](documentation/USERGUIDE.md) for installation, NPC identities, acting, scenes, recordings and YAML/GUI customization.

The primary target is Paper 26.2 with Java 25. The plugin produces Java 21 bytecode and uses the shared Paper API surface for 1.21.8, 1.21.11 and 26.1 compatibility. Test evidence and its limits are in [TESTING.md](documentation/TESTING.md). Purpur is a compatibility target; Folia and Spigot are not supported.

Optional integrations:

* **Citizens:** required for `PLAYER` actors and their skins. Install a Citizens build that supports your exact server version. Mob actors such as `ZOMBIE` work without it. The latest Citizens release may drop older Minecraft adapters; use the matching historical build when running older Paper.
* **Simple Voice Chat:** enables voice mute/broadcast. Participating clients need its voice mod. EasyScripting does not record microphone audio.

Neither dependency is bundled. Without them the rest of the plugin loads and their commands explain what is missing.

## First shot

With Citizens installed, `/actor create guard_1` automatically assigns a random displayed username and skin. Keep the result as-is, use `/actor set guard_1 name RiverScout` or `/actor set guard_1 skin Notch` to change one part, or `/actor randomize guard_1` to reroll both. `/actor info guard_1` shows the identity; the actor's ID remains `guard_1`. Names, skin owners and resolved skin textures persist across restarts. Pools and automatic assignment are configured in `npc-identities.yml`; existing actors are unchanged on upgrade.

`/actor gui guard_1` opens a tabbed NPC panel with Appearance, Movement, Acting & Playback, and Combat sections. Choose an end mode (`stop`, `repeat` or `reverse`) before recording. `/actor act guard_1 entrance` puts you in the NPC's place and costume; `/actor finish` restores you, saves the performance and starts it automatically. Autoplay is enabled by default; use `/actor autoplay guard_1 off` for manual playback. The performer is protected from damage and knockback while acting. During playback, a hittable NPC receives real damage and knockback; turn Immortal off to allow death. See [the acting workflow](documentation/USERGUIDE.md#act-as-an-npc-and-save-its-performance).

The redesigned studio groups tools into clear categories, provides saved-recording/costume pickers and uses consistent Back, Home and Close buttons. All menus are configured in `guis.yml`. Upgrading from the old layout saves a `guis-v1-backup-<unique-id>.yml` beside it before installing layout schema 2. Other project documentation is in [`documentation/`](documentation/USERGUIDE.md).

Run these in-game with the documented permissions. They create an actor, announce the shot, show a swing and damage animation, then restore the actor after three seconds:

```text
/actor create guard ZOMBIE
/scene create opening
/scene bind opening guard actor:guard
/scene add opening 0 title self text=<aqua>Take one;subtitle=Camera rolling
/scene add opening 20 swing guard
/scene add opening 30 hurt guard
/scene add opening 60 wait guard
/scene restore opening on
/scene play opening
```

Use `/scene gui opening` to inspect the timeline. `/scene pause opening`, `/scene resume opening` and `/scene stop opening` control playback. With automatic restoration off, use `/scene reset opening` after a completed take. Run `/scene play opening` again for another shot.

`self` means the director executing the scene; a scene using it needs an in-game director. Bind explicit `player:Name` targets for a scene launched from console. Every action is permission-checked for the director, including after playback starts. Operating on another real player also requires `easyscripting.player.others`.

## Common workflows

* Save a costume with `/es kit save guard_kit`, then `/actor kit guard guard_kit`. The kit GUI copies inventory items into a saved loadout; saving a kit intentionally grants the ability to reproduce those items.
* Record your route with `/es record start approach`, walk and swing, then `/es record stop`. Use `/es record play approach guard off off`; the last two arguments enable looping and reverse playback.
* Record several performers with `/es record startall approach` and `/es record stopall`. Play tracks together with `/es record playgroup off off approach_alex=guard approach_steve=hero`.
* Capture your state with `/es take snapshot`, adjust your health/inventory/gamemode, and `/es take reset`. `/es take discard` releases the saved take.
* Start a coordinated take with `/es take start episode self` or `all`; end it with `/es take stop reset`. Chat and MOTD behavior are in `recording.yml`.
* Save locations with `/es warp save courtyard`; set access with `/es warp permission courtyard everyone`. Unauthorized warps are hidden from lists and completion.
* Select two corners using `/es region wand`, then `/es region save set_a`. Restore blocks with `/es region restore set_a`. Capture the set before altering it; restoration is incremental and changes blocks.

All durations are server ticks unless a setting explicitly says seconds. Lag stretches real elapsed time; the action order stays deterministic.

## Configuration and permissions

`config.yml` controls limits; `features.yml` enables feature groups; `messages.yml` contains MiniMessage templates; `guis.yml` controls titles, layout, buttons and navigation. Separate YAML files configure moderation, items, potions, death, effects and recording. `/es reload` validates settings and GUI layout before replacing the active configuration. Scene/actor definitions edited on disk load on a full server restart.

Most control permissions default to operators. Merely opening the studio does not grant its controls. Console/player command actions are disabled by default and need an explicit configuration option plus their separate permission. Real destructive explosions need both configuration and `easyscripting.destructive`; cleanup and forced death also require the destructive node, which does not default to operators.

See [USERGUIDE.md](documentation/USERGUIDE.md), [COMMANDS.md](documentation/COMMANDS.md), [PERMISSIONS.md](documentation/PERMISSIONS.md), [CONFIGURATION.md](documentation/CONFIGURATION.md), [API.md](documentation/API.md), [TESTING.md](documentation/TESTING.md) and [CHANGELOG.md](documentation/CHANGELOG.md).

## Operational boundaries

Use a staging copy of an important production world for acceptance testing. Snapshot restoration can be vetoed by another plugin's teleport handler and will report that failure. Completed take snapshots are session-local; deferred player restores are persisted when a player disconnects or dies. A hard process crash cannot guarantee recovery of an active in-memory take.

Player NPCs use Citizens. Raw PNG/NameMC skin conversion, per-viewer team colors/glow, silent container animations during vanish and proprietary kit import formats are not implemented. Region storage preserves block data, container contents and sign text; it does not preserve every specialized block entity. Camera interpolation is limited by the vanilla client. See the parity matrix for the complete qualification.
