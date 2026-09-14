# EasyScripting behavioral parity

Independent clean-room implementation based on public descriptions, both listing/gallery images and public updates. No original plugin binary, source, private assets, licensing or DRM was accessed. Branding, source and configuration are independent.

## Evidence inspected 2026-09-13

* [BuiltByBit listing](https://builtbybit.com/resources/scriptedessentials-full-release.75101/): feature overview and first gallery image.
* [Full gallery image](https://builtbybit.com/attachments/sepage1-3-png.1429318/?preset=fullr1): opened in the browser at original size; all panels of the 930 × 8000 image inspected. It shows feature controls and command labels, not every editor or argument default.
* [Public updates](https://builtbybit.com/resources/scriptedessentials-full-release.75101/updates): combat, identity, kit, restriction and production workflow clarifications.
* Platform sources: [Paper project setup](https://docs.papermc.io/paper/dev/project-setup/), [Java requirements](https://docs.papermc.io/paper/getting-started/), [BlockData snapshots](https://jd.papermc.io/paper/1.21.8/org/bukkit/block/data/BlockData.html#createBlockState()).

IMPLEMENTED means working code exists. VERIFIED identifies behavior exercised in recorded tests. PARTIAL identifies a known difference, NOT STARTED an absent behavior, and UNKNOWN insufficient public detail for equivalence. Confidence describes the observed requirement, not code quality. Verification is against the behavior specification, not a running reference binary.

Commands follow /es unless shown otherwise. Permission suffixes follow easyscripting. UI names refer to guis.yml inventory menus. Features are permission-gated and grouped under 18 feature switches, not one switch for every command.

## Feature inventory

| Feature | Observed behavior / implementation scope | Commands | Permissions | UI | Config | Status | Confidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Feature controls | Persistent switches; broad groups rather than every reference subfeature | features | admin | Features | features.yml | PARTIAL | HIGH |
| Reload validation | Invalid GUI rejected; live settings and bad source file preserved | reload | admin | Command | guis.yml, config.yml | VERIFIED | HIGH |
| Permission editor | Custom feature node/public access; sensitive nodes excluded | permissions | admin | Permissions | permissions.yml | IMPLEMENTED | HIGH |
| Actor creation | Mob and Citizens PLAYER backends; 100-actor fixture | /actor create/list/delete | actor | Actors | actors/, config.yml | VERIFIED | HIGH |
| Random NPC identities | Generated username and random account skin on creation; independent manual edits, reroll, stable ID and cached signed texture | /actor create/info/randomize/set | actor | Actor editor | npc-identities.yml, actors/, guis.yml | VERIFIED | HIGH |
| Varied generated endings | Expanded suffix pool; avoids the last eight endings when alternatives remain | /actor create/randomize | actor | Appearance | npc-identities.yml | VERIFIED | HIGH |
| Acting as an NPC | Temporarily adopts actor position, player-model identity and costume; save/cancel restores performer; checkpoint for disconnect/respawn recovery | /actor act/finish/cancel | actor, record | Acting & Playback | recordings/, pending/ | VERIFIED | HIGH |
| Actor playback modes | Saved recording selection; once and hold, repeat from start, continuous forward/backward reversal; legacy commands preserved | /actor recording/mode/play/stop | actor, record | Acting & Playback | actors/, recordings/ | VERIFIED | HIGH |
| Actor combat controls (0.1.5) | Melee-only Hittable, immortal hit/knockback with no death, mortal creation default and named leave announcement on permanent deletion | /actor set/respawn | actor | Combat | actors/, guis.yml | IMPLEMENTED | HIGH |
| Autoplay (0.1.3) | Per-NPC saved switch; start after acting save, show, respawn, enable or startup; busy/hidden/disabled guards | /actor autoplay | actor, record | Acting & Playback | actors/, config.yml | IMPLEMENTED | HIGH |
| Melee-protected acting (0.1.4) | Direct melee blocked; falls/projectiles/explosions allowed; prior player state restored afterward | /actor act/finish/cancel | actor, record | Acting & Playback | pending/ | IMPLEMENTED | HIGH |
| Replay combat (0.1.3) | Native knockback pause and route recovery; received damage survives reset; death ends playback | /actor play/stop | actor, record | Combat | recording.yml | IMPLEMENTED | HIGH |
| Studio redesign (0.1.3) | Grouped tools, NPC tabs, pickers, readable states, safe deletion, consistent navigation and backed-up schema migration | /es | use plus action nodes | All | guis.yml | IMPLEMENTED | HIGH |
| Actor appearance | Copy equipment/name, account-name skin, visibility, pose and glow | /actor copy/set/kit | actor, actual operator for kits | Actor editor | actors/ | IMPLEMENTED | HIGH |
| Actor movement | Navigate, face, rotate, sprint/sneak, jump, swing | /actor move; /scene add | actor, player, effects | Actor, timeline | actors/, scenes/ | IMPLEMENTED | HIGH |
| Mass/groups | Line/circle/grid/square; group settings, hide/show/respawn/jump/kit; default cap 200 | /actor pattern/all/group | actor, actual operator for kits | Actors, command | actors/, config.yml | IMPLEMENTED | HIGH |
| Actor combat | Face/swing/damage once within 6 blocks; immortal and unhittable differ | /actor attack/set | actor, player.others | Actor editor | actors/ | IMPLEMENTED | HIGH |
| Scripted actor death (0.1.4) | Explicit permission; dead actor is permanently deleted and cannot be reset | /scene add … death | scene.edit, destructive | Timeline | scenes/ | IMPLEMENTED | HIGH |
| Wander/look | Nearby wander with bounded safe destination candidates; look at closest player | /actor set | actor | Actor editor | actors/ | IMPLEMENTED | HIGH |
| Advanced navigation | Explicit portal traversal, nearest-shore swimming and comprehensive hazard avoidance absent | — | — | — | — | NOT STARTED | HIGH |
| Movement recording | NPC acting captures transforms/equipment/animations; three replay modes | /actor act/finish/play/mode | actor, record | Acting & Playback | recordings/ | IMPLEMENTED | HIGH |
| Legacy synchronized command surface | Removed in 0.1.6 at user request; NPC acting remains available | — | — | — | recordings/ | REMOVED | HIGH |
| Boat playback | Owned boat, passenger movement and cleanup | /actor play | actor, record | Acting & Playback | recordings/ | IMPLEMENTED | HIGH |
| Nicknames (0.1.5) | API-generated online real-player aliases, tab/nametag/death/quit display, reset one/all, expire on disconnect; skin preserved | /nickname; nick | identity, player.others | Command | nicknames.yml, state/identities.yml | IMPLEMENTED | HIGH |
| Identity blacklist | Blocks names/skins and purges matching actors/nicknames | nick blacklist | identity, admin | Command | state/identities.yml | IMPLEMENTED | HIGH |
| Player skins | Asynchronous account lookup or direct texture URL; auto/slim/classic model | skin | identity | Command | state/identities.yml | IMPLEMENTED | HIGH |
| PNG/NameMC | Upload/conversion provider and NameMC page resolver absent | — | — | — | — | NOT STARTED | HIGH |
| Kits (0.1.6) | Create/edit, self/player/wildcard/NPC claims, three access modes, portable YAML; actual operators manage/gift | kits; kit | per-kit policy; actual operator manages | Kits, details, editor | kits.yml, loadouts/, kit-exports/ | IMPLEMENTED | HIGH |
| Plugin kit imports (0.1.6) | Item-only adapters for installed PlayerKits2, legacy PlayerKits, EssentialsX and CMI; provider actions not copied; runtime acceptance pending | kits import/importall/cancelimport | actual operator | Import picker | kits.yml | IMPLEMENTED | HIGH |
| Live NPC identity / tab-list (0.1.5) | Change name/skin during recorded playback; persistent player NPC tab-list toggle | /actor set/randomize | actor | Appearance | actors/, guis.yml | IMPLEMENTED | HIGH |
| Live playback controls (0.1.5) | Mode edits after capture or during replay remain selected; Stop turns autoplay off and holds position | /actor mode/play/stop/autoplay | actor, record | Acting & Playback | actors/ | IMPLEMENTED | HIGH |
| Observer kit import | Public conversion claim, but no authoritative input schema | — | — | — | — | UNKNOWN | LOW |
| Warps | Named anchors, per-warp access, denied entries hidden in listing/completion | warp | warp, warp.edit | Warps | warps/ | IMPLEMENTED | HIGH |
| Custom spawn | Save/use; optional first-join/respawn routing and bypass | spawn | warp, warp.edit, spawn.bypass | Warps | config.yml, warps/ | IMPLEMENTED | HIGH |
| Item editing | Names/lore, enchantments, attributes, durability, amount, player/random heads | item | items | Item editor | items.yml | IMPLEMENTED | HIGH |
| Inventory inspection | Read-only player/ender copy | inventory view/ender | inventory | Viewer | — | IMPLEMENTED | HIGH |
| Rollback/restock/fill | Ten snapshots per player, exact-ID restore, replenish stacks, random container fill | inventory | inventory | Viewer, player | rollback/, items.yml | IMPLEMENTED | HIGH |
| Player state | Health/hunger/heal/feed, gamemode/flight/speed, invisibility/glow/fire/invulnerability | player | player, player.others | Player, command | state/players.yml | IMPLEMENTED | HIGH |
| Health/take reset | Health changes and take restoration exercised by actual client | player health; take | player, record | Player | In-memory takes | VERIFIED | HIGH |
| Restrictions | Freeze, hunger/durability, build/break/PvP, inventory/armor/pickup | player | player, player.others | Player | state/players.yml | IMPLEMENTED | HIGH |
| Half heart | Lethal damage including starvation prevented; one health point remains | player halfheart | player, player.others | Player | state/players.yml | IMPLEMENTED | HIGH |
| Keep inventory | Retain inventory/XP; configurable Curse of Vanishing consumption | player keepinv | player, player.others | Player | death.yml | IMPLEMENTED | HIGH |
| Potions/pause | Presets; freeze remaining effect durations and resume | player potion/pauseeffects | player, player.others | Player, command | potions.yml | IMPLEMENTED | HIGH |
| Vanish | Hide body/tab and join/quit; silent container/interaction effects absent | player vanish | player, see.vanish | Player | state/players.yml | PARTIAL | HIGH |
| Death policy | Normal/respawn/spectator/kick, radius, optional post-respawn scene | death | death, death.spectator, player.others | Command | death.yml, state/deaths.yml | IMPLEMENTED | HIGH |
| Totem/stasis | Visual pop; real item-carried pop count and delayed final teleport | item stasis; effect totem | items, effects | Effects, command | Item PDC | IMPLEMENTED | HIGH |
| Staff items | Silent kick stick, freeze rod, region wand; use-time authorization | item tool | items plus tool permission | Command | Item PDC | IMPLEMENTED | HIGH |
| Container/frame locks | Interaction/break/explosion/hopper/equipment restrictions and bypass | lock | locks, locks.bypass | Command | state/locks.yml | IMPLEMENTED | HIGH |
| Chat (0.1.5) | Operator-only block, literal filter, clear, chat/title broadcast, fake join/leave and white death announcement | chat | chat; current operator to speak while blocked | Production | messages.yml, moderation.yml | IMPLEMENTED | HIGH |
| Signs | Literal filtering and staff edit alerts | Configuration | moderation for alerts | Configuration | moderation.yml | IMPLEMENTED | HIGH |
| Recording sessions (0.1.6) | /es record on/off: MOTD and operator-only joining/reconnecting; take start/stop adds participant snapshots | record on/off; take start/stop | record, player.others for all | Production | recording.yml | IMPLEMENTED | HIGH |
| Server lock | Allowed names including never-joined players, permission bypass | server lock/allow/deny | moderation, server.bypass | Production | moderation.yml | IMPLEMENTED | HIGH |
| Dimension lock | Entry restrictions, bypass and allowlist | world lock/allow | world, world.bypass | World, command | moderation.yml | IMPLEMENTED | HIGH |
| Command blocker | Namespace-normalized blocked command roots, bypass | Configuration | commands.bypass | Configuration | moderation.yml | IMPLEMENTED | HIGH |
| Global protections | Separate build/break/PvP switches | server build/break/pvp | moderation | Command | moderation.yml | IMPLEMENTED | HIGH |
| World controls | Time/weather/top/world teleport; teleport to saved offline logout position | world; player otp | world, player | World, command | state/positions.yml | IMPLEMENTED | HIGH |
| Fake border | Per-player displayed border and reset | world border | world | World | In memory | IMPLEMENTED | HIGH |
| Region/chunk capture | Incremental blocks/container/sign save and restore; other specialized block entities omitted | region | world.edit | World, command | regions/, config.yml | PARTIAL | HIGH |
| Region restoration safety | Chest block/contents restored; malformed later inventory rejected before first mutation | region save/restore | world.edit | Command | regions/ | VERIFIED | HIGH |
| Chunk-ban shulker | Utility named publicly; precise trigger/ban semantics unclear; no equivalent implemented | — | — | — | — | UNKNOWN | MEDIUM |
| Teams | Shared scoreboard color/prefix/glow/friendly-fire/invisibility/name/collision | team | team | Teams | state/teams.yml | IMPLEMENTED | HIGH |
| Per-viewer teams | Individual viewer color/glow/visibility absent | — | — | — | — | NOT STARTED | HIGH |
| Villagers | Templates, professions, held-item trades, one-time trade exhaustion | villager | villager | Villagers, command | villagers/ | IMPLEMENTED | HIGH |
| Combat utilities | Bounded railgun/arrows/snowballs/rod/wolves/orbital; opt-in real explosion | effect | effects, destructive | Effects | effects.yml, config.yml | IMPLEMENTED | HIGH |
| Cleanup/distances | Radius/category cleanup excludes players/actors; item autoclear; view/send/simulation | world clean/limit | world, destructive for cleanup | World, command | config.yml | IMPLEMENTED | HIGH |
| Voice | Optional Simple Voice Chat mute/broadcast and session restoration; no audio recording | voice | voice | Production | recording.yml | IMPLEMENTED | HIGH |
| Feedback sounds | Global enabled flag and configurable success/error sounds; default off | Configuration | admin for reload | Menus/commands | messages.yml | IMPLEMENTED | HIGH |
| GUI safety | Holder-bound clicks/drags, pages, back, confirmations, expiring input; transfer/copy paths client tested | menu | use plus action nodes | All | guis.yml | IMPLEMENTED | HIGH |
| Timeline scenes | User-requested ordering, pause/resume/stop/reset, resource conflicts; eight concurrent scenes | /scene | scene.play, scene.edit, action nodes | Scenes, timeline | scenes/ | VERIFIED | HIGH |
| Composition | User-requested append/repeat, aliases and budgets | /scene append/repeat/bind | scene.edit | Timeline, command | scenes/, config.yml | IMPLEMENTED | HIGH |
| Repeated takes | User-requested player/entity/environment snapshots, pending offline/dead restores | take; /scene reset | record, scene.play | Player, timeline | pending/, memory | IMPLEMENTED | HIGH |
| Restart/disconnect | Equipped glowing actor and scene persist; disconnected performer's health restored; fixtures leave no jobs | /actor; /scene | actor, scene.play | Actor, timeline | actors/, scenes/, pending/ | VERIFIED | HIGH |
| Cinematic tools | Titles/actionbars/bossbars, sound/particles/lightning, spectator camera interpolation | effect/camera; /scene add | effects | Effects, timeline | effects.yml, scenes/ | IMPLEMENTED | HIGH |

## Behavioral acceptance and assumptions

[TESTING.md](TESTING.md) specifies setup, equivalent action, pass condition and reset for the major capabilities. Pass conditions concern visible behavior and resulting state, not source resemblance. IMPLEMENTED rows still need their multiplayer/visual acceptance checks before becoming VERIFIED.

Undocumented defaults are independently selected and configurable. PLAYER actors use Citizens; mob actors work without it. External skin upload services are not silently introduced. Vanilla clients render ordinary server entities, inventory menus, text and supported effects. Voice transport needs the Simple Voice Chat client mod. Server-side camera movement cannot control arbitrary shaders or guarantee smooth interpolation at low tick rates.

Known differences remain explicit: advanced navigation, image-based skin conversion, per-viewer teams, silent container effects, unknown import/chunk-ban semantics, specialized block entities and fine-grained feature switches. This release is usable and its core workflows are tested; it does not claim full reference parity or unconditional production certification.

