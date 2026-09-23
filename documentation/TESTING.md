# Testing and acceptance

Current release: **0.2.5**. Test results are version-specific. The current release used build-only validation; older server/client runs below are historical evidence.

## Unreleased death kicks, invisible identities and nickname coverage fixes

**195 unit tests pass**, adding coverage for the rejoin lockout clock (a full minute after the kick, extended but never shortened by a second death, forgotten the moment it elapses, and per player), for obfuscating an invisible player's messages (the hidden name scrambled while the rest of the cast stays readable, the whole message scrambled in `message` mode, the hover card and shift-click insertion cleared, an account name and its nickname hidden together, and the configured mode rejected when it is not one of the three), and for rewriting the hover card and shift-click insertion carried by a name inside a death or leave message. Production, test and smoke compilation pass against Paper 1.21.8. No Minecraft server was started.

The kick, the refused rejoin and the obfuscated rendering itself are server behavior no fixture reaches. These cases decide them:

* Die to a mob, to another player and to the void. Each time the death must resolve in full — damage, death message, dropped items — and the kick must arrive about a second later, not at the moment of the hit.
* Try to rejoin immediately after that kick. The server must refuse with a countdown, refuse again at 30 seconds, and let you in once the minute has passed. Confirm a second player's death does not affect your own wait.
* Disconnect deliberately in the second between the death and the kick. The lockout must still apply.
* Set `kick-on-death: false` and confirm ordinary deaths no longer kick, while a player set to `/es death kick` still is and still has to wait out the lockout.
* Drink an invisibility potion, then kill somebody and die yourself. Both messages must arrive scrambled, and hovering and shift-clicking the scrambled name must give nothing away. Repeat with `invisible-obfuscation: names` and confirm the other player's name and the rest of the sentence stay readable.
* Repeat while nicknamed, and confirm neither the nickname nor the account name can be read out of the message.
* Go invisible and disconnect: the leave message must be scrambled. Come back visible and confirm ordinary messages return.
* Nickname yourself, then die to a mob and to another player. Both the victim and the killer must read as the nickname, and **hovering** the name in the death message and **shift-clicking** it must show the nickname, not the account.
* Nickname yourself and disconnect normally, then repeat while being kicked. Both leave messages must use the nickname.
* Nickname yourself and check the skin changes to an unrelated public skin, that it is not your own account's skin, and that `/nickname <you> off` restores your real name and your real skin. Set `random-skin: false` and confirm the nickname applies with your own skin kept.
* Nickname yourself with the username API unreachable and with an empty skin pool; the nickname must still apply and the skin must simply stay as it was.

## 0.2.5 validation

**183 unit tests pass**, adding coverage for block-aligned line-ups (every member on its own block across awkward and cardinal yaws, front/behind symmetry, rejected counts and column widths), the new mace, falling and formation settings, melee accuracy falling off toward the edge of reach, formation places assigned by proximity with a preference for the place already held, per-file configuration fallback (every bundled default is itself a usable replacement, and one broken file does not condemn its neighbours), and releasing every dead username at once. Production, test and smoke compilation pass against Paper 1.21.8. No Minecraft server was started.

Artifact: `build/libs/EasyScripting-0.2.5.jar`; source companion: `build/libs/EasyScripting-0.2.5-sources.jar`; SHA-256: `0A8CCE08578402A694F793CD6CF95FE91FC71FA37C1345A86A38FA5AE383B4CC`; sources companion SHA-256: `33876F313703EA1BE66126E7B50CB224B131DC7DD5405F4DD3DEE6E42AAE0E4B`. The JAR carries `plugin.yml` version 0.2.5 and the `actor-ai.yml` with `combat.reach-accuracy` and without `combat.shield-ground-radius`. These hashes are from `gradlew clean build compileSmokeJava` on a clean checkout of the committed tree and reproduce across repeated builds; attach these exact artifacts to the v0.2.5 release.

Most of this batch is behaviour no fixture can reach. These cases decide it:

* Hold a mace in your **inventory** rather than your hand and jump above a shielded NPC; the shield must come up. Repeat with the mace held. Standing in front of the NPC with a mace on level ground must **not** raise the shield. With `shield-inventory-mace` off, only a held mace counts.
* Fight an NPC with a sword at the very edge of its reach and then close in: edge-of-reach swings must visibly miss far more often than close ones.
* Give an NPC several beneficial splash potions and hurt it. It must throw one, then stop while those effects are running, and throw again once they expire or are cleared with `/effect clear`. An unhurt NPC must not throw an instant-health potion.
* Fight an NPC down to its heal threshold and watch the break-off: it must turn and run, not walk backwards with its head turned.
* Drop NPCs from 30+ blocks. The fall must look and land like a player's, with ordinary fall damage, and they must resume moving on landing.
* Run `/es group lineup` with 1, 5, 23 and 100 members, facing each cardinal direction and diagonally, on flat ground, on stairs, indoors under a roof and beside a cliff. Every NPC must be on its own whole block, all facing your way; an unusable column must refuse the whole line-up and move nobody; indoors must not place anyone on the roof.
* Open the group GUI and use Attack with a target in range, out of range, in creative, and with Intelligence off. In-range targets must appear and work; the others must not be listed, and any refusal must print one sentence with no syntax help after it.
* Use both delete-all buttons and confirm the group survives a purge while its NPCs do not, and that `/actors` empties without touching group definitions.
* Kill an equipped, mortal NPC. Its kit must drop once — check nothing is duplicated against its backpack — and the death message must appear before the "left the game" line.

