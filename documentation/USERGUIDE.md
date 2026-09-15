# EasyScripting user guide

This guide covers EasyScripting 0.1.7: setting up your cast, acting as an NPC, autoplay, customizing identities, making a scene, recording movement, and repeating a take. The complete syntax is in [COMMANDS.md](COMMANDS.md); supported features and remaining differences from the public reference are in [PARITY.md](PARITY.md).

## 1. Install and open the studio

1. Stop your Paper/Purpur server. The primary tested target is Paper 26.2 on Java 25; see [TESTING.md](TESTING.md) for other versions.
2. Put `build/libs/EasyScripting-0.1.7.jar` in the server's `plugins/` folder. Replace the previous EasyScripting JAR so only one version is installed. Do not install the sources or SmokeTests JAR.
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

Name, skin and random identity can be changed while a recorded performance is playing, including continuous autoplay. Citizens may briefly refresh the entity; the replay continues with the current recording and mode. Randomization keeps the actor ID, position, equipment and behavior settings. It excludes names already used by actors or online player account names, as well as blacklisted names. It chooses a different skin owner when another allowed owner is available. Different actors can share a skin; there are more generated names than default skin choices.

The chosen name and skin owner are saved immediately. Skin lookup can take several seconds and needs access to Minecraft's profile services. Once Citizens resolves the skin, EasyScripting also saves its signed texture so subsequent spawns can retain that appearance. Hide/show, respawn and restart do not reroll identities. To deliberately refresh an account's skin later, run `/actor set guard_1 skin <account>` again.

Existing actors keep their previous identity when you upgrade. `/actor copy guard_1 guard_2` copies the current appearance; it does not randomize the copy. Use `/actor randomize guard_2` afterward if you want a different identity. New actors created by `/actor pattern` each receive a generated identity.

Without Citizens, specify a mob:

```text
/actor create guard_1 ZOMBIE
```

Mobs receive a random displayed name and keep their natural entity appearance. To make mobs the default, set `actors.default-type: ZOMBIE` in `config.yml` and run `/es reload`.

### NPC controls in the GUI

Run `/actor gui guard_1`, or open `/es` → Actors → your actor. Choose a section:

* **Appearance:** name, skin, random identity, costume kit, nametag visibility, glow and a PLAYER NPC tab-list switch.
* **Movement:** teleport, walk, hide, respawn and behavior settings.
* **Acting & Playback:** act as the NPC, finish/cancel, browse saved recordings, select an end mode, toggle autoplay, play and stop.
* **Combat:** Hittable and Immortal switches, plus Reset NPC spawn for living/hidden NPCs.

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

`immortal` lets the NPC receive hits, hurt feedback and knockback but prevents death; new NPCs default to Immortal OFF. `hittable off` blocks direct melee attacks and sweeps only. Falls, projectiles (including wither skulls), explosions and other environmental damage remain enabled. `look` and `wander` enable ambient behavior; turn them off for a stationary shot. Actor settings also include collision, nametag visibility, pose, glow, sneak, sprint and group.

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

You move to the NPC's position with its name, skin and costume. The NPC is temporarily removed so you can perform in its place. You use Survival mode while acting. Direct melee damage and its knockback are blocked, but falls, projectiles (including wither skulls), explosions, fire and other environmental damage affect you normally. Your prior invulnerability state is restored afterward. Lethal non-melee damage can end your performance; your original player state is queued for restoration after respawn. The NPC Immortal switch does not grant your acting player invulnerability. Walk, run, jump, turn, sneak, change your held items or armor, and swing your arms. Capture includes location/rotation, equipment, poses, main/off-hand swings, visual flames and supported boat movement. Saved hurt/flame cues are visual; real hits during playback are handled separately. It records **movements, equipment and animations**; block edits, damage to other entities, commands, chat and third-party abilities are not replayed.

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

Normal gravity and collisions resume when playback completes; finish on solid ground to hold that position. `/actor stop guard_1` stops at the current position, keeps damage received, disables wandering and turns Autoplay OFF. You can change the selected mode after recording and during replay; the highlighted selection changes immediately and the current frame continues. Name/skin/random identity edits also work during replay. Stop before changing the assigned recording or costume kit. To use another saved route, choose **Choose saved recording** or `/actor recording guard_1 another_take`.

