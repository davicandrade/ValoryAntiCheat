package net.valory.anticheat.math;

public record Vec3(double x, double y, double z) {
  public static final Vec3 ZERO = new Vec3(0, 0, 0);

  public boolean finite() {
    return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
  }

  public Vec3 add(Vec3 v) {
    return new Vec3(x + v.x, y + v.y, z + v.z);
  }

  public Vec3 subtract(Vec3 v) {
    return new Vec3(x - v.x, y - v.y, z - v.z);
  }

  public Vec3 multiply(double k) {
    return new Vec3(x * k, y * k, z * k);
  }

  public double length() {
    return Math.sqrt(x * x + y * y + z * z);
  }

  public double horizontal() {
    return Math.hypot(x, z);
  }

  public static Vec3 direction(float yaw, float pitch) {
    double y = Math.toRadians(yaw), p = Math.toRadians(pitch), c = Math.cos(p);
    return new Vec3(-Math.sin(y) * c, -Math.sin(p), Math.cos(y) * c);
  }
}