## 0.2.0 validation

**171 unit tests pass**, including the seven that previously covered group tactics plus new coverage for aligned rows and columns over a partial last row, requested and automatic column counts, the centred Move block, a movement heading that ignores head turns but follows sampled travel, attacker places around a shared target, sidesteps that keep the radius they started from, weapon-paced melee defaults, survival thresholds that gap before they run and leave more health than an ender pearl's own damage, a maximum shield hold that never falls below a single hold, and a resume margin that cannot invert or outgrow the catch-up distance. Production, test and smoke compilation pass against Paper 1.21.8. No Minecraft server was started.

Artifact: `build/libs/EasyScripting-0.2.0.jar`; source companion: `build/libs/EasyScripting-0.2.0-sources.jar`; SHA-256: `C1F4FE64E5F44EAC5B79FF6CDAFA800ADC95550011F25E2B922439C8A11338A0`; sources companion SHA-256: `61C1C23014DC8241BCC9201DC3F5BA220C1A559F2C9F9765DE2FC23EA802474E`. The JAR carries `plugin.yml` version 0.2.0 and the expanded `actor-ai.yml` including the new `survival` section. These hashes are from `gradlew clean build compileSmokeJava` on the committed tree and match the assets attached to the v0.2.0 release.

These are pure layout, allocation and configuration checks. Bukkit fixtures cannot exercise navigation, the attack-strength ticker or hitbox reach, so the acceptance cases below still decide whether the repairs feel right in a world. Four cases are specific to this release:

* Walk, stop, turn, strafe and sprint as a leader with groups of 4, 9, 25 and 100 members over level ground, stairs, slopes and through doorways. Verify the rows and columns stay aligned and behind the direction of travel rather than the direction of view, that a stopped leader looking around does not rotate the block, that a settled formation does not stutter between walking and standing, that killing a middle member closes the gap instead of reshuffling the group, that a slot inside a wall compresses toward the leader instead of stranding a member, and that stragglers still receive paths at 100 members. Repeat with `follow-columns` set to 1, 5 and 20.
* Fight equipped groups with swords, axes and maces on flat ground, on stairs and across slabs. Verify NPCs wait for the weapon to recharge and land full-damage hits, that critical hops occur at roughly the configured rate without the NPC pathing away mid-hop, that several attackers surround one enemy instead of stacking, that a killed enemy is reassigned within a tick or two, and that an enemy standing one block higher can be hit. Watch the movement too: NPCs should sprint in from a distance, walk the last few blocks, circle rather than freeze between swings, and finish a sidestep instead of twitching.
* Approach an NPC holding a mace, both from above and on the level, and confirm the shield comes up, stays up while you keep the mace out and drops once you leave or once the maximum hold elapses. **This is the case most worth running first:** `startUsingItem` and `completeUsingActiveItem` are Paper APIs whose behavior on a Citizens NPC entity is not covered by any fixture.
* Give an NPC golden apples and ender pearls, then fight it down through both thresholds with **Immortal OFF**. Verify it backs off and eats with the real animation, that the apple's effects actually apply and exactly one apple is consumed, that hitting it mid-meal interrupts it and returns the apple to its backpack with the weapon back in hand, that it pearls away and is teleported as a PLAYER actor, that it never pearls itself to death, and that an NPC carrying neither item simply keeps fighting. Check the backpack after each case: no item should be duplicated or lost. Repeat with a mob actor, where Paper may not simulate the item use — the apple must come back untouched rather than vanish.

## 0.1.8 validation

**160 unit tests pass.** New coverage includes generated-name length/letter/digit-or-underscore rules, bounded public-provider parsing, case-insensitive dead-user persistence/search/release, group shared settings/counter persistence, compact trailing formations, stable movement headings, directional friendly protection, filled disc/square layouts, per-column uniqueness, copy identity allocation and hidden nickname reservation. Existing scheduler, storage, command, GUI, recording, kit, combat and API suites also pass. Bukkit item/entity fixtures do not emulate a running server.

Production and smoke compilation pass against Paper 1.21.8; production compilation also passes against Paper 1.21.11. The baseline distributable emits Java 21 bytecode. Existing chat/login and newer-API passenger-retaining teleport deprecations remain. This evidence checks code and contracts; it does not prove native path appearance, Citizens rendering, provider uptime or production-scale performance.

