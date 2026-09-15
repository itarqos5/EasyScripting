package dev.easyscripting.recording;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pose;

/** A horizontal pose alone renders as swimming; elytra animation also needs the gliding flag. */
public record ReplayPose(Pose pose, boolean gliding) {
  public ReplayPose {
    if (gliding) {
      pose = Pose.FALL_FLYING;
    }
  }

  public static ReplayPose capture(LivingEntity entity) {
    return new ReplayPose(entity.getPose(), entity.isGliding());
  }

  public static ReplayPose read(ConfigurationSection yaml) {
    Pose pose =
        Pose.valueOf(yaml.getString("pose", yaml.getBoolean("sneak") ? "SNEAKING" : "STANDING"));
    return new ReplayPose(pose, yaml.getBoolean("gliding", pose == Pose.FALL_FLYING));
  }

  public void apply(LivingEntity entity) {
    entity.setGliding(gliding);
    entity.setPose(pose, true);
  }
}
