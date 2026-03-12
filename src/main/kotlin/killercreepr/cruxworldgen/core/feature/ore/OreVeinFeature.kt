package killercreepr.cruxworldgen.core.feature.ore

import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.feature.Feature
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.core.feature.BlockPos
import java.util.*

class OreVeinFeature : Feature<OreConfig> {

  override fun place(region: LimitedRegion, rng : Random, origin: BlockPos, cfg: OreConfig) {
    // Random line endpoints
    val angle = rng.nextDouble() * Math.PI
    val dx = kotlin.math.sin(angle)
    val dz = kotlin.math.cos(angle)

    val half = cfg.size / 2.0
    val x0 = origin.x + dx * half
    val x1 = origin.x - dx * half
    val z0 = origin.z + dz * half
    val z1 = origin.z - dz * half

    val y0 = origin.y + rng.nextInt(3) - 1
    val y1 = origin.y + rng.nextInt(3) - 1

    val steps = cfg.size.coerceAtLeast(1)
    for (i in 0 until steps) {
      val t = i / (steps - 1.0).coerceAtLeast(1.0)
      val cx = Curve.lerp(x0, x1, t)
      val cy = Curve.lerp(y0.toDouble(), y1.toDouble(), t)
      val cz = Curve.lerp(z0, z1, t)

      // radius varies along vein
      val r = (rng.nextDouble() * cfg.size / 16.0 + 1.0) * kotlin.math.sin(Math.PI * t)
      val rx = r
      val ry = r
      val rz = r

      val minX = kotlin.math.floor(cx - rx).toInt()
      val maxX = kotlin.math.floor(cx + rx).toInt()
      val minY = kotlin.math.floor(cy - ry).toInt()
      val maxY = kotlin.math.floor(cy + ry).toInt()
      val minZ = kotlin.math.floor(cz - rz).toInt()
      val maxZ = kotlin.math.floor(cz + rz).toInt()

      for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
        // only write inside current chunk; if you want cross-chunk veins later, see note below
        if(!region.isInRegion(x, y,z)) continue

        val nx = (x + 0.5 - cx) / rx
        val ny = (y + 0.5 - cy) / ry
        val nz = (z + 0.5 - cz) / rz
        if (nx*nx + ny*ny + nz*nz >= 1.0) continue

        if(!cfg.canReplace.canReplace(region, rng,x, y, z)) continue
        val block = cfg.ore.getBlock(region, rng, x, y, z) ?: continue
        region.setBlock(x, y, z, block)
      }
    }
  }

}