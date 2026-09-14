# EasyScripting user guide

This guide covers EasyScripting 0.1.3: setting up your cast, acting as an NPC, autoplay, customizing identities, making a scene, recording movement, and repeating a take. The complete syntax is in [COMMANDS.md](COMMANDS.md); supported features and remaining differences from the public reference are in [PARITY.md](PARITY.md).

## 1. Install and open the studio

1. Stop your Paper/Purpur server. The primary tested target is Paper 26.2 on Java 25; see [TESTING.md](TESTING.md) for other versions.
2. Put `build/libs/EasyScripting-0.1.3.jar` in the server's `plugins/` folder. Replace the previous EasyScripting JAR so only one version is installed. Do not install the sources or SmokeTests JAR.
3. For human NPCs and skins, also install a Citizens build compatible with your exact Minecraft version. Mob actors work without Citizens.
4. Start the server. EasyScripting creates its YAML files under `plugins/EasyScripting/`.
5. Join with operator access or the appropriate [permissions](PERMISSIONS.md), then run `/es`. Use `/es help` for commands you can access, and `/es status` to check loaded actors, scenes and active jobs.

Simple Voice Chat is optional and only needed for voice mute/broadcast; participating clients need its voice mod. Movement recordings do not record microphone audio or video.

## 2. Create an NPC with a random identity

Stand where you want the NPC and run:

```text
/actor create guard_1
/actor info guard_1
```

The default type is `PLAYER`. Creation automatically chooses a random username and a random skin account from `npc-identities.yml`. For example, the result might have ID `guard_1`, displayed username `RiverFox`, and the skin of `jeb_`. This is an example, not a fixed result. **Do nothing else to keep that appearance.**

These three values are independent:

| Value | What it controls | Example |
| --- | --- | --- |
| Actor ID | Permanent reference used by commands, scene bindings and recordings | `guard_1` |
| Username/name | The NPC's displayed name | `RiverFox` |
| Skin owner | Java account whose skin supplies the NPC texture | `jeb_` |

Continue using `guard_1` in commands even after renaming the NPC. IDs use lowercase letters, numbers, `_` or `-`, start with a letter/number and contain at most 48 characters.

Change either part independently:

```text
/actor set guard_1 name RiverScout
/actor set guard_1 skin Notch
/actor info guard_1
```

Changing the name keeps the selected skin. Changing the skin keeps the name. A skin value must be an existing Java account name, using 1–16 letters, numbers or underscores. NPC skin commands accept account names; PNG files and NameMC page URLs are not supported.

To choose another random name and skin together:

```text
/actor randomize guard_1
```

Randomization keeps the actor ID, position, equipment and behavior settings. It excludes names already used by actors or online player account names, as well as blacklisted names. It chooses a different skin owner when another allowed owner is available. Different actors can share a skin; there are more generated names than default skin choices.

The chosen name and skin owner are saved immediately. Skin lookup can take several seconds and needs access to Minecraft's profile services. Once Citizens resolves the skin, EasyScripting also saves its signed texture so subsequent spawns can retain that appearance. Hide/show, respawn and restart do not reroll identities. To deliberately refresh an account's skin later, run `/actor set guard_1 skin <account>` again.

Existing actors keep their previous identity when you upgrade. `/actor copy guard_1 guard_2` copies the current appearance; it does not randomize the copy. Use `/actor randomize guard_2` afterward if you want a different identity. New actors created by `/actor pattern` each receive a generated identity.

Without Citizens, specify a mob:

```text
/actor create guard_1 ZOMBIE
```

Mobs receive a random displayed name and keep their natural entity appearance. To make mobs the default, set `actors.default-type: ZOMBIE` in `config.yml` and run `/es reload`.

### NPC controls in the GUI

Run `/actor gui guard_1`, or open `/es` → Actors → your actor. Choose a section:

