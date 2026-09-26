package net.valory.anticheat.prediction;

import net.valory.anticheat.math.Vec3;

/** Vanilla simple-terrain hypothesis envelope. Explicitly not a complete Minecraft simulator. */
public final class MovementPrediction {
  public record Input(
      Vec3 previousDelta,
      boolean previousGround,
      boolean ground,
      double friction,
      double movementSpeed,
      int jumpBoost,
      boolean sprint,
      boolean usingItem) {}

  public record Envelope(double horizontal, double minY, double maxY) {}

  public Envelope predict(Input in) {
    double drag = in.previousGround() ? in.friction() * .91 : .91;
    // Unknown input is maximized over all directions; attribute already includes sprint/effects.
    double acceleration =
        in.previousGround() ? in.movementSpeed() * .21600002 / Math.pow(in.friction(), 3) : .026;
    double horizontal = in.previousDelta().horizontal() * drag + acceleration;
    if (in.previousGround()) horizontal += .2; // legitimate sprint-jump impulse hypothesis
    double falling = (in.previousDelta().y() - .08) * .98;
    double jump = .42 + .1 * Math.max(0, in.jumpBoost());
    double minY = Math.min(falling, 0), maxY = in.previousGround() ? Math.max(jump, .6) : falling;
    if (in.ground()) maxY = Math.max(0, maxY);
    return new Envelope(horizontal + .035, minY - .055, maxY + .055);
  }
}
