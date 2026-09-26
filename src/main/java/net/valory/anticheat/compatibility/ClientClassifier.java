package net.valory.anticheat.compatibility;

import java.util.UUID;
import org.bukkit.Bukkit;

/**
 * Optional adapters resolve only when their owning plugin is enabled. No reflection or name-prefix
 * guessing.
 */
public final class ClientClassifier {
  public enum Identity {
    JAVA,
    BEDROCK,
    BRIDGE_UNKNOWN
  }

  public Identity identify(UUID player, boolean conservative) {
    boolean floodgate = Bukkit.getPluginManager().isPluginEnabled("floodgate");
    boolean geyser = Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot");
    try {
      if (floodgate && FloodgateIdentity.isBedrock(player)) return Identity.BEDROCK;
      if (geyser) return GeyserIdentity.isBedrock(player) ? Identity.BEDROCK : Identity.JAVA;
      // A false backend Floodgate result cannot establish that proxy identity forwarding works.
      if (floodgate && conservative) return Identity.BRIDGE_UNKNOWN;
    } catch (LinkageError | IllegalStateException unavailable) {
      return Identity.BRIDGE_UNKNOWN;
    }
    return Identity.JAVA;
  }
}