Artifact: `build/libs/EasyScripting-0.1.8.jar`; source companion: `build/libs/EasyScripting-0.1.8-sources.jar`; SHA-256: `401043CA19B4B37C61586AA5376A9AABD15790142FA5629930C10B8C21189D07`. The JAR includes `actor-ai.yml`, `command-help.yml`, `items.yml`, `npc-identities.yml` and GUI schema 3. No Minecraft server or client was started for 0.1.8. Previous runtime results below apply only to their stated versions.

Live acceptance still required:

* Follow a moving/stopping/turning leader over level ground, stairs, slopes and obstacles with small and large groups. Verify compact rows remain behind movement direction, head turns while stopped do not rotate/cross the formation, ordinary pace looks player-like, sprint catch-up happens only when behind, distant members use paths without teleporting, and path budgets remain stable.
* Hit same-group NPCs as the real leader and confirm normal damage/knockback. Repeat attacks from a member NPC and a player acting as one, using melee, arrows, TNT/fangs, harmful splash and lingering clouds: teammates/leader must be protected while helpful effects and enemy damage work.
* Run every pattern shape in front of and behind both an assigned leader and creator fallback across uneven safe terrain. Confirm kit/group assignment, highest-safe-surface placement, full preflight and rollback, and clear errors for unloaded/duplicate/unsafe/out-of-border columns.
* Issue/copy/restart with a bound group actor tool. Verify monotonic `<group>-actor-N` IDs, kit/type/shared-Immortal application, cooldown, main-hand-only use, current-op enforcement, safe terrain and stale group/kit errors. Exercise shared Immortal/kit/identity GUI controls and confirm group deletion removes every member without retiring its name.
* Generate enough actors/nicknames to exercise public and fallback names. Verify 5–16 characters, at least one letter plus a digit/underscore, case-insensitive uniqueness, and exclusion of joined accounts, current operators, blacklisted names and Dead Users. Test provider timeout/offline startup and `/es reload` refresh without blocking actor creation.
* Kill mortal actors and nicknamed players, restart, browse/search/page the head GUI, and release with shift-right and `/deadusers remove`. Confirm natural deaths retire the exact displayed name, nicknamed players reset, released names become eligible, and manual actor/group deletion creates no retired entry.

## Reproducing the current build checks

With JDK 25, from the repository root on Windows:

```powershell
.\gradlew.bat build compileSmokeJava
.\gradlew.bat compileJava -PpaperVersion=1.21.11-R0.1-SNAPSHOT
.\gradlew.bat build compileSmokeJava
```

The final command rebuilds the distributable against the baseline 1.21.8 API after the compatibility compile. Use `./gradlew` on Linux/macOS. These commands do not start a Minecraft server. The HTML unit report is `build/reports/tests/test/index.html`; JUnit XML is under `build/test-results/test/`.

