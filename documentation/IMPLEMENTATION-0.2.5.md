# 0.2.5 engineering notes

Release record for **EasyScripting 0.2.5**. These notes describe the follow-formation repairs, the distance-dependent melee accuracy, the reversal of ground-level shield detection, half-heart protection that keeps the hit, and configuration loading that survives a broken file. Current service ownership is summarized in [ARCHITECTURE.md](ARCHITECTURE.md); the previous release record is [IMPLEMENTATION-0.2.0.md](IMPLEMENTATION-0.2.0.md).

## Delivered changes

| Concern | Implementation |
| --- | --- |
| NPCs landing every blow from exactly their maximum reach | Accuracy interpolated from `combat.accuracy` at half reach to the new `combat.reach-accuracy` at the limit, applied on every melee path |
| NPCs turtling through an ordinary ground fight | Only a mace held overhead raises the shield; `combat.shield-ground-radius` is removed |
| Members walking to the wrong side of a turning formation | Places assigned by proximity, preferring the place a member already holds, instead of a fixed number |
| Settled members shuffling on the spot | A navigator that stopped inside the resume distance counts as settled, and one repath interval covers a completed path as well as a running one |
| NPCs standing still in fire, water or a fall | Only an `EntityDamageByEntityEvent` pauses navigation for knockback |
| Half-heart protection swallowing the whole attack | The lethal hit is kept and its damage zeroed rather than the event being cancelled |
| One broken configuration file disabling the plugin | Per-file read, completion and validation, with the jar's own copy answering for a file that fails and the file on disk left untouched |
| A broken file being invisible in game | `Settings#broken` drives an operator join notice, a `/es reload` report, and a refusal to save that file |
| Dead usernames only releasable one at a time | `/es deadusers clear` and an operator-only confirmation button in the browser |

## Melee accuracy

`groups.melee-reach` stays at vanilla's 3.0 blocks, measured eye-to-hitbox as 0.2.0 established. What changed is that reaching that distance no longer guarantees a hit. `ActorCombatService.hitChance` applies `combat.accuracy` in full from half the configured reach and closer, then interpolates linearly to `combat.reach-accuracy` at the limit itself; past the limit nothing improves again. The shipped edge accuracy is 0.25 against a close-range 0.82, so an NPC poking from maximum range mostly misses while close-quarters fighting stays as accurate as it was.

The roll also moved so that nothing escapes it. `ActorGroupService.melee` has a fallback branch used when no `ActorCombatService` is attached, and that branch previously called `LivingEntity#attack` with no accuracy check at all. Both paths now share the same pure `hitChance` function, which is what makes the setting mean one thing everywhere. A failed roll still swings; only the damage is withheld.

## Shields

0.2.0 added a ground case to `maceThreat` within `combat.shield-ground-radius`, reasoning that a mace is a threat wherever it is carried. In play that was wrong: an NPC fighting someone who simply holds a mace spent the fight behind its shield instead of fighting. 0.2.5 reverses it. Only a mace more than 1.5 blocks above the NPC, below 10 blocks, and within a 4-block horizontal radius counts — the box a falling smash can actually come from. The setting is removed rather than defaulted to zero, so the behaviour is the same on an existing server without anyone editing `actor-ai.yml`; a leftover key there is ignored. `shield-inventory-mace` and `shield-max-hold-ticks` are unchanged, so a mace swapped in from the backpack is still seen and the guard still drops once the threat leaves.

## Following

Formation places were numbered in roster order. That is stable, which was the 0.2.0 fix, but a stable number is the wrong thing to be stable about: when the formation rotates with the leader, the square carrying a given number moves to the other side of the block, and its member runs around the leader and through its neighbours to follow it. `GroupTactics.nearestSlots` pairs members to places greedily, cheapest first, with the place a member already holds discounted by the same 0.75 factor target allocation uses. `ActorGroupService.layout` computes the world position of every place once per refresh and feeds the squared distances in. A member whose distances are all unusable still receives a leftover place, so nobody is left without somewhere to stand.

