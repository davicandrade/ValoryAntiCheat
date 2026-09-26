package net.valory.anticheat.processor;

/** Leaky time budget. A two-second burst allowance absorbs coalescing; idle credit is capped. */
public final class MovementClock {
  private long previous;
  private double debt;
  private int samples;
  private boolean initialized;

  public double accept(long now) {
    if (!initialized) {
      initialized = true;
      previous = now;
      return 0;
    }
    if (now < previous) return debt;
    long elapsed = now - previous;
    previous = now;
    debt = Math.max(-2_000_000_000.0, debt - elapsed) + 50_000_000;
    samples++;
    return debt;
  }

  public boolean sustained() {
    return samples >= 200 && debt > 2_000_000_000.0;
  }

  public void reset() {
    previous = 0;
    debt = 0;
    samples = 0;
    initialized = false;
  }
}