The recording, end mode and autoplay setting survive restarts. Autoplay also starts a visible, idle NPC on server startup, show or respawn, or when switched ON. A hidden, dead or busy NPC does not start; it does not queue behind an active scene. Explicit Stop turns Autoplay OFF, including across restart, until you enable it again. Manual Play still works. Switching autoplay OFF does not interrupt an existing replay; use Stop for that. `stop` still plays once; use `repeat` or `reverse` for continuous action. Old actor files without an autoplay field default to ON; set `/actor autoplay <id> off` to retain manual playback.

Version 0.1.6 uses `/actor act`, `/actor finish` and `/actor play` for NPC performances. `/es record` now controls server recording-session mode with on/off; the old movement subcommands are removed.

### Allow hits and death

Open **Combat**. **Hittable: ON** allows direct melee damage and knockback; **OFF** blocks melee and sweeps only. Both settings allow falls, projectiles, wither skulls, explosions and environmental damage under normal Minecraft rules. **Immortal: ON** prevents death while retaining ordinary hit feedback and knockback, including hits that would otherwise kill it. EasyScripting removes Citizens' extra spawn-immunity timer so a new NPC can receive its first hit; normal Minecraft combat cooldowns still apply. **Immortal defaults to OFF** for newly created NPCs, so they can die unless you enable it. Both switches can be changed during replay.

When a replaying NPC is knocked back, the recorded timeline pauses for 12 ticks so normal physics can move it, then blends back to the route over 10 ticks. Hits do not get erased by the next recorded teleport. Further hits restart this recovery interval. Armor, attack cooldowns and other plugins still affect actual damage and knockback. `recording.yml` exposes both timings as integers from 1 to 100. Death ends the replay; it does not resurrect the NPC or drop copied equipment.

```text
/actor set guard_1 hittable on
/actor set guard_1 immortal off
```

When an NPC dies, everyone sees a yellow `<NPC name> left the game` message. It is removed from the actor list and its saved definition is deleted from active storage. The message can be changed in `messages.yml` (`actor-left`) or disabled with `actors.announce-death-leave: false`. Playback stops; show, respawn, autoplay, scene reset and server restart cannot bring it back. This includes scripted NPC death actions. Its selected take is also removed unless another NPC still references it; shared takes are removed with their last NPC. Hiding an NPC does not delete its take. Deleted YAML uses the existing `trash/` retention mechanism, without automatic recovery. To use the same ID again, create a new actor with `/actor create guard_1`. **Reset NPC spawn** / `/actor respawn` only recreates an existing living or hidden NPC. Deaths drop no copied equipment or experience.

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

Stop cancels playback and restores the take. With automatic restoration disabled, `/scene reset opening` restores a completed take. Use `/scene remove opening 2` to remove the second displayed action; action numbers are one-based. Running scenes retain exclusive control of their targets. Running recordings permit name, skin, random identity, playback mode, tab-list, nametag and combat-switch changes; operations such as replacing the recording or costume still require Stop.

