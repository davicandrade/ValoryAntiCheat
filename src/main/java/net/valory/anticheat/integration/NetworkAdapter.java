package net.valory.anticheat.integration;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.valory.anticheat.check.CheckResult;
import org.bukkit.entity.Player;

public interface NetworkAdapter {
  boolean available();

  boolean territoryFlight(UUID player);

  Optional<String> preserve(Player player, CheckResult result, Map<String, String> metadata);

  boolean durable(String evidence);

  void play(Player staff, String evidence);

  CompletableFuture<String> punish(Player player, String evidence, String reason, String notes);

  String status();
}
