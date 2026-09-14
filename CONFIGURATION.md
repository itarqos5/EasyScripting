# Configuration and storage

Files are generated under `plugins/EasyScripting/`. Keep your existing files when updating. Missing top-level files are copied from the JAR, but existing files are never silently replaced. Reload uses `/es reload`; definitions edited outside the plugin load during a server restart. Invalid YAML is preserved and its path is logged.

## Top-level YAML

| File | Settings |
| --- | --- |
| config.yml | Limits, security gates, actor backend/defaults, optional item autoclear |
| npc-identities.yml | Automatic NPC identities, username prefixes/suffixes and Java account skin pool |
| features.yml | Boolean feature groups: actors, scenes, recording, players, identity, kits, warps, items, inventory, locks, death, chat, world, regions, teams, villagers, effects, voice |
| messages.yml | MiniMessage templates and unparsed `<detail>`; feedback.enabled/success/error control optional command sounds |
| guis.yml | Titles, content slots, navigation, controls, item icons, lore, configured command/input/menu buttons |
| permissions.yml | Feature access overrides; see PERMISSIONS.md |
| moderation.yml | Join lock, whitelist, locked worlds, dimension whitelists, blocked commands/phrases, signs and global build/break/PvP |
| recording.yml | Session chat policy, MOTD and optional voice mute |
| items.yml | Head display name, random-fill material pool |
| potions.yml | Named presets; each effect has ticks and amplifier |
| effects.yml | Projectile count/lifetime, orbital height, railgun range/damage, wolf limit and bossbar appearance |
| death.yml | Default mode, radius, spectator permission, kick message and keep-inventory-respects-vanishing |

### Limits

Default limits: 200 actors, 8 concurrent scenes, 200 actions per scene per tick, 10,000 actions per scene, 2,400 frames per movement recording, 131,072 blocks per region and 1,024 block operations per region job per tick. Movement recording has a separate eight-performer cap. Projectile effects have count/lifetime limits; active owned effect entities are capped at 500, orbital jobs at 100 and spawned villager templates at 100.

The timeline fails and restores the take if its per-tick budget is exceeded. It does not silently shift action timing. Keep scripted bursts under that limit. Disk work uses a bounded single-writer queue; a full queue reports a failure instead of blocking the server tick.

Region jobs permit one operation per world and at most `limits.active-scenes` jobs overall. Validation and application are incremental; all record data is validated before the first mutation. Keep selected chunks loaded. Cancellation or unload leaves already restored blocks in place; there is no automatic world rollback. Remove saved definitions with `/es region delete <id>`.

`security.allow-command-actions` and `security.destructive-effects` default to false. Both need additional permissions. Time/weather commands and selected region restoration intentionally alter the world; scene time/weather actions snapshot their original environment.

`actors.default-type` defaults to PLAYER. Set it to ZOMBIE on a server without Citizens. `actors.defaults` controls immortal, hittable, collidable, nametag, look-nearby and wander for newly created actors. Actor-specific files persist their own choices.

`world.auto-clear-seconds: 0` disables automatic cleanup. When enabled, only dropped items within `auto-clear-radius` of online players in `auto-clear-worlds` are removed. It is not a scan of unloaded worlds. Explicit cleanup can remove other chosen entity categories.

`spawn.on-first-join` and `spawn.on-respawn` optionally route players to the saved `spawn` warp. Both default to false; `easyscripting.spawn.bypass` exempts a player. The ordinary `/es spawn` command works independently.

### Moderation examples

```yaml
server-lock:
  enabled: false
  allowed: [CameraOp, Performer]
locked-worlds: [world_the_end]
dimension-whitelist:
  world_the_end: [CameraOp]
blocked-commands: [op, minecraft:kill]
blocked-phrases: ['private address']
sign-alerts: true
join-messages: true
leave-messages: true
build: true
break: true
pvp: true
```

Names are compared without case for whitelists. Chat/sign filtering uses literal, case-insensitive substrings, not regex. Command blocking normalizes the command root and namespace; it is not a replacement for a server permission system. Chat bypass permission changes take effect on join, session start or `/es reload`.

### Random NPC identities

`npc-identities.yml` uses `schema: 1`, a boolean `enabled`, and three lists: `name-prefixes`, `name-suffixes`, `skin-owners`. Each list must have 1..64 unique entries (case-insensitive), using only letters, digits and underscores. Combined prefix/suffix usernames must be at most 16 characters. Skin owners must be real Java account names, at most 16 characters; actual profile availability is resolved by Citizens after creation. The bundled 24 × 48 name pool provides 1,152 choices and six skin accounts. The generator prefers endings not used in its last eight selections. If a custom pool has no alternative ending, it falls back to any unused allowed name. Existing pool files are preserved; add the new suffixes yourself if desired.

