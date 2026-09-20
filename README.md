# EasyScripting

Current release: **[0.2.0](https://github.com/itarqos5/EasyScripting/releases/tag/v0.2.0)**. [Download the plugin JAR](https://github.com/itarqos5/EasyScripting/releases/download/v0.2.0/EasyScripting-0.2.0.jar). Documentation describes this release.

Paper/Purpur tools for scripted SMP productions: actors, timed scenes, movement recordings, repeatable takes, kits, inventory tools, world controls and configurable inventory menus.

**Need help with a command?** The [complete command guide](documentation/COMMANDS.md) explains every `/es` command and shortcut, every NPC/player setting, and every scene action with simple descriptions and examples. Follow the [step-by-step user guide](documentation/USERGUIDE.md) for your first NPC or recording.

This is an independent clean-room implementation based on public feature descriptions and gallery images. It does not include ScriptedEssentials code or assets. **Full reference parity is not claimed.** See [PARITY.md](documentation/PARITY.md) for the implemented behaviors, acceptance criteria and outstanding differences.

## Build and install

Use JDK 25 and the included Gradle wrapper:

```sh
./gradlew build
```

On Windows, use `.\gradlew.bat build`.

Copy `build/libs/EasyScripting-0.2.0.jar` into your server's `plugins/` directory and restart. Do not install the sources JAR or the optional smoke-test JAR. Configuration files are created under `plugins/EasyScripting/`. Open the studio with `/es` or `/es menu`. Follow [USERGUIDE.md](documentation/USERGUIDE.md) for installation, NPC identities, acting, scenes, recordings and YAML/GUI customization.

The project targets Paper/Purpur, with Paper 26.2 as its original primary target. Release 0.2.0 is compiled against Paper 1.21.8 and 1.21.11 using JDK 25 and produces Java 21 bytecode. Validation totals: **171 unit tests**; no server/client was launched for this release. Earlier 26.x compilation and runtime results are historical, not fresh 0.2.0 verification. See [TESTING.md](documentation/TESTING.md) for the exact matrix. Purpur is a compatibility target; Folia and Spigot are not supported.

Optional integrations:

* **Citizens:** required for `PLAYER` actors and their skins. Install a Citizens build that supports your exact server version. Mob actors such as `ZOMBIE` work without it. The latest Citizens release may drop older Minecraft adapters; use the matching historical build when running older Paper.
* **Simple Voice Chat:** enables voice mute/broadcast. Participating clients need its voice mod. EasyScripting does not record microphone audio.

Neither dependency is bundled. Without them the rest of the plugin loads and their commands explain what is missing.

## NPC movement, groups and identities

Version 0.2.0 forms followers into aligned rows and columns of a configurable width, oriented by the direction the leader is actually travelling, at a normal walking pace with a sprint-like catch-up only when they fall behind. Melee waits for the held weapon to recharge, NPCs circle between swings, and a hurt NPC will break off to eat a carried golden apple or throw a carried ender pearl. The leader can hit their own group NPCs; members still cannot hurt their leader or each other. Start a group with `/es group create red`. See the [group and combat guide](documentation/NPC-GROUPS.md).

Mass creation now requires an existing group and saved kit. `/actor pattern red disc behind 40 fighter 2 PLAYER` fills a disc behind the assigned leader and resolves every X/Z column to its highest safe standing surface. `/es group tool red fighter PLAYER` gives an operator a persistent bound tool; right-click a block to create `red-actor-1`, `red-actor-2`, and later members with that kit. Group controls can apply Immortal, one kit or fresh identities to every member. Deleting a group permanently deletes all its NPCs.

Generated NPC names and `/nickname` aliases use asynchronously cached public identity pools. Every generated username is 5–16 Minecraft characters, includes at least one letter plus at least one digit or underscore, and excludes current actor/nickname names, blacklisted names, retired names, current operators, and every real account known to have joined the server. Actor copies receive a fresh generated identity instead of duplicating the source identity. Local readable fallbacks keep creation available when a provider is down. Natural actor deaths and nicknamed-player deaths retire their displayed name; `/deadusers` opens the searchable, paginated registry, where shift-right-click releases a name.

`/kits` opens kits. Studio home now has **Record session** to turn joining restrictions/MOTD on or off. The new GUI separates identity, movement, replay and combat; old layouts are backed up before migration. Configuration files are commented, and command mistakes show syntax and examples. Elytra performances preserve the gliding animation; player half-heart protection allows held totems to pop and stays enabled afterward.

## Nicknames, chat and kits

Use `/es kits claim <kit> [player|*|actor:id]`, bulk provider imports and per-kit access for operators/everyone/one player plus operators. Only actual operators can manage or gift kits. Commands now report their result. `/es record on|off` controls a recording MOTD and blocks non-operator joins/reconnects; NPC performances use `/actor act` and `/actor finish`.

* `/nickname <real-player-or-nickname>` assigns an API-generated username while preserving the skin. `/nickname <player-or-nickname> off` resets one; `/nickname off` resets everyone. Names expire on disconnect and are retired if the nicknamed player dies. Tab, nametag and ordinary death/quit messages use the nickname; the client's authenticated account name cannot be changed by a server plugin.
* `/es chat block on` restricts public chat to current operators; `off` restores it. No argument toggles. `/es chat death <name>` prints a white simulated death message without killing anyone; broadcast sends chat and a title.
* `/es kits` opens the kit library: create, import inventory, edit, Save or Save & equip, and export YAML. Import items from installed PlayerKits 2, legacy PlayerKits, EssentialsX and CMI, or another EasyScripting export. Provider commands, prices and cooldowns are not copied.

See the [usage guide](documentation/USERGUIDE.md#7-nickname-real-players), [kit workflow](documentation/USERGUIDE.md#9-create-edit-and-import-kits) and [configuration reference](documentation/CONFIGURATION.md).

## First shot

With Citizens installed, `/actor create guard_1` automatically assigns an unused generated username and a public skin-owner profile. Keep the result as-is, use `/actor set guard_1 name RiverScout` or `/actor set guard_1 skin Notch` to change one part, or `/actor randomize guard_1` to reroll both. `/actor info guard_1` shows the identity; the actor's ID remains `guard_1`. Names, skin owners and resolved skin textures persist across restarts. Public providers, refresh timing and local fallbacks are configured in `npc-identities.yml`; existing actors are unchanged on upgrade.

`/actor gui guard_1` opens an overview with Identity & clothing, Movement, Record & replay, and Combat & supplies cards. Choose or change the end mode (`stop`, `repeat` or `reverse`) before recording, afterward or during playback. Name, skin and random identity can also change during playback. Identity & clothing includes a tab-list toggle for PLAYER NPCs. `/actor act guard_1 entrance` puts you in the NPC's place and costume; `/actor finish` restores you, saves the performance and starts it automatically. Autoplay is enabled by default; use `/actor autoplay guard_1 off` for manual playback. While acting, direct melee is blocked but falls, projectiles and explosions can hurt you. On NPCs, Hittable only controls melee; Immortal keeps hits and knockback but prevents death. New NPCs default to Immortal OFF. Natural NPC deaths permanently remove the actor, announce its name leaving the game, retire its displayed username, and delete its take unless another NPC uses it. Shared takes remain until their final NPC is removed. `/actor stop guard_1` holds its current position and disables autoplay. See [the acting workflow](documentation/USERGUIDE.md#act-as-an-npc-and-save-its-performance).

The redesigned studio groups tools into clear categories, provides saved-recording/costume pickers and uses consistent Back, Home and Close buttons. All menus are configured in `guis.yml`. Upgrading from layout 1 or 2 saves `guis-before-v3-<UUID>.yml` beside it before installing layout schema 3. Other project documentation is in [`documentation/`](documentation/USERGUIDE.md).

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
* Record an NPC performance with `/actor act guard approach`, then `/actor finish`. Replay it with `/actor play guard`; choose stop/repeat/reverse in its GUI.
* `/es record on` enables a server recording session: recording MOTD, no non-operator joins/reconnects. `/es record off` restores normal MOTD and login rules. Old movement subcommands were removed; NPC acting remains available.
* Capture your state with `/es take snapshot`, adjust your health/inventory/gamemode, and `/es take reset`. `/es take discard` releases the saved take.
* Start a coordinated take with `/es take start episode self` or `all`; end it with `/es take stop reset`. Chat and MOTD behavior are in `recording.yml`.
* Save locations with `/es warp save courtyard`; set access with `/es warp permission courtyard everyone`. Unauthorized warps are hidden from lists and completion.
* Select two corners using `/es region wand`, then `/es region save set_a`. Restore blocks with `/es region restore set_a`. Capture the set before altering it; restoration is incremental and changes blocks.

All durations are server ticks unless a setting explicitly says seconds. Lag stretches real elapsed time; the action order stays deterministic.

## Configuration and permissions

`actor-ai.yml` controls NPC movement and combat; `command-help.yml` supplies syntax explanations; `config.yml` controls limits; `features.yml` enables feature groups; `messages.yml` contains MiniMessage templates; `guis.yml` controls titles, layout, buttons and navigation; `npc-identities.yml` configures public and fallback identity pools; `items.yml` configures the bound group actor tool. Separate YAML files configure moderation, kits, potions, death, effects and recording. `/es reload` validates settings and GUI layout before replacing the active configuration. Actor/group/scene/kit/recording definitions edited on disk load on a full server restart. Edit saved definitions only while stopped, so live saves cannot overwrite them.

Most control permissions default to operators. Merely opening the studio does not grant its controls. Console/player command actions are disabled by default and need an explicit configuration option plus their separate permission. Real destructive explosions need both configuration and `easyscripting.destructive`; cleanup and forced death also require the destructive node, which does not default to operators.

See [NPC-GROUPS.md](documentation/NPC-GROUPS.md) for factions and supplies, [ARCHITECTURE.md](documentation/ARCHITECTURE.md) for service ownership, [USERGUIDE.md](documentation/USERGUIDE.md), [COMMANDS.md](documentation/COMMANDS.md), [PERMISSIONS.md](documentation/PERMISSIONS.md), [CONFIGURATION.md](documentation/CONFIGURATION.md), [API.md](documentation/API.md), [TESTING.md](documentation/TESTING.md) and [CHANGELOG.md](documentation/CHANGELOG.md).

## Operational boundaries

Use a staging copy of an important production world for acceptance testing. Snapshot restoration can be vetoed by another plugin's teleport handler and will report that failure. Completed take snapshots are session-local; deferred player restores are persisted when a player disconnects or dies. A hard process crash cannot guarantee recovery of an active in-memory take.

Player NPCs use Citizens. Raw PNG/NameMC skin conversion, per-viewer team colors/glow and silent container animations during vanish are not implemented. Kit imports support the four documented providers; unsupported formats can use inventory capture. Region storage preserves block data, container contents and sign text; it does not preserve every specialized block entity. Camera interpolation is limited by the vanilla client. See the parity matrix for the complete qualification.
