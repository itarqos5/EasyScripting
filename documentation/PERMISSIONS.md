# Permissions

Permission reference for **EasyScripting 0.1.8**, checked against `plugin.yml`, command registration and the live access policies.

Acting as an NPC and playing its selected recording require both `easyscripting.actor` and `easyscripting.record`. Finish/cancel restore the current performer's own session. Actor GUI sections and Hittable/Immortal toggles use `easyscripting.actor`; all buttons still dispatch permission-checked commands.

Every command requires `easyscripting.use`. Permission checks apply to GUI actions too. Most production nodes default to operators. Kit/group management, mass equipped actor creation, bound-tool issue/use, kit gifting and sending blocked public chat check actual current operator status; granting a permission node cannot bypass these operator rules.

| Node | Default | Capability |
| --- | --- | --- |
| `easyscripting.use` | true | Open the studio and view status |
| `easyscripting.admin` | op | Reload settings and edit feature and access policies |
| `easyscripting.actor` | op | Create and direct actors |
| `easyscripting.scene.play` | op | Play and control scenes |
| `easyscripting.scene.edit` | op | Create and edit scenes |
| `easyscripting.record` | op | NPC capture/play/stop/autoplay/take selection and server recording sessions; player take snapshots |
| `easyscripting.player` | op | Change own player state |
| `easyscripting.player.others` | op | Change other players |
| `easyscripting.identity` | op | Set temporary names and skins; use `/nickname`; browse/search/release `/deadusers` entries |
| `easyscripting.kit` | true | Compatibility node; browsing is public and each kit's access policy controls self-claims |
| `easyscripting.kit.edit` | op | Compatibility node; management, access changes, importing and gifting require actual operator status |
| `easyscripting.warp` | op | Use permitted warps and spawn |
| `easyscripting.warp.edit` | op | Save and delete warps |
| `easyscripting.warp.admin` | op | Use operator-access warps |
| `easyscripting.spawn.bypass` | op | Bypass automatic custom spawn routing |
| `easyscripting.items` | op | Create and edit items |
| `easyscripting.inventory` | op | Inspect save and restore inventories |
| `easyscripting.locks` | op | Lock containers and item frames |
| `easyscripting.locks.bypass` | op | Bypass production locks |
| `easyscripting.death` | op | Set player death behavior |
| `easyscripting.death.spectator` | op | Allow automatic spectator mode after death |
| `easyscripting.chat` | op | Manage production chat |
| `easyscripting.chat.bypass` | op | Bypass recording-session chat suppression; never bypass /es chat block |
| `easyscripting.world` | op | Set environment and view limits |
| `easyscripting.world.edit` | op | Capture and restore selected blocks |
| `easyscripting.world.bypass` | op | Bypass dimension and build restrictions |
| `easyscripting.team` | op | Manage production teams |
| `easyscripting.villager` | op | Create and edit villagers |
| `easyscripting.effects` | op | Trigger visual and combat effects |
| `easyscripting.voice` | op | Mute or broadcast voice chat |
| `easyscripting.moderation` | op | Use moderation tools and receive sign alerts |
| `easyscripting.server.bypass` | op | Bypass the ordinary server lock; never bypass the operator-only recording-session admission rule |
| `easyscripting.commands.bypass` | op | Bypass blocked commands |
| `easyscripting.commands.player` | false | Run explicitly enabled player command actions |
| `easyscripting.commands.console` | false | Run explicitly enabled console command actions |
| `easyscripting.destructive` | false | Trigger opt-in destructive effects and entity cleanup |
| `easyscripting.see.vanish` | op | See vanished players |

`op` means granted to server operators by default; `false` needs an explicit grant even for operators; `true` is available to everyone unless denied. The console is trusted by Bukkit. No wildcard parent grants are installed by EasyScripting.

## NPC group access

`/es group` requires `easyscripting.use`. Current operators create/delete groups, manage membership, assign leaders, change Intelligence, set shared Immortal/kit values, randomize all identities and issue/use bound group actor tools. `/actor pattern` also hard-checks operator status because every created member receives a kit. A group's assigned real-player leader may view its GUI and issue follow/hold/stop/move/attack/fight orders. Other players cannot order it. One real player leads at most one group. `/actors` opens the actor library and `/kits` the kit library; their controls retain their existing checks.

Legacy actor editing/bulk commands remain staff tools under `easyscripting.actor`, including actor group tags. Do not grant actor administration to ordinary group leaders. A tool copied from an operator does not grant its holder use rights: the click checks current operator status again. No new group wildcard or privilege bypass is installed.

## Additional checks

Scene execution requires the permission for **every action**, and another real player requires `easyscripting.player.others`. Command actions additionally need `security.allow-command-actions: true`. Destructive explosions additionally need `security.destructive-effects: true`. The scene editor can store an action it cannot execute; this does not bypass execution permissions.

The inventory permission grants inspection, duplication through restocking, rollback and inventory changes. Kit editing and item utilities can create items. These are production staff capabilities, not ordinary survival-player permissions.

## Editable feature access

`permissions.yml` can override the following suffixes: actor, scene.play, scene.edit, record, player, identity, warp, warp.edit, items, inventory, locks, death, chat, world, world.edit, team, villager, effects and voice. Legacy kit/kit.edit overrides are ignored; kit access has its own controls.

Example:

```yaml
overrides:
  scene:
    play: production.director
  warp: everyone
```

Use `/es permissions scene.play production.director` or the permission GUI. Admin, destructive, command execution and bypass nodes cannot be overridden to `everyone` here. Warp-specific permission rules remain additional checks. Event-driven bypass checks use their explicitly documented nodes.

To delegate a director role, grant use, scene.play, scene.edit, actor, record, player, effects and other feature nodes they need. Kit management still requires an operator. Grant player.others only when that director may control other performers. Grant command/destructive nodes individually when required.

Changing only an NPC playback mode requires `actor`; selecting a take also requires `record`. Group leaders do not gain actor editing or kit management. `/es server` management uses `admin`; the separate `moderation` node covers staff moderation tools/alerts.

Autoplay configuration requires both `easyscripting.actor` and `easyscripting.record`, like manual NPC playback. Runtime autoplay follows the saved actor configuration and feature switches; it does not execute commands or use a logged-out director's permissions. Hittable/Immortal changes require `easyscripting.actor` and are allowed during replay.

`/nickname` requires `identity`; targeting another real player or `/nickname off` additionally requires `player.others`. Resets remain available when the identity feature is disabled. Nicknames cannot target Citizens NPCs. `/deadusers` list/search/remove also uses `identity`; that permission defaults to operators but may be changed through the ordinary editable identity access rule. No separate dead-users permission exists.

## Kit access

Actual operators can create, edit, delete, import/export, change access and give kits to another player, every online player or an actor. Non-operators can browse and self-claim only kits whose policy allows them. Policies are **Operators only**, **Everyone**, or **One selected UUID + operators**. New/legacy/provider-imported kits default to Operators only; an EasyScripting export carries its saved policy. Changes remain effective after nickname changes/reconnects. Switching away from specific-player access clears the previous UUID.

`/es kits claim` and the older `/es kit apply` enforce the same policy. Operator gifting intentionally bypasses the recipient's self-claim restriction. GUI clicks recheck operator status and policies; a menu opened while op does not retain management rights after de-op. Neither `kit.edit` grants nor `permissions.yml` overrides can make a non-operator a kit manager. See [kit commands and examples](COMMANDS.md#kits).
