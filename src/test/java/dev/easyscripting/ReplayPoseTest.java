package dev.easyscripting;

import static org.junit.jupiter.api.Assertions.*;

import dev.easyscripting.recording.ReplayPose;
import java.lang.reflect.Proxy;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;

class ReplayPoseTest {
  @Test
  void flightCaptureCorrectsSwimmingPoseAndAppliesActualGlideFlag() {
    List<String> calls = new ArrayList<>();
    LivingEntity entity =
        (LivingEntity)
            Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {LivingEntity.class},
                (proxy, method, args) -> {
                  return switch (method.getName()) {
                    case "getPose" -> Pose.SWIMMING;
                    case "isGliding" -> true;
                    default -> {
                      if (method.getName().startsWith("set"))
                        calls.add(method.getName() + Arrays.toString(args));
                      yield null;
                    }
                  };
                });
    ReplayPose.capture(entity).apply(entity);
    assertEquals(List.of("setGliding[true]", "setPose[FALL_FLYING, true]"), calls);
  }

  @Test
  void oldFlightFramesGainFlagButOldSwimmingDoesNotBecomeFlight() throws Exception {
    var y = new YamlConfiguration();
    y.loadFromString("pose: FALL_FLYING\n");
    assertTrue(ReplayPose.read(y).gliding());
    y.set("pose", "SWIMMING");
    assertEquals(new ReplayPose(Pose.SWIMMING, false), ReplayPose.read(y));
    y.set("gliding", true);
    assertEquals(new ReplayPose(Pose.FALL_FLYING, true), ReplayPose.read(y));
  }

  @Test
  void landingClearsGlidingAndMissingLegacyPoseRetainsSneak() {
    var y = new YamlConfiguration();
    y.set("sneak", true);
    assertEquals(new ReplayPose(Pose.SNEAKING, false), ReplayPose.read(y));
    y.set("pose", "STANDING");
    assertEquals(new ReplayPose(Pose.STANDING, false), ReplayPose.read(y));
  }
}
