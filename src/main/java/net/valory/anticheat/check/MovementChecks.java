package net.valory.anticheat.check;

import java.util.Map;
import net.valory.anticheat.api.Family;
import net.valory.anticheat.math.*;
import net.valory.anticheat.packet.PacketFrame;
import net.valory.anticheat.prediction.MovementPrediction;
import net.valory.anticheat.state.PlayerData;

public final class MovementChecks implements Check {
  private static final String[] KEYS = {
    "Speed.A", "Fly.A", "AirJump.A", "Step.A", "Phase.A", "NoFall.A", "Spider.A"
  };
  private final MovementPrediction prediction = new MovementPrediction();

  public String name() {
    return "Movement";
  }

  public void evaluate(PlayerData d, PacketFrame p, CheckContext c) {
    if (p.kind() != PacketFrame.Kind.MOVE || !p.position()) return;
    if (!d.consistent(p.nanos())) {
      for (String key : KEYS) d.streaks.remove(key);
      return;
    }
    Vec3 next = new Vec3(p.x(), p.y(), p.z()), change = next.subtract(d.position);
    long elapsed = p.nanos() - d.lastMove;
    if (!d.environment.covers(next, p.nanos()) || elapsed < 25_000_000 || elapsed > 90_000_000) {
      d.stableMoves = 0;
      return;
    }
    var e =
        prediction.predict(
            new MovementPrediction.Input(
                d.delta,
                d.previousGround,
                d.ground,
                d.environment.friction(),
                d.movementSpeed,
                d.jumpBoost,
                d.sprint,
                d.usingItem));
    d.predictedHorizontal = e.horizontal();
    d.predictedMinY = e.minY();
    d.predictedMaxY = e.maxY();
    double actual = change.horizontal();
    boolean speed = c.streak(d, "Speed.A", actual > e.horizontal(), 6);
    boolean fly =
        c.streak(
            d,
            "Fly.A",
            !d.ground
                && !d.serverGround
                && change.y() > e.maxY()
                && change.y() >= -.03
                && d.delta.y() <= .03,
            8);
    // Jump reversal is an edge, not a sustained state. VL requires recurring edges.
    boolean airJump = !d.ground && !d.serverGround && d.delta.y() < -.1 && change.y() > .2;
    boolean step = d.ground && change.y() > e.maxY();
    Box body = Box.player(next, d.width, d.height);
    boolean noFall =
        c.streak(
            d, "NoFall.A", p.ground() && !d.environment.supported(body) && change.y() < -.3, 8);
    boolean spider =
        c.streak(
            d,
            "Spider.A",
            !d.environment.clear(Box.player(next, d.width + .08, d.height - .1))
                && !d.ground
                && !d.serverGround
                && change.y() > .1
                && change.y() > e.maxY(),
            8);
    boolean phase =
        c.streak(d, "Phase.A", !d.environment.clear(body.expand(-.02)) && change.length() > .03, 8);
    if (!(speed || fly || airJump || step || noFall || spider || phase)) return;
    // Allocate evidence only on inconsistencies, not on ordinary movement.
    Map<String, String> debug =
        Map.of(
            "deltaXZ",
            "" + actual,
            "predictedMax",
            "" + e.horizontal(),
            "dy",
            "" + change.y(),
            "minY",
            "" + e.minY(),
            "maxY",
            "" + e.maxY(),
            "below",
            d.environment.below(),
            "previousDelta",
            d.delta.toString(),
            "serverGround",
            "" + d.serverGround);
    d.lastDebug = debug.toString();
    if (speed)
      c.flag(
          d,
          "Speed",
          "A",
          Family.MOVEMENT,
          "movement-model",
          .75,
          2,
          "Horizontal envelope exceeded repeatedly",
          debug,
          p.nanos());
    if (fly)
      c.flag(
          d,
          "Fly",
          "A",
          Family.MOVEMENT,
          "movement-model",
          .75,
          2,
          "Air trajectory incompatible with gravity envelope",
          debug,
          p.nanos());
    if (airJump)
      c.flag(
          d,
          "AirJump",
          "A",
          Family.MOVEMENT,
          "movement-model",
          .65,
          1,
          "Unsupported falling-to-jumping transition",
          debug,
          p.nanos());
    if (step)
      c.flag(
          d,
          "Step",
          "A",
          Family.MOVEMENT,
          "movement-model",
          .65,
          1,
          "Step exceeds current model",
          debug,
          p.nanos());
    if (noFall)
      c.flag(
          d,
          "NoFall",
          "A",
          Family.MOVEMENT,
          "ground-consistency",
          .7,
          2,
          "Client ground claim contradicts falling collision history",
          debug,
          p.nanos());
    if (spider)
      c.flag(
          d,
          "Spider",
          "A",
          Family.MOVEMENT,
          "movement-model",
          .65,
          1,
          "Sustained wall ascent outside gravity model",
          debug,
          p.nanos());
    if (phase)
      c.flag(
          d,
          "Phase",
          "A",
          Family.MOVEMENT,
          "collision-model",
          .65,
          2,
          "Repeated solid intersection; world replication not authoritative",
          debug,
          p.nanos());
  }
}
