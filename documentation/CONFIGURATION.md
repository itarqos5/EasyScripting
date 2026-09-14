# Configuration and storage

Files are generated under `plugins/EasyScripting/`. Keep your existing files when updating. Missing top-level files are copied from the JAR. Existing values are preserved except for the documented, backed-up GUI/default migrations below; exact old shipped message/help text is updated in memory. Reload uses `/es reload`; definitions edited outside the plugin load during a server restart. Invalid YAML is preserved and its path is logged.

## Top-level YAML

| File | Settings |
| --- | --- |
| config.yml | Limits, security gates, actor backend/defaults, optional item autoclear |
| npc-identities.yml | Automatic NPC identities, username prefixes/suffixes and Java account skin pool |
| nicknames.yml | Username API enablement, timeout and local fallback; connection-only nicknames preserve skins |
| kits.yml | Enabled kit import providers and maximum provider list size |
| features.yml | Boolean feature groups: actors, scenes, recording, players, identity, kits, warps, items, inventory, locks, death, chat, world, regions, teams, villagers, effects, voice |
| messages.yml | MiniMessage templates and unparsed `<detail>`; feedback.enabled/success/error control optional command sounds |
| guis.yml | Titles, content slots, navigation, controls, item icons, lore, configured command/input/menu buttons |
| permissions.yml | Feature access overrides; see PERMISSIONS.md |
| moderation.yml | Join lock, whitelist, locked worlds, dimension whitelists, blocked commands/phrases, signs and global build/break/PvP |
| recording.yml | Session chat policy, MOTD, optional voice mute and replay knockback/recovery timing |
| items.yml | Head display name, random-fill material pool |
| potions.yml | Named presets; each effect has ticks and amplifier |
| effects.yml | Projectile count/lifetime, orbital height, railgun range/damage, wolf limit and bossbar appearance |
| death.yml | Default mode, radius, spectator permission, kick message and keep-inventory-respects-vanishing |

### Limits

Default limits: 200 actors, 8 concurrent scenes, 200 actions per scene per tick, 10,000 actions per scene, 2,400 frames per movement recording, 131,072 blocks per region and 1,024 block operations per region job per tick. Movement recording has a separate eight-performer cap. Projectile effects have count/lifetime limits; active owned effect entities are capped at 500, orbital jobs at 100 and spawned villager templates at 100.

The timeline fails and restores the take if its per-tick budget is exceeded. It does not silently shift action timing. Keep scripted bursts under that limit. Disk work uses a bounded single-writer queue; a full queue reports a failure instead of blocking the server tick.

Region jobs permit one operation per world and at most `limits.active-scenes` jobs overall. Validation and application are incremental; all record data is validated before the first mutation. Keep selected chunks loaded. Cancellation or unload leaves already restored blocks in place; there is no automatic world rollback. Remove saved definitions with `/es region delete <id>`.

`security.allow-command-actions` and `security.destructive-effects` default to false. Both need additional permissions. Time/weather commands and selected region restoration intentionally alter the world; scene time/weather actions snapshot their original environment.

`actors.default-type` defaults to PLAYER. Set it to ZOMBIE on a server without Citizens. `actors.defaults` controls immortal, hittable, collidable, nametag, tablist, look-nearby, wander and autoplay for newly created actors. Autoplay defaults to true, Immortal to false and tablist to false. Upgrade 0.1.5 backs up `config.yml` as `config-before-0.1.5-<UUID>.yml`, sets the creation default `immortal: false` and records `actors.defaults.version: 2`. This is a one-time migration; afterward you may change that default again. Individual saved actor Immortal values are preserved. Actor-specific files persist their own choices; old actor files without an autoplay key default to true.

In `recording.yml`, `playback.knockback-pause-ticks: 12` controls how long replay yields to native physics after a hit, and `playback.return-to-route-ticks: 10` controls the following blend back to its recorded route. Both must be integers from 1 to 100. Missing keys on an older installation use these defaults. The recording cursor pauses during the physics interval; subsequent hits restart it. These settings do not change combat damage or bypass Hittable/Immortal.

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

