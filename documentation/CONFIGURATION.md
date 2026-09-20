# Configuration and storage

Current schema/reference: **EasyScripting 0.1.8**. See [the shipped YAML files](../src/main/resources/) for the authoritative defaults and inline explanations.

Files are generated under `plugins/EasyScripting/`. Keep your existing files when updating. Missing top-level files are copied from the JAR. Existing values are preserved except for the documented, backed-up GUI/default migrations below; exact old shipped message/help text is updated in memory. Reload uses `/es reload`; definitions edited outside the plugin load during a server restart. Invalid YAML is preserved and its path is logged. Shipped explanations are added to uncommented existing keys on reload; configured values and your own comments stay intact. The asynchronous YAML writer preserves headers, nested comments and inline comments.

## Top-level YAML

| File | Settings |
| --- | --- |
| actor-ai.yml | Commented and validated movement, social wandering, stable following/catch-up, group budgets, totem refill, combat reaction and self-preservation settings |
| command-help.yml | Command syntax, simple descriptions and examples used for contextual error feedback |
| config.yml | Limits, security gates, actor backend/defaults, optional item autoclear |
| npc-identities.yml | Public-provider/cache timing, automatic NPC identities, local username fragments and fallback Java account skin pool |
| nicknames.yml | Username API enablement, timeout and local fallback; connection-only nicknames preserve skins |
| kits.yml | Enabled kit import providers and maximum provider list size |
| features.yml | Boolean feature groups: actors, scenes, recording, players, identity, kits, warps, items, inventory, locks, death, chat, world, regions, teams, villagers, effects, voice |
| messages.yml | MiniMessage templates and unparsed `<detail>`; feedback.enabled/success/error control optional command sounds |
| guis.yml | Titles, content slots, navigation, controls, item icons, lore, configured command/input/menu buttons |
| permissions.yml | Feature access overrides; see PERMISSIONS.md |
| moderation.yml | Join lock, whitelist, locked worlds, dimension whitelists, blocked commands/phrases, signs and global build/break/PvP |
| recording.yml | Session chat policy, MOTD, optional voice mute and replay knockback/recovery timing |
| items.yml | Head display name, random-fill material pool, and persistent bound group actor tool appearance/type/cooldown |
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

### Generated identities and public providers

`npc-identities.yml` uses `schema: 1`, `enabled`, `api-enabled`, `api-timeout-millis` (500–10000), `api-refresh-minutes` (1–1440), and three fallback lists: `name-prefixes`, `name-suffixes`, `skin-owners`. Each list must have 1..64 unique entries (case-insensitive), using only letters, digits and underscores. A fallback prefix plus suffix must leave room for the generated digit/underscore suffix. Skin owners must be real Java account names, at most 16 characters; actual profile availability is resolved by Citizens after creation.

With `api-enabled: true`, a two-thread bounded client requests up to 256 Random User usernames (262,144-byte response limit) and a rotating set of Craftdex Minecraft profile names for skins (131,072-byte response limit). Calls use configured connect/read timeouts, run off the server thread and populate caches of at most 512 usernames and 128 skin owners. The cache refreshes after `api-refresh-minutes` or sooner when its username pool falls below 64. A provider failure does not block actor creation; local prefix/suffix candidates and the configured fallback skin owners remain available. No Minecraft player identity or client address is placed in a provider request; the providers still see the server's ordinary network connection.

Every automatically generated actor or `/nickname` username must be 5–16 Minecraft username characters, include at least one letter, and include at least one digit or underscore. Selection is case-insensitive and excludes current actor/nickname names, the identity blacklist, every real account known to have joined the server, current operators, and every entry in `state/dead-users.yml`. The requested actor ID is also excluded. Local fallback output is lower-case and always carries the required digit or underscore instead of using the old CapitalCapital fragment pattern. Skin accounts respect the blacklist but may repeat across actors. Pool exhaustion is a bounded error. `/actor randomize <id>` selects another name and, where possible, another skin owner even if automatic creation is disabled.

