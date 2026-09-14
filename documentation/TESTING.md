# Testing and acceptance

## Recorded environment

Tests ran locally on Windows using Oracle JDK 25.0.1, Gradle wrapper 9.3.1 and disposable worlds. The user explicitly accepted the Minecraft EULA for these local servers. All server listeners were bound to 127.0.0.1, with RCON/query disabled. The 1.21.8 fixture uses offline authentication solely to admit the local protocol client. The original compatibility matrix was recorded for 0.1.0; the targeted 0.1.1 reruns are detailed below.

| Target | Evidence |
| --- | --- |
| Paper 26.2 build 123 | Startup/shutdown, configuration reload rejection, persistence across restarts; 14 real-server smoke checks including Citizens 2.0.43 build 4250; 17 additional NPC identity/restart checks in 0.1.1 |
| Paper 1.21.8 build 60 | Startup without optional integrations; 13 real-server smoke checks and six restart/rejection checks in 0.1.0; protocol-client suite expanded from 13 to 17 checks and passed on 0.1.1 |
| Paper API 26.1.2 build 74 | Compilation passed; runtime not tested |
| Paper API 1.21.11 | Compilation passed; runtime not tested |
| Paper API 26.2 build 123 | Compilation passed |
| Purpur | Public Paper API compatibility intended; runtime not tested |
| Simple Voice Chat | API compilation only; no voice-client runtime verification |

This evidence is not a visual comparison of two running proprietary/reference plugins. Screenshots establish the behavioral requirements; equivalence checks still needing two Minecraft clients are listed below. Citizens fake players cannot substitute for a real inventory client.

Paper emitted Windows performance-counter/OSHI warnings unrelated to the plugin. The retained `PlayerLoginEvent` permission-aware admission handler causes Paper's configuration-phase re-entry warning; plugins requiring that re-entry API are a current compatibility limitation. Boat replay uses the shared passenger-retaining teleport flag, deprecated on newer Paper; the targeted compile matrix still includes it.

## Automated checks

```sh
./gradlew build
./gradlew smokeJar
```

All 44 pure tests passed on 0.1.2. The original 24 cover timeline ordering, state transitions, cancellation inside an action, execution budgets, argument/identifier validation, malformed scene lists, round trips, repeat/append semantics, detached YAML, atomic writes and GUI layout rejection. Nine identity tests added in 0.1.1 cover 200 unique generated names, occupied names, blacklist filtering, alternate skins, mob behavior, bounded pool exhaustion and malformed YAML choices. Eleven checks added in 0.1.2 cover varied name endings, bounded tiny-pool fallback, all playback endpoint sequences (including single-frame and legacy reverse behavior), invalid playback inputs, and inheriting missing actor GUI controls while preserving custom entries. The unit test report is `build/reports/tests/test/index.html`.

The `smokeJar` task produces **EasyScripting-SmokeTests-0.1.2.jar**, never included in the production JAR. Install it alongside EasyScripting only in a disposable test server and run `essmoke` from console. It creates 100 actors, tests ordering/pause/resume/cancellation/resource conflicts, deletes a referenced actor, tests forced-death permission denial and explicit authorization, restores a dead actor, and runs eight concurrent scenes. It removes its fixtures afterward. With compatible Citizens installed one actor uses the PLAYER backend. Without Citizens the suite uses mobs only. It grants the console a temporary destructive permission solely for the death fixture and removes it afterward. All 14 checks were rerun successfully on Paper 26.2 with 0.1.1.

### NPC identity and persistence checks (0.1.1)

With compatible Citizens and the smoke-test plugin installed, run `esidentitytest prepare` in the disposable server console. It creates `identity_fixture` and performs 12 checks: a generated username distinct from the ID, a chosen skin, actual resolved NPC texture matching the saved cache after each skin operation, independent name/skin edits, hidden-actor skin editing, and rerolling without changing the ID. It deliberately leaves one actor and saves expected identity data in the smoke plugin's folder.

