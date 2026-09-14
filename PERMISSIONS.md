# Permissions

Acting as an NPC and playing its selected recording require both `easyscripting.actor` and `easyscripting.record`. Finish/cancel restore the current performer's own session. Actor GUI sections and Hittable/Immortal toggles use `easyscripting.actor`; all buttons still dispatch permission-checked commands.

Every command requires `easyscripting.use`. Permission checks apply to GUI actions too. A permission plugin may grant or deny these nodes; operator status is only the default policy.

| Node | Default | Capability |
| --- | --- | --- |
| `easyscripting.use` | true | Open the studio and view status |
| `easyscripting.admin` | op | Reload settings and edit feature and access policies |
| `easyscripting.actor` | op | Create and direct actors |
| `easyscripting.scene.play` | op | Play and control scenes |
| `easyscripting.scene.edit` | op | Create and edit scenes |
| `easyscripting.record` | op | Record movement and manage takes |
| `easyscripting.player` | op | Change own player state |
| `easyscripting.player.others` | op | Change other players |
| `easyscripting.identity` | op | Set temporary names and skins |
| `easyscripting.kit` | op | List and apply kits |
| `easyscripting.kit.edit` | op | Save edit and delete kits |
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
| `easyscripting.chat.bypass` | op | Talk while production chat is muted |
| `easyscripting.world` | op | Set environment and view limits |
| `easyscripting.world.edit` | op | Capture and restore selected blocks |
| `easyscripting.world.bypass` | op | Bypass dimension and build restrictions |
| `easyscripting.team` | op | Manage production teams |
| `easyscripting.villager` | op | Create and edit villagers |
| `easyscripting.effects` | op | Trigger visual and combat effects |
| `easyscripting.voice` | op | Mute or broadcast voice chat |
| `easyscripting.moderation` | op | Use moderation tools and receive sign alerts |
| `easyscripting.server.bypass` | op | Join locked recording servers |
| `easyscripting.commands.bypass` | op | Bypass blocked commands |
| `easyscripting.commands.player` | false | Run explicitly enabled player command actions |
| `easyscripting.commands.console` | false | Run explicitly enabled console command actions |
| `easyscripting.destructive` | false | Trigger opt-in destructive effects and entity cleanup |
| `easyscripting.see.vanish` | op | See vanished players |

`op` means granted to server operators by default; `false` needs an explicit grant even for operators; `true` is available to everyone unless denied. The console is trusted by Bukkit. No wildcard parent grants are installed by EasyScripting.

## Additional checks

Scene execution requires the permission for **every action**, and another real player requires `easyscripting.player.others`. Command actions additionally need `security.allow-command-actions: true`. Destructive explosions additionally need `security.destructive-effects: true`. The scene editor can store an action it cannot execute; this does not bypass execution permissions.

The inventory permission grants inspection, duplication through restocking, rollback and inventory changes. Kit editing and item utilities can create items. These are production staff capabilities, not ordinary survival-player permissions.

## Editable feature access

`permissions.yml` can override the following suffixes: actor, scene.play, scene.edit, record, player, identity, kit, kit.edit, warp, warp.edit, items, inventory, locks, death, chat, world, world.edit, team, villager, effects and voice.

Example:

```yaml
overrides:
  scene:
    play: production.director
  warp: everyone
```

Use `/es permissions scene.play production.director` or the permission GUI. Admin, destructive, command execution and bypass nodes cannot be overridden to `everyone` here. Warp-specific permission rules remain additional checks. Event-driven bypass checks use their explicitly documented nodes.

To delegate a director role, grant use, scene.play, scene.edit, actor, record, kit, kit.edit, player, effects and the other feature nodes they need. Grant player.others only when that director may control other performers. Grant command/destructive nodes individually when the production requires them.
