package dev.easyscripting.integration;

import dev.easyscripting.actors.*;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.*;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Location;
import org.bukkit.entity.*;

/** All Citizens references are confined here, so missing Citizens cannot break core startup. */
public final class CitizensBackend implements ActorBackend {
  private final NPCRegistry registry = CitizensAPI.createInMemoryNPCRegistry("EasyScripting");

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
    npc.data().setPersistent(NPC.Metadata.KNOCKBACK, true);
    npc.data().setPersistent(NPC.Metadata.REMOVE_FROM_TABLIST, true);
    npc.data().setPersistent(NPC.Metadata.COLLIDABLE, d.collidable);
    npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, d.nametag);
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
      public LivingEntity entity() {
        return npc.isSpawned() && npc.getEntity() instanceof LivingEntity e ? e : null;
      }

      public void move(Location target, double speed) {
        npc.getNavigator().getLocalParameters().speedModifier((float) speed);
        npc.getNavigator().setTarget(target);
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
        npc.destroy();
      }
    };
  }

  @Override
  public void close() {
    registry.deregisterAll();
  }
}
