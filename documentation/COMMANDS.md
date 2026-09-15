# Every EasyScripting command, explained simply

This is the command guide for **EasyScripting 0.1.7**. Use `/es status` to see your installed version. Incomplete or invalid commands now show their expected syntax, explanation and an example in chat.

Every successful command sends feedback, including commands that previously finished silently.

Reviewed against release 0.1.7: 31 registered command groups plus `/es help`, and 33 scene action types.

Each section lists the exact syntax, what it does and an example. Replace example names such as `Alex`, `guard`, `starter` and `opening` with names on your server.

## Start here

- `<value>` means you **must** supply a value. Do not type the angle brackets.
- `[value]` means the value is optional. Do not type the square brackets.
- `on|off` means choose **one**: `on` or `off`.
- `/es`, `/easyscripting` and `/script` are the same command.
- `/actor ...` is short for `/es actor ...`; `/scene ...` is short for `/es scene ...`; `/nickname ...` is short for `/es nickname ...`.
- `/actors` opens the NPC library; `/actors ...` with arguments also accepts the `/actor ...` subcommands. `/kits ...` is short for `/es kits ...`. There is no EasyScripting `/kit`, `/act` or `/finish` root command.
- Use lowercase command words. IDs such as `guard_1` use lowercase letters, numbers, underscores or hyphens, start with a letter/number, and contain at most 48 characters. Team IDs contain at most 12.
- A **player name** identifies a real player; an **actor ID** identifies your saved NPC. Renaming an NPC does not change its ID.
- Most time values use **ticks**: 20 ticks = about 1 second at normal server speed. 100 ticks = about 5 seconds.
- Health uses points: 2 health = 1 heart; a normal full-health player has 20 health.
- Coordinates are numbers in the specified/current world. These commands do not interpret vanilla `~` or `^` coordinates.
- Press Tab for available suggestions. GUI text input accepts `cancel` and expires after 60 seconds.
- Most production tools default to operators. Opening a menu does not grant its controls. Permission names below are shorthand: `actor` means `easyscripting.actor`. See [all permissions](PERMISSIONS.md).
- **Kit management and giving kits to someone else require actual operator status**, even if a permissions plugin grants other access. Each kit controls who may claim it for themselves.
- Commands that use your position, inventory or GUI must run in-game. In a server console, omit the initial slash; supply explicit recipients where supported.

## Find a command

