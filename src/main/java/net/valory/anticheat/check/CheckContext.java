package net.valory.anticheat.check;

import java.util.*;
import net.valory.anticheat.api.Family;
import net.valory.anticheat.state.*;

public interface CheckContext {
  PlayerData entity(int id);

  boolean enabled(String key);

  void result(PlayerData data, CheckResult result);

  default void flag(
      PlayerData d,
      String check,
      String subtype,
      Family family,
      String group,
      double confidence,
      double weight,
      String description,
      Map<String, String> debug,
      long now) {
    String key = check + "." + subtype;
    if (!enabled(key)
        || d.exempt(family, now)
        || d.bypassChecks.contains(check.toLowerCase(Locale.ROOT))) return;
    long previous = d.emitted.getOrDefault(key, Long.MIN_VALUE / 2);
    if (now - previous < 500_000_000L) return;
    d.emitted.put(key, now);
    result(
        d,
        new CheckResult(
            check,
            subtype,
            family,
            group,
            confidence,
            weight,
            description,
            debug,
            System.currentTimeMillis(),
            now));
  }

  default boolean streak(PlayerData d, String key, boolean fail, int required) {
    int count = fail ? Math.min(1000, d.streaks.getOrDefault(key, 0) + 1) : 0;
    d.streaks.put(key, count);
    return count >= required;
  }
}