* **Appearance:** name, skin, random identity, costume kit and identity details.
* **Movement:** teleport, walk, hide, respawn and behavior settings.
* **Acting & Playback:** act as the NPC, finish/cancel, browse saved recordings, select an end mode, toggle autoplay, play and stop.
* **Combat:** Hittable and Immortal switches, plus Respawn NPC.

Tabs across the top switch sections. Back returns to the actor overview, then the cast list; Home returns to the studio and Close exits. The overview shows the NPC ID, status and assigned recording. Selected modes and active switches glow. Unavailable actions explain what to do first. Appearance → Choose costume and Acting → Choose saved recording open pickers instead of asking you to remember an ID. Open a section directly with `/actor gui guard_1 acting` or `appearance`, `movement`, `combat`.

The studio groups kits and item editing under **Wardrobe**, world/effects under **Stage tools**, teams/warps/villagers under **Organization**, and feature/permission controls under **Settings**. Empty libraries show a create button; hover over an entry to see its click actions. Deletion requires Shift-right click and confirmation. The kit editor keeps navigation and Save below the editable inventory.

Input buttons close the inventory and ask for text in chat. Enter the requested value without repeating the command. Type `cancel` to return without changes; input expires after 60 seconds. After a response, the originating page reopens. The selected actor stays identified by its ID.

### Configure the random name and skin pools

Edit `plugins/EasyScripting/npc-identities.yml`, then run `/es reload`. The bundled file has 24 prefixes × 48 suffixes, giving 1,152 possible names, and six skin accounts. The generator avoids the last eight selected endings whenever another allowed ending is available, so consecutive choices vary beyond just the prefix. Your existing custom pool is preserved on upgrade; expand its suffix list if it only contains one ending. Here is a smaller example for a small cast:

```yaml
schema: 1
enabled: true
name-prefixes: [Amber, River, Silver, Winter]
name-suffixes: [Fox, Hawk, Otter, Wolf]
skin-owners: [Notch, jeb_, Dinnerbone, Grumm, MHF_Steve, MHF_Alex]
```

A name combines one prefix and one suffix, such as `SilverOtter`. Each list must contain 1–64 unique entries, compared without case. Entries use letters, numbers or underscores; every combined name must fit within 16 characters. Configure enough combinations for your cast: this example has only 16 names. Exhaustion produces an error and leaves existing actors intact.

Replace `skin-owners` with real Java account names whose skins you want in your cast. The displayed username is generated separately; it does not need to belong to a real account. Blacklisted skin owners are excluded. If every owner is blocked, creating or randomizing a PLAYER actor reports an error.

Set `enabled: false` to stop automatically assigning identities on creation. New actors then start with their ID as the name. Explicit `/actor randomize <id>` still uses the configured pools. Reloading the file never changes existing actors by itself.

## 3. Dress and direct the cast

Put the desired costume in your own inventory, armor and hands, then save a kit:

```text
/es kit save guard_costume
/actor kit guard_1 guard_costume
/actor set guard_1 immortal on
/actor set guard_1 look off
/actor set guard_1 wander off
```

`immortal` prevents lethal damage while `hittable off` cancels damage entirely. `look` and `wander` enable ambient behavior; turn them off for a stationary shot. Actor settings also include collision, nametag visibility, pose, glow, sneak, sprint and group.

To reposition the actor, stand at the destination and use `/actor here guard_1` for a teleport or `/actor move guard_1 1` for navigation. Hide it with `/actor hide guard_1` and return it with `/actor show guard_1`. Remove it with `/actor delete guard_1`.

For a crowd:

```text
/actor pattern crowd line 6 2 PLAYER
/actor all crowd immortal on
/actor group crowd kit guard_costume
/actor group crowd hide
/actor group crowd show
```

Other patterns are `circle`, `grid` and `square`. The default server cap is 200 actors. IDs and group membership appear in `/actor list`; group commands accept a group name or `*` for all actors.

### Act as an NPC and save its performance

Open **Acting & Playback**, choose an end mode, then click **1 · Act as NPC**. A recording ID is generated automatically. To choose the ID yourself:

```text
/actor act guard_1 entrance_take
```

You move to the NPC's position with its name, skin and costume. The NPC is temporarily removed so you can perform in its place. You use Survival mode while acting, with damage and knockback protection even if the NPC is mortal. Your original invulnerability state returns afterward. Walk, run, jump, turn, sneak, change your held items or armor, and swing your arms. Capture includes location/rotation, equipment, poses, main/off-hand swings, visual flames and supported boat movement. Saved hurt/flame cues are visual; real hits during playback are handled separately. It records **movements, equipment and animations**; block edits, damage to other entities, commands, chat and third-party abilities are not replayed.

For a PLAYER NPC, let its skin finish loading before starting. Names outside Java's 1–16 character username format remain display/list labels while your underlying profile name stays unchanged. When acting for a mob, your character remains a player model wearing the mob's equipment; this is not a mob disguise.

Finish with `/actor finish` or **2 · Finish & save**. Your original position, name/skin, inventory, health, XP and gamemode return, and the NPC returns to its starting state. The saved recording is selected on that actor. **Autoplay is ON by default**, so the NPC starts the performance on the next server tick. `/actor cancel` or **Discard this take** restores you and discards the unfinished recording without starting playback. A disconnect, death or plugin shutdown ends capture and restores your state immediately or after rejoining/respawning. Shutdown does not start playback. Do not use a plugin manager to hot-unload the plugin.

An active scene, camera or another performance cannot take over the same player/NPC. Reset and discard an existing personal take before starting. Acting needs both `easyscripting.actor` and `easyscripting.record`.

For manual playback, disable autoplay, then select playback in the GUI or run:

```text
/actor autoplay guard_1 off
/actor mode guard_1 stop
/actor play guard_1
```

| Mode | At the end of the recording |
| --- | --- |
| `stop` | Stops at the final recorded position and keeps the final equipment; wandering is disabled so the NPC stays there |
| `repeat` | Teleports to the first recorded position and repeats continuously |
| `reverse` | Reverses along the recorded path, then plays forward again, continuously; the endpoints are not duplicated |

Normal gravity and collisions resume when playback completes; finish on solid ground to hold that position. `/actor stop guard_1` stops playback and restores its starting state while keeping damage received during the replay. Stop before changing mode, costume or recording. To use another saved route, choose **Choose saved recording** or `/actor recording guard_1 another_take`.

The recording, end mode and autoplay setting survive restarts. Autoplay also starts a visible, idle NPC on server startup, show or respawn, or when switched ON. A hidden, dead or busy NPC does not start; it does not queue behind an active scene. Explicit Stop keeps it stopped until a new autoplay trigger or manual Play. Switching autoplay OFF does not interrupt an existing replay; use Stop for that. `stop` still plays once; use `repeat` or `reverse` for continuous action. Old actor files without an autoplay field default to ON; set `/actor autoplay <id> off` to retain manual playback.

The older `/es record play <recording> <actor> [loop] [reverse]` command keeps its original behavior: `reverse` plays backward from the outset, and a completed single run restores the previous actor state. Use the actor GUI or `/actor play` for the three modes above.

### Allow hits and death

Open **Combat**. Set **Hittable: ON** to accept damage and knockback. With **Immortal: ON**, lethal hits are prevented but nonlethal hits can still hurt. Set **Immortal: OFF** to let a hittable NPC die. **Hittable: OFF** cancels incoming damage and knockback regardless of immortality. Both switches can be changed during replay.

When a replaying NPC is knocked back, the recorded timeline pauses for 12 ticks so normal physics can move it, then blends back to the route over 10 ticks. Hits do not get erased by the next recorded teleport. Further hits restart this recovery interval. Armor, attack cooldowns and other plugins still affect actual damage and knockback. `recording.yml` exposes both timings as integers from 1 to 100. Death ends the replay; it does not resurrect the NPC or drop copied equipment.

```text
/actor set guard_1 hittable on
/actor set guard_1 immortal off
```

