package net.valory.anticheat.state;

import java.util.*;
import net.valory.anticheat.api.*;

/** Monotonic clock deadlines. Source removal cannot erase another plugin's lease. */
public final class Exemptions {
  private record Key(ExemptionType type, String source) {}

  private final Map<Key, Long> deadlines = new HashMap<>();

  public void grant(ExemptionType type, String source, long now, long duration) {
    Objects.requireNonNull(type);
    if (source == null
        || source.isBlank()
        || source.length() > 80
        || duration <= 0
        || duration > 3_600_000_000_000L) throw new IllegalArgumentException("Invalid lease");
    Key key = new Key(type, source);
    expire(now);
    if (!deadlines.containsKey(key) && deadlines.size() >= 32)
      throw new IllegalStateException("Exemption capacity");
    deadlines.put(key, now + duration);
  }

  public void remove(ExemptionType type, String source) {
    deadlines.remove(new Key(type, source));
  }

  public void expire(long now) {
    deadlines.values().removeIf(deadline -> now >= deadline);
  }

  public boolean applies(Family family, long now) {
    for (var e : deadlines.entrySet())
      if (now < e.getValue() && e.getKey().type.affects(family)) return true;
    return false;
  }

  public String describe(long now) {
    StringBuilder s = new StringBuilder();
    for (var e : deadlines.entrySet())
      if (now < e.getValue())
        s.append(e.getKey().type).append(':').append(e.getKey().source).append(' ');
    return s.length() == 0 ? "NONE" : s.toString();
  }
}
