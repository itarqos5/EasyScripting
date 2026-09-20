# 0.2.0 engineering notes

Release record for **EasyScripting 0.2.0**. These notes describe the group following repairs, the weapon-paced melee rebuild and the new NPC self-preservation layer. Current service ownership is summarized in [ARCHITECTURE.md](ARCHITECTURE.md); the previous release record is [IMPLEMENTATION-0.1.8.md](IMPLEMENTATION-0.1.8.md).

## Delivered changes

| Concern | Implementation |
| --- | --- |
| Rows lining up with where the leader looked, not where they walked | Heading sampled from the leader's per-tick position delta instead of `Player#getVelocity`, which Bukkit never populates from walking input |
| Ragged, reshuffling formations | One shared lateral grid for every row, whole-slot centring of a partial last row, configurable column count, and places numbered over present, free members |
| Members freezing or stacking | Unreachable slots compress toward the anchor; a member with no numbered place waits instead of sharing slot zero |
| Formations stuttering and stragglers starved of paths | A resume margin above the arrival distance, and a path budget ranked by need rather than by iteration order |
| Weak, mistimed and unreachable melee | Attacks wait for the held weapon's own recharge, reach measured eye-to-hitbox, engaged members reassessed every two ticks, optional critical hops |
| Squads stacking on one enemy and swinging at corpses | Per-target attacker places around a ring, and immediate reallocation on an enemy's death |
| Shields answering only a falling mace | Ground-radius mace detection alongside the overhead case, with a sustained hold while the threat remains |
| NPCs fighting to the death with full supplies | Graded self preservation: break off and eat a carried golden apple, then throw a carried ender pearl |
| Combat movement reading as robotic | Sprint while closing and walk inside melee range, circle between swings, and back away still facing the enemy |

## Following and formations

`GroupTactics.movementHeading` now takes the distance the leader actually covered during the tick. `ActorGroupService.trackHeading` samples every group's leader position once per tick and feeds the delta in; the previous input, `Player#getVelocity`, is only written by knockback and explosions, so for a walking player it was almost always empty and the smoothing fell through to the leader's yaw. The formation therefore tracked head movement, or froze on the first yaw it observed. Heading inertia is 0.85 per tick, which is stable enough for a hundred members to turn as one body.

`trailingFormation` and the new `blockFormation` share a single `grid` helper. Every row is centred on the same lateral grid rather than on its own occupancy, so columns line up from row to row, and a partial last row is offset by whole slots. `groups.follow-columns` selects the width; `0` keeps the automatic squarest block. A Move order uses the centred variant anchored on the destination and oriented by the commanding player's yaw at the time of the order.

Slot numbers were previously `List#indexOf` over a list rebuilt from every member. A death, a hide or a recording reservation shifted every later index, so the whole formation reshuffled and members walked through one another; an actor missing from that list took `Math.max(0, -1)` and shared slot zero with its occupant. `ActorGroupService.layout` now numbers only members that are present and unreserved, caching the result per refresh. That removes the per-tick `indexOf` scan as well, which was quadratic in group size.

`slotGround` retries a slot that resolves to no standing surface by pulling it toward the anchor at 0.75, 0.5 and 0.25 before giving up, so a member whose square falls inside a wall compresses into the formation instead of stopping where it stands.

## Path budget and settling

`groups.follow-resume-margin` is added to the arrival distance to give a parked member a wider threshold before it walks again. It is expressed as a margin, not an absolute distance, specifically so that no previously valid `actor-ai.yml` can become invalid on upgrade; it is capped at the catch-up distance, past which the member is sprinting back into place anyway.

Path requests are no longer issued inline. `act` records a `PathRequest` and `dispatch` ranks the queue before spending `paths-per-tick`, with stopped members promoted above merely distant ones. Requests that would repeat an active path, or that are still inside their repath interval, never enter the queue and so cannot crowd it out. Previously the budget went to whichever member the round-robin cursor reached first, which starved stragglers in large groups.

## Melee

