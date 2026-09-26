package net.valory.anticheat.risk;

import java.util.*;
import net.valory.anticheat.check.CheckResult;

/** Decayed evidence index [0,100], NOT a calibrated posterior probability. */
public final class RiskEngine {
  private static final class Bucket {
    double value;
    long at, lastEvidence;
    double confidence, halfLife;
    String group;
  }

  private final Map<String, Bucket> buckets = new HashMap<>();
  private final double halfLifeSeconds;

  public RiskEngine(double halfLifeSeconds) {
    if (!Double.isFinite(halfLifeSeconds) || halfLifeSeconds <= 0)
      throw new IllegalArgumentException();
    this.halfLifeSeconds = halfLifeSeconds;
  }

  public double add(CheckResult r) {
    return add(r, halfLifeSeconds);
  }

  public double add(CheckResult r, double decaySeconds) {
    if (!Double.isFinite(decaySeconds) || decaySeconds <= 0) throw new IllegalArgumentException();
    Bucket b = buckets.computeIfAbsent(r.key(), ignored -> new Bucket());
    if (b.halfLife == 0) b.halfLife = decaySeconds;
    decay(b, r.monotonicNanos());
    b.halfLife = decaySeconds;
    b.value = Math.min(100, b.value + r.weight() * r.confidence());
    b.lastEvidence = r.monotonicNanos();
    b.confidence = r.confidence();
    b.group = r.independenceGroup();
    return b.value;
  }

  private void decay(Bucket b, long now) {
    if (now > b.at) b.value *= Math.pow(.5, (now - b.at) / 1e9 / b.halfLife);
    b.at = Math.max(b.at, now);
  }

  public double vl(String key, long now) {
    Bucket b = buckets.get(key);
    if (b == null) return 0;
    decay(b, now);
    return b.value;
  }

  public int independent(long now) {
    Set<String> groups = new HashSet<>();
    for (Bucket b : buckets.values()) {
      decay(b, now);
      if (b.value >= 6 && b.confidence >= .8 && now - b.lastEvidence <= 30_000_000_000L)
        groups.add(b.group);
    }
    return groups.size();
  }

  public double score(long now) {
    Map<String, Double> groups = new HashMap<>();
    for (Bucket b : buckets.values()) {
      decay(b, now);
      groups.merge(b.group, b.value, Math::max);
    }
    double sum = 0;
    for (double v : groups.values()) sum += v;
    return 100 * (-Math.expm1(-sum / 40));
  }

  public Map<String, Double> levels(long now) {
    Map<String, Double> out = new TreeMap<>();
    for (var e : buckets.entrySet()) {
      decay(e.getValue(), now);
      if (e.getValue().value > .01) out.put(e.getKey(), e.getValue().value);
    }
    return Map.copyOf(out);
  }
}
