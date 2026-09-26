package net.valory.anticheat.storage;

import java.util.*;
import net.valory.anticheat.check.CheckResult;

public record EvidenceRecord(
    UUID player,
    String name,
    String server,
    CheckResult result,
    double risk,
    double vl,
    String context,
    String replay) {}
