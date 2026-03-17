package killercreepr.cruxworldgen.core.feature.blob

import killercreepr.crux.core.util.CruxBlockFace
import killercreepr.cruxworldgen.api.block.BlockGetter
import killercreepr.cruxworldgen.api.block.CanReplaceBlock
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.feature.Feature
import killercreepr.cruxworldgen.core.feature.BlockPos
import java.util.*

data class BlobConfig(
  val material: BlockGetter,
  val canReplace: CanReplaceBlock,
  val minRadius: Int = 2,
  val maxRadius: Int = 4,
  val minRadiusY: Int = 1,
  val maxRadiusY: Int = 3,
  val discardChanceOnAirExposure: Double = 0.0,

  // irregularity
  val irregularity: Double = 0.18,

  // bite carving
  val minBites: Int = 1,
  val maxBites: Int = 3,
  val minBiteRadius: Double = 0.8,
  val maxBiteRadius: Double = 1.8,
  val biteChance: Double = 0.85
)

class BlobFeature : Feature<BlobConfig> {
  override fun place(region: LimitedRegion, rng: Random, origin: BlockPos, cfg: BlobConfig) {
    val sizeRange = (cfg.maxRadius - cfg.minRadius + 1).coerceAtLeast(1)

    val rx = cfg.minRadius + rng.nextInt(sizeRange)
    val ry = cfg.minRadiusY + rng.nextInt((cfg.maxRadiusY - cfg.minRadiusY + 1).coerceAtLeast(1))
    val rz = cfg.minRadius + rng.nextInt(sizeRange)

    val radiusX = rx.toDouble() + rng.nextDouble() * 0.35
    val radiusY = ry.toDouble() + rng.nextDouble() * 0.35
    val radiusZ = rz.toDouble() + rng.nextDouble() * 0.35

    val centerX = origin.x + 0.5
    val centerY = origin.y + 0.5
    val centerZ = origin.z + 0.5

    // small random offsets for a few "bite" centers
    val biteCount = cfg.minBites + rng.nextInt((cfg.maxBites - cfg.minBites + 1).coerceAtLeast(1))
    val bites = ArrayList<Triple<Double, Double, Double>>(biteCount)
    val biteRadii = DoubleArray(biteCount)

    repeat(biteCount) {
      val bx = centerX + (rng.nextDouble() * 2.0 - 1.0) * radiusX * 0.75
      val by = centerY + (rng.nextDouble() * 2.0 - 1.0) * radiusY * 0.75
      val bz = centerZ + (rng.nextDouble() * 2.0 - 1.0) * radiusZ * 0.75
      bites += Triple(bx, by, bz)
      biteRadii[it] = cfg.minBiteRadius + rng.nextDouble() * (cfg.maxBiteRadius - cfg.minBiteRadius)
    }

    val minX = kotlin.math.floor(centerX - radiusX - cfg.irregularity).toInt()
    val maxX = kotlin.math.floor(centerX + radiusX + cfg.irregularity).toInt()
    val minY = kotlin.math.floor(centerY - radiusY - cfg.irregularity).toInt()
    val maxY = kotlin.math.floor(centerY + radiusY + cfg.irregularity).toInt()
    val minZ = kotlin.math.floor(centerZ - radiusZ - cfg.irregularity).toInt()
    val maxZ = kotlin.math.floor(centerZ + radiusZ + cfg.irregularity).toInt()

    val block = cfg.material.getBlock(region, rng, origin.x, origin.y, origin.z) ?: return

    for (x in minX..maxX) for (y in minY..maxY) for (z in minZ..maxZ) {
      if (!region.isInRegion(x, y, z)) continue

      val nx = (x + 0.5 - centerX) / radiusX
      val ny = (y + 0.5 - centerY) / radiusY
      val nz = (z + 0.5 - centerZ) / radiusZ

      val ellipsoid = nx * nx + ny * ny + nz * nz

      // Per-block random boundary wobble
      val edgeThreshold = 1.0 + (rng.nextDouble() * 2.0 - 1.0) * cfg.irregularity
      if (ellipsoid > edgeThreshold) continue

      // Optional carved "bites" to break roundness
      var insideBite = false
      for (i in bites.indices) {
        val (bx, by, bz) = bites[i]
        val br = biteRadii[i]

        val dx = (x + 0.5 - bx) / br
        val dy = (y + 0.5 - by) / br
        val dz = (z + 0.5 - bz) / br

        if (dx * dx + dy * dy + dz * dz <= 1.0) {
          insideBite = true
          break
        }
      }
      if (insideBite && rng.nextDouble() < cfg.biteChance) continue

      if (!cfg.canReplace.canReplace(region, rng, x, y, z)) continue

      if (cfg.discardChanceOnAirExposure > 0.0 && rng.nextDouble() < cfg.discardChanceOnAirExposure) {
        val exposedToAir = CruxBlockFace.CARTESIAN.any { dir ->
          val xx = x + dir.modX
          val yy = y + dir.modY
          val zz = z + dir.modZ
          if (!region.isInRegion(xx, yy, zz)) return@any false
          region.terrainQueries.isEmpty(xx, yy, zz)
        }
        if (exposedToAir) continue
      }

      region.setBlock(x, y, z, block)
    }
  }
}