Use **Respawn NPC** after death, or `/actor respawn guard_1`. NPC deaths do not drop copied equipment or experience. Both switches are saved per actor.

## 4. Build and play your first scene

With `guard_1` already created:

```text
/scene create opening
/scene bind opening guard actor:guard_1
/scene add opening 0 title self text=<aqua>Take one;subtitle=Camera rolling
/scene add opening 20 swing guard
/scene add opening 30 hurt guard
/scene add opening 60 wait guard
/scene restore opening on
/scene gui opening
/scene play opening
```

At normal server speed, 20 ticks are one second. The title appears immediately, the guard swings at tick 20, and its hurt animation plays at tick 30. `wait` holds the timeline until tick 60. `restore on` restores captured state at completion so you can repeat the shot.

The binding `guard` is a scene-local alias for `actor:guard_1`. Renaming or reskinning the actor does not break this binding. `self` is the director playing the scene; it requires an in-game player. For console-started scenes, bind an explicit `player:AccountName` instead.

Control a running scene with:

```text
/scene pause opening
/scene resume opening
/scene status opening
/scene stop opening
```

Stop cancels playback and restores the take. With automatic restoration disabled, `/scene reset opening` restores a completed take. Use `/scene remove opening 2` to remove the second displayed action; action numbers are one-based. Actors controlled by a running scene or recording cannot be edited by another operation until released.