[Studio and settings](#studio-and-settings) · [NPCs](#actors-and-npcs) · [Acting and playback](#acting-and-playback) · [NPC groups](#npc-groups-and-combat) · [Scenes](#scenes) · [Scene actions](#action-types) · [Recording sessions](#recording-sessions) · [Camera](#camera) · [Player controls](#player-controls) · [Takes](#takes-and-snapshots) · [Kits](#kits) · [Nicknames and skins](#nicknames-and-skins) · [Inventory](#inventory) · [Items and tools](#items-and-tools) · [Warps and spawn](#warps-and-spawn) · [World](#world-controls) · [Regions](#regions) · [Locks](#container-and-item-frame-locks) · [Effects](#effects) · [Chat](#chat) · [Server rules](#server-rules) · [Death behavior](#death-behavior) · [Teams](#teams) · [Villagers](#villagers) · [Voice](#voice-chat)

## Studio and settings

Access: `use` for opening the studio, help and status; `admin` for features, permissions and reload.

| Command | What it does | Example |
| --- | --- | --- |
| `/es` | Open the main studio GUI. | `/es` |
| `/actors` | Open the NPC library directly. | `/actors` |
| `/kits` | Open the kit library directly. All `/es kits` subcommands also work after `/kits`. | `/kits` |
| `/es help` | Show command groups you have permission to use. Use this guide for their individual options. | `/es help` |
| `/es menu [name]` | Open the main menu or a named page. | `/es menu actors` |
| `/es status` | Show plugin version, loaded scene/NPC counts, active jobs and voice availability. | `/es status` |
| `/es features` | Open feature switches. | `/es features` |
| `/es features <feature>` | Toggle that feature ON/OFF and save the setting. It toggles; there is no separate on/off argument. | `/es features effects` |
| `/es permissions <feature> <everyone\|permission.node>` | Change which permission is needed for an editable feature. | `/es permissions warp everyone` |
| `/es reload` | Validate and reload configuration/GUI YAML; close open studio menus. Invalid configuration keeps the previous settings. | `/es reload` |

Menu names: `main`, `scenes`, `actors`, `groups`, `players`, `kits`, `warps`, `session`, `recording`, `features`, `item`, `teams`, `production`, `world`, `effects`, `permissions`, `villagers`. Category pages also include `wardrobe`, `stage`, `organization` and `settings`.

Feature switches: `actors`, `scenes`, `recording`, `players`, `identity`, `kits`, `warps`, `items`, `inventory`, `locks`, `death`, `chat`, `world`, `regions`, `teams`, `villagers`, `effects`, `voice`.

Editable permission features: `actor`, `scene.play`, `scene.edit`, `record`, `player`, `identity`, `warp`, `warp.edit`, `items`, `inventory`, `locks`, `death`, `chat`, `world`, `world.edit`, `team`, `villager`, `effects`, `voice`. For example, `/es permissions warp easyscripting.warp` restores the ordinary warp permission rule. Kits use their own access settings. Admin, destructive and command-execution permissions are not editable through this command.

Studio home **Record session** opens the ON/OFF page (`/es menu session`). The saved NPC take picker remains available through Record & replay or `/es menu recording`.

Reload applies settings, not hand-edited actor/scene/kit/recording/group definitions. Restart the server to reload those definitions from disk.

## Actors and NPCs

Access: `actor`. A `PLAYER` NPC requires **Citizens** installed for your server version. Mob NPCs such as `ZOMBIE` do not.

| Command | What it does | Example |
| --- | --- | --- |
| `/es actor` or `/es actor list` | List saved actor IDs. | `/actor list` |
| `/es actor create <id> [type]` | Create an NPC at your location. Default type is PLAYER; a random name and skin are chosen automatically. | `/actor create guard` |
| `/es actor info <id>` | Show its name, skin owner, entity type, selected recording, mode, autoplay and combat settings. | `/actor info guard` |
| `/es actor gui <id> [section]` | Open its GUI. Sections: overview, appearance, movement, acting, combat. | `/actor gui guard combat` |
| `/es actor randomize <id>` | Choose another name and PLAYER skin; retain the same ID and equipment. Works during replay. | `/actor randomize guard` |
| `/es actor set <id> <setting> <value>` | Change one setting; see the complete list below. | `/actor set guard name RiverScout` |
| `/es actor here <id>` | Teleport the actor to you. | `/actor here guard` |
| `/es actor move <id> [speed]` | Make it navigate to where you are standing. Speed 0.1–5; default 1. | `/actor move guard 1.2` |
| `/es actor copy <source> <new-id>` | Create a copy at your location, retaining appearance and equipment. | `/actor copy guard guard_copy` |
| `/es actor hide <id>` | Remove its visible entity while keeping the saved actor. | `/actor hide guard` |
| `/es actor show <id>` | Show a hidden actor again; autoplay may start. | `/actor show guard` |
| `/es actor respawn <id>` | Recreate an existing actor at its saved spawn. It cannot restore an actor deleted by death. | `/actor respawn guard` |
| `/es actor delete <id>` | Remove the actor and its active saved definition. | `/actor delete guard_copy` |
| `/es actor attack <id> <online-player> [damage]` | Face the player, swing and deal damage once. Must be within 6 blocks; damage defaults to 1, range 0–1000. | `/actor attack guard Alex 2` |
| `/es actor kit <id> <kit>` | Give it the saved kit and save its equipment. Actual operators only; stop an active replay first. | `/actor kit guard starter` |
| `/es actor pattern <prefix> <line\|circle\|grid\|square> <count> [spacing] [type]` | Create a group of 1–200 actors arranged around you. Spacing defaults to 2 blocks (0.5–20); type defaults to PLAYER. | `/actor pattern extras line 5 2 ZOMBIE` |
| `/es actor all <group\|*> <setting> <value>` | Change a setting for a group, or every actor with `*`. | `/actor all extras look on` |
| `/es actor group <group\|*> <hide\|show\|respawn\|jump>` | Run that operation for the group/every actor. | `/actor group extras jump` |
| `/es actor group <group\|*> kit <kit>` | Give a kit to the group/every actor. Actual operators only. | `/actor group extras kit starter` |

The visible actor section labels are **Identity & clothing**, **Movement**, **Record & replay**, and **Combat & supplies**. Direct `/actor gui` section arguments remain `appearance`, `movement`, `acting`, and `combat`.

For example, create a mob without Citizens using `/actor create guard ZOMBIE`. Group patterns use their prefix as the group name. Server actor limits still apply.

### Every actor setting

Use `/actor set <id> <setting> <value>`.

| Setting | Meaning | Example |
| --- | --- | --- |
| `name <text>` | Change displayed name; keep skin and ID. | `/actor set guard name RiverScout` |
| `skin <account>` | Use a Java account's skin; PLAYER only. Account name, not a PNG or NameMC URL. | `/actor set guard skin Notch` |
| `group <id>` | Set its group tag; a matching registered `/es group` faction uses that membership. | `/actor set guard group extras` |
| `immortal on\|off` | ON: can be hit and knocked back but will not die. OFF: can die. New actors default to OFF. | `/actor set guard immortal on` |
| `hittable on\|off` | ON: allow direct melee hits. OFF: block melee/sweeps; falls, projectiles and explosions still work. | `/actor set guard hittable off` |
| `collidable on\|off` | Toggle physical entity collision. | `/actor set guard collidable off` |
| `nametag on\|off` | Show/hide overhead name. | `/actor set guard nametag on` |
| `tablist on\|off` | Show/hide a PLAYER NPC in the Tab player list. | `/actor set guard tablist on` |
| `look on\|off` | Toggle idle looking at nearby visible players; walking/wandering also tracks them. | `/actor set guard look on` |
| `wander on\|off` | Wander near visible players/NPCs, falling back to a bounded area around home. | `/actor set guard wander on` |
| `aggressive on\|off` | An ungrouped NPC retaliates when hit, using its own supplies and fallible combat reactions. Grouped NPCs use group Intelligence instead. | `/actor set guard aggressive on` |
| `pose <pose>` | Set an entity pose, such as STANDING, SNEAKING, SWIMMING or SLEEPING. Rendering depends on the entity. | `/actor set guard pose SNEAKING` |
| `glow on\|off` | Toggle glowing outline. | `/actor set guard glow on` |
| `sneak on\|off` | Toggle sneaking. | `/actor set guard sneak on` |
| `sprint on\|off` | Toggle PLAYER sprint flag; this alone does not create a route. | `/actor set guard sprint on` |
| `mode stop\|repeat\|reverse` | Alternative form of `/actor mode`. | `/actor set guard mode repeat` |
| `recording <id>` | Select an existing take; same validation and old-take cleanup as `/actor recording`. Requires record permission. | `/actor set guard recording entrance` |

Names/skins/modes can change during recording **playback**. A currently possessed NPC or scene-controlled NPC may reject conflicting edits. Stop before changing position, kit or selected recording.

A real NPC death permanently deletes it and broadcasts its displayed name leaving the server. Its selected take is deleted too, unless another NPC still uses it. `show`, `respawn`, scene reset and restarting the server cannot bring that deleted NPC back; create a new actor if needed.

## Acting and playback

Access: `actor`; starting/playing/stopping recordings and autoplay also need `record`.

| Command | What it does | Example |
| --- | --- | --- |
| `/es actor act <id> [recording-id]` | Put you in the NPC's place, identity and costume, then record your movement, equipment and animations. Omit the recording ID to generate one. | `/actor act guard entrance` |
| `/es actor finish` | Finish your acting, save the performance, restore you and start autoplay if enabled. | `/actor finish` |
| `/es actor cancel` | Discard the current acting session and restore you. | `/actor cancel` |
| `/es actor recording <id> <recording-id>` | Select an existing recording for an available actor. | `/actor recording guard entrance` |
| `/es actor play <id>` | Play its selected recording using its selected mode. | `/actor play guard` |
| `/es actor stop <id>` | Stop at its current position; turn off autoplay/wandering and keep damage already taken. | `/actor stop guard` |
| `/es actor mode <id> stop` | Play once, then stay at the last recorded position. | `/actor mode guard stop` |
| `/es actor mode <id> repeat` | At the end, teleport back to the recording start and play again forever. | `/actor mode guard repeat` |
| `/es actor mode <id> reverse` | Play forward, then backward, continuing back and forth. | `/actor mode guard reverse` |
| `/es actor autoplay <id> on` | Automatically start the selected take when eligible, including after acting save, load, show or respawn. | `/actor autoplay guard on` |
| `/es actor autoplay <id> off` | Disable future automatic starts; an already-running replay continues until stopped. | `/actor autoplay guard off` |

Select the mode before or after recording, or while the NPC is playing. The selected option glows in the GUI. Autoplay does not change the mode: use autoplay ON **and** repeat/reverse for continuous playback.

Example: `/actor create guard` → `/actor mode guard repeat` → `/actor act guard entrance` → move/jump/swing/change equipment → `/actor finish`. With default autoplay ON, the NPC starts replaying automatically.

While you act, direct melee damage/knockback is blocked. Falls, projectiles and explosions can still hurt you. NPC playback follows its Hittable and Immortal settings and allows native knockback before returning to the recorded route. NPC performances capture movement/equipment/animations; they do not re-execute block breaking, block placement or attacks as recorded world actions.

Elytra takes save the actual gliding state as well as pose/equipment. Old `FALL_FLYING` frames gain the flag on load; re-record an old take if it saved only a swimming pose. Stop clears the flight state.

## NPC groups and combat

Groups are factions of independent actors. Each NPC keeps its own equipment, backpack, health and other entity state. Nothing is pooled. Operators manage groups; the assigned real player leader and operators can issue orders. One real player may lead one group. `default` means unassigned and cannot be a managed faction ID.

| Command | What it does | Example |
| --- | --- | --- |
| `/es group` | Open your NPC groups. | `/es group` |
| `/es group gui [group]` | Open the group library or one group's controls. | `/es group gui red` |
| `/es group list` | List groups you can direct. | `/es group list` |
| `/es group create <group>` | Create a faction; adopt existing actors with the same group tag. Operators only. | `/es group create red` |
| `/es group delete <group>` | Delete the faction and unassign its actors, keeping them alive. Operators only. | `/es group delete red` |
| `/es group info <group>` | Print leader, member count, order, Intelligence and active enemies. | `/es group info red` |
| `/es group add <group> <actor>` | Add or transfer one existing actor, retaining its equipment and health. Operators only. | `/es group add red guard` |
| `/es group add <group> tag:<old-group>` | Transfer all actors with an existing tag. Operators only. | `/es group add red tag:guards` |
| `/es group remove <group> <actor>` | Remove an NPC from the faction without deleting it. Operators only. | `/es group remove red guard` |
| `/es group leader <group> <online-player>` | Assign a real account name as leader and begin following. Operators only. | `/es group leader red Alex` |
| `/es group leader <group> off` | Clear its leader and hold position. Operators only. | `/es group leader red off` |
| `/es group intelligence <group> on\|off` | ON enables coordinated attacks and defensive combat reactions. OFF follows movement orders only; automatic totem handling is separate. Operators only. | `/es group intelligence red on` |
| `/es group follow <group>` | Clear current attacks and follow the leader in formation; the leader's later hits add enemies. | `/es group follow red` |
| `/es group hold <group>` | Clear orders and stop at current positions. Intelligent groups can still defend themselves if attacked. | `/es group hold red` |
| `/es group stop <group>` | Same as hold. Use Intelligence OFF as well to stop retaliation. | `/es group stop red` |
| `/es group move <group>` | Walk into a formation around your current position. Requires an in-game director. | `/es group move red` |
| `/es group attack <group> <online-player>` | Add a nearby enemy player. Multiple targets divide the members into squads. | `/es group attack red Alex` |
| `/es group attack <group> actor:<id>` | Add an enemy NPC. Its managed group becomes an enemy too. | `/es group attack red actor:blue_1` |
| `/es group fight <group> <enemy-group>` | Engage another faction and its leader. An intelligent opposing group responds. | `/es group fight red blue` |
| `/es actor set <id> aggressive on\|off` | Toggle standalone retaliation. Uses real melee damage, misses, reaction delays, potions and shields. | `/actor set guard aggressive on` |

Start with `/actor pattern red grid 100 2 PLAYER`, then `/es group create red` and `/es group leader red Alex`. A pattern gives each NPC the `red` tag automatically. Use `/actor kit red_1 fighter` or `/es kits claim fighter actor:red_1` to give one NPC its own supplies. Read [NPC-GROUPS.md](NPC-GROUPS.md) for a complete setup and prototype limits.

NPCs offhand spare totems and refill after a pop, with a default one-tick delay (about 50 ms at 20 TPS). Intelligent NPCs can throw up to three carried beneficial splash potions upward, one at a time, before fighting. They sometimes miss or jump after being hit. A carried shield may be raised after a random delay if an enemy above them holds a mace; this reaction is deliberately not guaranteed. They never get free replacement supplies. AI yields while an NPC is acting, replaying, or owned by a scene.

## Scenes

A scene is a saved list of actions at chosen times. Access: `scene.play`; editing also needs `scene.edit`. Actions have their own permissions (next section).

| Command | What it does | Example |
| --- | --- | --- |
| `/es scene` or `/es scene list` | List scene IDs. | `/scene list` |
| `/es scene create <id>` | Create an empty scene. | `/scene create opening` |
| `/es scene bind <id> <alias> <target>` | Give a target a short name inside this scene. | `/scene bind opening guard actor:guard` |
| `/es scene add <id> <tick> <type> <target> [key=value;key=value]` | Add a timed action. | `/scene add opening 20 swing guard` |
| `/es scene remove <id> <number>` | Delete one action by its displayed number, starting at 1. | `/scene remove opening 2` |
| `/es scene append <destination> <source> <offset>` | Copy the source's actions into destination, adding offset ticks to each original time. | `/scene append opening salute 100` |
| `/es scene repeat <id> <from> <through> <copies> <interval>` | Add more copies of an inclusive tick range. Interval must be longer than that range; 1–100 additional copies. | `/scene repeat opening 0 40 3 60` |
| `/es scene restore <id> on\|off` | Choose whether completion automatically restores the captured state. | `/scene restore opening on` |
| `/es scene play <id>` | Start the scene. | `/scene play opening` |
| `/es scene pause <id>` | Pause a running scene. | `/scene pause opening` |
| `/es scene resume <id>` | Continue a paused scene. | `/scene resume opening` |
| `/es scene stop <id>` | Stop and restore the captured state. | `/scene stop opening` |
| `/es scene reset <id>` | Restore the retained snapshot of a completed take. | `/scene reset opening` |
| `/es scene status <id>` | Show whether it is idle, running or paused. | `/scene status opening` |
| `/es scene gui <id>` | Open its timeline editor. | `/scene gui opening` |
| `/es scene delete <id>` | Stop and delete the saved scene. | `/scene delete opening` |

Targets: `actor:guard` for an actor ID, `player:Alex` for an online player, `self`/`player:self` for the director, or a binding such as `guard`. A console-started scene cannot use self. Other real-player targets also need `player.others`.

Times/offsets/range boundaries are 0–720000 ticks; repeat intervals are 1–720000 ticks. Actions at the same tick run in saved order. Scene/recording ownership prevents two productions from controlling the same target at once.

### Action types

These are **all** scene action types. The columns below provide an example of the part **after** `/scene add opening 20`. For example, `swing actor:guard` becomes `/scene add opening 20 swing actor:guard`.

Separate arguments with semicolons. Values can contain spaces; literal semicolons inside a value require editing the scene YAML. `world`, `x`, `y`, `z` are required for positions; yaw/pitch are optional (default 0). MiniMessage such as `<gold>Hello` works in text fields that say formatted text.

| Action | What it does / arguments | Example suffix | Permission |
| --- | --- | --- | --- |
| `teleport` | Teleport to world/x/y/z; optional yaw/pitch. | `teleport actor:guard world=world;x=100;y=64;z=100` | player |
| `move` | Navigate an actor to world/x/y/z; optional speed 0.1–5, default 1. | `move actor:guard world=world;x=105;y=64;z=100;speed=1` | actor |
| `look` | Face target `at`. | `look actor:guard at=self` | player |
| `rotation` | Set yaw (−360–360) and pitch (−90–90). | `rotation actor:guard yaw=90;pitch=0` | player |
| `velocity` | Push with x/y/z velocities, each −10–10. | `velocity actor:guard x=0;y=0.5;z=1` | player |
| `health` | Set `value` from 0.01 to the target's max health. | `health actor:guard value=10` | player |
| `damage` | Deal `value` damage, 0–1000; normal damage rules apply. | `damage actor:guard value=2` | effects |
| `attack` | Actor attacks `victim` within 6 blocks; optional damage defaults to 1. | `attack actor:guard victim=self;damage=2` | actor |
| `swing` | Main-hand swing animation. | `swing actor:guard` | effects |
| `hurt` | Hurt flash only; does not remove health. | `hurt actor:guard` | effects |
| `critical` | Critical-hit particles. | `critical actor:guard` | effects |
| `jump` | Set upward velocity; optional height 0.1–3, default 0.42. | `jump actor:guard height=0.42` | actor |
| `pose` | Set `value` to an entity pose. | `pose actor:guard value=SNEAKING` | actor |
| `sneak` | Toggle actor sneaking. | `sneak actor:guard value=on` | actor |
| `sprint` | Toggle PLAYER actor sprint flag. | `sprint actor:guard value=on` | actor |
| `equip` | Set slot/material; optional amount 1–64, default 1. Slots include HAND, OFF_HAND, HEAD, CHEST, LEGS, FEET. | `equip actor:guard slot=HAND;material=IRON_SWORD` | items |
| `potion` | Apply effect; optional ticks 1–72000 (default 200), amplifier 0–10 (0 = level I). | `potion self effect=speed;ticks=200;amplifier=1` | player |
| `flag` | Apply a supported player control using name/value. | `flag self name=no-hunger;value=on` | player |
| `fire` | Set fire duration 0–12000 ticks; 0 extinguishes. | `fire actor:guard ticks=40` | effects |
| `death` | Kill target. An actor is permanently removed; this is a real death. | `death actor:guard` | destructive |
| `fake-death` | Death sound, hurt flash and particles only; no actual death/corpse/disconnect. | `fake-death actor:guard` | effects |
| `title` | Formatted title for a player; optional subtitle and ticks 1–1200 (default 40). | `title self text=<gold>Take one;subtitle=Action!;ticks=60` | effects |
| `actionbar` | Formatted text above a player's hotbar. | `actionbar self text=<yellow>Ready` | effects |
| `message` | Formatted chat text to the target player. | `message self text=<green>Go!` | chat |
| `sound` | Sound at target; volume 0–4 (default 1), pitch 0.1–2 (default 1). | `sound actor:guard sound=entity.player.levelup;volume=1;pitch=1` | effects |
| `particle` | Data-free particle at target; count 1–500, default 20. | `particle actor:guard particle=POOF;count=20` | effects |
| `lightning` | Visual lightning at target; no real strike damage. | `lightning actor:guard` | effects |
| `explosion` | Explosion particles/sound; no block destruction. | `explosion actor:guard` | effects |
| `time` | Set target world's time, 0–24000. | `time self value=6000` | world |
| `weather` | Set target world's weather to clear/rain/thunder. | `weather self value=clear` | world |
| `wait` | Do nothing at this tick; useful as the scene's final time marker. Still requires a valid target. | `wait self` | scene.play |
| `player-command` | Run command as target player; supply command without leading slash. | `player-command self command=spawn` | commands.player |
| `console-command` | Run command as server console; still supply a valid scene target. | `console-command self command=say Action!` | commands.console |

Command actions also require `config.yml → security.allow-command-actions: true`; both command permission nodes default to false. Real death requires the separate `easyscripting.destructive` permission (not granted just by being op). Target/world changes follow their feature switches and other server plugins' rules.

A small working example, after creating `guard`:

```text
/scene create opening
/scene bind opening guard actor:guard
/scene add opening 0 title self text=<gold>Take one
/scene add opening 20 swing guard
/scene add opening 40 hurt guard
/scene add opening 60 wait guard
/scene restore opening on
/scene play opening
```

## Recording sessions

Access: `record` (operators by default). These commands control server recording-session mode. They do not capture anyone's movement or inventory.

| Command | What it does | Example |
| --- | --- | --- |
| `/es record on` | Show the recording MOTD and block all non-operator logins, including reconnects. Players already online stay connected. | `/es record on` |
| `/es record off` | End the session and return to normal MOTD/login rules. Repeating on/off reports the current state. | `/es record off` |

Only current operators can join during a recording session. A previous connection, the server allow-list and `server.bypass` do not let a non-operator reconnect. Independent bans/whitelist/server-lock rules still apply; stopping a recording does not turn those off. The recording MOTD is configured in recording.yml and always shown while the session is on; the old change-motd switch is no longer used.

The old `/es record start`, stop, startall, stopall, play, playgroup, stopplay, delete and list subcommands were removed in 0.1.6. For NPC movement, use `/actor act guard entrance`, perform, then `/actor finish`; use `/actor play guard`, `/actor mode guard repeat` and `/actor stop guard` for replay. Browse saved NPC performances with `/es menu recording`.

A permanent NPC death/deletion also removes its selected take when no other NPC references it. Shared takes stay until the last referencing NPC is removed. Hiding, stopping, world unloading, disabling actors and normal shutdown retain takes. Replacing an NPC's selected take removes its old take when unreferenced. Existing independent legacy files are not automatically purged just by upgrading.

`/es take start` and `/es take stop` remain available for sessions that also capture/restore player snapshots. They share the same session state and login/MOTD rules; `/es record off` ends such a session and restores its participating snapshots too. recording.yml controls session chat and optional voice behavior.

## Camera

Access: `effects`; run in-game.

| Command | What it does | Example |
| --- | --- | --- |
| `/es camera move <x> <y> <z> <ticks> [yaw] [pitch]` | Temporarily use spectator mode and move your camera to coordinates in your current world; 1–72000 ticks. Omitted rotation keeps your initial view direction. | `/es camera move 100 80 100 100 90 0` |
| `/es camera stop` | Stop the camera and restore your saved state. | `/es camera stop` |

## Player controls

Access: `player`; changing someone else also needs `player.others`. General form: `/es player <operation> [value] [online-player]`. Omit player to affect yourself.

For commands without a value, targeting somebody still needs a placeholder: `/es player heal on Alex`. Here `on` simply occupies the value position.

| Command | What it does | Example |
| --- | --- | --- |
| `/es player health <points> [player]` | Set health, 0.01 to max health. | `/es player health 6 Alex` |
| `/es player heal [on] [player]` | Fill health and extinguish fire. | `/es player heal on Alex` |
| `/es player feed [on] [player]` | Fill hunger and saturation. | `/es player feed` |
| `/es player hunger <0..20> [player]` | Set food level and clear saturation. | `/es player hunger 10` |
| `/es player gamemode <survival\|creative\|adventure\|spectator> [player]` | Change gamemode. | `/es player gamemode survival Alex` |
| `/es player flight on\|off [player]` | Allow/disallow flight. | `/es player flight on` |
| `/es player invulnerable on\|off [player]` | Toggle the real player's invulnerability. | `/es player invulnerable on` |
| `/es player invisible on\|off [player]` | Toggle entity invisibility; different from hiding the whole player with vanish. | `/es player invisible on` |
| `/es player glow on\|off [player]` | Toggle glowing outline. | `/es player glow on Alex` |
| `/es player speed <0..1> [player]` | Set walking speed; ordinary default is 0.2. | `/es player speed 0.2` |
| `/es player fire <0..12000> [player]` | Set fire ticks; 0 extinguishes. | `/es player fire 0 Alex` |
| `/es player potion <preset> [player]` | Apply a preset from potions.yml. Shipped names: chase, underwater, dramatic. | `/es player potion chase Alex` |
| `/es player clear-effects [on] [player]` | Remove current potion effects. | `/es player clear-effects on Alex` |
| `/es player otp <name>` | Teleport you to the last logout position saved for that player; does not edit their offline inventory. | `/es player otp Alex` |

### Every player toggle

Each supports `on|off` and optional `[player]`. Example: `/es player freeze on Alex`; undo with `/es player freeze off Alex`.

| Command | When ON |
| --- | --- |
| `/es player freeze on\|off [player]` | Hold the player's position for the shot. |
| `/es player halfheart on\|off [player]` | Prevent lethal damage without a held totem. Held totems pop normally, and half-heart protection stays enabled afterward. This is separate from NPC Immortal. |
| `/es player keepinv on\|off [player]` | Keep inventory/experience through death. |
| `/es player no-hunger on\|off [player]` | Stop hunger changes. |
| `/es player no-durability on\|off [player]` | Stop item durability loss. |
| `/es player no-build on\|off [player]` | Block that player's block placement. |
| `/es player no-break on\|off [player]` | Block that player's block breaking. |
| `/es player no-pvp on\|off [player]` | Prevent PvP involving that player. |
| `/es player lock-inventory on\|off [player]` | Block inventory rearrangement. |
| `/es player lock-armor on\|off [player]` | Block armor changes through supported inventory interactions. |
| `/es player lock-pickup on\|off [player]` | Block item pickup. |
| `/es player vanish on\|off [player]` | Hide from viewers without see.vanish; suppress their ordinary join/leave announcements while vanished. |
| `/es player pauseeffects on\|off [player]` | Keep current potion effects active without their timers running down, then restore their saved remaining durations when switched OFF. |

## Takes and snapshots

Access: `record`; the `all` scope also needs `player.others`. A take snapshot saves player state for resetting a shot. It does **not** create a movement replay or back up world blocks.

| Command | What it does | Example |
| --- | --- | --- |
| `/es take snapshot` | Save your location, inventory, health, hunger, XP, gamemode, effects and supported flags. | `/es take snapshot` |
| `/es take reset` | Restore your saved take; you can repeat the reset. | `/es take reset` |
| `/es take discard` | Forget the take snapshot without restoring it. | `/es take discard` |
| `/es take start <id> [self\|all]` | Start a production session with snapshots for yourself (default) or all online participants. | `/es take start episode all` |
| `/es take stop [reset\|keep]` | End the production session; default reset restores participants, keep leaves their current state. | `/es take stop reset` |

Recording-session chat, server-list/MOTD and voice options are in recording.yml. A snapshot is not a world backup or a recording of other plugins' state.

## Kits

Anyone with ordinary `use` access can browse the kits they are allowed to claim. **Only actual operators manage kits or give kits to others/NPCs.** A kit replaces the recipient's saved 41-slot loadout: hotbar, storage, armor and offhand. It does not add items around the existing inventory. Blank kit slots clear those inventory slots.

### Claim or give a kit

| Command | What it does | Example |
| --- | --- | --- |
| `/es kits` | Open the library; non-operators see only kits they may claim. | `/es kits` |
| `/es kits claim <kit>` | Equip it on yourself, subject to that kit's access setting. | `/es kits claim starter` |
| `/es kits claim <kit> <player>` | Equip it on an online real player. Giving to others requires op. | `/es kits claim starter Alex` |
| `/es kits claim <player> <kit>` | Player-first alternative to the line above. | `/es kits claim Alex starter` |
| `/es kits claim <kit> *` | Equip it on every online real player; NPCs are excluded. Op only. | `/es kits claim starter *` |
| `/es kits claim * <kit>` | Recipient-first alternative for all online players. | `/es kits claim * starter` |
| `/es kits claim <kit> actor:<id>` | Give the kit to an NPC/actor and save its equipment. Op only. | `/es kits claim starter actor:guard` |
| `/es kits claim <kit> player:<name>` | Explicit player target; useful if their name is also a kit ID. UUIDs are accepted too. | `/es kits claim starter player:Alex` |

A real player's account name or temporary nickname works. If both arguments are kit IDs, use `player:` or `actor:` to remove ambiguity. Console must specify a recipient. Wildcard validates the selected players first; if one is dead or busy in a reserved take, resolve that before trying again.

For NPCs, stop the active recording/scene or finish acting before applying a kit. PLAYER actors receive a player loadout; other living actors receive supported main-hand, armor and offhand equipment plus their own 36-slot reserve backpack. Mob main-hand equipment comes from kit slot 0; that item is removed from the reserve copy.

### Create, edit and delete

Use singular `/es kit` for create/save/edit/delete. `/kits` is an alias of plural `/es kits` for the GUI, claims, access and imports; `/kits create` and `/es kits create` are not commands. Use `/es kit save fighter` to capture your inventory, or `/es kit create fighter` for an empty kit.

| Command | What it does | Example |
| --- | --- | --- |
| `/es kit` or `/es kit list` | List kit IDs visible to you. | `/es kit list` |
| `/es kit create <id>` | Create an empty kit; initially operators only. | `/es kit create starter` |
| `/es kit save <id>` | Copy your current inventory into a new/existing kit. Existing access settings are retained. | `/es kit save starter` |
| `/es kit edit <id>` | Open the copied-item editor. Save applies edits to the kit; leaving without Save discards the draft. | `/es kit edit starter` |
| `/es kit apply <id>` | Older self-equip command; follows the same claim policy as kits claim. | `/es kit apply starter` |
| `/es kit delete <id>` | Delete the saved kit. | `/es kit delete starter` |

Editor: slots 0–8 hotbar, 9–35 storage, 36 boots, 37 leggings, 38 chestplate, 39 helmet, 40 offhand. Click your inventory to copy an item into the editor; right-click a draft slot to clear it. **Import my inventory** replaces the draft. **Save & equip** saves and puts the loadout on you. The GUI has separate **Give to player**, **Give to NPC** and **Who can claim?** controls.

### Who may claim

| Command | Who can claim it | Example |
| --- | --- | --- |
| `/es kits access <kit> operators` | Operators only. | `/es kits access starter operators` |
| `/es kits access <kit> everyone` | Every player. | `/es kits access starter everyone` |
| `/es kits access <kit> player <online-player>` | Exactly that player's UUID plus all operators. | `/es kits access starter player Alex` |

Choosing operators/everyone clears the previous specific-player selection. Choosing another player replaces the old one. A nickname change or reconnect does not transfer access to someone else. New, legacy and external-provider imports default to operators only; EasyScripting exported files retain their own access settings. Operators may explicitly gift a kit even to someone who cannot self-claim it. There are no built-in claim prices, cooldowns or one-time limits.

### Import and export

| Command | What it does | Example |
| --- | --- | --- |
| `/es kits imports` | Open the provider picker, then choose a kit or **Import all kits**. | `/es kits imports` |
| `/es kits import <provider> <kit> [new-id]` | Import one kit's items. Omit new-id to generate an unused ID. | `/es kits import PlayerKits2 starter imported_starter` |
| `/es kits importall <provider>` | Import every kit from one provider gradually, one per tick. | `/es kits importall PlayerKits2` |
| `/es kits cancelimport` | Stop the active bulk import; keep kits already imported. | `/es kits cancelimport` |
| `/es kits export <id>` | Write a portable YAML copy under plugins/EasyScripting/kit-exports/. | `/es kits export starter` |

Provider command names: `PlayerKits2` (PlayerKits 2), `PlayerKits` (legacy), `Essentials` (EssentialsX), `CMI`, `EasyScripting` (exported YAML). Other providers must already be installed/enabled and allowed in kits.yml. For provider kit names containing spaces, use the GUI.

Imports copy supported items/metadata, armor and offhand. They do not copy costs, cooldowns, permissions, claim history or reward commands. Values resolved for the importing player's placeholders are saved as those values. Unsupported/oversized definitions report errors.

Bulk import never overwrites saved kits: collisions receive suffixes such as `_2`. Importing again creates further copies. One bulk import can run at a time; it stops if the initiating player disconnects/loses op, kits are disabled or the plugin shuts down. It reports success/failure counts and up to five error details. The provider kit-count cap defaults to 1000 in kits.yml.

To move a kit between servers: export, copy its YAML into the other server's kit-exports folder, then `/es kits import EasyScripting starter imported_starter`. Keep Minecraft item formats compatible. For an unsupported provider, equip the kit yourself and use **Import my inventory**.

## Nicknames and skins

Access: `identity`; `/nickname` targeting someone else/resetting all also requires `player.others`. Blacklist/profile editing additionally needs `admin`.

| Command | What it does | Example |
| --- | --- | --- |
| `/es nickname <online-player-or-nickname>` | Give an online real player a random readable API nickname; preserve skin. Excludes NPCs. | `/nickname Alex` |
| `/es nickname <player-or-nickname> off` | Reset one player, accepting their account name or current nickname. | `/nickname RiverScout off` |
| `/es nickname off` | Reset all temporary nicknames and cancel pending requests. | `/nickname off` |
| `/es nick set <name>` | Set your own chosen temporary name. | `/es nick set RiverScout` |
| `/es nick random [profile]` | Choose your own name from a configured local profile; default profile is default. | `/es nick random default` |
| `/es nick reset` | Restore your original identity. | `/es nick reset` |
| `/es nick info <name>` | Show recorded identity history associated with the name. | `/es nick info Alex` |
| `/es nick blacklist add <name>` | Block the identity and purge matching existing NPCs/nicknames. | `/es nick blacklist add ReservedName` |
| `/es nick blacklist remove <name>` | Allow the name again. | `/es nick blacklist remove ReservedName` |
| `/es nick blacklist list` | List blocked names. | `/es nick blacklist list` |
| `/es nick profile add <profile> <name>` | Add a name to a local random pool. | `/es nick profile add extras RiverScout` |
| `/es nick profile remove <profile> <name>` | Remove a name from that pool. | `/es nick profile remove extras RiverScout` |
| `/es skin <account-or-texture-url> [auto\|slim\|classic]` | Change your skin. Account lookup is asynchronous. auto preserves the account's model; raw texture URLs default to classic. | `/es skin Notch auto` |

Texture URLs must be `https://textures.minecraft.net/texture/<hash>`, not arbitrary PNG/NameMC links. Use `/actor set <id> skin <account>` for NPCs.

Temporary nicknames appear in normal display-name chat, Tab, nametags, death/killer and leave messages, then expire on disconnect. The next join uses the real name. Your client may show the nickname in server-controlled UI, but a server cannot rename the account authenticated by your launcher. Another plugin's custom formatting may deliberately retain account names. API/fallback settings are in nicknames.yml.

## Inventory

Access: `inventory`. These are staff inventory tools, separate from kit claiming. Optional target defaults to yourself and must be online.

| Command | What it does | Example |
| --- | --- | --- |
| `/es inventory view [player]` | Open a read-only inventory copy; reopen to refresh. | `/es inventory view Alex` |
| `/es inventory ender [player]` | Open a read-only ender-chest copy. | `/es inventory ender Alex` |
| `/es inventory save [player]` | Save an inventory rollback snapshot. | `/es inventory save Alex` |
| `/es inventory history [player]` | List saved rollback IDs. | `/es inventory history Alex` |
| `/es inventory restore <player> <snapshot-id>` | Replace that player's inventory with the selected snapshot; no world rollback. | `/es inventory restore Alex <id-from-history>` |
| `/es inventory restock [player]` | Fill existing stacks to their maximum size. | `/es inventory restock Alex` |
| `/es inventory fill` | Randomly fill empty slots of a container you are looking at within 6 blocks, using items.yml. | `/es inventory fill` |

## Items and tools

Access: `items`; run in-game. Editing commands affect the item in your **main hand**.

| Command | What it does | Example |
| --- | --- | --- |
| `/es item name <text>` | Set a formatted item name. | `/es item name <gold>Director's Key` |
| `/es item lore <line\|line>` | Set description lines; the literal `\|` separates lines. | `/es item lore <gray>Scene prop\|<yellow>Return after filming` |
| `/es item repair` | Remove all durability damage. | `/es item repair` |
| `/es item durability <damage>` | Set damage used, from 0 to max durability. 0 means fully repaired. | `/es item durability 10` |
| `/es item unbreakable on\|off` | Toggle unbreakable item metadata. | `/es item unbreakable on` |
| `/es item amount <number>` | Set current stack size, 1 to the item's max. | `/es item amount 16` |
| `/es item enchant <enchantment> <level>` | Add an enchantment, 1–255; level 0 removes it. | `/es item enchant sharpness 5` |
| `/es item attribute <attribute> <amount> [operation] [slot]` | Add/replace this plugin's modifier for an attribute, amount −1000–1000. | `/es item attribute attack_damage 3 ADD_NUMBER mainhand` |
| `/es item give <material> [amount]` | Give yourself an item; default amount 1, bounded by max stack size. | `/es item give DIAMOND 8` |
| `/es item head <account>` | Give yourself a player head. | `/es item head Notch` |
| `/es item randomhead [profile]` | Give a head using a name from a configured identity pool. | `/es item randomhead default` |
| `/es item tool kickstick` | Give the tagged moderation kick stick; using it still requires moderation permission. | `/es item tool kickstick` |
| `/es item tool stasisrod` | Give a tagged rod. Right-click a player to toggle freeze (requires player and player.others), or a living mob to toggle its AI. | `/es item tool stasisrod` |
| `/es item tool regionwand` | Give the region selection wand; selecting requires world.edit. | `/es item tool regionwand` |
| `/es item stasis <pops> <delay-ticks>` | Give a special totem linked to where you stand. After 1–64 pops, its last pop schedules a teleport there after 0–72000 ticks. | `/es item stasis 3 40` |

Attribute operations: `ADD_NUMBER` (default: add a flat amount), `ADD_SCALAR` (add a fraction of the base value), `MULTIPLY_SCALAR_1` (multiply by 1 + amount). Slots: `mainhand` (default), `offhand`, `hand`, `head`, `chest`, `legs`, `feet`, `any`. Example attribute names: `attack_damage`, `movement_speed`, `max_health`.

## Warps and spawn

Access: `warp`; saving/deleting/access changes need `warp.edit`; teleporting another player also needs `player.others`. Unauthorized warps are hidden from lists and suggestions.

| Command | What it does | Example |
| --- | --- | --- |
| `/es warp save <id>` | Save your current location and view direction. | `/es warp save courtyard` |
| `/es warp go <id> [player]` | Teleport you, or the selected online player, to the warp. | `/es warp go courtyard Alex` |
| `/es warp delete <id>` | Delete the saved warp. | `/es warp delete courtyard` |
| `/es warp permission <id> <everyone\|op\|permission.node>` | Set the warp's access rule. `op` uses warp.admin permission. | `/es warp permission courtyard everyone` |
| `/es warp list` | Show warps you may use. | `/es warp list` |
| `/es spawn set` | Save the special spawn warp at your location. | `/es spawn set` |
| `/es spawn` or `/es spawn go` | Teleport yourself to the saved spawn warp. | `/es spawn` |

Automatic spawn routing is configured in config.yml (`spawn.on-first-join` and `spawn.on-respawn`); `spawn.bypass` bypasses that automatic routing.

## World controls

Access: `world`; run in-game. These generally affect your current world or your own view. Lock/allow also need `admin`; clean needs the explicit `destructive` permission.

| Command | What it does | Example |
| --- | --- | --- |
| `/es world time <0..24000>` | Set current world's time; 6000 is noon. | `/es world time 6000` |
| `/es world weather clear\|rain\|thunder` | Set current world's weather. | `/es world weather clear` |
| `/es world border <size>` | Show your own temporary border centered at you, size 1–59999968. | `/es world border 40` |
| `/es world border reset` | Restore your normal world border view. | `/es world border reset` |
| `/es world top` | Teleport you to the surface at your horizontal location. | `/es world top` |
| `/es world teleport <loaded-world>` | Teleport you to that world's spawn; does not create/load worlds. | `/es world teleport world_nether` |
| `/es world clean <mobs\|hostile\|passive\|items\|all\|ENTITY_TYPE> [radius]` | Remove matching nearby entities, excluding players/actors; default radius 32, range 1–128. | `/es world clean items 16` |
| `/es world limit view <2..32>` | Set your view distance in chunks. | `/es world limit view 8` |
| `/es world limit send <2..32>` | Set your chunk-send distance. | `/es world limit send 8` |
| `/es world limit simulation <2..32>` | Set your simulation distance. | `/es world limit simulation 6` |
| `/es world lock <world> on\|off` | Restrict entering a dimension/world. | `/es world lock world_nether on` |
| `/es world allow <world> <account-name> on\|off` | Add/remove a name from that world's entry allow-list. | `/es world allow world_nether Alex on` |

World-access bypass uses `world.bypass`. The personal border does not resize the whole server's world border. Cleanup removes entities; resetting a take does not undo it.

## Regions

Access: `world.edit`; run in-game. A region is a saved rectangular selection of blocks.

| Command | What it does | Example |
| --- | --- | --- |
| `/es region wand` | Give a selection wand; left/right click blocks to choose the two corners. | `/es region wand` |
| `/es region pos1` | Set corner 1 at your position. | `/es region pos1` |
| `/es region pos2` | Set corner 2 at your position. | `/es region pos2` |
| `/es region chunk` | Select your current chunk's full world height; saving may exceed the configured block-count limit. | `/es region chunk` |
| `/es region save <id>` | Gradually capture the selection as a saved region. | `/es region save stage` |
| `/es region restore <id>` | Gradually replace blocks with the saved region, including supported container/sign contents. | `/es region restore stage` |
| `/es region cancel <id>` | Stop an active save/restore job; already restored blocks remain changed. | `/es region cancel stage` |
| `/es region delete <id>` | Delete the saved region definition. | `/es region delete stage` |
| `/es region list` | List saved region IDs. | `/es region list` |

Capture **before** modifying the set, and wait for capture to finish. Keep relevant chunks loaded. Limits are in config.yml. This does not back up players, entities or every specialized block entity. See [storage details](CONFIGURATION.md).

## Container and item-frame locks

Access: `locks`; run in-game and look at the target within reach. Players with `locks.bypass` bypass these locks.

| Command | What it does | Example |
| --- | --- | --- |
| `/es lock container on` | Lock the targeted container. | `/es lock container on` |
| `/es lock container off` | Unlock it. | `/es lock container off` |
| `/es lock frame on` | Lock the targeted item frame. | `/es lock frame on` |
| `/es lock frame off` | Unlock it. | `/es lock frame off` |

## Effects

Access: `effects`; most require an in-game player. Point at a block (up to 64 blocks) for position-based effects; otherwise they use a point ahead of you.

| Command | What it does | Example |
| --- | --- | --- |
| `/es effect lightning` | Visual lightning where you aim; no strike damage. | `/es effect lightning` |
| `/es effect explosion` | Explosion particles/sound; no destruction. | `/es effect explosion` |
| `/es effect orbital` | Descending flame effect ending in a visual explosion. | `/es effect orbital` |
| `/es effect totem` | Show your totem activation animation; does not consume a totem. | `/es effect totem` |
| `/es effect arrows [amount]` | Launch arrows in your view direction; default 1, cap from effects.yml. | `/es effect arrows 5` |
| `/es effect snowballs [amount]` | Launch snowballs. | `/es effect snowballs 5` |
| `/es effect rod [amount]` | Launch fast snowball projectiles; not a fishing-hook capture. | `/es effect rod 3` |
| `/es effect railgun` | Fire a traced beam with particles and configured damage; blocks can stop it. | `/es effect railgun` |
| `/es effect wolves [amount]` | Spawn temporary wolves owned by you; default 1, default cap 12. | `/es effect wolves 3` |
| `/es effect destructive-explosion` | A real explosion that can damage entities and destroy blocks. | `/es effect destructive-explosion` |
| `/es effect bossbar <text>` | Show a formatted bossbar to yourself. | `/es effect bossbar <gold>Take one` |
| `/es effect bossbar off` | Hide your bossbar. | `/es effect bossbar off` |
| `/es effect stop` | Stop this plugin's temporary effect entities/orbital jobs for everyone; does not hide bossbars. | `/es effect stop` |

Projectile counts accept 1–40 and may be restricted further by effects.yml. Projectiles/wolves/railgun can cause normal combat damage. Destructive explosion needs **both** `easyscripting.destructive` and `config.yml → security.destructive-effects: true`; it is not enabled merely by being op.

## Chat

Access: `chat`. These messages/settings are separate from the death-behavior commands below.

| Command | What it does | Example |
| --- | --- | --- |
| `/es chat block` | Toggle public chat blocking and announce the state change. | `/es chat block` |
| `/es chat block on` | Only current operators may send public chat. | `/es chat block on` |
| `/es chat block off` | Unblock ordinary public chat and announce it. | `/es chat block off` |
| `/es chat clear` or `/es chat clear all` | Send blank lines to everyone's chat. Does not delete server logs. | `/es chat clear` |
| `/es chat clear self` | Clear only your visible chat area. | `/es chat clear self` |
| `/es chat broadcast <text>` | Send the text to everyone's chat and as an on-screen title. | `/es chat broadcast Filming starts now` |
| `/es chat join <name>` | Print a yellow simulated join message. Does not create/connect a player. | `/es chat join RiverScout` |
| `/es chat leave <name>` | Print a yellow simulated leave message. Does not disconnect anyone. | `/es chat leave RiverScout` |
| `/es chat death <name>` | Print a white “Name died” message. Does not kill anyone. | `/es chat death RiverScout` |

`block` replaces the old `mute` subcommand. A de-opped player cannot bypass blocked chat through `chat.bypass`; that permission only bypasses separate recording-session chat suppression. Repeating the same block state does not repeat the announcement. Templates are in messages.yml, title options in moderation.yml. Broadcast text is inserted as plain text into those templates.

## Server rules

Access: `admin`. These settings persist in moderation.yml.

| Command | What it does | Example |
| --- | --- | --- |
| `/es server lock on\|off` | Enable/disable the recording server's login lock. Existing players are not kicked just by enabling it. | `/es server lock on` |
| `/es server allow <account-name>` | Add an account to the login allow-list. | `/es server allow Alex` |
| `/es server deny <account-name>` | Remove it from that allow-list; not a separate Minecraft ban. | `/es server deny Alex` |
| `/es server build on\|off` | ON permits block placing; OFF blocks it for non-bypass players. | `/es server build off` |
| `/es server break on\|off` | ON permits block breaking; OFF blocks it for non-bypass players. | `/es server break off` |
| `/es server pvp on\|off` | ON permits PvP; OFF blocks it under the production rule. | `/es server pvp off` |

`server.bypass` bypasses the login lock. `world.bypass` bypasses the applicable global building/world restrictions. Individual player flags and other plugins can still impose their own restrictions.

## Death behavior

Access: `death`; targeting others also needs `player.others`. These commands choose what happens **after a player really dies**; running them does not kill the player.

| Command | What it does | Example |
| --- | --- | --- |
| `/es death normal [player]` | Use ordinary death/respawn behavior. | `/es death normal Alex` |
| `/es death spectator [player]` | Auto-respawn into spectator after death; the player also needs the configured spectator permission. | `/es death spectator Alex` |
| `/es death kick [player]` | Kick the player after their next death. | `/es death kick Alex` |
| `/es death respawn [player]` | Automatically respawn after death. | `/es death respawn Alex` |
| `/es death scene <scene-id> [player]` | Run that scene after the player's respawn; setting it also needs scene.edit. | `/es death scene aftermath Alex` |
| `/es death scene off [player]` | Remove their respawn-scene trigger. | `/es death scene off Alex` |

Modes remain saved until changed. Respawn scenes run with the affected player's permissions and a 10-second recursion guard. `/es chat death Alex` is only an announcement; the scene action `death` is an actual kill. NPC deaths permanently delete the actor.

## Teams

Access: `team`. These are shared scoreboard teams, not per-viewer disguises. IDs are at most 12 characters.

| Command | What it does | Example |
| --- | --- | --- |
| `/es team` or `/es team list` | List team IDs. | `/es team list` |
| `/es team create <id>` | Create a saved team. | `/es team create cast` |
| `/es team delete <id>` | Delete the team and its configuration. | `/es team delete cast` |
| `/es team join <id> [name]` | Add a scoreboard name; defaults to your current name. | `/es team join cast Alex` |
| `/es team leave <id> [name]` | Remove the name from this team. | `/es team leave cast Alex` |
| `/es team set <id> color <color>` | Set a named Minecraft team color, such as aqua, red, gold, green, white. | `/es team set cast color aqua` |
| `/es team set <id> glow on\|off` | Toggle member glow. | `/es team set cast glow on` |
| `/es team set <id> prefix <text>` | Set a formatted scoreboard prefix. | `/es team set cast prefix <gold>[Cast]` |
| `/es team set <id> friendly-fire on\|off` | Allow/block teammates hurting each other. | `/es team set cast friendly-fire off` |
| `/es team set <id> see-invisible on\|off` | Toggle seeing invisible teammates. | `/es team set cast see-invisible on` |
| `/es team set <id> nametags on\|off` | Show/hide team nametags. | `/es team set cast nametags on` |
| `/es team set <id> collision on\|off` | Toggle team collision. | `/es team set cast collision off` |

Team membership uses scoreboard names; it is separate from `/es group` combat factions and the UUID-based kit allow-list.

## Villagers

Access: `villager`. A template is a saved villager with configured appearance and trades.

| Command | What it does | Example |
| --- | --- | --- |
| `/es villager` or `/es villager list` | List templates. | `/es villager list` |
| `/es villager create <id>` | Create a template and spawn its villager where you stand. | `/es villager create trader` |
| `/es villager spawn <id>` | Spawn/move the template's active villager to your location. | `/es villager spawn trader` |
| `/es villager set <id> name <text>` | Set its formatted name. | `/es villager set trader name <gold>Merchant` |
| `/es villager set <id> profession <profession>` | Set a valid profession, such as farmer, librarian, armorer or none. | `/es villager set trader profession librarian` |
| `/es villager set <id> type <biome-type>` | Set desert, jungle, plains, savanna, snow, swamp or taiga appearance. | `/es villager set trader type plains` |
| `/es villager set <id> level <1..5>` | Set villager level. | `/es villager set trader level 5` |
| `/es villager trade <id> [once]` | Add a trade: hold the result in main hand and cost in offhand. `on` permits one use; default `off` gives a high-use trade. | `/es villager trade trader on` |
| `/es villager clear <id>` | Remove all saved trades, keeping the template. | `/es villager clear trader` |
| `/es villager delete <id>` | Remove the active villager and its template. | `/es villager delete trader` |

At most 32 trades per template. A trade copies held stacks including their amounts; it does not consume them while configuring the template.

## Voice chat

Access: `voice`. Requires the **Simple Voice Chat** server plugin and participating players' voice mod. This does not record microphone audio.

| Command | What it does | Example |
| --- | --- | --- |
| `/es voice mute on\|off` | Mute/unmute ordinary voice through the integration; enabled broadcasters can still speak. | `/es voice mute on` |
| `/es voice broadcast on\|off` | Toggle broadcasting your voice beyond normal proximity; run in-game. | `/es voice broadcast on` |

If the integration is unavailable, the command explains which dependency is needed.

## GUI use and common mistakes

- **Back**, **Home** and **Close** are in the footer. Pages with more entries have Previous/Next.
- NPC overview cards open Identity & clothing, Movement, Record & replay, and Combat & supplies. Home **Record session** opens server-session ON/OFF; it does not start an NPC performance.
- Kit operators can create/import/edit/delete; eligible non-operators can browse and claim.
- Destructive GUI deletion/capture controls ask for confirmation. Direct delete/save commands execute directly.
- A “busy” error means a scene, acting session, camera or replay currently owns the entity. Stop or finish that operation before applying a conflicting change.
- A “feature disabled” error means its feature switch must be enabled. Permission errors identify the missing node.
- Use `/actor list` for actor IDs and `/es menu recording` to browse saved takes; displayed usernames are not actor IDs.
- Keep README.md at repository root; this guide and other project documentation live under documentation/.

For guided workflows see [USERGUIDE.md](USERGUIDE.md); for YAML fields see [CONFIGURATION.md](CONFIGURATION.md). [TESTING.md](TESTING.md) records which behaviors have been tested and which still need an in-game check.