Settled members shuffling on the spot had a different cause. Both Citizens and Paper finish a path a little short of its destination — Citizens at the `distanceMargin` of 0.6 the backend has configured since 0.1.7 — while `follow-arrival-distance` also ships at 0.6, so a member could stop at a distance the navigator considered arrived and the director considered still walking. The director then re-issued a path every few ticks for that last fraction of a block, restarting the walk cycle each time. A member that is not navigating now settles at the wider resume distance, `follow-arrival-distance` plus `follow-resume-margin`, instead of the arrival distance alone. That absorbs any navigator's margin without the plugin having to know what it is, and `request` honours one repath interval whether or not a path is still running.

Navigation pausing on damage was applied to every `EntityDamageEvent`. The pause exists so real knockback can carry, and fire, fall damage, drowning and cactus carry nobody anywhere; an NPC standing in those simply stopped for `groups.knockback-pause-ticks` each time it was hurt, which is the opposite of what it should do. Only `EntityDamageByEntityEvent` pauses now.

## Half heart

`/es player halfheart` cancelled the lethal `EntityDamageEvent`. Cancelling removes the entire attack: no knockback, no hurt animation, no hit sound, and no mace smash, so a fight involving a protected player stopped reading as a fight and the previous `playHurtAnimation` workaround only replaced the animation. The handler now calls `setDamage(0)`, which recalculates every dependent damage modifier to zero, and sets the health to half a heart. The blow lands and throws the player around; it simply costs nothing. The `no-pvp` flag still cancels, because there the attack genuinely should not have happened. `PlayerDeathEvent` remains a safety net.

## Configuration fault isolation

`Settings#load` read every file, then ran a single block of validation covering all of them, then committed. Any failure anywhere threw, `onEnable` caught it and disabled the plugin: a stray quote in `items.yml` took the whole plugin down.

Loading is now per file. `Settings#prepare` performs one file's completion from defaults, its migrations and its validation, and is the only place a file can be refused; the scattered checks for `config.yml` limits, `kits.yml`, `features.yml`, `recording.yml` and the menus moved into it. A file that throws is logged at `SEVERE` with its exception and answered in memory by `plugin.getResource`, the copy inside the jar. Falling back to a default that does not itself validate would be worse than failing, so that second `prepare` is deliberately left fatal, and a unit test asserts every shipped default is a usable replacement for itself.

Nothing is written back to a broken file. Broken files are excluded from the shipped-comment write-back and from the `config.yml` and `guis.yml` migrations, and `persist` refuses outright — without that, toggling a feature while `features.yml` was broken would have saved the jar's defaults over the owner's file, which is precisely what running on defaults is meant to prevent.

`Settings#broken` exposes the list. `ConfigAlerts` sends operators `<file>.yml file is broken, please read console.` on join, and `/es reload` names the affected files to whoever ran it. `prepare` and `FILES` are public so the isolation itself is unit-testable without a server; `items.yml` validation needs the live material and entity registries and is the one file those tests skip.

## Dead usernames

`DeadUserRegistry#clear` empties the registry and returns how many names it released, so an empty list reports as such rather than as a silent success. `/es deadusers clear` and the browser's **Release every dead username** button are both operator-gated; the button uses the existing confirmation menu and counts the whole registry rather than the current page, because an active search filter must not quietly leave entries behind.

## Compatibility

No new runtime dependency. PLAYER actors still require a compatible Citizens installation, and the public Java facade is unchanged, so 0.2.5 remains source-compatible with the documented 0.1 API. `combat.reach-accuracy` is added to an existing `actor-ai.yml` by the ordinary value inheritance on load. `combat.shield-ground-radius` is no longer read; an existing file keeps the key harmlessly. Behaviour that changes without any file being edited: shields stop answering a ground-level mace, melee misses more often at the edge of reach, and a configuration file that used to disable the plugin now falls back to the jar's copy, which may differ from what that file contained.

## Validation

**183 unit tests**, production, test and smoke compilation against Paper 1.21.8. No Minecraft server or client was started for 0.2.5. New coverage is the accuracy falloff, proximity slot assignment, per-file configuration fallback and the dead-username clear; as before, Bukkit fixtures cannot exercise navigation, the attack-strength ticker, hitbox reach or the Paper item-use APIs. Live acceptance cases, including the ones specific to this release, are in [TESTING.md](TESTING.md).
