package net.valory.anticheat.compatibility;

import java.util.UUID;
import org.bukkit.Bukkit;

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
      if (floodgate && conservative) return Identity.BRIDGE_UNKNOWN;
    } catch (LinkageError | IllegalStateException unavailable) {
      return Identity.BRIDGE_UNKNOWN;
    }
    return Identity.JAVA;
  }
}