The chosen identity belongs to the actor definition; reloading settings or restarting does not reroll it. Existing saved actors keep their appearance. New copies, pattern members and tool members receive fresh generated identities when automatic identities are enabled; a copy uses its new actor ID as its name when they are disabled. Once Citizens resolves a skin, its signed texture is cached in the actor file. `/actor set <id> skin <account>` clears the previous cache and requests a refresh without changing the displayed name. A natural actor or nicknamed-player death retires the generated display name; manual actor/group deletion does not. See [the user guide](USERGUIDE.md#configure-generated-names-and-skins) for a complete example.

### Bound group actor tool

`items.yml → group-actor-tool` defines the tool's Bukkit `material`, default living `actor-type`, `cooldown-ticks` (1–100), MiniMessage `name` and MiniMessage `lore` (up to 20 lines). The name/lore accept `{group}`, `{kit}` and `{type}`. Issued items also store those values in persistent item data, so copies and restarts retain their binding. Deleting the referenced group or kit makes the stale tool fail safely.

Only current operators may issue or use this tool. A main-hand right-click on a block creates one actor in the bound group with the bound kit, using the clicked X/Z column's highest motion-blocking safe surface and three clear standing blocks. IDs use `<group>-actor-<number>` and a monotonic counter stored in the group definition; copying a tool does not duplicate the counter.

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

`guis.yml` uses `schema: 3`. Upgrading a schema-1, schema-2 or unversioned layout validates the complete new defaults, saves the old file beside it as `guis-before-v3-<UUID>.yml`, then installs the redesigned layout. A backup failure aborts the upgrade. Existing schema-3 values survive reload; missing leaves inherit bundled defaults. Unknown future schemas are rejected.

Inventories use six rows so all 41 player inventory/equipment slots fit the kit editor. Slot indices are 0..53. The top row holds the information header. Actor sections are opened from overview cards; the old top-row tabs are removed. The default 21 content slots occupy the interior of rows 2–4; row 5 holds workflow controls. Footer slots are previous 45, create/save 47, Back 48, Home 49, Close 50, Help 51 and next 53. `layout.content-slots` must be unique and separate from navigation and workflow controls. Keep at least 13 slots for player flags. Overlapping controls on the same screen are rejected.

`menus.<name>` defines `title`, `heading`, `material`, `description` and optional `parent` for Back navigation. `buttons.<id>` accepts `slot`, `material`, `name`, `lore`, `action`, optional permission suffix and optional `prompt`. Actions begin with `menu `, `command ` or `input `. Commands are suffixes of `/es`, run as the clicking player, and always pass through permission checks. `dynamic.controls` configures slots/materials/lore for actor, timeline and take controls; labels are under `dynamic`. Keep the kit footer in slots 45..53; slots 0..40 are the saved loadout and 41..44 are reserved spacing.

Titles accept `{name}` and `{page}`. `entries.<menu>` configures create labels, empty labels and entry lore with `{detail}` and `{id}`. `player-flags`, `features` and `permissions` configure readable control labels. MiniMessage is supported in titles, names, lore, scene text and configured templates. Player-supplied diagnostic text uses unparsed placeholders. Some contextual validation explanations are generated from the operation rather than separately translated strings.

Changing a YAML button cannot grant permissions or cause it to execute as console. Chat input is bound to the initiating player's UUID, expires in 60 seconds and is never treated as an arbitrary root command.

Actor screens use `menus.actor`, `menus.actor-appearance`, `menus.actor-movement`, `menus.actor-acting` and `menus.actor-combat`. The overview uses four `dynamic.controls.actor-section-*` cards with Back/Home navigation. The old top-row tabs are removed. Acting controls include `actor-act`, `actor-finish`, `actor-cancel`, `actor-play`, `actor-stop`, `actor-recording`, `actor-autoplay` and `actor-mode-stop/repeat/reverse`. Identity & clothing includes `actor-tablist`; internal keys still use `actor-appearance`. Combat controls are `actor-health`, `actor-hittable`, `actor-immortal`, `actor-combat-respawn`, `actor-aggressive`, `actor-combat-kit` and `actor-combat-group`.

Group controls use `menus.group-details` and `dynamic.controls.group-*`. New controls are `group-deploy`, `group-shared-immortal`, `group-shared-kit`, `group-shared-identities` and `group-tool`; the delete confirmation states that every member will be permanently removed. The `dead-users` menu uses content slots for saved heads plus `dead-users-search` in slot 47 and `dead-users-clear` in slot 52. Its pagination retains the active query and shift-right-click releases an entry. The home Record session page uses `menus.session`; `{session}` expands to ON or OFF. Placeholders include `{id}`, `{name}`, `{skin}`, `{mode}`, `{recording}`, `{acting}`, `{autoplay}`, `{health}`, `{status}`, `{shared-immortal}` and `{shared-kit}`; toggle labels use `{state}`.

### NPC AI tuning

See the fully commented `actor-ai.yml` and [NPC guide](NPC-GROUPS.md). `movement` controls eye tracking, social radius, wander/home radius, walking speed and wander cadence. `groups` controls enablement, group/target limits, shared path budget, general/follow repathing, formation goal change/arrival distance, the settling margin before a parked member walks again, row spacing, the number of members per row, normal follow speed, catch-up distance/speed, intermediate waypoint distance, chase speed, engagement/reach and attack/knockback timing. `combat` controls carried-totem handling, reaction delays, accuracy, attack jitter, waiting for the held weapon to recharge, critical-hit hops, jumps, beneficial potion bursts, and delayed shield use against a mace overhead or alongside. `survival` controls the health thresholds and cooldowns for breaking off to eat a carried golden apple and for throwing a carried ender pearl, how far and how long an NPC backs off, and how often it circles its target between swings.

Times are whole server ticks (20 per second at normal TPS), speed values are native navigation multipliers, distances are blocks and chances range from 0 to 1. Every setting is validated with the bounds documented beside it. `combat.totem-refill-ticks: 1` is the minimum supported refill delay. `groups.enabled: false` disables faction simulation and alliance protection; standalone aggression still works. `features.actors: false` stops both. Neither setting pools NPC resources.

`command-help.yml` has `commands` entries with `syntax`, `description` and optional `example`. Values are plain text shown through unparsed chat placeholders. The bundled catalogue is generated from COMMANDS.md by `scripts/update-command-help.py`. Editing help text changes explanations, not command behavior or permissions.

## Definitions and state

| Directory/file | Contents |
| --- | --- |
| scenes/ | Schema-1 timelines |
| actors/ | Commented actor definitions with transform, identity, group, aggressive state, equipment, 36 personal backpack slots, held slot, recording and playback options |
| groups/ | Commented schema-1 faction definitions: real-player leader UUID, intelligence, follow/hold order, optional shared Immortal/kit values and `next-actor-index` (1..2147483646); active wars and targets do not persist |
| loadouts/ | Saved 41-slot kits |
| kit-exports/ | Portable EasyScripting schema-1 kit YAML; explicit import/export |
| recordings/ | Schema-3 frame lists with transforms, hands, armor, pose, explicit gliding, animation/fire cues and movement state; legacy schema-1/2 files remain readable |
| warps/ | Locations and individual access nodes |
| regions/ | Selected block data, container contents and sign text |
| villagers/ | Profession/biome/level and trade templates |
| pending/ | Deferred restoration for offline/dead players and recovery checkpoints for temporary acting, including original profile properties |
| rollback/ | Last ten inventory snapshots per player, captured at death/logout or manually |
| positions/ | Last observed logout position and player name |
| state/ | Player flags, identities, deaths, teams, locks and `dead-users.yml` retired identity records |
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

Snapshots restore location/rotation, inventory/equipment, health, food, XP, gamemode/flight/gliding/speeds, potion effects, velocity, fire, air, fall state, glow/invisibility/invulnerability/gravity, pose and player control flags. Display/list names are captured; the identity service's underlying profile history/skin policy is separate. Scoreboard membership, custom borders and arbitrary third-party state are not part of a take snapshot.

## Kit access and recording sessions

`/es kits` exposes a per-kit **Who can claim?** page. Management/gifts use actual operator status; `permissions.yml` kit overrides cannot bypass it. Claiming replaces the 41-slot hotbar/storage/armor/offhand loadout. Access lives alongside `schema: 1` and `contents` in `loadouts/<id>.yml`:

```yaml
access:
  mode: operators
```

Modes are `operators`, `everyone`, or `player`. Player mode also needs `access.player` (a UUID); `access.player-name` is only a display hint. Set it without editing YAML using `/es kits access starter player Alex`. Switching to operators/everyone clears the selected player. Missing policies default to operators; malformed policies never grant public access. Inventory edits and EasyScripting export/import retain the policy. Imported external-provider kits default to operators.

Mob kit recipients store reserve items in `actors/<id>.yml → inventory`; PLAYER actors use their real inventory. `equipment` contains main hand, offhand, helmet, chestplate, leggings and boots; `held-slot` is the player hotbar selection (0–8). These are individual per-NPC supplies, not group storage.

Bulk imports use one shared-engine job, at most one kit per tick, one batch at a time, and the existing `max-provider-kits` cap. IDs receive numeric suffixes when occupied; no existing kit is overwritten. Cancel/de-op/disconnect/feature-disable/shutdown stops further imports and retains completed ones. Saves go through the bounded YAML writer; disk failures are logged and rejected submissions do not create phantom kits.

New GUI controls under `dynamic.controls`: `kits-import-all`, `kits-import-cancel`, `kit-give-player`, `kit-give-actor`, `kit-access`, `kit-access-info`, `kit-access-operators`, `kit-access-everyone`, `kit-access-player`. Their labels are under `dynamic`; `{access}` shows the policy. New pages are `menus.kit-access`, `menus.kit-provider`, `menus.kit-recipients`. Missing defaults are inherited; overlapping controls fail validation. Customized labels are preserved except documented old shipped help/action migrations.

`/es record on` starts server-session mode without capturing snapshots. `recording.yml → motd` is always shown while active; old `change-motd` values are ignored. All non-operator joins/reconnects are refused using `messages.yml → recording-locked`, regardless of the production allow-list or server.bypass. Existing players remain. `/es record off` restores ordinary MOTD/login rules, without removing independent locks/bans. Sessions are memory-only and end on shutdown. Take start/stop use the same session state with participant snapshots.

NPC performances are created with `/actor act` and `/actor finish`; `/es record`'s old movement subcommands are removed. Permanent NPC deletion/death removes its unshared selected take from active storage. Shared takes remain until their last NPC is removed. Hide/unload/disable/shutdown do not delete takes. Removed YAML uses existing trash retention. Successful commands now send descriptive feedback; the existing `success` template controls its style. `feedback.enabled` still controls only sounds.

## Nicknames and kit imports

`nicknames.yml`: `schema: 1`, `api-enabled: true`, `api-timeout-millis: 4000` (integer 500–10000), `local-fallback: true`. It reuses the bounded Random User client and the same 5–16-character generated-name rules described above; no Minecraft player data is sent in the request. Disable its API flag to use the local NPC prefix/suffix pool only. Skin properties are preserved. Nicknames expire on disconnect; stale saved `state/identities.yml` active aliases are cleared at startup. If a nicknamed real player dies, the nickname is retired in `state/dead-users.yml` and the online player is restored to their real identity on the following tick.

`kits.yml`: `schema: 1`, boolean `providers.PlayerKits2`, `providers.PlayerKits`, `providers.Essentials`, `providers.CMI` (all true), and `max-provider-kits: 1000` (integer 1–10000). Imports require the provider installed and enabled. Only items are copied; actions, costs, permissions and cooldowns are excluded. Supported public API adapters live in the EasyScripting JAR, without redistributing another plugin. Unknown formats can be captured through your inventory. See [kit import usage](USERGUIDE.md#9-create-edit-and-import-kits).

Kit GUI additions use `menus.kit-details`, `dynamic.controls.kits-import`, `kit-apply`, `kit-edit`, `kit-capture`, `kit-export`, `kit-delete`, `kit-import-inventory`, and `kit-save-apply`. Their names/lore/icons/slots are configurable. The library importer defaults to slot 40; editor Import inventory uses 46 and Save & equip uses 52. Current schema-3 layouts inherit missing leaves; schema-1/2 layouts first receive the documented backup and full upgrade. In current layouts, collisions with custom slots are reported for you to resolve. Old configured `command chat mute ...` buttons are rewritten to `command chat block ...` in memory.
