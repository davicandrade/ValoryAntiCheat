package net.valory.anticheat.metrics;

import java.util.*;

public final class Performance {
  private long window = System.nanoTime(), packets, checks, nanos, ticks;
  public double packetsPerSecond, checksPerSecond, meanTickMillis;
  public long overBudget;
  private final Map<String, Long> cost = new HashMap<>(), count = new HashMap<>();

  public void packet() {
    packets++;
  }

  public void check(String name, long elapsed) {
    checks++;
    cost.merge(name, elapsed, Long::sum);
    count.merge(name, 1L, Long::sum);
  }

  public void tick(long elapsed) {
    nanos += elapsed;
    ticks++;
    long now = System.nanoTime();
    if (now - window >= 1_000_000_000L) {
      double seconds = (now - window) / 1e9;
      packetsPerSecond = packets / seconds;
      checksPerSecond = checks / seconds;
      meanTickMillis = nanos / 1e6 / Math.max(1, ticks);
      packets = checks = nanos = ticks = 0;
      window = now;
    }
  }

  public String costs() {
    List<String> rows = new ArrayList<>();
    for (var e : cost.entrySet())
      rows.add(
          e.getKey()
              + "="
              + String.format(Locale.ROOT, "%.2f us", e.getValue() / 1e3 / count.get(e.getKey())));
    return String.join(", ", rows);
  }
}