After the prepare success message, gracefully stop and restart the server, then run `esidentitytest verify`. Five checks confirm the same name, skin owner, cached texture, texture on the actual NPC profile, and fixture removal. All 17 checks passed on Paper 26.2 build 123 with Citizens build 4250. These assert server-side profile data; they are not a visual two-client rendering test. Logs are `build/reports/integration/identity-prepare-0.1.1.log` and `identity-restart-smoke-0.1.1.log`.

### Protocol-client checks

For protocol inventory checks on the loopback Paper 1.21.8 fixture:

```text
npm install --prefix .runtime/bot --no-audit --no-fund mineflayer@4.39.0
node scripts/client-tests.cjs
```

Grant `op EasyTest` in that isolated server console before the test sequence. The script creates kit/actor fixtures, checks inventory contents and exits. It must not run against a production server: it clears the test player's inventory and intentionally creates kit copies. No user credentials are used. Mineflayer's upstream runtime did not support 26.2 during this verification.

The 17 passing client checks include item delivery, 54-slot studio, blocked shift-transfer, kit restore, health control, take health restore, kit listing, editor opening, no inventory consumption during copying, saved ghost slots becoming intentional loadout items, synchronized capture with looping reverse playback, region block restoration and region container-inventory restoration. Four checks added in 0.1.1 verify random mob names without Citizens, identity controls appearing with an older `guis.yml`, the GUI identity report, and GUI randomization retaining the actor ID. The post-test status returned zero actors and zero active jobs. Output is retained in `build/reports/integration/client-tests-0.1.1.log`.

`node scripts/restart-tests.cjs prepare` creates an equipped/glowing actor and starts a player scene, then disconnects while the player's health is changed. Stop the disposable server, copy `scripts/fixtures/malformed-region.yml` to its `plugins/EasyScripting/regions/malformed_region.yml`, restart, then run `node scripts/restart-tests.cjs verify`. The fixture assumes the provided loopback world's name `easy-test-world`; adjust only its world name for another disposable world. Six assertions passed: restored player health, exactly one equipped actor, persisted glow, persisted scene definition, invalid inventory rejection before the first block mutation, and complete fixture/job cleanup. Natural regeneration was excluded by setting food to 10 during preparation.

Paper 26.2 also loaded a valid scene while rejecting a separate file whose action list contained a scalar. It logged the exact file and action index and preserved the malformed file. Logs and an artifact fingerprint are retained under `build/reports/integration/`.

### Acting, GUI sections and playback checks (0.1.2)

Install the current production and smoke JARs on the disposable Paper 1.21.8 fixture, with exactly one copy of each plugin. Use Citizens 2.0.39 build 3938 for PLAYER actors on that server version. The companion `/esactingtest` command exists only in the test JAR.

```text
node scripts/acting-tests.cjs
# Wait for ACTING CLIENT PREPARE PASSED, then gracefully stop and restart the server.
node scripts/acting-tests.cjs verify
```

All 22 preparation checks and four restart checks passed. Preparation checks the section navigation and old-YAML defaults, Hittable/Immortal toggles with actual damage/death/respawn, same-tick Citizens rename equipment retention, NPC position/name/skin/costume adoption, conflicting scene/camera/actor rejection, recording contents, full performer restoration, final position/armor after STOP, visual-only hurt/fire cues, repeat/reverse boundaries, GUI cancellation and disconnect during acting. The restart phase checks deferred restoration, saved recording/mode selection and playback from disk, then removes its fixtures. Logs are `build/reports/integration/acting-client-0.1.2.log`, `acting-client-restart-0.1.2.log`, `acting-server-prepare-0.1.2.log` and `acting-server-restart-0.1.2.log`.

The acting fixture drives a known airborne/grounded path with server teleports. Mineflayer drops its chunk cache after Paper's profile-refresh respawn packet, so this test disables its terrain physics rather than recording a false fall through missing client terrain. Recorded positions and profiles are server-state assertions; this does not replace checking movement/skin rendering with two graphical clients. Unit checks verify the exact forward/backward frame order; the client checks confirm looping remains active across boundaries.

