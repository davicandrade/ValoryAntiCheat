package net.valory.anticheat.api;

import java.util.UUID;
import org.bukkit.event.*;

public final class PunishmentDecisionEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();
  private final UUID player;
  private final String evidence;
  private final double risk;
  private boolean cancelled;

  public PunishmentDecisionEvent(UUID player, String evidence, double risk) {
    this.player = player;
    this.evidence = evidence;
    this.risk = risk;
  }

  public UUID player() {
    return player;
  }

  public String evidence() {
    return evidence;
  }

  public double risk() {
    return risk;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  public void setCancelled(boolean value) {
    cancelled = value;
  }

  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
