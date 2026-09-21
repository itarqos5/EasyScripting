package dev.easyscripting.actors;

import java.util.*;
import org.bukkit.Location;
import org.bukkit.util.Vector;

public final class PatternLayout {
  private PatternLayout() {}

  public static List<Location> locations(
      String shape, int count, double spacing, Location anchor, String side) {
    if (count < 1 || count > 200 || !Double.isFinite(spacing) || spacing < 1 || spacing > 20)
      throw new IllegalArgumentException("Pattern count must be 1..200 and spacing 1..20.");
    int direction =
        switch (side.toLowerCase(Locale.ROOT)) {
          case "front" -> 1;
          case "behind" -> -1;
          default -> throw new IllegalArgumentException("Pattern side must be front or behind.");
        };
    List<Vector> offsets = offsets(shape.toLowerCase(Locale.ROOT), count, spacing);
    double extent = offsets.stream().mapToDouble(v -> Math.abs(v.getZ())).max().orElse(0);
    double shift = direction * (3 + extent);
    double angle = Math.toRadians(anchor.getYaw());
    Vector forward = new Vector(-Math.sin(angle), 0, Math.cos(angle));
    Vector right = new Vector(forward.getZ(), 0, -forward.getX());
    List<Location> result = new ArrayList<>(count);
    for (Vector local : offsets) {
      Vector world =
          right.clone().multiply(local.getX()).add(forward.clone().multiply(local.getZ() + shift));
      Location at = anchor.clone().add(world);
      at.setPitch(0);
      at.setDirection(anchor.toVector().subtract(at.toVector()).setY(0));
      result.add(at);
    }
    return List.copyOf(result);
  }

  /**
   * Block-aligned rows and columns for a teleported line-up. The anchor's facing is snapped to
   * the nearest cardinal direction so every row and column follows the world grid exactly and
   * each member lands on its own whole block, which free-angle placement cannot guarantee.
   */
  public static List<Location> lineUp(int count, int columns, Location anchor, String side) {
    if (count < 1 || count > 200)
      throw new IllegalArgumentException("A line-up needs 1..200 members.");
    if (columns < 1 || columns > 32)
      throw new IllegalArgumentException("A line-up needs 1..32 members per row.");
    int direction =
        switch (side.toLowerCase(Locale.ROOT)) {
          case "front" -> 1;
          case "behind" -> -1;
          default -> throw new IllegalArgumentException("Line-up side must be front or behind.");
        };
    int lanes = Math.min(columns, count);
    // Snap to N/E/S/W: a diagonal grid would round two members onto one block.
    int quadrant = Math.floorMod(Math.round(anchor.getYaw() / 90f), 4);
    Vector forward =
        switch (quadrant) {
          case 0 -> new Vector(0, 0, 1);
          case 1 -> new Vector(-1, 0, 0);
          case 2 -> new Vector(0, 0, -1);
          default -> new Vector(1, 0, 0);
        };
    Vector right = new Vector(forward.getZ(), 0, -forward.getX());
    // Floor rather than getBlock(): this class stays free of any world lookup.
    Location origin =
        new Location(
            anchor.getWorld(),
            Math.floor(anchor.getX()),
            Math.floor(anchor.getY()),
            Math.floor(anchor.getZ()));
    List<Location> result = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      int row = index / lanes;
      int first = row * lanes;
      int inRow = Math.min(lanes, count - first);
      int lane = (lanes - inRow) / 2 + (index - first);
      int lateral = lane - (lanes - 1) / 2;
      int depth = direction * (2 + row);
      Location at =
          origin
              .clone()
              .add(
                  right.clone().multiply(lateral).add(forward.clone().multiply(depth)))
              .add(0.5, 0, 0.5);
      at.setPitch(0);
      // Every member faces the way the anchor faces, so the block reads as one formation.
      at.setDirection(forward);
      result.add(at);
    }
    return List.copyOf(result);
  }

  private static List<Vector> offsets(String shape, int count, double spacing) {
    return switch (shape) {
      case "line" ->
          java.util.stream.IntStream.range(0, count)
              .mapToObj(i -> new Vector((i - (count - 1) / 2.0) * spacing, 0, 0))
              .toList();
      case "circle" -> {
        if (count == 1) yield List.of(new Vector());
        double radius = Math.max(spacing, count * spacing / (2 * Math.PI));
        yield java.util.stream.IntStream.range(0, count)
            .mapToObj(
                i -> {
                  double angle = Math.PI * 2 * i / count;
                  return new Vector(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                })
            .toList();
      }
      case "grid", "square" -> rectangular(count, spacing);
      case "disc" -> disc(count, spacing);
      default ->
          throw new IllegalArgumentException(
              "Pattern must be line, circle, disc, grid or square.");
    };
  }

  private static List<Vector> rectangular(int count, double spacing) {
    int width = (int) Math.ceil(Math.sqrt(count));
    int rows = (int) Math.ceil((double) count / width);
    List<Vector> result = new ArrayList<>(count);
    for (int row = 0; row < rows; row++) {
      int inRow = Math.min(width, count - row * width);
      for (int column = 0; column < inRow; column++)
        result.add(
            new Vector(
                (column - (inRow - 1) / 2.0) * spacing,
                0,
                (row - (rows - 1) / 2.0) * spacing));
    }
    return List.copyOf(result);
  }

  private static List<Vector> disc(int count, double spacing) {
    int radius = (int) Math.ceil(Math.sqrt(count / Math.PI)) + 2;
    List<int[]> cells = new ArrayList<>();
    for (int z = -radius; z <= radius; z++)
      for (int x = -radius; x <= radius; x++) cells.add(new int[] {x, z});
    cells.sort(
        Comparator.<int[]>comparingInt(cell -> cell[0] * cell[0] + cell[1] * cell[1])
            .thenComparingInt(cell -> cell[1])
            .thenComparingInt(cell -> cell[0]));
    return cells.stream()
        .limit(count)
        .map(cell -> new Vector(cell[0] * spacing, 0, cell[1] * spacing))
        .toList();
  }
}
