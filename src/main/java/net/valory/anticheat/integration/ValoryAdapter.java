package net.valory.anticheat.integration;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.valory.anticheat.check.CheckResult;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Optional bridge that deliberately has no compile-time dependency on ValoryPunish. */
public final class ValoryAdapter implements NetworkAdapter {
  private final Plugin punish;

  public ValoryAdapter(Plugin punish) { this.punish = punish; }
  public boolean available() { return punish.isEnabled(); }

  public boolean territoryFlight(UUID player) {
    try {
      Method method = punish.getClass().getMethod("isTerritorySelecting", UUID.class);
      return Boolean.TRUE.equals(method.invoke(punish, player));
    } catch (ReflectiveOperationException ignored) { return false; }
  }

  @SuppressWarnings("unchecked")
  public Optional<String> preserve(Player player, CheckResult result, Map<String, String> metadata) {
    try {
      Object replay = punish.getClass().getMethod("replay").invoke(punish);
      Method create = replay.getClass().getMethod("createEvidence", Player.class, String.class, int.class, int.class, String.class, Map.class);
      Object value = create.invoke(replay, player, result.key() + ": " + result.description(), 20, 5, "ValoryAntiCheat", metadata);
      return value instanceof Optional<?> optional && optional.orElse(null) instanceof String id ? Optional.of(id) : Optional.empty();
    } catch (ReflectiveOperationException | RuntimeException ignored) { return Optional.empty(); }
  }

  public boolean durable(String evidence) {
    if (evidence == null || evidence.isBlank()) return false;
    try {
      Object replay = punish.getClass().getMethod("replay").invoke(punish);
      Object value = replay.getClass().getMethod("summary", String.class).invoke(replay, evidence);
      return value instanceof Optional<?> optional && optional.isPresent();
    } catch (ReflectiveOperationException | RuntimeException ignored) { return false; }
  }

  public void play(Player staff, String evidence) {
    try {
      Object replay = punish.getClass().getMethod("replay").invoke(punish);
      replay.getClass().getMethod("play", Player.class, String.class).invoke(replay, staff, evidence);
    } catch (ReflectiveOperationException exception) { staff.sendMessage("[VAC] Replay indisponível nesta versão do ValoryPunish."); }
  }

  public CompletableFuture<String> punish(Player player, String evidence, String reason, String notes) {
    return CompletableFuture.failedFuture(new IllegalStateException("A punição automática exige uma integração de punição explicitamente configurada."));
  }

  public String status() { return "ValoryPunish replay bridge (opcional)"; }
}