Names are compared without case for whitelists. Chat/sign filtering uses literal, case-insensitive substrings, not regex. Command blocking normalizes the command root and namespace; it is not a replacement for a server permission system. `/es chat block [on|off]` uses the current operator status on each message. The stored `chat-muted` boolean retains its original internal key. `easyscripting.chat.bypass` only affects recording-session suppression, not public blocking.

### Random NPC identities

`npc-identities.yml` uses `schema: 1`, a boolean `enabled`, and three lists: `name-prefixes`, `name-suffixes`, `skin-owners`. Each list must have 1..64 unique entries (case-insensitive), using only letters, digits and underscores. Combined prefix/suffix usernames must be at most 16 characters. Skin owners must be real Java account names, at most 16 characters; actual profile availability is resolved by Citizens after creation. The bundled 24 × 48 name pool provides 1,152 choices and six skin accounts. The generator prefers endings not used in its last eight selections. If a custom pool has no alternative ending, it falls back to any unused allowed name. Existing pool files are preserved; add the new suffixes yourself if desired.

With `enabled: true`, new actors get a random displayed name; PLAYER actors also get a random skin owner. Generated names exclude existing actor names, online player account names, the requested actor ID and the identity blacklist. Skin accounts also respect the blacklist. Skin owners may repeat across actors. Pool exhaustion is a bounded error. `/actor randomize <id>` selects another name and, where possible, another skin owner even if automatic creation is disabled.

