package net.valory.anticheat.world;

import java.util.*;
import net.valory.anticheat.math.*;

/** Main-thread sample. Unknown or special physics deliberately returns no movement verdict. */
public record Environment(
    long nanos,
    UUID world,
    Vec3 center,
    List<Box> collisions,
    boolean loaded,
    boolean special,
    boolean ground,
    double friction,
    String below,
    String reason) {
  public Environment {
    collisions = List.copyOf(collisions);
  }

  public boolean covers(Vec3 p, long now) {
    return loaded
        && !special
        && now >= nanos
        && now - nanos < 200_000_000L
        && Math.abs(center.x() - p.x()) <= 1.5
        && Math.abs(center.y() - p.y()) <= 1.5
        && Math.abs(center.z() - p.z()) <= 1.5;
  }

  public boolean clear(Box box) {
    for (Box solid : collisions) if (box.intersects(solid)) return false;
    return true;
  }

  public boolean supported(Box body) {
    Box feet =
        new Box(
            body.minX(),
            body.minY() - .04,
            body.minZ(),
            body.maxX(),
            body.minY() + .001,
            body.maxZ());
    for (Box solid : collisions) if (feet.intersects(solid)) return true;
    return false;
  }
}
