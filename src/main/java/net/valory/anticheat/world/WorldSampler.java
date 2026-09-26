package net.valory.anticheat.world;

import java.util.*;
import net.valory.anticheat.math.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

public final class WorldSampler {
  public Environment sample(Player p, long now) {
    Location l = p.getLocation();
    World w = l.getWorld();
    List<Box> solids = new ArrayList<>(48);
    boolean loaded = true, special = false;
    String reason = "SIMPLE";
    for (int x = l.getBlockX() - 2; x <= l.getBlockX() + 2; x++)
      for (int z = l.getBlockZ() - 2; z <= l.getBlockZ() + 2; z++) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) {
          loaded = false;
          continue;
        }
        for (int y = Math.max(w.getMinHeight(), l.getBlockY() - 2);
            y <= Math.min(w.getMaxHeight() - 1, l.getBlockY() + 3);
            y++) {
          Block b = w.getBlockAt(x, y, z);
          Material m = b.getType();
          if (m.isAir()) continue;
          String n = m.name();
          if (b.isLiquid()
              || n.contains("WATER")
              || n.contains("LAVA")
              || n.contains("SLIME")
              || n.contains("HONEY")
              || n.contains("WEB")
              || n.contains("LADDER")
              || n.contains("VINE")
              || n.contains("SCAFFOLD")
              || n.contains("BUBBLE")
              || n.contains("PISTON")
              || n.contains("POWDER_SNOW")
              || n.contains("SOUL")
              || n.contains("BED")
              || b.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl
                  && wl.isWaterlogged()) {
            special = true;
            reason = n;
          }
          for (BoundingBox box : b.getCollisionShape().getBoundingBoxes()) {
            solids.add(
                new Box(
                    box.getMinX() + x,
                    box.getMinY() + y,
                    box.getMinZ() + z,
                    box.getMaxX() + x,
                    box.getMaxY() + y,
                    box.getMaxZ() + z));
            if (solids.size() > 384) {
              special = true;
              reason = "COLLISION_BUDGET";
              break;
            }
          }
        }
      }
    Vec3 pos = new Vec3(l.getX(), l.getY(), l.getZ());
    String below = "UNLOADED";
    double friction = .6;
    int by = (int) Math.floor(l.getY() - .2);
    if (loaded && by >= w.getMinHeight() && by < w.getMaxHeight()) {
      below = w.getBlockAt(l.getBlockX(), by, l.getBlockZ()).getType().name();
      if (below.equals("BLUE_ICE")) friction = .989;
      else if (below.contains("ICE")) friction = .98;
    }
    Environment e =
        new Environment(
            now, w.getUID(), pos, solids, loaded, special, false, friction, below, reason);
    return new Environment(
        now,
        w.getUID(),
        pos,
        solids,
        loaded,
        special,
        e.supported(Box.player(pos, p.getWidth(), p.getHeight())),
        friction,
        below,
        reason);
  }

  public boolean safe(Player player, Location target) {
    World w = target.getWorld();
    if (w == null
        || w != player.getWorld()
        || !w.isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)
        || !w.getWorldBorder().isInside(target)
        || target.getY() < w.getMinHeight() + 1
        || target.getY() + player.getHeight() >= w.getMaxHeight()) return false;
    Box body =
        Box.player(
            new Vec3(target.getX(), target.getY(), target.getZ()),
            player.getWidth(),
            player.getHeight());
    boolean support = false;
    for (int x = (int) Math.floor(body.minX()); x <= (int) Math.floor(body.maxX()); x++)
      for (int z = (int) Math.floor(body.minZ()); z <= (int) Math.floor(body.maxZ()); z++) {
        if (!w.isChunkLoaded(x >> 4, z >> 4)) return false;
        for (int y = (int) Math.floor(body.minY() - .1); y <= (int) Math.floor(body.maxY()); y++) {
          Block b = w.getBlockAt(x, y, z);
          if (b.isLiquid() || b.getType() == Material.FIRE || b.getType() == Material.POWDER_SNOW)
            return false;
          for (BoundingBox box : b.getCollisionShape().getBoundingBoxes()) {
            Box solid =
                new Box(
                    box.getMinX() + x,
                    box.getMinY() + y,
                    box.getMinZ() + z,
                    box.getMaxX() + x,
                    box.getMaxY() + y,
                    box.getMaxZ() + z);
            if (body.expand(-.001).intersects(solid)) return false;
            if (body.move(new Vec3(0, -.05, 0)).intersects(solid)) support = true;
          }
        }
      }
    return support;
  }
}
