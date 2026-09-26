package net.valory.anticheat.compatibility;

import java.util.UUID;
import org.geysermc.geyser.api.GeyserApi;

final class GeyserIdentity {
  static boolean isBedrock(UUID player) {
    GeyserApi api = GeyserApi.api();
    if (api == null) throw new IllegalStateException("Geyser API is not ready");
    return api.isBedrockPlayer(player);
  }
}
