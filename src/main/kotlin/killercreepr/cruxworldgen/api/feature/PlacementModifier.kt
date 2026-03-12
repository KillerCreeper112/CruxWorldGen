package killercreepr.cruxworldgen.api.feature

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.core.feature.BlockPos
import java.util.*

interface PlacementModifier {
  fun emitPositions(
    region: LimitedRegion,
    rng: Random,
    chunkX: Int,
    chunkZ: Int,
    out: MutableList<BlockPos>
  )
}

class NearAirFilter(
  private val base: PlacementModifier,
  private val radius: Int = 3,
  private val keepIfNearAir: Double = 1.0,
  private val keepIfNotNearAir: Double = 0.15,
  private val minAirCount: Int = 1,
  private val isSolid: (GenerateContext, Int, Int, Int) -> Boolean
) : PlacementModifier {

  override fun emitPositions(
    region: LimitedRegion,
    rng: Random,
    chunkX: Int,
    chunkZ: Int,
    out: MutableList<BlockPos>
  ) {
    val ctx = region.ctx
    val tmp = ArrayList<BlockPos>(128)
    base.emitPositions(region, rng, chunkX, chunkZ, tmp)

    fun nearAir(p: BlockPos): Boolean {
      var air = 0
      val px = p.x
      val py = p.y
      val pz = p.z

      for (dx in -radius..radius)
        for (dy in -radius..radius)
          for (dz in -radius..radius) {
            if (!isSolid(ctx, px + dx, py + dy, pz + dz)) {
              air++
              if (air >= minAirCount) return true
            }
          }
      return false
    }

    for (p in tmp) {
      val k = if (nearAir(p)) keepIfNearAir else keepIfNotNearAir
      if (k >= 1.0 || rng.nextDouble() < k) out.add(p)
    }
  }
}

class Repeat(val n: Int, val inner: PlacementModifier) : PlacementModifier {
  override fun emitPositions(region: LimitedRegion, rng: Random, chunkX: Int, chunkZ: Int, out: MutableList<BlockPos>) {
    repeat(n) { inner.emitPositions(region, rng, chunkX, chunkZ, out) }
  }
}

class InChunkSquare : PlacementModifier {
  override fun emitPositions(region: LimitedRegion, rng: Random, chunkX: Int, chunkZ: Int, out: MutableList<BlockPos>) {
    val wx = chunkX * region.bufferX + rng.nextInt(region.bufferX)
    val wz = chunkZ * region.bufferZ + rng.nextInt(region.bufferZ)
    out.add(BlockPos(wx, 0, wz)) // y filled by Height modifier later or combined modifier
  }
}

class XZHeight(val height: HeightSampler) : PlacementModifier {
  override fun emitPositions(region: LimitedRegion, rng: Random, chunkX: Int, chunkZ: Int, out: MutableList<BlockPos>) {
    val center = region.centerBounds

    val wx = rng.nextInt(center.minX, center.maxX+1)
    val wz = rng.nextInt(center.minZ, center.maxZ+1)

    val y  = height.sampleY(
      rng,
      region,
      wx, wz
    )

    out.add(BlockPos(wx, y, wz))
  }
}

class Rarity(val chance: Double, val inner: PlacementModifier) : PlacementModifier {
  override fun emitPositions(region: LimitedRegion, rng: Random, chunkX: Int, chunkZ: Int, out: MutableList<BlockPos>) {
    if (rng.nextDouble() <= chance) inner.emitPositions(region, rng, chunkX, chunkZ, out)
  }
}