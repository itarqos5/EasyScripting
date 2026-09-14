package dev.easyscripting.actors;

import java.util.*;
import org.bukkit.Location;

public final class PatternLayout {
  private PatternLayout() {}

  public static List<Location> locations(String shape, int count, double spacing, Location center) {
    if (count < 1 || count > 200 || !Double.isFinite(spacing) || spacing < 0.5 || spacing > 20)
      throw new IllegalArgumentException("Pattern count must be 1..200 and spacing 0.5..20.");
    List<Location> result = new ArrayList<>();
    int width = (int) Math.ceil(Math.sqrt(count));
    for (int i = 0; i < count; i++) {
      double x, z;
      switch (shape) {
        case "line" -> {
          x = (i - (count - 1) / 2.0) * spacing;
          z = 3;
        }
        case "grid" -> {
          x = (i % width - (width - 1) / 2.0) * spacing;
          z = (i / width + 1) * spacing;
        }
        case "circle" -> {
          double angle = Math.PI * 2 * i / count;
          double radius = Math.max(spacing, count * spacing / (2 * Math.PI));
          x = Math.cos(angle) * radius;
          z = Math.sin(angle) * radius;
        }
        case "square" -> {
          double side = Math.max(spacing, Math.ceil(count / 4.0) * spacing);
          double t = 4.0 * i / count;
          int edge = (int) t;
          double v = (t - edge) * side;
          x =
              switch (edge) {
                case 0 -> v;
                case 1 -> side;
                case 2 -> side - v;
                default -> 0;
              };
          z =
              switch (edge) {
                case 0 -> 0;
                case 1 -> v;
                case 2 -> side;
                default -> side - v;
              };
          x -= side / 2;
          z -= side / 2;
        }
        default ->
            throw new IllegalArgumentException("Pattern must be line, circle, grid or square.");
      }
      Location at = center.clone().add(x, 0, z);
      at.setDirection(center.toVector().subtract(at.toVector()));
      result.add(at);
    }
    return List.copyOf(result);
  }
}
