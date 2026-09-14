# Commands

Use `/es help` for permission-filtered help. `/script` and `/easyscripting` alias `/es`; `/scene …` and `/actor …` alias their grouped subcommands. Identifiers use lowercase letters, digits, `_` and `-`, begin with a letter/digit and are at most 48 characters. Team identifiers are at most 12 characters. Values `on/off` and `true/false` are accepted for booleans. Slot numbers in YAML are zero-based; scene action numbers in commands are **one-based**.

## Studio and policy

| Command | Behavior |
| --- | --- |
| `/es menu [name]` | Open main, scenes, actors, players, kits, warps, recording, features, item, teams, production, world, effects, permissions or villagers |
| `/es features [group]` | Open toggles, or toggle one group and persist it |
| `/es permissions <feature> <everyone\|permission.node>` | Override access for an editable feature; sensitive admin/destructive/command permissions cannot be made public here |
| `/es reload` | Validate/reload settings and menus; close open studio menus |
| `/es status` | Loaded actors/scenes, active tick jobs and voice integration |

## Scenes

| Command | Behavior |
| --- | --- |
| `/scene create <id>` | Create an empty timeline |
| `/scene list` | List saved scenes |
| `/scene bind <id> <alias> <actor:id\|player:name\|self>` | Define a named target |
| `/scene add <id> <tick> <type> <target> [key=value;key=value]` | Add an action; spaces inside values are retained |
| `/scene remove <id> <number>` | Remove an action by its displayed order |
| `/scene append <destination> <source> <offset>` | Copy choreography, resolving source bindings and shifting ticks |
| `/scene repeat <id> <from> <through> <copies> <interval>` | Add bounded repetitions of an inclusive tick range; interval must exceed its length |
| `/scene restore <id> on\|off` | Restore captured state automatically on completion |
| `/scene play\|pause\|resume\|stop\|reset\|status\|gui <id>` | Control or inspect a scene; stop restores state |
| `/scene delete <id>` | Stop and remove the definition; disk file moves to trash |

Arguments are semicolon-separated, not quoted JSON. A value cannot contain a literal semicolon with this command parser; edit YAML for such text. Example: `/scene add duel 20 attack guard victim=hero;damage=2`.

### Action types

Every action has a target. `at` and `victim` also accept bindings. Positions need `world`, `x`, `y`, `z`, with optional `yaw` and `pitch`.

| Types | Arguments / effect | Additional permission suffix |
| --- | --- | --- |
| teleport | Position | player |
| move | Position; speed 0.1..5 (default 1), actor navigation | actor |
| look / rotation | `at` / `yaw;pitch` | player |
| velocity | `x;y;z`, each -10..10 | player |
| health / damage | `value`, positive health / damage 0..1000 | player / effects |
| attack | `victim`; optional `damage=1`, within 6 blocks | actor |
| swing / hurt / critical | Main-hand swing / damage flash / particles | effects |
| jump | Optional `height=0.42` (0.1..3 vertical velocity) | actor |
| pose / sneak / sprint | `value`; sprint flag requires a player actor | actor |
| equip | `slot;material`; optional `amount=1` | items |
| potion | `effect`; optional `ticks=200;amplifier=0` | player |
| flag | `name;value`, a supported player control | player |
| fire | `ticks` (0..12000) | effects |
| death | Set health to zero; scene reset can respawn an actor | destructive |
| fake-death | Sound, hurt animation and particles; no corpse/disconnect | effects |
| title | `text`; optional `subtitle;ticks=40` | effects |
| actionbar / message | `text`, MiniMessage | effects / chat |
| sound | `sound`; optional `volume=1;pitch=1` | effects |
| particle | `particle`; optional `count=20`; data-free particles only | effects |
| lightning / explosion | Visual-only effects | effects |
| time / weather | `value`: 0..24000 / clear, rain, thunder | world |
| wait | Timeline marker, useful to hold a take until a tick | scene.play |
| player-command / console-command | `command`, no leading slash needed; explicitly opt in | commands.player / commands.console |

## Actors and recording