See [all scene actions](COMMANDS.md#action-types) for movement, equipment, health, potion, sound, particles, world controls and other cues. Arguments are separated by semicolons, as in `sound=entity.player.levelup;volume=1;pitch=1`. Actions at the same tick execute in their saved order. Some actions require additional permissions; command execution and destructive effects also require explicit configuration switches.

## 5. Record a route and replay it

Record your movement, hand items and movement cues:

```text
/es record start entrance
```

Walk the route and perform the hand swings you want, then:

```text
/es record stop
/es record play entrance guard_1 off off
```

The final arguments are `loop` and `reverse`. For a repeating reverse route, use `/es record play entrance guard_1 on on`. Replay uses the exact recorded world coordinates. `/es record stopplay guard_1` ends playback and restores the actor. Recording length defaults to 2,400 frames; record several shorter routes for longer productions.

For multiple performers, `/es record startall entrance` begins synchronized capture and `/es record stopall` ends it. `/es record list` shows the resulting IDs, such as `entrance_alex`. Map those exact IDs to distinct actors:

```text
/es record playgroup off off entrance_alex=guard_1 entrance_steve=guard_2
```

Both tracks begin on a shared tick. The capture cap is eight performers. Use the IDs actually produced on your server; the names above are examples.

## 6. Repeat player takes and prepare the set

Capture your current state before changing costume, location or health:

```text
/es take snapshot
/es player health 6
/es take reset
```

Reset restores the saved take; repeat it as needed. `/es take discard` releases it. Snapshots include location, inventory, health, hunger, XP, gamemode, effects and supported player flags. They do not snapshot the entire world or arbitrary third-party plugin state.

For a coordinated production session, use `/es take start episode all`, then `/es take stop reset` when finished. Use `self` instead of `all` for only yourself. Chat/MOTD/optional voice behavior is configured in `recording.yml`. A production session and a movement recording are separate workflows; start movement capture explicitly if you need it.

Useful set preparation commands:

| Task | Commands |
| --- | --- |
| Save a camera position | `/es warp save camera_a`, then `/es warp go camera_a` |
| Set and visit spawn | `/es spawn set`, then `/es spawn` |
| Set lighting and weather | `/es world time 6000`, `/es world weather clear` |
| Freeze a performer | `/es player freeze on Alex`; undo with `off` |
| Stop hunger/durability changes | `/es player no-hunger on`, `/es player no-durability on` |
| Save inventory rollback | `/es inventory save`, then `/es inventory history` |
| Rename the held prop | `/es item name <gold>Director's Key` |
| Cue visual effects | `/es effect lightning`, `/es effect explosion` |
| Move the camera | `/es camera move <x> <y> <z> <ticks>`; end with `/es camera stop` |

To restore a physical set, select corners with `/es region pos1` and `/es region pos2`, then `/es region save stage`. After capture finishes, modify the set. `/es region restore stage` restores the saved blocks, container contents and sign text incrementally. Capture before making changes and keep the chunks loaded. Specialized block entities are not all covered; see [configuration and storage](CONFIGURATION.md).

## 7. Customize YAML and menus

All editable files are under `plugins/EasyScripting/`:

| File | What you customize |
| --- | --- |
| `config.yml` | Limits, default actor type/behavior, explicit security switches |
| `npc-identities.yml` | Automatic random NPC names and skin accounts |
| `features.yml` | Enable/disable feature groups |
| `guis.yml` | Inventory titles, icons, slots, labels, lore and workflow buttons |
| `messages.yml` | Feedback text and optional command sounds |
| `permissions.yml` | Feature permission overrides |
| `recording.yml` | Production-session behavior and replay knockback/recovery timing |
| `moderation.yml`, `death.yml` | Join/chat/world rules and death behavior |
| `items.yml`, `potions.yml`, `effects.yml` | Item pools, potion presets and effect settings |

After editing settings, run `/es reload`. Invalid settings produce an error and leave the previous active configuration in use. Existing files are preserved on upgrade; missing top-level files are supplied automatically. Actor and scene YAML definitions edited by hand load on a full restart, not `/es reload`.

For example, change the randomize button by editing the existing keys under `dynamic` in `guis.yml`:

```yaml
dynamic:
  actor-randomize: '<aqua>New random identity'
  actor-info: '<aqua>Identity details'
  controls:
    actor-randomize:
      slot: 31
      material: ENDER_EYE
      lore: ['<gray>Choose another name and skin.']
    actor-info:
      slot: 22
      material: BOOK
      lore: ['<gray>Show the ID, name and skin account.']
```

Merge this into the existing `dynamic` section; do not create duplicate top-level keys or remove the other controls. Slots are zero-based. Keep buttons on the same screen in different slots. The randomize control is on Appearance; the identity summary is on Overview. Version 0.1.3 replaces legacy GUI layouts after validation and saves the original as `guis-v1-backup-<unique-id>.yml` in the plugin folder. Reapply custom labels to the new layout; do not merge the old slot arrangement into it. Schema-2 customizations are preserved on reload.

Custom buttons execute as the clicking player and obey the same permissions as commands. Opening a menu does not grant control over another player. See [PERMISSIONS.md](PERMISSIONS.md) before granting staff access and [CONFIGURATION.md](CONFIGURATION.md) for complete YAML rules.

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| PLAYER creation says Citizens is unavailable | Install a compatible Citizens build and restart, or create a mob such as `ZOMBIE` |
| NPC initially has a default skin | Allow several seconds for profile lookup; check the skin account spelling, outbound connectivity and Citizens log messages |
| A skin account changed its skin but the NPC did not | Cached appearances are intentional; repeat `/actor set <id> skin <account>` to refresh |
| Renamed NPC cannot be found in a command | Use its original ID, shown by `/actor list` and `/actor info <id>` |
| Randomization reports no names available | Expand the prefix/suffix pools, remove unused actors, or review the identity blacklist |
| NPC edit reports the actor is in use | Stop the owning scene or movement playback before editing |
| Scene commands reject an action | Check its permission, target, arguments and feature switch; command/destructive actions have extra gates |
| A GUI edit does not appear | Run `/es reload` and reopen it; check errors for invalid slots or YAML |
| Changes disappear after a crash | Use a graceful server stop to drain queued saves; active in-memory takes are not crash recovery backups |

The release's exact test evidence and unverified multiplayer/visual cases are recorded in [TESTING.md](TESTING.md). For integrations written in Java, use the supported service API in [API.md](API.md).
