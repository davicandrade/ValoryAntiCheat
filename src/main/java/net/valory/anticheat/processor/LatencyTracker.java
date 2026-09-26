package net.valory.anticheat.processor;

/**
 * At most one outstanding unguessable ping, matched once. Unsolicited/stale ACKs cannot update RTT.
 */
public final class LatencyTracker {
  private int token;
  private long sent;
  private boolean pending;
  private double rtt, jitter;
  private int samples;

  public void sent(int token, long now) {
    this.token = token;
    sent = now;
    pending = true;
  }

  public boolean acknowledge(int id, long now) {
    if (!pending || id != token || now < sent || now - sent > 10_000_000_000L) return false;
    double sample = (now - sent) / 1_000_000.0;
    pending = false;
    if (samples++ == 0) {
      rtt = sample;
      jitter = 0;
    } else {
      jitter = .75 * jitter + .25 * Math.abs(sample - rtt);
      rtt = .875 * rtt + .125 * sample;
    }
    return true;
  }

  public boolean ready() {
    return samples >= 3;
  }

  public double rttMillis() {
    return rtt;
  }

  public double jitterMillis() {
    return jitter;
  }

  public boolean pending(long now) {
    return pending && now - sent < 10_000_000_000L;
  }

  public long rewindNanos() {
    return (long) (Math.min(500, rtt / 2 + 50) * 1_000_000);
  }
}