The released plugin is [EasyScripting-0.2.5.jar](https://github.com/itarqos5/EasyScripting/releases/download/v0.2.5/EasyScripting-0.2.5.jar), SHA-256 `0A8CCE08578402A694F793CD6CF95FE91FC71FA37C1345A86A38FA5AE383B4CC`. It contains the production descriptor and YAML resources, Java class major 65, and no smoke/Paper/Citizens classes. Sources are a separate release asset.

Current documentation checks cover all 14 Markdown files, local links/anchors, all 32 registered command groups, the public facade methods, permission declarations and configuration filenames. Old GUI/recording commands are retained only where explicitly describing history or migration.

## 0.1.7 validation

138 unit tests pass. Added regression checks for eye-origin facing, zero-distance orientation, balanced/stable assignments across 100 NPCs, unique formation positions, missing/unreachable target inputs, real group YAML persistence, leader authorization, AI/combat bounds, held-totem half-heart behavior, consumed-stack conservation, command shortcuts/contextual help, legacy flight-frame compatibility and actual glide-flag application. Tests also cover comment inheritance without changing user values and preservation of detached nested/inline comments through the asynchronous writer. GUI schema-3 slot checks and existing suites pass. The item and entity fixtures do not emulate a running server.

Production and smoke compilation pass against Paper 1.21.8; production compilation also passes against Paper 1.21.11. The distributable is rebuilt against the baseline and emits Java 21 bytecode. Existing chat/login and newer-API passenger-retaining teleport deprecations remain. Shield activation uses Paper's experimental `LivingEntity.startUsingItem`, isolated in ActorCombatService; API compilation does not prove that a particular Citizens adapter renders/blocks correctly.

Artifact: `build/libs/EasyScripting-0.1.7.jar`; source companion: `build/libs/EasyScripting-0.1.7-sources.jar`. The JAR contains `actor-ai.yml`, `command-help.yml` and GUI schema 3. No Minecraft server or client was started. Previous runtime results below apply only to their stated versions.

Live acceptance still required:

* On level terrain and around stairs/obstacles, use Wander and Walk to me; verify normal walking, eye-height tracking during movement, no roof/void destinations, and no drift away from home while alone. Ground sampling itself is not exercised against real blocks by the unit suite.
* Assign a real leader to a faction, test leader/non-leader/op controls, follow/hold/move, and hit multiple enemies. Verify actual native melee, knockback, split targeting, friendly melee/projectile/potion protection, leader disconnect/death, group battles and permanent NPC deletion. Profile two 100-member groups before claiming production capacity.
* Equip finite totems/potions/shields. Pop main/offhand totems and verify a one-tick refill without losing displaced items or creating new stacks. Verify three separate upward beneficial potion throws, real effects, misses/jumps and a delayed/non-guaranteed shield against a descending mace. Test both mob actors and matching Citizens PLAYER actors, external cancellations, reservations and shutdown.
* Break one configuration file on purpose (an unclosed quote in `actor-ai.yml`, or `limits.actors: 0` in `config.yml`) and run `/es reload`. The console must log that one file with its error, the server must keep running on the bundled copy of it, every other file must keep its own values, and the file on disk must be byte-for-byte unchanged. Rejoin as an operator: the join message must name the broken file. Fix the file and reload; the notice must stop.
* With a file broken, toggle a feature in `/es features` or edit a moderation setting. The save must be refused with a message rather than replacing the broken file with defaults.
* Open `/deadusers` as an operator with saved entries, use **Release every dead username**, and confirm. Every entry must disappear, `/deadusers clear` must report the same count, and a released name must be usable for a new NPC again. Repeat as a non-operator: the button must be absent.
* Enable `/es player halfheart`, suffer lethal damage with main/offhand totems, verify actual vanilla consumption/animation/effects and continued protection after the final totem. With no totem left, the lethal hit must still knock you back and play its hurt animation while leaving you on half a heart. Confirm cancelled PvP does not reduce health.
* Record a complete elytra flight and landing, finish with autoplay, inspect from another client in STOP/REPEAT/REVERSE modes, change skin during flight, take knockback, and stop midair. Confirm flight renders as elytra, swimming stays swimming, and stopping/restoration clears replay flight state. Old FALL_FLYING frames are supported; an old ambiguous swimming-only take must be recorded again.
* Browse `/actors`, `/kits`, NPC/group pages and Record session ON/OFF. Check selected highlights, de-op while a GUI is open, invalid syntax feedback, upgrade backups and preserved custom comments after reload/restart.

## Reproducing the 0.1.7 build checks

With JDK 25, from the repository root on Windows:

```powershell
.\gradlew.bat build compileSmokeJava
.\gradlew.bat compileJava -PpaperVersion=1.21.11-R0.1-SNAPSHOT
.\gradlew.bat build compileSmokeJava
```

The final command rebuilds the distributable against the baseline 1.21.8 API after the compatibility compile. Use `./gradlew` on Linux/macOS. These commands do not start a Minecraft server. The HTML unit report is `build/reports/tests/test/index.html`; JUnit XML is under `build/test-results/test/`.

The released plugin is [EasyScripting-0.1.7.jar](https://github.com/itarqos5/EasyScripting/releases/download/v0.1.7/EasyScripting-0.1.7.jar), SHA-256 `69f5af04262b1e3111d3eab609d655880ae027ae31bf5adf9040ec8c6aeeb434`. It contains the production descriptor and YAML resources, Java class major 65, and no smoke/Paper/Citizens classes. Sources are a separate release asset. No new runtime evidence is implied by a documentation-only commit.

The 0.1.7 documentation checks covered all 12 Markdown files, local links/anchors, all 31 registered command groups, the public facade methods, permission declarations and configuration filenames. Old GUI/recording commands were retained only where explicitly describing history or migration.

## 0.1.6 validation

109 unit tests pass. Added regression coverage for kit policy defaults, UUID-specific access, live de-op enforcement despite permission grants, legacy apply/list authorization, real disk save/export/import/reload preservation, closed-writer rejection, both claim argument orders, wildcard/actor syntax and ambiguous recipients. Bulk tests cover one kit per step, retained partial progress, cancellation, bounded error details and unique valid IDs. Additional tests cover nested command feedback/exception cleanup, session login policy, shared take retention, and GUI overlap/migration validation.

Build and smoke sources compile against Paper 1.21.8; production also compiles against Paper 1.21.11. The distributable is rebuilt against the baseline and produces Java 21 bytecode. Command-guide coverage checks account for all 30 registered groups plus help and 33 action types, with valid internal links. Project/JAR checks verify the descriptor, version and resources. Existing chat/login and newer-API passenger-teleport deprecations remain.

No Minecraft server/client was launched. Native multiplayer kit GUI behavior, actual installed-provider bulk imports, login/MOTD interaction with other plugins and permanent-deletion event ordering remain in-game acceptance checks. Unit policy/retention tests do not claim native-event coverage. Verify a non-op recipient cannot manage through old/open GUIs, a selected UUID retains access after a nickname change, wildcard claims reject busy/dead recipients before grants, and hide/unload/shutdown retain takes while final NPC deletion removes its take.

Artifact: `build/libs/EasyScripting-0.1.6.jar`. Source companion: `build/libs/EasyScripting-0.1.6-sources.jar`. Old movement command workflows below describe historical versions only; current `/es record` accepts on/off, and NPC movement capture uses actor act/finish.

## 0.1.5 validation

The build passes **88 unit tests**. New coverage includes repeated lethal damage remaining positive/nonlethal, one-time mortal-default migration, live playback cursor mode changes, operator-only chat policy, old message/GUI migrations, nickname collision/reset/reconnect/message/API parsing, preventing old takes from restoring expired aliases, and isolated PlayerKits2/CMI adapter contracts with item layout/overflow/clone checks. The item fixtures use a minimal ItemStack subclass; they do not claim to validate server item metadata serialization. The existing storage/scheduler/scene suites still pass.

Production sources compile against the shared Paper 1.21.8 API and the affected-server Paper 1.21.11 API. The distributable is rebuilt against 1.21.8 with Java 21 bytecode. `compileSmokeJava`, client-script syntax, project validation and JAR contents are checked. There are no bundled server/provider/protocol-library classes. The synchronous chat event has a known deprecation warning; it provides a main-thread, current operator check. Existing login-event deprecations remain.

No Minecraft server or client was launched. The previous integration results below apply only to their stated versions. Native hit/knockback rendering, Citizens refresh/tab-list behavior, graphical nickname rendering and imports against running PlayerKits 2/PlayerKits/EssentialsX/CMI installations remain runtime acceptance work. Adapter method names/signatures were checked against each provider's official source/API, and failures provide inventory-import fallback.

Artifact: `build/libs/EasyScripting-0.1.5.jar`. Updated `scripts/acting-tests.cjs` and the smoke companion cover the new default, live identity/mode edits and positive immortal damage, but were only compiled/syntax-checked.

Future acceptance checklist:

* Create a fresh NPC: Immortal OFF; first ordinary hit works. Enable Immortal and hit repeatedly through lethal damage: native hurt/knockback remain, no death. Turn Hittable OFF: block only melee/sweeps, allow projectiles/falls/explosions. Check with a mob and compatible Citizens PLAYER.
* Kill a mortal NPC during playback: one named leave announcement, no drops, permanent removal after restart. Finish/quit/die while acting and shut down with active actors: restore/cleanup without late task registration.
* Record, finish with autoplay, change highlighted STOP/REPEAT/REVERSE while playing, rename/reskin/reroll and toggle tab listing. The recording continues; armor, position and health survive refresh. Stop during refresh: gravity restores and autoplay stays OFF across restart.
* Nickname two real players, resolve/reset by account and alias, reject NPC targets, verify tab/nametag/chat/death/killer/quit display from both clients, then reconnect under account name. Reset-all during pending API work, disconnect mid-request, force API fallback, and join with an account matching somebody else's alias.
* Block chat, de-op a sender while retaining chat.bypass, send again: blocked. Operators can speak. Unblock: normal chat. Verify fake death is white and only an announcement; broadcast displays both chat and title.
* Create a blank kit, import inventory, edit/Save/Save & equip, close without saving, export/reimport with a new ID, test duplicate destination, custom footer collisions and permissions. Import named/enchant/PDC items, armor/offhand, explicit slots and oversized kits from each installed provider. Ensure imports never claim kits, charge money or execute commands.


## 0.1.4 validation

The build passes 64 unit tests, including scheduler shutdown/rejected-registration regressions, melee versus environmental/projectile damage policy, old-message defaults, invalid broadcast timing and GUI help migration. Compilation against Paper 1.21.11 (the version in the reported shutdown trace) passed. The final JAR targets the shared 1.21.8 API with Java 21 bytecode; the smoke source set also compiles. No test server or Minecraft client was started.

Future runtime checks: graceful shutdown with idle actors and active recording/playback; melee versus fall/wither-skull/explosion damage on both NPCs and acting players; permanent NPC deletion during playback/scenes and across restart; cancelled death events leaving actors/performances active; reused actor IDs after death; titles and mute/unmute announcements with old and customized YAML. NPC death now means deletion, so historical respawn-after-death checks below describe old versions only.

Artifact: `build/libs/EasyScripting-0.1.4.jar`.

## 0.1.3 validation

The 0.1.3 Gradle build passes all **53 unit tests**. Nine new tests cover knockback pause/blend timing, repeated hits, immutable vectors, invalid recovery durations, complete legacy GUI migration, preserved schema-2 customization, conflicting NPC/tab slots, timeline overlap and protected kit footer navigation. Bundled GUI material names are also checked against the Paper enum. Compilation against the Paper 26.2 build-123 API passed; the distributable is rebuilt against the shared 1.21.8 API with Java 21 bytecode.

No server or Minecraft client was launched for 0.1.3, following the requested build-only scope. Earlier integration results below apply to their stated versions. The optional client scripts have been updated for schema-2 slots and explicit manual playback, and the acting smoke fixture now checks performer damage immunity; these updated fixtures have not been run. Unit tests do not verify native knockback, Citizens physics, live autoplay or visual quality in Minecraft.

For future in-game acceptance, exercise these new scenarios on both a mob and a matching Citizens PLAYER NPC:

* Record with Autoplay ON, finish and observe the replay without a command. Repeat with OFF and with cancellation. Test STOP, REPEAT and REVERSE; explicit Stop must stay stopped.
* Try player melee/projectile damage and knockback during acting, then finish/cancel and confirm original health, invulnerability, equipment and profile restoration.
* During playback, attack a hittable mortal NPC: observe damage, knockback, route recovery, repeated hits, a hit near the final frame and death. Confirm Stop does not heal damage. Check Hittable OFF and Immortal ON separately.
* Restart/show/respawn a visible NPC with a saved recording and Autoplay ON. Check hidden actors, scene reservations, feature disable, missing recordings and shutdown; none may start an invalid or duplicate replay.
* Upgrade a customized legacy guis.yml, inspect the saved backup, navigate every menu and picker, and confirm schema-2 custom values persist after reload. Exercise copy-editor footer buttons, cancelling chat input and deletion confirmation.

The downloadable plugin is `build/libs/EasyScripting-0.1.3.jar`. The complete unit report is `build/reports/tests/test/index.html`.

## Historical server environment (0.1.0–0.1.2)

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

## Historical automated checks (0.1.0–0.1.2)

```sh
./gradlew build
./gradlew smokeJar
```

The commands, artifacts and counts in this historical section describe the named earlier versions. Use the current build instructions above for 0.2.5; do not infer a fresh live test from these results.

All 44 pure tests passed on 0.1.2. The original 24 cover timeline ordering, state transitions, cancellation inside an action, execution budgets, argument/identifier validation, malformed scene lists, round trips, repeat/append semantics, detached YAML, atomic writes and GUI layout rejection. Nine identity tests added in 0.1.1 cover 200 unique generated names, occupied names, blacklist filtering, alternate skins, mob behavior, bounded pool exhaustion and malformed YAML choices. Eleven checks added in 0.1.2 cover varied name endings, bounded tiny-pool fallback, all playback endpoint sequences (including single-frame and legacy reverse behavior), invalid playback inputs, and inheriting missing actor GUI controls while preserving custom entries. The unit test report is `build/reports/tests/test/index.html`.

For 0.1.2, the `smokeJar` task produced **EasyScripting-SmokeTests-0.1.2.jar**,  never included in the production JAR. Install it alongside EasyScripting only in a disposable test server and run `essmoke` from console. It creates 100 actors, tests ordering/pause/resume/cancellation/resource conflicts, deletes a referenced actor, tests forced-death permission denial and explicit authorization, restores a dead actor, and runs eight concurrent scenes. It removes its fixtures afterward. With compatible Citizens installed one actor uses the PLAYER backend. Without Citizens the suite uses mobs only. It grants the console a temporary destructive permission solely for the death fixture and removes it afterward. All 14 checks were rerun successfully on Paper 26.2 with 0.1.1.

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

The recorded 0.1.2 run used its matching production and smoke JARs on the disposable Paper 1.21.8 fixture, with exactly one copy of each plugin. Use Citizens 2.0.39 build 3938 for PLAYER actors on that server version. The companion `/esactingtest` command exists only in the test JAR.

```text
node scripts/acting-tests.cjs
# Wait for ACTING CLIENT PREPARE PASSED, then gracefully stop and restart the server.
node scripts/acting-tests.cjs verify
```

All 22 preparation checks and four restart checks passed. Preparation checks the section navigation and old-YAML defaults, Hittable/Immortal toggles with actual damage/death/respawn, same-tick Citizens rename equipment retention, NPC position/name/skin/costume adoption, conflicting scene/camera/actor rejection, recording contents, full performer restoration, final position/armor after STOP, visual-only hurt/fire cues, repeat/reverse boundaries, GUI cancellation and disconnect during acting. The restart phase checks deferred restoration, saved recording/mode selection and playback from disk, then removes its fixtures. Logs are `build/reports/integration/acting-client-0.1.2.log`, `acting-client-restart-0.1.2.log`, `acting-server-prepare-0.1.2.log` and `acting-server-restart-0.1.2.log`.

The acting fixture drives a known airborne/grounded path with server teleports. Mineflayer drops its chunk cache after Paper's profile-refresh respawn packet, so this test disables its terrain physics rather than recording a false fall through missing client terrain. Recorded positions and profiles are server-state assertions; this does not replace checking movement/skin rendering with two graphical clients. Unit checks verify the exact forward/backward frame order; the client checks confirm looping remains active across boundaries.

## Manual behavioral acceptance

Each row defines setup, equivalent action, pass condition and reset procedure. Use the same starting state when comparing with a legitimately obtained reference installation. Do not compare source code. These rows are current 0.2.5 acceptance procedures, not claims that all have been run. A future server run must use updated fixtures and the exact deployment stack.

| Feature | Setup | Action | Expected result / pass condition | Reset |
| --- | --- | --- | --- | --- |
| Actor creation | Loaded empty area; Citizens for PLAYER | Create mob/player, copy it, restart | Exactly one entity per visible actor; saved equipment/name/location retained; optional dependency absence has a clear error | Delete actors; verify chunk tickets released |
| Actor direction | Guard and performer within 3 blocks | Move, look, sneak, sprint, jump, swing | One intended movement/animation; no ambient AI fighting scene directions | Stop/reset scene |
| Actor damage | Give target 20 health, immortal on | Attack for 2, then lethal damage; set hittable off | Nonlethal hit once; lethal prevention distinct from complete hit cancellation | Restore health and flags |
| Actor death | Mortal actor, scene death action and destructive grant | Play with auto-restore on | Actor dies, announces leaving, retires displayed name and is permanently deleted; reset/restart cannot recreate it | Release dead name; delete fixture scene |
| Grounded mass patterns | Existing groups/kits, assigned leader plus creator fallback, uneven loaded terrain | Create all five shapes front/behind with 12 actors; force one invalid column | Filled compact layout, group/kit assignment and highest safe Y; invalid preflight makes none; creation failure rolls back batch | Delete fixture group (all members) |
| Group following | 20 members and real leader on pathable mixed terrain | Walk/sprint/stop/look/turn; let members fall far behind | Stable trailing rows, normal walk and bounded catch-up, head turns do not cross formation, no teleports | Hold and delete fixtures |
| Group shared/tool | Group, two kits, copied actor tools, restart | Apply shared Immortal/kit/identity; use both tools; delete group | Current/later members receive settings, one monotonic ID stream, safe surfaces, stale tool fails, deletion removes every member | Release any natural-death names; remove tools/kits |
| Scene ordering | Two same-tick health actions, later swing | Play, pause, resume, stop | Stable order, no progress while paused, cancellation prevents later actions | Reset/delete fixture |
| Scene conflict | Two scenes share actor or world | Play both | Second start rejected before mutations | Stop first |
| Scene edits | Two scenes with different aliases | Append and repeat bounded range | Source targets resolve correctly; no unbounded task creation; excessive size rejected | Remove fixture scenes |
| Takes | Costume, XP, potion, pose and location set | Snapshot, modify state/flags, reset twice | Original state restored repeatedly; changing a live item cannot alter snapshot | Discard take |
| Disconnect/respawn | Player target in running scene | Disconnect or die | Scene cancels; pending restore applies once when alive and online | Rejoin, verify pending file soft-deleted |
| World unload | Actor/scene in disposable secondary world | Request unload during take | Dependent scene stops; owned entities/jobs cleaned; no subsequent access to unloaded world | Reload world and respawn actors |
| Movement recording | Player wears costume, holds two items | Use actor act/finish for route/swing/boat; replay reverse/repeat | Recorded transforms/hand cues repeat; stop removes the owned boat, restores gravity and keeps current actor position/equipment | Stop playback; delete the fixture actor so its unshared take is removed |
| Acting and playback | PLAYER actor and two graphical clients, performer with saved costume/profile | Act, walk/jump/sneak/swing/change armor; finish; play each mode | Observer sees NPC identity during acting and restored identity afterward; STOP holds endpoint, REPEAT resets to start, REVERSE alternates direction; equipment and cues match | Stop playback; delete the fixture actor and its unshared take |
| Combat factions | Two mortal hittable factions, two real leaders, finite kits | Follow/hold/move, hit multiple enemies and fight factions; leader/member attack own side; disconnect leader | Correct authorization, split targeting, leader can hurt own NPCs, members cannot hurt leader/teammates, independent damage/deaths; no scripted winner | Hold, delete fixture groups |
| NPC supplies | Mortal NPC with extra totems, beneficial splash potions and shield | Pop held totem; trigger retaliation and overhead mace threat | Consumed stacks stay consumed, spare refill respects delay, potion throws are separate and shield reaction is fallible | Remove fixtures; restore AI tuning |
| Elytra replay | Two clients, NPC costume with elytra | Record flight/landing, finish, replay modes, refresh identity and stop midair | Actual flight animation, old flight-frame support, swimming preserved and flight state released on stop | Stop and delete fixture actor/take |
| Camera | Two clients; spectator path | Move camera for 80 ticks | Viewer follows smooth interpolation; other clients do not gain a player body; stop restores state | Camera stop |
| Kit GUI | Three diamonds and armor | Save, clear, apply; copy through editor; try shift/drag/number/drop/double clicks | Original kit remains intact, no GUI item escapes; intentional kit edits/copies require actual operator status | Delete kit and clear fixtures |
| Pagination/confirmation | More entries than content slots | Next/back, shift-right delete, cancel then confirm | Page bounds correct; cancel preserves definition; confirm removes only selected entry | Delete fixtures |
| Feature controls | Authorized admin plus ordinary player | Toggle feature, restart, click as unauthorized player | State persists; unauthorized user cannot toggle or invoke restricted action | Enable original groups |
| Permission overrides | Explicit custom grant and denied control user | Override scene.play/warp; test console-command without extra grant | Custom node respected; sensitive privilege cannot be made public through GUI | Restore permissions.yml |
| Nicknames/skins | Joined/op account history, two clients, public provider and forced fallback | Generate actor/nickname identities, lookup skin, blacklist a name | Names obey 5–16/letter/digit-or-underscore rule; joined/op/dead/current/blocked names excluded; provider failure leaves usable fallback | Nick reset; remove test blacklist |
| Dead usernames | Natural actor death, nicknamed-player death and manual/group deletion | Restart; list/search/page; shift-right and command remove | Only natural deaths retire exact displayed names; heads/details persist; search keeps paging; release permits reuse | Release fixtures |
| Warps/spawn | Two warps with different nodes | Tab/list as denied player; teleport; enable optional spawn routing | Hidden unauthorized anchors; correct position; bypass excludes operators from automatic routing | Delete fixture warps and restore spawn switches |
| Inventory rollback | Distinct before/after inventory | Save, clear, restore exact snapshot ID | Correct owner's contents restored; viewer cannot take inspection items | Clear fixture items |
| Player flags | Survival performer and observer | Freeze, halfheart, hunger, keepinv, build/break/PvP, armor/pickup locks | Each enabled restriction applies; disabling restores ordinary behavior; starvation cannot kill with halfheart | Turn flags off |
| Potion pause | Short speed effect | Pause, wait, resume | Duration resumes from captured remaining ticks; quit/disable removes indefinite replacement | Clear effects |
| Death policy | Set mode on performer | Die in normal, respawn, spectator and kick modes; test radius | Correct mode/message scope, no duplicate drops; configured scene runs after respawn with permission checks | Normal mode; scene off |
| Stasis totem | Tagged 3-pop totem and known destination | Trigger three genuine resurrections | Count remains on item; final pop schedules one teleport; no extra totem duplication | Remove test totems |
| Item tools | Held item and staff permissions | Rename/lore/enchant/attribute/durability; kickstick and rod | Correct metadata; lower-privilege holder cannot use staff effect; kick omits quit announcement | Replace test items and reset freeze |
| Container/frame locks | Container, hopper and item frame | Interact, break, dispense/equip, explode with/without bypass | Protected state survives unauthorized changes; bypass works | Unlock and remove fixtures |
| Chat/sign policies | Two clients, literal blocked phrase | Block, de-op sender, broadcast, fake messages, sign edit | Only current operators speak while blocked; templates/literal filtering/alerts work | Unblock and restore phrases |
| Session/voice | Two clients with voice mod and optional plugin | Start/end take with voice mute, use broadcast | Prior mute state restored; allowed broadcaster reaches connected voice users; no audio stored | Stop take, broadcast off |
| Server/dimension lock | Allowed and denied test accounts | Connect/teleport with ordinary lock on, then recording-session mode on | Ordinary allow-list/bypass works; recording mode still rejects every non-operator reconnect | Stop session and unlock |
| Region | Small set with chest and two-sided sign | Select/save, modify, restore/cancel | Block data, contents and sign text restored; bounds enforced; cancel stops further blocks | Restore fixture and remove it |
| Effects | Test world, no valuable structures | Visual effects then projectiles; deny destructive permission | Visual effects leave blocks intact; projectiles obey caps; destructive command denied without gates | Effect stop; repair test world |
| Teams | Two players and external scoreboard plugin | Create/join/color/glow/leave/delete | Basic team settings visible; glow restored on leave; check scoreboard compatibility | Delete test team |
| Villagers | Hold result/cost items | Create, add one-time trade, buy twice, respawn template | First trade succeeds, second exhausted; explicit template respawn creates a new actor/trade state | Delete villager template |
| Cleanup/distances | Mixed mobs/items/actors nearby | Cleanup category/radius; set view/send/simulation | Chosen category removed inside radius; actors/players retained; bounded distances applied | Restore original distances |
| Malformed settings | Copy valid config | Set GUI rows to 9 and reload | Error contains file/key/value/expected range; old runtime stays usable and bad file is preserved | Restore file and reload |
| Shutdown | Active scene, camera, recording and effect | Graceful stop then restart | Tasks cancelled, actors/boats removed, saves drained; no duplicate owned entities | Inspect logs/status |

## Remaining release gates

Run the multi-client visual, audio, world-unload and combat edge cases above on the exact deployment stack. The historical 100-actor creation smoke test does not benchmark the new group combat or establish a measured performance guarantee for 20 concurrent human players. Profile representative movement/recording and region jobs with Paper's bundled spark before raising limits. Validate screen appearance on a real Minecraft client; the protocol tests only inspect inventory state and packets.
