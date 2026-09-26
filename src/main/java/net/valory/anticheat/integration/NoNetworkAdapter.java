package net.valory.anticheat.integration;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.valory.anticheat.check.CheckResult;
import org.bukkit.entity.Player;

public final class NoNetworkAdapter implements NetworkAdapter {
  public boolean available() {
    return false;
  }

  public boolean territoryFlight(UUID id) {
    return false;
  }

  public Optional<String> preserve(Player p, CheckResult r, Map<String, String> m) {
    return Optional.empty();
  }

  public boolean durable(String id) {
    return false;
  }

  public void play(Player p, String id) {
    p.sendMessage("[VAC] Replay indisponível.");
  }

  public CompletableFuture<String> punish(Player p, String e, String r, String n) {
    return CompletableFuture.failedFuture(
        new IllegalStateException("Valory integration unavailable"));
  }

  public String status() {
    return "Indisponível: requer a Suite com hooks VAC";
  }
}