See [all scene actions](COMMANDS.md#action-types) for movement, equipment, health, potion, sound, particles, world controls and other cues. Arguments are separated by semicolons, as in `sound=entity.player.levelup;volume=1;pitch=1`. Actions at the same tick execute in their saved order. Some actions require additional permissions; command execution and destructive effects also require explicit configuration switches.

## 5. Start a server recording session

```text
/es record on
```

The server list now displays the recording MOTD. Players already online can stay, but **all non-operators are blocked from joining or reconnecting**, even if they were allowed by another EasyScripting server allow-list or have server.bypass. Operators can join subject to ordinary server bans/whitelist rules.

```text
/es record off
```

Normal MOTD and ordinary login rules return. Existing independent server locks are not removed. Both commands report whether the session is ON or OFF. Customize the MOTD in recording.yml and the rejection message in messages.yml (`recording-locked`). The old change-motd switch is ignored: sessions always show their recording MOTD.

For NPC performances, use `/actor act guard_1 entrance`, move/jump/swing/change equipment, then `/actor finish`. Set `/actor mode guard_1 repeat` or reverse for continuous replay; `/actor stop guard_1` holds the current position and disables autoplay. The actor GUI remains the movement-recording workflow; `/es menu recording` lets you assign saved NPC performances.

Deleting/killing an NPC also removes its selected take unless another NPC still uses it. Shared takes are deleted with the final referencing NPC. Replacing a take cleans up its unreferenced predecessor. Hide/show, stop, world unloading and normal shutdown keep the take. The older standalone `/es record` movement subcommands are no longer available.

## 6. Repeat player takes and prepare the set

Capture your current state before changing costume, location or health:

```text
/es take snapshot
/es player health 6
/es take reset
```

Reset restores the saved take; repeat it as needed. `/es take discard` releases it. Snapshots include location, inventory, health, hunger, XP, gamemode, effects and supported player flags. They do not snapshot the entire world or arbitrary third-party plugin state.

For a coordinated production session, use `/es take start episode all`, then `/es take stop reset` when finished. Use `self` instead of `all` for only yourself. MOTD, chat and optional voice behavior are configured in `recording.yml`; sessions also block non-operator joins/reconnects. `/es record off` can end the same session and restore its participant snapshots. A production session and a movement recording are separate workflows; start movement capture explicitly if you need it.

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

## 7. Nickname real players

```text
/nickname Alex
/nickname Alex off
/nickname SilverOtter off
/nickname off
```

The first command assigns Alex a random readable username from the [Random User API](https://randomuser.me/documentation). The next two examples reset one player using their real account name or current nickname; the last resets every current nickname and cancels pending requests. `/es nickname` is the namespaced equivalent. Targets must be online real players; NPCs are excluded. The command uses `easyscripting.identity`, plus `easyscripting.player.others` when targeting another player or resetting everyone.

The nickname appears in the tab list, overhead nametag, normal display-name chat, victim/killer death messages and the leave message. It preserves the current skin. On disconnect the nickname expires; the next join uses the real username. Nicknames are unique without regard to case and cannot take another online player's real name or nickname. The API path also excludes NPC names and the configured identity blacklist. Reset/quit/shutdown invalidate unfinished lookups.

Your own tab and display-name chat can show the nickname too. A vanilla server cannot replace the account name authenticated by your launcher, so client mods, account screens or third-party plugins that deliberately show account names may still use the real one. No client mod, ProtocolLib or PacketEvents is required for this feature. Other plugins can override chat/scoreboard formatting; EasyScripting preserves suppressed messages.

Configure API enablement, timeout and local fallback in `nicknames.yml`. Failed/unavailable lookups use the varied `npc-identities.yml` name pool by default. No player username, UUID or IP address is placed in the API request; the external service receives the server's normal network connection. For a chosen name instead, use `/es nick set RiverScout`; `/es nick reset` restores your identity. All nicknames are temporary for the connection.

## 8. Control production chat

| Command | What it does |
| --- | --- |
| `/es chat block` | Toggle blocking; announce the change to everyone |
| `/es chat block on` | Only current server operators can send public chat |
| `/es chat block off` | Allow ordinary public chat again |
| `/es chat clear` | Send blank lines to clear the visible chat area for everyone |
| `/es chat clear self` | Clear only your visible chat area |
| `/es chat broadcast Filming starts now` | Send the message in chat and as a configurable title |
| `/es chat join RiverScout` | Print a yellow simulated join message |
| `/es chat leave RiverScout` | Print a yellow simulated leave message |
| `/es chat death RiverScout` | Print a white `RiverScout died` announcement |

`block` replaces the old `mute` subcommand. Being de-opped takes effect on the next message, even if a permission plugin still grants `easyscripting.chat.bypass`; that node only bypasses recording-session chat suppression. Other moderation filters still apply. Join/leave/death above only print announcements: they do not connect, disconnect or kill a player. `/es death ...` is a separate command for what happens after a real player death.

Templates are in `messages.yml`, including `fake-death`, `chat-muted` and `chat-unmuted` (the existing internal keys are retained). Title settings are in `moderation.yml`. Old default gray death text upgrades to white; customized templates remain yours to edit.

## 9. Create, edit and import kits

Run `/es kits` or open Studio → Wardrobe → Kits. In 0.1.6, actual operators manage kits; other players see only kits they can claim. The [complete command guide](COMMANDS.md#kits) lists every kit command with examples.

1. Click **Create a new kit** and enter a unique ID such as `guard_costume`.
2. Click the kit to open its controls. **Import my inventory** copies your current storage, hotbar, armor and offhand; replacing an existing kit requires confirmation. **Edit kit** opens the 41-slot editor.
3. Edit the copied items. **Import my inventory** inside the editor replaces the draft slots. **Save kit** saves and returns to the library; **Save & equip** saves and applies it to you. Leaving the editor without saving discards draft changes. Item editing is intentionally a staff duplication tool.
4. Use **Claim kit** or `/es kits claim guard_costume` to equip it later. This replaces your hotbar, storage, armor and offhand, including clearing empty kit slots. Operators can choose **Give to player** or **Give to NPC**; `/actor kit guard_1 guard_costume` also works. Stop the NPC's active recording before replacing its costume.
5. Open **Who can claim?** and select **Operators only**, **Everyone**, or **One player + operators**. The selected choice glows. Choose the specific player from the online-player picker; access is saved by UUID and survives nickname changes/reconnects. Choosing another mode clears the old player selection. New/legacy/provider-imported kits start as Operators only.

```text
/es kits access guard_costume everyone
/es kits claim guard_costume
/es kits claim guard_costume Alex
/es kits claim Alex guard_costume
/es kits claim guard_costume *
/es kits claim guard_costume actor:guard_1
/es kits access guard_costume player Alex
/es kits access guard_costume operators
```

Account names and nicknames work for player recipients. Giving to somebody else, all players or NPCs requires actual op; operators may gift even when the recipient cannot self-claim. If a player name matches a kit ID, use `player:Alex`. Everyone means all eligible self-claimants; `*` gives a kit to all online real players immediately. Claims have no price/cooldown/one-use limit. Busy/dead recipients must be resolved before a grant proceeds.

Editor slots 0–35 are hotbar/storage, 36 boots, 37 leggings, 38 chestplate, 39 helmet and 40 offhand. The footer contains controls and is never saved as kit contents. Commands also work: `/es kit create <id>` creates a blank kit, `/es kit save <id>` copies your inventory, `/es kit edit <id>` opens its editor and `/es kit apply <id>` equips it.

### Import from another plugin

In the library click **Import kits**, choose an installed provider, then choose one kit or **Import all kits**. Single imports generate an unused ID and open the editor. Bulk imports process one kit per tick, preserve existing kits, and report imported/failed counts. Use **Stop import** or `/es kits cancelimport` to cancel; completed imports stay saved. Disconnecting, losing op, disabling kits or shutdown also stops the batch. Repeating an import creates new suffixed IDs rather than updating old kits.

Supported providers are **PlayerKits 2**, **legacy PlayerKits**, **EssentialsX** and **CMI**. They must already be installed/enabled and allowed in `kits.yml`. EasyScripting's adapter code is included; it does not bundle those other plugins.

```text
/es kits imports
/es kits import PlayerKits2 starter imported_starter
/es kits import PlayerKits starter imported_legacy
/es kits import Essentials tools imported_tools
/es kits import CMI starter imported_cmi
/es kits importall PlayerKits2
/es kits cancelimport
```

Imports read item definitions without claiming the kit, executing reward commands or charging currency. Provider item conversion preserves supported item metadata. PlayerKits auto-armor/offhand and CMI armor/offhand slots are retained; Essentials supports ordinary item metadata, serialized items and explicit slots. Player-specific placeholder values are resolved for the importing player and then saved. Claims, permissions, cooldowns, prices and commands stay with the original provider. Dynamic/nested formats outside the adapter's supported item definitions produce an error; they are not silently approximated. Oversized kits and conflicting item slots are rejected before saving.

Provider APIs can change. If an adapter reports an unsupported signature, or you use another kit plugin, claim/equip that kit yourself and choose **Import my inventory**. This also captures the final result of custom-item or reward systems. The four adapters have source/compile checks and isolated contract fixtures, but this release has not been tested against running installations of all four providers.

### Export and move EasyScripting kits

Use a kit's **Export kit YAML** control or `/es kits export guard_costume`. The writer saves `plugins/EasyScripting/kit-exports/guard_costume.yml`. Copy that file into the destination server's `plugins/EasyScripting/kit-exports/`, then select the **EasyScripting** import source or run:

```text
/es kits import EasyScripting guard_costume imported_guard
```

Use a new destination ID: imports never overwrite an existing kit. This is EasyScripting's own schema-1 `contents` list plus its access policy; exports preserve that policy, including a selected player's UUID. Missing policies default to Operators only. Keep the destination on a compatible Minecraft version for serialized items.

## 10. Customize YAML and menus

All editable files are under `plugins/EasyScripting/`:

| File | What you customize |
| --- | --- |
| `config.yml` | Limits, default actor type/behavior, explicit security switches |
| `npc-identities.yml` | Automatic random NPC names and skin accounts |
| `features.yml` | Enable/disable feature groups |
| `guis.yml` | Inventory titles, icons, slots, labels, lore and workflow buttons |
| `messages.yml` | Feedback text and optional command sounds |
| `permissions.yml` | Feature permission overrides |
| `nicknames.yml`, `kits.yml` | Username API/fallback and installed kit import providers |
| `recording.yml` | Production-session behavior and replay knockback/recovery timing |
| `moderation.yml`, `death.yml` | Join/chat/world rules and death behavior |
| `items.yml`, `potions.yml`, `effects.yml` | Item pools, potion presets and effect settings |

After editing settings, run `/es reload`. Invalid settings produce an error and leave the previous active configuration in use. Missing top-level files are supplied automatically. The documented GUI and new-actor-default migrations create backups; individual actors and custom schema-3 GUI values are preserved after the one-time layout upgrade. Actor and scene YAML definitions edited by hand load on a full restart, not `/es reload`.

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

Merge this into the existing `dynamic` section; do not create duplicate top-level keys or remove the other controls. Slots are zero-based. Keep buttons on the same screen in different slots. The randomize control is on Appearance; the identity summary is on Overview. Version 0.1.7 replaces schema-1/2 GUI layouts after validation and saves the original as `guis-before-v3-<UUID>.yml` in the plugin folder. Reapply custom labels to the new layout; do not merge the old slot arrangement into it. Schema-3 customizations are preserved on reload.

Custom buttons execute as the clicking player and obey the same permissions as commands. Opening a menu does not grant control over another player. See [PERMISSIONS.md](PERMISSIONS.md) before granting staff access and [CONFIGURATION.md](CONFIGURATION.md) for complete YAML rules.

## Broadcasts and chat moderation

`/es chat broadcast Filming starts now` sends the text to chat and displays it as a title to every online player. `/es chat block on` and `/es chat block off` announce the state change to everyone. Repeating the current block state does not repeat the announcement. These commands work through the same `easyscripting.chat` permission and chat feature switch.

Edit `broadcast`, `broadcast-title`, `broadcast-subtitle`, `chat-muted` and `chat-unmuted` in `messages.yml`. Broadcast text is inserted as plain text through `<detail>`. `moderation.yml` controls title enablement and timing; run `/es reload` to apply edits. Missing new keys inherit defaults on existing servers.

## NPC groups, supplies and the new controls

Open `/actors` for the NPC library and `/kits` for kits. An NPC overview has four cards: **Identity & clothing**, **Movement**, **Record & replay**, and **Combat & supplies**. Back returns to the overview. Studio home has **Record session**, with explicit ON/OFF buttons for the MOTD/join lock; saved NPC takes stay in Record & replay.

For a faction, start with `/es group create red`, `/es group add red guard_1`, then `/es group leader red Alex`. Alex can order their group; operators manage membership and intelligence. Each NPC keeps individual equipment and health. See the [step-by-step NPC group and combat guide](NPC-GROUPS.md) for 100-member formations, targeting, real supplies and limits.

NPCs automatically offhand carried totems and refill after a pop (default one tick, about 50 ms). Aggressive/intelligent NPCs can use carried beneficial splash potions, imperfect melee timing, jumps and delayed shields against overhead maces. These reactions yield during scenes and performances. Configure every timing/chance in the commented `actor-ai.yml`.

Player `/es player halfheart` now lets a held totem pop normally and keeps protection afterward. Elytra recordings now retain the gliding flag; old frames explicitly marked FALL_FLYING are repaired on load. If an old take saved only swimming, record it again. `/actor finish` retains the same save/autoplay workflow.

## Troubleshooting

| Symptom | What to check |
| --- | --- |
| PLAYER creation says Citizens is unavailable | Install a compatible Citizens build and restart, or create a mob such as `ZOMBIE` |
| NPC initially has a default skin | Allow several seconds for profile lookup; check the skin account spelling, outbound connectivity and Citizens log messages |
| A skin account changed its skin but the NPC did not | Cached appearances are intentional; repeat `/actor set <id> skin <account>` to refresh |
| Renamed NPC cannot be found in a command | Use its original ID, shown by `/actor list` and `/actor info <id>` |
| Randomization reports no names available | Expand the prefix/suffix pools, remove unused actors, or review the identity blacklist |
| NPC edit reports the actor is in use | Name/skin/mode edits work during replay; stop the owning scene or finish acting for operations still held by a take |
| Scene commands reject an action | Check its permission, target, arguments and feature switch; command/destructive actions have extra gates |
| A GUI edit does not appear | Run `/es reload` and reopen it; check errors for invalid slots or YAML |
| Changes disappear after a crash | Use a graceful server stop to drain queued saves; active in-memory takes are not crash recovery backups |

The release's exact test evidence and unverified multiplayer/visual cases are recorded in [TESTING.md](TESTING.md). For integrations written in Java, use the supported service API in [API.md](API.md).
