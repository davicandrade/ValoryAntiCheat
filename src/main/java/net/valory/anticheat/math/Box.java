package net.valory.anticheat.math;

public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
  public Box {
    if (!(minX <= maxX && minY <= maxY && minZ <= maxZ))
      throw new IllegalArgumentException("Limites inválidos");
  }

  public Box expand(double d) {
    return new Box(minX - d, minY - d, minZ - d, maxX + d, maxY + d, maxZ + d);
  }

  public Box move(Vec3 d) {
    return new Box(
        minX + d.x(), minY + d.y(), minZ + d.z(), maxX + d.x(), maxY + d.y(), maxZ + d.z());
  }

  public boolean intersects(Box b) {
    return maxX > b.minX
        && minX < b.maxX
        && maxY > b.minY
        && minY < b.maxY
        && maxZ > b.minZ
        && minZ < b.maxZ;
  }

  public double distance(Vec3 p) {
    double x = Math.max(Math.max(minX - p.x(), 0), p.x() - maxX),
        y = Math.max(Math.max(minY - p.y(), 0), p.y() - maxY),
        z = Math.max(Math.max(minZ - p.z(), 0), p.z() - maxZ);
    return Math.sqrt(x * x + y * y + z * z);
  }

  public double ray(Vec3 origin, Vec3 dir, double limit) {
    if (!origin.finite()
        || !dir.finite()
        || !Double.isFinite(limit)
        || limit < 0
        || dir.length() < 1e-12) return Double.POSITIVE_INFINITY;
    double near = 0, far = limit;
    for (int axis = 0; axis < 3; axis++) {
      double o = axis == 0 ? origin.x() : axis == 1 ? origin.y() : origin.z(),
          d = axis == 0 ? dir.x() : axis == 1 ? dir.y() : dir.z();
      double lo = axis == 0 ? minX : axis == 1 ? minY : minZ,
          hi = axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
      if (Math.abs(d) < 1e-12) {
        if (o < lo || o > hi) return Double.POSITIVE_INFINITY;
        continue;
      }
      double a = (lo - o) / d, b = (hi - o) / d;
      near = Math.max(near, Math.min(a, b));
      far = Math.min(far, Math.max(a, b));
      if (near > far) return Double.POSITIVE_INFINITY;
    }
    return near;
  }

  public static Box player(Vec3 p, double width, double height) {
    double r = width / 2;
    return new Box(p.x() - r, p.y(), p.z() - r, p.x() + r, p.y() + height, p.z() + r);
  }
}
