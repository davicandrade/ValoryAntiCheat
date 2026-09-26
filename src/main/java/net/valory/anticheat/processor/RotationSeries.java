package net.valory.anticheat.processor;

public final class RotationSeries {
  private final double[] deltas = new double[64];
  private int count;

  public record Summary(double mean, double variance, int repeated) {}

  public Summary add(double delta) {
    deltas[count++] = delta;
    if (count < deltas.length) return null;
    double mean = 0, variance = 0;
    int repeated = 0;
    for (double value : deltas) mean += value / deltas.length;
    for (int i = 0; i < deltas.length; i++) {
      variance += (deltas[i] - mean) * (deltas[i] - mean) / deltas.length;
      if (i > 0 && Math.abs(deltas[i] - deltas[i - 1]) < .00001) repeated++;
    }
    count = 0;
    return new Summary(mean, variance, repeated);
  }

  public void clear() {
    count = 0;
  }

  public static double yawDelta(float a, float b) {
    double delta = (a - b) % 360;
    return delta > 180 ? delta - 360 : delta < -180 ? delta + 360 : delta;
  }
}