With `enabled: true`, new actors get a random displayed name; PLAYER actors also get a random skin owner. Generated names exclude existing actor names, online player account names, the requested actor ID and the identity blacklist. Skin accounts also respect the blacklist. Skin owners may repeat across actors. Pool exhaustion is a bounded error. `/actor randomize <id>` selects another name and, where possible, another skin owner even if automatic creation is disabled.

The chosen identity belongs to the actor definition; reloading settings or restarting does not reroll it. Existing actors and copies keep their appearance. New pattern members are randomized. Once Citizens resolves a skin, its signed texture is cached in the actor file. `/actor set <id> skin <account>` clears the previous cache and requests a refresh without changing the displayed name. See [the user guide](USERGUIDE.md#configure-the-random-name-and-skin-pools) for a complete example.

## GUI customization

Inventories use six rows so all 41 player inventory/equipment slots fit the kit editor. Slot indices are 0..53. `layout.content-slots` must be unique and separate from previous/back/next/create slots. Keep at least 13 content slots for player flags.

`menus.<name>.buttons.<id>` accepts `slot`, `material`, `name`, `lore`, `action`, and optional `prompt`. Actions begin with `menu `, `command ` or `input `. Commands are suffixes of `/es`, run as the clicking player, and always pass through permission checks. `dynamic.controls` configures slots/materials/lore for actor, timeline and take controls; labels are under `dynamic`. Do not overlap controls that share the same screen. Keep kit save/navigation buttons outside slots 0..40.

Titles accept `{name}` and `{page}`. Listing labels use `{name}`, and listing lore uses `{detail}`. MiniMessage is supported in titles, names, lore, scene text and configured templates. Player-supplied diagnostic text uses unparsed placeholders. Some contextual validation explanations are deliberately generated from the operation rather than separately translated strings.

Changing a YAML button cannot grant permissions or cause it to execute as console. Chat input is bound to the initiating player's UUID, expires in 60 seconds and is never treated as an arbitrary root command.

Actor screens use `menus.actor`, `menus.actor-appearance`, `menus.actor-movement`, `menus.actor-acting` and `menus.actor-combat`. Section navigation is configured with `dynamic.controls.actor-section-*`. Acting controls use `actor-act`, `actor-finish`, `actor-cancel`, `actor-play`, `actor-stop`, `actor-recording` and `actor-mode-stop/repeat/reverse`. Combat controls are `actor-hittable`, `actor-immortal`, `actor-combat-respawn`. Labels live under `dynamic`; mode/recording/performer placeholders are `{mode}`, `{recording}`, `{acting}`, and combat labels use `{state}`. Missing actor-menu settings inherit bundled defaults in memory on older installations; existing customized values and files are preserved.

## Definitions and state

| Directory/file | Contents |
| --- | --- |
| scenes/ | Schema-1 timelines |
| actors/ | Actor type, transform, behavior, equipment, name, skin owner, optional cached signed texture, selected recording and playback-mode |
| loadouts/ | Saved 41-slot kits |
| recordings/ | Schema-2 frame lists with transforms, hands, armor, pose, animation/fire cues and movement state; legacy schema-1 files remain readable |
| warps/ | Locations and individual access nodes |
| regions/ | Selected block data, container contents and sign text |
| villagers/ | Profession/biome/level and trade templates |
| pending/ | Deferred restoration for offline/dead players and recovery checkpoints for temporary acting, including original profile properties |
| rollback/ | Last ten inventory snapshots per player, captured at death/logout or manually |
| positions/ | Last observed logout position and player name |
| state/ | Player flags, identities, deaths, teams and locks |
| trash/ | Soft-deleted definitions/snapshots; server administrators manage retention |

Writes use an atomic same-directory replacement where supported, with an ordinary replacement fallback. Bukkit item/vector objects are detached on the server thread; YAML encoding and disk writes run on the writer. Normal disable drains writes. Large region capture/restore is incremental; a region file is committed only after capture completes. Cancelling a restore leaves already applied blocks in place.

### Example scene

Store as `scenes/opening.yml`, then restart. Create the `guard` actor first:

```yaml
schema: 1
description: Opening cue
restore-on-complete: true
bindings:
  guard: actor:guard
actions:
  - tick: 0
    type: title
    target: self
    args:
      text: '<aqua>Take one'
      subtitle: 'Camera rolling'
  - tick: 20
    type: swing
    target: guard
    args: {}
  - tick: 60
    type: wait
    target: guard
    args: {}
```

Actions sort by tick, preserving YAML order for equal ticks. World positions require a loaded world and finite bounded coordinates. Unknown actions, malformed list entries and invalid identifiers are rejected. A missing actor is an execution preflight error, allowing definitions to be loaded before the cast is created.

Snapshots restore location/rotation, inventory/equipment, health, food, XP, gamemode/flight/speeds, potion effects, velocity, fire, air, fall state, glow/invisibility/invulnerability/gravity, pose and player control flags. Display/list names are captured; the identity service's underlying profile history/skin policy is separate. Scoreboard membership, custom borders and arbitrary third-party state are not part of a take snapshot.
