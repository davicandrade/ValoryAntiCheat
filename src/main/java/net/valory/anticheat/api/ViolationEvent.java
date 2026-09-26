package net.valory.anticheat.api;

import java.util.UUID;
import net.valory.anticheat.check.CheckResult;
import org.bukkit.event.*;

public final class ViolationEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();
  private final UUID player;
  private final CheckResult result;
  private boolean cancelled;

  public ViolationEvent(UUID player, CheckResult result) {
    this.player = player;
    this.result = result;
  }

  public UUID player() {
    return player;
  }

  public CheckResult result() {
    return result;
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
