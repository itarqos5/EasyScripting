package dev.easyscripting.integration;

import dev.easyscripting.actors.*;
import dev.easyscripting.players.EntitySnapshot;
import java.util.*;
import java.util.function.Consumer;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.npc.*;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** All Citizens references are confined here, so missing Citizens cannot break core startup. */
public final class CitizensBackend implements ActorBackend, Listener {
  private final NPCRegistry registry = CitizensAPI.createInMemoryNPCRegistry("EasyScripting");
  private final Map<NPC, Consumer<LivingEntity>> bindings = new IdentityHashMap<>();
  private final Map<NPC, EntitySnapshot> refreshing = new IdentityHashMap<>();

  public CitizensBackend(JavaPlugin plugin) {
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void despawning(NPCDespawnEvent event) {
    NPC npc = event.getNPC();
    if (event.getReason() == DespawnReason.PENDING_RESPAWN
        && bindings.containsKey(npc)
        && npc.getEntity() instanceof LivingEntity e
        && !e.isDead()) refreshing.put(npc, EntitySnapshot.capture(e));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void spawned(NPCSpawnEvent event) {
    NPC npc = event.getNPC();
    Consumer<LivingEntity> binding = bindings.get(npc);
    if (binding == null || !(npc.getEntity() instanceof LivingEntity e)) return;
    EntitySnapshot snapshot = refreshing.remove(npc);
    try {
      if (snapshot != null) {
        snapshot.restore(e);
        // Citizens invokes trait.onSpawn after this event. Keep its equipment trait in sync.
        npc.getOrAddTrait(Equipment.class).run();
      }
    } finally {
      binding.accept(e);
    }
  }

  @EventHandler
  public void joined(PlayerJoinEvent event) {
    for (NPC npc : List.copyOf(bindings.keySet()))
      if (npc.getEntity() instanceof Player player) {
        if (!npc.data().get(NPC.Metadata.REMOVE_FROM_TABLIST, true))
          event.getPlayer().listPlayer(player);
        else event.getPlayer().unlistPlayer(player);
      }
  }

  @Override
  public String name() {
    return "Citizens player";
  }

  @Override
  public Handle spawn(ActorDefinition d) {
    NPC npc = registry.createNPC(EntityType.PLAYER, d.name);
    Equipment equipment = npc.getOrAddTrait(Equipment.class);
    equipment.set(Equipment.EquipmentSlot.HAND, d.equipment[0]);
    equipment.set(Equipment.EquipmentSlot.OFF_HAND, d.equipment[1]);
    equipment.set(Equipment.EquipmentSlot.HELMET, d.equipment[2]);
    equipment.set(Equipment.EquipmentSlot.CHESTPLATE, d.equipment[3]);
    equipment.set(Equipment.EquipmentSlot.LEGGINGS, d.equipment[4]);
    equipment.set(Equipment.EquipmentSlot.BOOTS, d.equipment[5]);
    npc.setProtected(false);
    // A freshly created/refreshed NPC should accept its first hit; normal combat cooldown stays.
    npc.data().setPersistent(NPC.Metadata.SPAWN_NODAMAGE_TICKS, 0);
    npc.data().setPersistent(NPC.Metadata.KNOCKBACK, true);
    npc.data().setPersistent(NPC.Metadata.REMOVE_FROM_TABLIST, !d.tablist);
    npc.data().setPersistent(NPC.Metadata.COLLIDABLE, d.collidable);
    npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, d.nametag);
    // Keep destinations precise and fail a stuck path instead of teleporting through terrain.
    npc.getNavigator()
        .getDefaultParameters()
        .distanceMargin(0.6)
        .pathDistanceMargin(0.5)
        .range(96)
        .stationaryTicks(40)
        .stuckAction(null)
        .destinationTeleportMargin(-1);
    SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
    skin.setShouldUpdateSkins(false);
    if (!d.skin.isBlank()) {
      skin.setFetchDefaultSkin(false);
      if (!d.skinTexture.isEmpty() && !d.skinSignature.isEmpty())
        skin.setSkinPersistent(d.skin, d.skinSignature, d.skinTexture);
      else skin.setSkinName(d.skin, false);
    }
    if (!npc.spawn(d.location)) {
      npc.destroy();
      throw new IllegalArgumentException(
          "Citizens could not spawn actor '"
              + d.id
              + "'. Check its version and the destination chunk.");
    }
    return new Handle() {
      private Location looking;

      public LivingEntity entity() {
        return npc.isSpawned() && npc.getEntity() instanceof LivingEntity e ? e : null;
      }

      public void move(Location target, double speed) {
        // setTarget clones defaults, so changing local parameters beforehand loses the speed.
        npc.getNavigator().getDefaultParameters().speedModifier((float) speed);
        npc.getNavigator().setTarget(target);
        updateLook();
      }

      public boolean navigating() {
        return npc.getNavigator().isNavigating();
      }

      public void look(Location target) {
        if (target == null && looking != null && npc.isSpawned())
          npc.getOrAddTrait(net.citizensnpcs.trait.RotationTrait.class)
              .getPhysicalSession()
              .rotateToHave(
                  npc.getEntity().getLocation().getYaw(), npc.getEntity().getLocation().getPitch());
        looking = target == null ? null : target.clone();
        updateLook();
        if (!navigating() && looking != null && npc.isSpawned())
          npc.getOrAddTrait(net.citizensnpcs.trait.RotationTrait.class)
              .getPhysicalSession()
              .rotateToFace(looking);
      }

      private void updateLook() {
        if (!navigating()) return;
        // Use Citizens' look override while walking; rotating its movement body fights the path.
        npc.getNavigator()
            .getLocalParameters()
            .lookAtFunction(
                navigator -> {
                  LivingEntity entity = entity();
                  if (looking != null && entity != null && looking.getWorld() == entity.getWorld())
                    return looking;
                  Location ahead = navigator.getTargetAsLocation();
                  return ahead.clone().add(0, entity == null ? 1.62 : entity.getEyeHeight(), 0);
                });
      }

      public void stop() {
        npc.getNavigator().cancelNavigation();
      }

      public void name(String name) {
        // Citizens renames can respawn immediately, before its next equipment capture tick.
        if (npc.isSpawned()) equipment.run();
        npc.setName(name);
      }

      public void skin(String name) {
        if (npc.isSpawned()) equipment.run();
        skin.clearTexture();
        skin.setFetchDefaultSkin(false);
        skin.setShouldUpdateSkins(false);
        skin.setSkinName(name, true);
      }

      public void onEntityChanged(Consumer<LivingEntity> listener) {
        bindings.put(npc, listener);
      }

      public boolean refreshing() {
        return refreshing.containsKey(npc);
      }

      public void tablist(boolean listed) {
        npc.data().setPersistent(NPC.Metadata.REMOVE_FROM_TABLIST, !listed);
        if (npc.getEntity() instanceof Player player)
          for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (listed) viewer.listPlayer(player);
            else viewer.unlistPlayer(player);
          }
      }

      public void appearance(boolean nametag, boolean collidable) {
        npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, nametag);
        npc.data().setPersistent(NPC.Metadata.COLLIDABLE, collidable);
      }

      public boolean captureSkin(ActorDefinition definition) {
        if (definition.skin.isBlank() || !definition.skin.equalsIgnoreCase(skin.getSkinName()))
          return false;
        String texture = skin.getTexture(), signature = skin.getSignature();
        if (texture == null
            || signature == null
            || texture.isEmpty()
            || signature.isEmpty()
            || (texture.equals(definition.skinTexture)
                && signature.equals(definition.skinSignature))) return false;
        definition.skinTexture = texture;
        definition.skinSignature = signature;
        return true;
      }

      public void remove() {
        bindings.remove(npc);
        refreshing.remove(npc);
        npc.destroy();
      }
    };
  }

  @Override
  public void close() {
    HandlerList.unregisterAll(this);
    bindings.clear();
    refreshing.clear();
    registry.deregisterAll();
  }
}