| Command | Behavior |
| --- | --- |
| `/actor create <id> [type]` | Spawn here with a random username and, for PLAYER, random skin; default PLAYER requires Citizens |
| `/actor list`, `/actor gui <id>` | Manage cast |
| `/actor info <id>` | Show stable actor ID, displayed name, skin owner and type |
| `/actor randomize <id>` | Reroll name and PLAYER skin using npc-identities.yml; preserve ID/equipment/behavior |
| `/actor gui <id> [overview\|appearance\|movement\|acting\|combat]` | Open a section of the actor editor |
| `/actor act <id> [recording-id]` | Take the NPC position/name/skin/costume and record your performance; requires actor + record |
| `/actor finish`, `/actor cancel` | Save or discard acting and restore your original state |
| `/actor recording <id> <recording-id>` | Select an existing recording |
| `/actor autoplay <id> <on\|off>` | Save autoplay preference; enabling starts an available NPC. Requires actor + record. Disabling does not stop an active replay |
| `/actor mode <id> <stop\|repeat\|reverse>` | Choose final-position stop, teleport-to-start loop, or continuous forward/backward playback |
| `/actor play <id>`, `/actor stop <id>` | Play selected recording/mode; explicit stop restores starting state while preserving damage received during replay |
| `/actor set <id> <setting> <value>` | name, skin, group, immortal, hittable, collidable, nametag, look, wander, pose, glow, sneak, sprint |
| `/actor here <id>` | Teleport actor to director |
| `/actor move <id> [speed]` | Navigate to director's current location |
| `/actor copy <source> <new-id>` | Copy appearance/equipment here |
| `/actor hide\|show\|respawn\|delete <id>` | Actor lifecycle |
| `/actor attack <id> <online-player> [damage]` | Face, swing and damage once within 6 blocks |
| `/actor kit <id> <kit>` | Equip saved loadout |
| `/actor pattern <prefix> <line\|circle\|grid\|square> <count> [spacing=2] [type=PLAYER]` | Spawn 1..200 actors in a group, subject to server cap |
| `/actor all <group\|*> <setting> <value>` | Apply a setting to matching actors |
| `/actor group <group\|*> <hide\|show\|respawn\|jump\|kit> [kit-id]` | Group operation |
| `/es record start <id>`, `/es record stop` | Capture your location/rotation, hands, sneak, sprint, swing and boat state |
| `/es record startall <prefix>`, `/es record stopall` | Capture up to eight performers on one tick; files are `<prefix>_<lowercase-player-name>`; requires player.others |
| `/es record playgroup <loop> <reverse> <recording=actor>...` | Validate all tracks, then start on a shared tick; each actor occurs once |
| `/es record play <id> <actor> [loop=off] [reverse=off]` | Replay exact recorded world coordinates; restore actor when stopped |
| `/es record stopplay <actor>`, `/es record delete <id>`, `/es record list` | Manage recordings |
| `/es camera move <x> <y> <z> <ticks> [yaw] [pitch]` | Spectator camera interpolation within this world |
| `/es camera stop` | Stop camera and restore viewer |

