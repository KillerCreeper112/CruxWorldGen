package killercreepr.cruxworldgen.core.feature

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.block.BlockSection
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.util.Curve.lerp
import java.util.*

data class BlockPos(val x: Int, val y: Int, val z: Int)

interface Feature<Cfg> {
  fun place(region: LimitedRegion, rng : Random, origin: BlockPos, cfg: Cfg)
}

interface HeightSampler {
  fun sampleY(rng: java.util.Random, minY: Int, maxY: Int): Int
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



/** Uniform between */
class UniformHeight(val min: Int, val max: Int) : HeightSampler {
  override fun sampleY(rng: java.util.Random, minY: Int, maxY: Int): Int {
    val lo = kotlin.math.max(this.min, minY)
    val hi = kotlin.math.min(this.max, maxY)
    if (hi < lo) return lo
    return lo + rng.nextInt(hi - lo + 1)
  }
}

/** Triangle distribution peaking at center */
class TriangleHeight(val min: Int, val max: Int) : HeightSampler {
  override fun sampleY(rng: java.util.Random, minY: Int, maxY: Int): Int {
    val lo = kotlin.math.max(this.min, minY)
    val hi = kotlin.math.min(this.max, maxY)
    if (hi < lo) return lo
    val a = rng.nextInt(hi - lo + 1)
    val b = rng.nextInt(hi - lo + 1)
    return lo + (a + b) / 2
  }
}

/** Trapezoid-ish: flat middle, fades at edges (very useful for “wide band” ores) */
class TrapezoidHeight(val min: Int, val max: Int, val plateau: Int) : HeightSampler {
  override fun sampleY(rng: java.util.Random, minY: Int, maxY: Int): Int {
    val lo = kotlin.math.max(this.min, minY)
    val hi = kotlin.math.min(this.max, maxY)
    if (hi < lo) return lo
    val range = hi - lo + 1
    val p = plateau.coerceIn(0, range)
    // pick from [0..range+p) then clamp => creates a plateau in the middle
    val t = rng.nextInt(range + p)
    val v = (t - p / 2).coerceIn(0, range - 1)
    return lo + v
  }
}

class Repeat(val n: Int, val inner: PlacementModifier) : PlacementModifier {
  override fun emitPositions(region: LimitedRegion, rng: java.util.Random, chunkX: Int, chunkZ: Int, out: MutableList<BlockPos>) {
    repeat(n) { inner.emitPositions(region, rng, chunkX, chunkZ, out) }
  }
}

class InChunkSquare : PlacementModifier {
  override fun emitPositions(region: LimitedRegion, rng: java.util.Random, chunkX: Int, chunkZ: Int, out: MutableList<BlockPos>) {
    val wx = chunkX * region.bufferX + rng.nextInt(region.bufferX)
    val wz = chunkZ * region.bufferZ + rng.nextInt(region.bufferZ)
    out.add(BlockPos(wx, 0, wz)) // y filled by Height modifier later or combined modifier
  }
}

class XZHeight(val height: HeightSampler) : PlacementModifier {
  override fun emitPositions(region: LimitedRegion, rng: java.util.Random, chunkX: Int, chunkZ: Int, out: MutableList<BlockPos>) {
    val center = region.centerBounds

    val wx = rng.nextInt(center.minX, center.maxX+1)
    val wz = rng.nextInt(center.minZ, center.maxZ+1)
    val y  = height.sampleY(rng, center.minY, center.maxY)

    out.add(BlockPos(wx, y, wz))
  }
}

class Rarity(val chance: Double, val inner: PlacementModifier) : PlacementModifier {
  override fun emitPositions(region: LimitedRegion, rng: java.util.Random, chunkX: Int, chunkZ: Int, out: MutableList<BlockPos>) {
    if (rng.nextDouble() <= chance) inner.emitPositions(region, rng, chunkX, chunkZ, out)
  }
}

data class OreConfig(
  val ore: BlockData,
  val size: Int,
  val canReplace: (BlockSection) -> Boolean,
  val discardChanceOnAirExposure: Double = 0.0, // optional
)

fun interface OreConfigGetter{
  fun getOre(region: LimitedRegion, section : BlockSection)
}

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
      val cx = lerp(x0, x1, t)
      val cy = lerp(y0.toDouble(), y1.toDouble(), t)
      val cz = lerp(z0, z1, t)

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
        if(!region.isInRegion(x, z)) continue

        val nx = (x + 0.5 - cx) / rx
        val ny = (y + 0.5 - cy) / ry
        val nz = (z + 0.5 - cz) / rz
        if (nx*nx + ny*ny + nz*nz >= 1.0) continue

        val cur = region.getBlock(x, y, z)
        if (!cfg.canReplace(cur)) continue
        region.setBlock(x, y, z, cfg.ore)
      }
    }
  }

}
