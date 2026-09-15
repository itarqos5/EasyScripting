package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.core.Positions;
import java.lang.reflect.Proxy;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

class ActorFacingTest {
  @Test
  void facingUsesEyesSoEqualHeightPlayersDoNotMakeNpcLookAtSky() {
    float[] rotation = new float[2];
    LivingEntity entity =
        (LivingEntity)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {LivingEntity.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "getEyeLocation" -> new Location(null, 0, 65.62, 0);
                      case "getLocation" -> new Location(null, 0, 64, 0);
                      case "setRotation" -> {
                        rotation[0] = (float) args[0];
                        rotation[1] = (float) args[1];
                        yield null;
                      }
                      default -> throw new AssertionError("Unexpected access: " + method.getName());
                    });
    Positions.face(entity, new Location(null, 3, 65.62, 0));
    assertEquals(270, rotation[0], 0.001);
    assertEquals(0, rotation[1], 0.001);
  }

  @Test
  void overlappingEyePositionsDoNotProduceInvalidAnglesOrMutateOrigin() {
    var from = new Location(null, 1, 2, 3, 80, -10);
    var result = Positions.facing(from, from);
    assertEquals(80, result.getYaw());
    assertEquals(-10, result.getPitch());
    Positions.facing(from, new Location(null, 5, 10, 5));
    assertEquals(80, from.getYaw());
    assertEquals(-10, from.getPitch());
  }

  @Test
  void elevationGivesCorrectPitchSign() {
    var from = new Location(null, 0, 0, 0);
    assertTrue(Positions.facing(from, new Location(null, 2, 2, 0)).getPitch() < 0);
    assertTrue(Positions.facing(from, new Location(null, 2, -2, 0)).getPitch() > 0);
  }
}
