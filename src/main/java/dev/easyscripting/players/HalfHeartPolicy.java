package dev.easyscripting.players;

import org.bukkit.Material;

public final class HalfHeartPolicy {
  private HalfHeartPolicy() {}

  /**
   * Let vanilla consume a held totem, run resurrection listeners and send its animation/effects.
   */
  public static boolean intercept(
      boolean enabled, double damage, double health, Material main, Material off) {
    return enabled
        && damage >= health
        && main != Material.TOTEM_OF_UNDYING
        && off != Material.TOTEM_OF_UNDYING;
  }
}