## Manual behavioral acceptance

Each row defines setup, equivalent action, pass condition and reset procedure. Use the same starting state when comparing with a legitimately obtained reference installation. Do not compare source code. These rows are acceptance procedures, not claims that all have been run.

| Feature | Setup | Action | Expected result / pass condition | Reset |
| --- | --- | --- | --- | --- |
| Actor creation | Loaded empty area; Citizens for PLAYER | Create mob/player, copy it, restart | Exactly one entity per visible actor; saved equipment/name/location retained; optional dependency absence has a clear error | Delete actors; verify chunk tickets released |
| Actor direction | Guard and performer within 3 blocks | Move, look, sneak, sprint, jump, swing | One intended movement/animation; no ambient AI fighting scene directions | Stop/reset scene |
| Actor damage | Give target 20 health, immortal on | Attack for 2, then lethal damage; set hittable off | Nonlethal hit once; lethal prevention distinct from complete hit cancellation | Restore health and flags |
| Actor death | Scene with explicit death action and destructive grant | Play with auto-restore on | Actor dies, a replacement is restored to initial transform/equipment/health; scene completes once | Delete scene/actor |
| Groups/patterns | Empty loaded area, cap sufficient | Create each shape with 12 actors; hide/show group | Deterministic count/layout; group operations affect only members | Delete group members |
| Scene ordering | Two same-tick health actions, later swing | Play, pause, resume, stop | Stable order, no progress while paused, cancellation prevents later actions | Reset/delete fixture |
| Scene conflict | Two scenes share actor or world | Play both | Second start rejected before mutations | Stop first |
| Scene edits | Two scenes with different aliases | Append and repeat bounded range | Source targets resolve correctly; no unbounded task creation; excessive size rejected | Remove fixture scenes |
| Takes | Costume, XP, potion, pose and location set | Snapshot, modify state/flags, reset twice | Original state restored repeatedly; changing a live item cannot alter snapshot | Discard take |
| Disconnect/respawn | Player target in running scene | Disconnect or die | Scene cancels; pending restore applies once when alive and online | Rejoin, verify pending file soft-deleted |
| World unload | Actor/scene in disposable secondary world | Request unload during take | Dependent scene stops; owned entities/jobs cleaned; no subsequent access to unloaded world | Reload world and respawn actors |
| Movement recording | Player wears costume, holds two items | Record route/swing/boat; replay reverse and loop | Recorded transforms/hand cues repeat; stop removes owned boat and restores actor | Stop playback; delete recording |
| Acting and playback | PLAYER actor and two graphical clients, performer with saved costume/profile | Act, walk/jump/sneak/swing/change armor; finish; play each mode | Observer sees NPC identity during acting and restored identity afterward; STOP holds endpoint, REPEAT resets to start, REVERSE alternates direction; equipment and cues match | Stop playback; delete actor/recording |
| Camera | Two clients; spectator path | Move camera for 80 ticks | Viewer follows smooth interpolation; other clients do not gain a player body; stop restores state | Camera stop |
| Kit GUI | Three diamonds and armor | Save, clear, apply; copy through editor; try shift/drag/number/drop/double clicks | Original kit remains intact, no GUI item escapes; intentional copies require kit.edit | Delete kit and clear fixtures |
| Pagination/confirmation | More entries than content slots | Next/back, shift-right delete, cancel then confirm | Page bounds correct; cancel preserves definition; confirm removes only selected entry | Delete fixtures |
| Feature controls | Authorized admin plus ordinary player | Toggle feature, restart, click as unauthorized player | State persists; unauthorized user cannot toggle or invoke restricted action | Enable original groups |
| Permission overrides | Explicit custom grant and denied control user | Override scene.play/warp; test console-command without extra grant | Custom node respected; sensitive privilege cannot be made public through GUI | Restore permissions.yml |
| Nicknames/skins | Two clients with distinct identities | Set/random/reset names, lookup skin, blacklist a name | No name collision; blacklist purges affected actors/nicknames; failed lookup leaves usable state | Nick reset; remove test blacklist |
| Warps/spawn | Two warps with different nodes | Tab/list as denied player; teleport; enable optional spawn routing | Hidden unauthorized anchors; correct position; bypass excludes operators from automatic routing | Delete fixture warps and restore spawn switches |
| Inventory rollback | Distinct before/after inventory | Save, clear, restore exact snapshot ID | Correct owner's contents restored; viewer cannot take inspection items | Clear fixture items |
| Player flags | Survival performer and observer | Freeze, halfheart, hunger, keepinv, build/break/PvP, armor/pickup locks | Each enabled restriction applies; disabling restores ordinary behavior; starvation cannot kill with halfheart | Turn flags off |
| Potion pause | Short speed effect | Pause, wait, resume | Duration resumes from captured remaining ticks; quit/disable removes indefinite replacement | Clear effects |
| Death policy | Set mode on performer | Die in normal, respawn, spectator and kick modes; test radius | Correct mode/message scope, no duplicate drops; configured scene runs after respawn with permission checks | Normal mode; scene off |
| Stasis totem | Tagged 3-pop totem and known destination | Trigger three genuine resurrections | Count remains on item; final pop schedules one teleport; no extra totem duplication | Remove test totems |
| Item tools | Held item and staff permissions | Rename/lore/enchant/attribute/durability; kickstick and rod | Correct metadata; lower-privilege holder cannot use staff effect; kick omits quit announcement | Replace test items and reset freeze |
| Container/frame locks | Container, hopper and item frame | Interact, break, dispense/equip, explode with/without bypass | Protected state survives unauthorized changes; bypass works | Unlock and remove fixtures |
| Chat/sign policies | Two clients, literal blocked phrase | Mute, broadcast, fake messages, sign edit | Intended viewers receive template; forbidden literal is blocked; staff alert fires | Unmute and restore phrases |
| Session/voice | Two clients with voice mod and optional plugin | Start/end take with voice mute, use broadcast | Prior mute state restored; allowed broadcaster reaches connected voice users; no audio stored | Stop take, broadcast off |
| Server/dimension lock | Allowed and denied test accounts | Connect/teleport with lock on | Denied account rejected; configured name/bypass admitted; no world mutation | Unlock |
| Region | Small set with chest and two-sided sign | Select/save, modify, restore/cancel | Block data, contents and sign text restored; bounds enforced; cancel stops further blocks | Restore fixture and remove it |
| Effects | Test world, no valuable structures | Visual effects then projectiles; deny destructive permission | Visual effects leave blocks intact; projectiles obey caps; destructive command denied without gates | Effect stop; repair test world |
| Teams | Two players and external scoreboard plugin | Create/join/color/glow/leave/delete | Basic team settings visible; glow restored on leave; check scoreboard compatibility | Delete test team |
| Villagers | Hold result/cost items | Create, add one-time trade, buy twice, respawn template | First trade succeeds, second exhausted; explicit template respawn creates a new actor/trade state | Delete villager template |
| Cleanup/distances | Mixed mobs/items/actors nearby | Cleanup category/radius; set view/send/simulation | Chosen category removed inside radius; actors/players retained; bounded distances applied | Restore original distances |
| Malformed settings | Copy valid config | Set GUI rows to 9 and reload | Error contains file/key/value/expected range; old runtime stays usable and bad file is preserved | Restore file and reload |
| Shutdown | Active scene, camera, recording and effect | Graceful stop then restart | Tasks cancelled, actors/boats removed, saves drained; no duplicate owned entities | Inspect logs/status |

## Remaining release gates

Run the multi-client visual, audio, world-unload and combat edge cases above on the exact deployment stack. A 100-actor smoke test establishes functionality, not a measured performance guarantee for 20 concurrent human players. Profile representative movement/recording and region jobs with Paper's bundled spark before raising limits. Validate screen appearance on a real Minecraft client; the protocol tests only inspect inventory state and packets.
