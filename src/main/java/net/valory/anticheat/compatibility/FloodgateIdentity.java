package net.valory.anticheat.compatibility;

import java.util.UUID;
import org.geysermc.floodgate.api.FloodgateApi;

final class FloodgateIdentity {
  static boolean isBedrock(UUID player) {
    FloodgateApi api = FloodgateApi.getInstance();
    if (api == null) throw new IllegalStateException("Floodgate API is not ready");
    return api.isFloodgatePlayer(player);
  }
}