The chosen identity belongs to the actor definition; reloading settings or restarting does not reroll it. Existing actors and copies keep their appearance. New pattern members are randomized. Once Citizens resolves a skin, its signed texture is cached in the actor file. `/actor set <id> skin <account>` clears the previous cache and requests a refresh without changing the displayed name. See [the user guide](USERGUIDE.md#configure-the-random-name-and-skin-pools) for a complete example.

## Broadcast titles and chat announcements

`moderation.yml` supports the following settings (20 ticks = 1 second):

```yaml
broadcast-title:
  enabled: true
  fade-in-ticks: 10
  stay-ticks: 60
  fade-out-ticks: 10
```

Fade timings accept 0–1200; stay accepts 1–1200. All timings must be YAML integers. `messages.yml` supplies `broadcast-title`, `broadcast-subtitle`, `chat-muted` and `chat-unmuted`; `<detail>` is unparsed broadcast text. Missing keys inherit bundled defaults in memory without replacing customized messages or moderation settings.

`actors.announce-death-leave: true` announces an actual NPC death using `messages.yml` key `actor-left` with `<name>`. Hittable OFF blocks melee/sweep only, for both idle and replaying NPCs. Falls, projectiles and explosions remain damage sources. Acting players always block melee only and otherwise take normal damage. Dead NPCs are permanently removed from active actor storage; their old YAML is retained only by the existing trash mechanism.

## GUI customization

`guis.yml` uses `schema: 2`. Upgrading a schema-1 or unversioned layout validates the complete new defaults, saves the old file beside it as `guis-v1-backup-<unique-id>.yml`, then installs the redesigned layout. A backup failure aborts the upgrade. Existing schema-2 values survive reload; missing leaves inherit bundled defaults. Unknown future schemas are rejected.

Inventories use six rows so all 41 player inventory/equipment slots fit the kit editor. Slot indices are 0..53. The top row holds a header and NPC tabs. The default 21 content slots occupy the interior of rows 2–4; row 5 holds workflow controls. Footer slots are previous 45, create/save 47, Back 48, Home 49, Close 50, Help 51 and next 53. `layout.content-slots` must be unique and separate from navigation and workflow controls. Keep at least 13 slots for player flags. Overlapping controls on the same screen are rejected.

`menus.<name>` defines `title`, `heading`, `material`, `description` and optional `parent` for Back navigation. `buttons.<id>` accepts `slot`, `material`, `name`, `lore`, `action`, optional permission suffix and optional `prompt`. Actions begin with `menu `, `command ` or `input `. Commands are suffixes of `/es`, run as the clicking player, and always pass through permission checks. `dynamic.controls` configures slots/materials/lore for actor, timeline and take controls; labels are under `dynamic`. Keep the kit footer in slots 45..53; slots 0..40 are the saved loadout and 41..44 are reserved spacing.

Titles accept `{name}` and `{page}`. `entries.<menu>` configures create labels, empty labels and entry lore with `{detail}` and `{id}`. `player-flags`, `features` and `permissions` configure readable control labels. MiniMessage is supported in titles, names, lore, scene text and configured templates. Player-supplied diagnostic text uses unparsed placeholders. Some contextual validation explanations are generated from the operation rather than separately translated strings.

Changing a YAML button cannot grant permissions or cause it to execute as console. Chat input is bound to the initiating player's UUID, expires in 60 seconds and is never treated as an arbitrary root command.

Actor screens use `menus.actor`, `menus.actor-appearance`, `menus.actor-movement`, `menus.actor-acting` and `menus.actor-combat`. Navigation uses `dynamic.controls.actor-section-*` and persistent `actor-tab-*` tabs. Acting controls include `actor-act`, `actor-finish`, `actor-cancel`, `actor-play`, `actor-stop`, `actor-recording`, `actor-autoplay` and `actor-mode-stop/repeat/reverse`. Appearance includes `actor-tablist`. Combat controls are `actor-health`, `actor-hittable`, `actor-immortal`, `actor-combat-respawn`. Placeholders include `{id}`, `{name}`, `{skin}`, `{mode}`, `{recording}`, `{acting}`, `{autoplay}`, `{health}`, `{status}`; toggle labels use `{state}`.

## Definitions and state

| Directory/file | Contents |
| --- | --- |
| scenes/ | Schema-1 timelines |
| actors/ | Actor type, transform, behavior, equipment, name, skin owner, optional cached signed texture, recording, playback-mode, autoplay and tablist |
| loadouts/ | Saved 41-slot kits |
| kit-exports/ | Portable EasyScripting schema-1 kit YAML; explicit import/export |
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

## Nicknames and kit imports (0.1.5)

`nicknames.yml`: `schema: 1`, `api-enabled: true`, `api-timeout-millis: 4000` (integer 500–10000), `local-fallback: true`. The fixed HTTPS Random User endpoint generates names; no Minecraft player data is sent in the request. Its response is bounded to 16 KiB, eight candidates and two worker threads with a bounded queue. Disable the API to use the local NPC prefix/suffix pool only. Skin properties are preserved. Nicknames expire on disconnect; stale saved `state/identities.yml` active aliases are cleared at startup.

`kits.yml`: `schema: 1`, boolean `providers.PlayerKits2`, `providers.PlayerKits`, `providers.Essentials`, `providers.CMI` (all true), and `max-provider-kits: 1000` (integer 1–10000). Imports require the provider installed and enabled. Only items are copied; actions, costs, permissions and cooldowns are excluded. Supported public API adapters live in the EasyScripting JAR, without redistributing another plugin. Unknown formats can be captured through your inventory. See [kit import usage](USERGUIDE.md#9-create-edit-and-import-kits).

Kit GUI additions use `menus.kit-details`, `dynamic.controls.kits-import`, `kit-apply`, `kit-edit`, `kit-capture`, `kit-export`, `kit-delete`, `kit-import-inventory`, and `kit-save-apply`. Their names/lore/icons/slots are configurable. The library importer defaults to slot 40; editor Import inventory uses 46 and Save & equip uses 52. Schema-2 layouts inherit these missing leaves; collisions with custom slots are reported for you to resolve. Old configured `command chat mute ...` buttons are rewritten to `command chat block ...` in memory.
