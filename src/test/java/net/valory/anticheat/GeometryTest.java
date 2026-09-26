package net.valory.anticheat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import net.valory.anticheat.math.*;
import org.junit.jupiter.api.Test;

class GeometryTest {
  @Test
  void faceEdgeCornerAndInside() {
    Box b = new Box(1, 1, 1, 2, 2, 2);
    assertEquals(1, b.ray(new Vec3(0, 1.5, 1.5), new Vec3(1, 0, 0), 3));
    assertEquals(1, b.ray(new Vec3(0, 1, 1), new Vec3(1, 0, 0), 3));
    assertEquals(0, b.ray(new Vec3(1.5, 1.5, 1.5), new Vec3(1, 0, 0), 3));
  }

  @Test
  void parallelOutsideAndBehindMiss() {
    Box b = new Box(1, 1, 1, 2, 2, 2);
    assertEquals(Double.POSITIVE_INFINITY, b.ray(Vec3.ZERO, new Vec3(1, 0, 0), 4));
    assertEquals(Double.POSITIVE_INFINITY, b.ray(new Vec3(3, 1.5, 1.5), new Vec3(1, 0, 0), 4));
  }

  @Test
  void exactReachLimit() {
    Box b = new Box(3, -1, -1, 4, 1, 1);
    assertEquals(3, b.ray(Vec3.ZERO, new Vec3(1, 0, 0), 3));
    assertFalse(Double.isFinite(b.ray(Vec3.ZERO, new Vec3(1, 0, 0), 2.999)));
  }

  @Test
  void rejectMalformedMath() {
    assertThrows(IllegalArgumentException.class, () -> new Box(Double.NaN, 0, 0, 1, 1, 1));
    Box b = new Box(0, 0, 0, 1, 1, 1);
    assertFalse(Double.isFinite(b.ray(Vec3.ZERO, Vec3.ZERO, 10)));
    assertFalse(Double.isFinite(b.ray(new Vec3(Double.NaN, 0, 0), new Vec3(1, 0, 0), 10)));
  }

  @Test
  void touchingFacesAreNotPenetration() {
    assertFalse(new Box(0, 0, 0, 1, 1, 1).intersects(new Box(1, 0, 0, 2, 1, 1)));
  }

  @Test
  void cardinalRotations() {
    assertEquals(1, Vec3.direction(0, 0).z(), 1e-10);
    assertEquals(-1, Vec3.direction(90, 0).x(), 1e-10);
    assertEquals(-1, Vec3.direction(0, 90).y(), 1e-10);
  }

  @Test
  void translationInvariantRaycast() {
    Random r = new Random(8292);
    for (int i = 0; i < 10000; i++) {
      double dist = r.nextDouble() * 100 + .001;
      Vec3 shift = new Vec3(r.nextDouble() * 1e4, r.nextDouble() * 1e4, r.nextDouble() * 1e4);
      Box box = new Box(dist, -1, -1, dist + 1, 1, 1);
      assertEquals(
          box.ray(Vec3.ZERO, new Vec3(1, 0, 0), 200),
          box.move(shift).ray(shift, new Vec3(1, 0, 0), 200),
          1e-9);
    }
  }

  @Test
  void boxDistanceUsesSurface() {
    assertEquals(2, new Box(2, 0, -1, 3, 2, 1).distance(Vec3.ZERO));
    assertEquals(0, new Box(-1, -1, -1, 1, 1, 1).distance(Vec3.ZERO));
  }
}
