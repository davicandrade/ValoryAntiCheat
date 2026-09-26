package net.valory.anticheat.check;

import java.util.*;
import net.valory.anticheat.api.Family;

/** confidence denotes completeness of the model, never a probability that someone cheats. */
public record CheckResult(
    String check,
    String subtype,
    Family family,
    String independenceGroup,
    double confidence,
    double weight,
    String description,
    Map<String, String> debugData,
    long timestamp,
    long monotonicNanos) {
  public CheckResult {
    if (!Double.isFinite(confidence)
        || confidence < 0
        || confidence > 1
        || !Double.isFinite(weight)
        || weight <= 0) throw new IllegalArgumentException();
    debugData = Map.copyOf(debugData);
  }

  public String key() {
    return check + "." + subtype;
  }
}
