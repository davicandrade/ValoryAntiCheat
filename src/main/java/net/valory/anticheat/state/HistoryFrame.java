package net.valory.anticheat.state;

import java.util.UUID;
import net.valory.anticheat.math.*;

public record HistoryFrame(
    long nanos,
    long tick,
    UUID world,
    Vec3 position,
    float yaw,
    float pitch,
    Box bounds,
    long teleportEpoch) {}