`combat.weapon-cooldown` gates each swing on `HumanEntity#getAttackCooldown` reaching full charge. A vanilla attack made before the weapon recharges deals a fraction of its damage, so the previous fixed-timer attacks were quietly weak. `groups.attack-cooldown-ticks` becomes a floor and its shipped default drops from 20 to 10; an `actor-ai.yml` carried over from an earlier release keeps its own value, so an existing server must lower it to see weapon-paced attacks. A bounded wait guards against an entity whose attack-strength ticker is not simulated: after 40 ticks the NPC swings regardless rather than stalling forever.

Reach is measured from the attacker's eye location to the nearest point of the target's bounding box, the way a player's reach is measured. Comparing foot positions denied hits on anything standing on a slab, a stair or the attacker's own head.

Engaged members run on a two-tick cadence rather than five, since a five-tick decision interval quantised swings into misses. `GroupTactics.engagementOffset` distributes the attackers sharing one target around a ring keyed to each attacker's own bearing, so a squad surrounds rather than stacking on the target's block and nobody has to run around the fight to reach a place. An `EntityDeathEvent` handler clears the dead entity from assignments, solo targets and group orders and forces an immediate reallocation, instead of leaving a squad swinging at a corpse until the next twenty-tick sweep.

## Shields, apples and pearls

`maceThreat` keeps the overhead smash case and adds a ground case within `combat.shield-ground-radius`, so a mace carried at the NPC's own level also raises the shield. When a hold expires the guard is refreshed while the mace holder is still a threat, up to `combat.shield-max-hold-ticks`, after which it drops so the NPC can fight back rather than blocking indefinitely.

The new `survival` section adds two graded reactions, both gated on the NPC actually carrying the item. At or below `heal-health` it sets a retreat, backs away `retreat-distance` and then eats a carried golden apple; at or below `escape-health` it throws a carried ender pearl away from the fight. `escape-health` may not exceed `heal-health`, which reload validation enforces: an NPC gaps before it runs.

Eating swaps the apple into the main hand, stows the weapon in the slot the apple came from, plays the real animation through `startUsingItem` and hands the consumption to Paper's `completeUsingActiveItem`, so the apple's own effects apply exactly as they would for a player and nothing is invented. A PLAYER actor's held slot is part of its own backpack, so the swap explicitly skips the held slot and an NPC already holding an apple eats it in place with no swap at all; the inverse swap writes the backpack before the hand, so the weapon exists in exactly one place throughout. A hit taken mid-meal interrupts it and returns the apple. If Paper does not simulate item use for a given entity the apple comes back untouched rather than being destroyed or exchanged for synthesized effects.

A landing ender pearl damages its thrower for 5 health, so an NPC that would die to its own pearl keeps fighting instead. Vanilla only teleports PLAYER actors; a mob actor throws the pearl and stays where it is.

## Combat movement

`groups.sprint-chase-distance` separates closing from finishing: an NPC sprints while further away than that and walks inside it, the way a player drops sprint before a hit to keep their knockback. Sprint state is now carried on the path request itself rather than inferred from whether the path was a follow.

While in reach and waiting on its weapon an NPC circles the target with `GroupTactics.circleOffset`, which rotates the attacker's own bearing at the radius it already holds, so a sidestep never gives up reach. A sidestep in progress is allowed to finish rather than being cancelled on the next two-tick decision. `survival.strafe-chance` and `survival.strafe-interval-ticks` set the frequency. Retreats path away at chase speed while the head keeps facing the enemy.

Ambient wandering yields to a raised shield, a potion burst, a meal or a retreat, so none of them can be interrupted partway through.

## Compatibility

No new runtime dependency is bundled. PLAYER actors still require a compatible Citizens installation. The public Java facade is unchanged, so 0.2.0 remains source-compatible with the documented 0.1 API. Eleven new `actor-ai.yml` keys and the new `survival` section are added to existing files by the ordinary comment/value inheritance on load; the only shipped default that changes meaning for a fresh install is `groups.attack-cooldown-ticks`.

## Validation

**171 unit tests**, production, test and smoke compilation against Paper 1.21.8. No Minecraft server or client was started for 0.2.0. The unit coverage is pure layout, allocation and configuration logic; Bukkit fixtures cannot exercise navigation, the attack-strength ticker, hitbox reach or the Paper item-use APIs behind eating. Live acceptance cases, including the ones specific to this release, are in [TESTING.md](TESTING.md).
