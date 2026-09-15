package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.players.HalfHeartPolicy;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class HalfHeartPolicyTest {
  @Test
  void heldTotemsReachVanillaResurrectionInEitherHand() {
    assertFalse(HalfHeartPolicy.intercept(true, 8, 1, Material.TOTEM_OF_UNDYING, Material.AIR));
    assertFalse(
        HalfHeartPolicy.intercept(true, 8, 1, Material.DIAMOND_SWORD, Material.TOTEM_OF_UNDYING));
  }

  @Test
  void protectionContinuesAfterLastTotemIsConsumed() {
    assertFalse(HalfHeartPolicy.intercept(true, 20, 1, Material.AIR, Material.TOTEM_OF_UNDYING));
    assertTrue(HalfHeartPolicy.intercept(true, 20, 1, Material.AIR, Material.AIR));
  }

  @Test
  void NonLethalHitsAndDisabledModeKeepVanillaBehavior() {
    assertFalse(HalfHeartPolicy.intercept(true, 2, 10, Material.AIR, Material.AIR));
    assertFalse(HalfHeartPolicy.intercept(false, 20, 1, Material.AIR, Material.AIR));
    assertTrue(HalfHeartPolicy.intercept(true, 10, 10, Material.POTION, Material.AIR));
  }
}