Actor IDs remain unchanged when a name or skin is edited. `/actor set <id> name <name>` keeps the skin; `/actor set <id> skin <account>` keeps the name and supports hidden PLAYER actors. NPC skin values are Java account names, not PNG/NameMC URLs. Newly resolved signed textures are saved for reuse after restart; repeating the skin command deliberately refreshes them. Copying an actor keeps its appearance; pattern members receive new identities. See [USERGUIDE.md](USERGUIDE.md#2-create-an-npc-with-a-random-identity) for examples and pool customization.

Autoplay defaults to ON, starts after a successful acting save and when an eligible NPC loads, is shown or respawns. Choose the end mode before recording. Stop playback before changing the mode or recording; Hittable, Immortal and Autoplay can be toggled during playback. Acting performers are damage/knockback immune; replaying NPCs follow their combat flags.

## Player, takes and inventory

`/es player <operation> [value] [online-player]` supports `health`, `heal`, `feed`, `hunger`, `gamemode`, `flight`, `invulnerable`, `invisible`, `glow`, `speed`, `fire`, `potion <preset>`, `clear-effects` and these boolean flags:

`freeze`, `halfheart`, `keepinv`, `no-hunger`, `no-durability`, `no-build`, `no-break`, `no-pvp`, `lock-inventory`, `lock-armor`, `lock-pickup`, `vanish`, `pauseeffects`.

For a valueless operation on someone else, use a placeholder value: `/es player heal on Alex`. `/es player otp <name>` teleports **you** to that player's recorded logout location; it does not alter offline player data.

| Command | Behavior |
| --- | --- |
| `/es take snapshot\|reset\|discard` | Your repeatable take |
| `/es take start <id> [self\|all]` | Capture participants and begin session |
| `/es take stop [reset\|keep]` | End session, optionally restore |
| `/es kit save\|apply\|delete\|edit <id>`, `/es kit list` | Saved inventories; GUI editor copies items |
| `/es inventory view\|ender [player]` | Read-only copy, refreshed when reopened |
| `/es inventory save\|history [player]` | Save/list inventory rollback snapshots |
| `/es inventory restore <player> <snapshot-id>` | Restore inventory only |
| `/es inventory restock [player]` | Fill existing stacks to their max size |
| `/es inventory fill` | Randomly fill empty slots in targeted container, within 6 blocks |
| `/es nick set <name>`, `random [profile]`, `reset`, `info <name>` | Temporary identity and history |
| `/es nick blacklist add\|remove\|list [name]` | Admin name/skin blacklist; adding purges matching existing actors and nicknames |
| `/es nick profile add\|remove <profile> <name>` | Edit random nickname pool |
| `/es skin <player-name\|https://textures.minecraft.net/texture/hash> [auto\|slim\|classic]` | Apply texture/model; account lookup is asynchronous; auto preserves account model and uses classic for a raw texture URL |
| `/es death normal\|spectator\|kick\|respawn [player]` | Configure death handling |
| `/es death scene <scene-id\|off> [player]` | Run a scene after respawn, under that player's permissions; 10-second recursion guard |

## Items, world and production

| Command | Behavior |
| --- | --- |
| `/es item name <text>`, `lore <line\|line>` | Edit held item with MiniMessage |
| `/es item repair`, `durability <damage>`, `unbreakable on\|off`, `amount <n>` | Held item properties |
| `/es item enchant <enchantment> <0..255>` | 0 removes enchantment |
| `/es item attribute <attribute> <amount> [operation=ADD_NUMBER] [slot=mainhand]` | Replace EasyScripting's modifier for that attribute; operations ADD_NUMBER, ADD_SCALAR, MULTIPLY_SCALAR_1 |
| `/es item give <material> [amount]`, `head <name>`, `randomhead [profile]` | Give items/heads |
| `/es item tool kickstick\|stasisrod\|regionwand` | Tagged production tool; using it still checks permissions |
| `/es item stasis <pops> <delay-ticks>` | Totem at current destination, 1..64 pops; final pop schedules teleport |
| `/es warp save\|go\|delete <id>`, `permission <id> <node\|everyone\|op>`, `list` | Teleport anchors; `go` accepts optional other player |
| `/es spawn [set]` | Use/save the `spawn` warp |
| `/es world time <ticks>`, `weather clear\|rain\|thunder` | Environment |
| `/es world border <size\|reset>` | Per-player fake border |
| `/es world top`, `teleport <world>` | Surface or loaded world's spawn |
| `/es world clean <mobs\|hostile\|passive\|items\|all\|ENTITY_TYPE> [radius]` | Bounded entity cleanup, excluding actors and players |
| `/es world limit <view\|send\|simulation> <2..32>` | Per-player distance |
| `/es world lock <world> on\|off`, `allow <world> <name> on\|off` | Dimension access |
| `/es region wand\|chunk\|pos1\|pos2`, `save\|restore\|cancel\|delete <id>`, `list` | Selected blocks, containers and signs; deleting moves the definition to trash |
| `/es lock container\|frame on\|off` | Targeted container/frame lock |
| `/es effect lightning\|explosion\|orbital\|totem` | Visual cues |
| `/es effect arrows\|snowballs\|rod\|wolves [amount]`, `railgun` | Combat/projectile tools; these can damage entities |
| `/es effect destructive-explosion` | Explicitly opted-in real block destruction |
| `/es effect bossbar <text\|off>`, `stop` | Bossbar or stop owned temporary effect entities/jobs |
| `/es chat mute on\|off`, `clear [self]`, `broadcast <text>`, `join\|leave\|death <name>` | Production chat; fake messages do not connect/disconnect players |
| `/es server lock on\|off`, `allow\|deny <name>`, `build\|break\|pvp on\|off` | Access and global production restrictions |
| `/es team create\|delete <id>`, `join\|leave <id> [name]`, `set <id> <option> <value>`, `list` | Options color, glow, prefix, friendly-fire, see-invisible, nametags, collision |
| `/es villager create\|spawn\|clear\|delete <id>`, `set <id> <option> <value>`, `list` | Template options name, profession, type, level |
| `/es villager trade <id> [once=off]` | Main hand is result, offhand is cost; max 32 trades |
| `/es voice mute on\|off`, `broadcast on\|off` | Simple Voice Chat integration |

Menus execute the same permission-checked commands. Left-click opens/uses an entry, shift-right requests deletion confirmation, and right-clicking a kit opens its editor. Type `cancel` to abandon chat input. Input expires after 60 seconds.
