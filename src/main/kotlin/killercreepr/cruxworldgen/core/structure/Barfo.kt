package killercreepr.cruxworldgen.core.structure

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.context.ChunkContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.structure.Aabb
import killercreepr.cruxworldgen.api.structure.StructureInstance
import killercreepr.cruxworldgen.api.structure.StructureTemplate
import killercreepr.cruxworldgen.api.structure.Terraformer
import killercreepr.cruxworldgen.api.util.HashUtil.hash01
import killercreepr.cruxworldgen.core.noise.BaseNoiseKeys
import kotlin.math.abs
import kotlin.math.sqrt

class BuildSurfaceCacheStep : TerraformStep {
  override fun run(s: TerraformState): Boolean {
    // arrays are created later once we know work bounds
    return true
  }
}
class ComputeFootprintStep : TerraformStep {
  override fun run(s: TerraformState): Boolean {
    val chunkWorldX = s.chunkWorldX
    val chunkWorldZ = s.chunkWorldZ

    val anchorLX = s.inst.worldX - chunkWorldX
    val anchorLZ = s.inst.worldZ - chunkWorldZ
    if (anchorLX !in 0..15 || anchorLZ !in 0..15) return false

    s.anchorLX = anchorLX
    s.anchorLZ = anchorLZ

    val fp = footprintAabb(s.template.bounds, s.inst.rot)

    val minLX = anchorLX + fp.minX
    val maxLX = anchorLX + fp.maxX
    val minLZ = anchorLZ + fp.minZ
    val maxLZ = anchorLZ + fp.maxZ

    // Single chunk mode: footprint must stay in this chunk
    if (minLX < 0 || maxLX > 15 || minLZ < 0 || maxLZ > 15) return false

    s.minLX = minLX
    s.maxLX = maxLX
    s.minLZ = minLZ
    s.maxLZ = maxLZ

    // Work bounds include buffer (clamped to chunk)
    s.workMinX = (minLX - s.bufferRadius).coerceIn(0, 15)
    s.workMaxX = (maxLX + s.bufferRadius).coerceIn(0, 15)
    s.workMinZ = (minLZ - s.bufferRadius).coerceIn(0, 15)
    s.workMaxZ = (maxLZ + s.bufferRadius).coerceIn(0, 15)

    // Allocate arrays
    s.surface = IntArray(16 * 16) { Int.MIN_VALUE }
    s.falloff = DoubleArray(16 * 16) { 0.0 }
    s.desired = IntArray(16 * 16) { Int.MIN_VALUE }

    // Fill surface cache for work rect
    for (lx in s.workMinX..s.workMaxX) {
      for (lz in s.workMinZ..s.workMaxZ) {
        s.surface[idx(lx, lz)] = s.ctx.queries.surfaceY(lx, lz)
      }
    }

    return true
  }

  private fun idx(x: Int, z: Int) = x + z * 16

  private fun footprintAabb(bounds: Aabb, rot: Int): Aabb {
    val w = bounds.sizeX
    val d = bounds.sizeZ
    val (sx, sz) = when ((rot % 360 + 360) % 360) {
      0, 180 -> Pair(w, d)
      90, 270 -> Pair(d, w)
      else -> Pair(w, d)
    }
    return Aabb.aabb(bounds.minX, 0, bounds.minZ, bounds.minX + sx - 1, 0, bounds.minZ + sz - 1)
  }
}

class FitPlaneHeightModelStep : TerraformStep {
  override fun run(s: TerraformState): Boolean {
    val samples = ArrayList<Triple<Int, Int, Int>>()

    for (lx in s.minLX..s.maxLX) {
      for (lz in s.minLZ..s.maxLZ) {
        val y = s.surface[idx(lx, lz)]
        samples.add(Triple(lx, lz, y))
      }
    }

    s.plane = fitPlaneLeastSquares(samples)
    return true
  }

  private fun idx(x: Int, z: Int) = x + z * 16

  private fun fitPlaneLeastSquares(samples: List<Triple<Int, Int, Int>>): FlattenPadTerraformer.Plane {
    var sx = 0.0; var sz = 0.0; var sy = 0.0
    var sxx = 0.0; var szz = 0.0; var sxz = 0.0
    var sxy = 0.0; var szy = 0.0
    val n = samples.size.toDouble().coerceAtLeast(1.0)

    for ((x, z, y) in samples) {
      val X = x.toDouble()
      val Z = z.toDouble()
      val Y = y.toDouble()
      sx += X; sz += Z; sy += Y
      sxx += X * X; szz += Z * Z; sxz += X * Z
      sxy += X * Y; szy += Z * Y
    }

    val det =
      sxx * (szz * n - sz * sz) -
        sxz * (sxz * n - sz * sx) +
        sx * (sxz * sz - szz * sx)

    if (abs(det) < 1e-9) {
      val ys = samples.map { it.third }.sorted()
      val m = ys[ys.size / 2].toDouble()
      return FlattenPadTerraformer.Plane(0.0, 0.0, m)
    }

    fun det3(
      a11: Double, a12: Double, a13: Double,
      a21: Double, a22: Double, a23: Double,
      a31: Double, a32: Double, a33: Double
    ): Double =
      a11 * (a22 * a33 - a23 * a32) -
        a12 * (a21 * a33 - a23 * a31) +
        a13 * (a21 * a32 - a22 * a31)

    val detA = det3(sxy, sxz, sx,  szy, szz, sz,  sy, sz, n)
    val detB = det3(sxx, sxy, sx,  sxz, szy, sz,  sx, sy, n)
    val detC = det3(sxx, sxz, sxy, sxz, szz, szy, sx, sz, sy)

    return FlattenPadTerraformer.Plane(detA / det, detB / det, detC / det)
  }
}

class ComputeFalloffStep : TerraformStep {
  override fun run(s: TerraformState): Boolean {
    val br = s.bufferRadius.toDouble()

    for (lx in s.workMinX..s.workMaxX) {
      for (lz in s.workMinZ..s.workMaxZ) {
        val dist = distanceToRect(lx, lz, s.minLX, s.minLZ, s.maxLX, s.maxLZ)

        val wx = s.chunkWorldX + lx
        val wz = s.chunkWorldZ + lz

        // edgeWarp in blocks
        //val edge01 = (s.ctx.noise.pillarHeight2D(wx, wz) + 1.0) * 0.5
        val edgeWarp = (/*edge01 - */0.5) * 2.0 * s.edgeWarpAmp

        val distWarped = (dist - edgeWarp).coerceAtLeast(0.0)

        val t = ((br - distWarped) / br).coerceIn(0.0, 1.0) // 1 inside, 0 outside buffer
        s.falloff[idx(lx, lz)] = smoothstep01(t)
      }
    }

    return true
  }

  private fun idx(x: Int, z: Int) = x + z * 16

  private fun distanceToRect(x: Int, z: Int, minX: Int, minZ: Int, maxX: Int, maxZ: Int): Double {
    val dx = when {
      x < minX -> (minX - x).toDouble()
      x > maxX -> (x - maxX).toDouble()
      else -> 0.0
    }
    val dz = when {
      z < minZ -> (minZ - z).toDouble()
      z > maxZ -> (z - maxZ).toDouble()
      else -> 0.0
    }
    return sqrt(dx * dx + dz * dz)
  }

  private fun smoothstep01(t: Double): Double = t * t * (3.0 - 2.0 * t)
}

class BuildDesiredSurfaceStep : TerraformStep {
  override fun run(s: TerraformState): Boolean {
    for (lx in s.workMinX..s.workMaxX) {
      for (lz in s.workMinZ..s.workMaxZ) {
        val f = s.falloff[idx(lx, lz)]
        if (f <= 0.0001) continue

        val surface = s.surface[idx(lx, lz)]
        val wx = s.chunkWorldX + lx
        val wz = s.chunkWorldZ + lz

        // Blend to plane
        val planeY = s.plane.yAt(lx.toDouble(), lz.toDouble())
        val base = surface + (planeY - surface) * f

        // Small jitter (stable)
        val jitter = hashSigned01(s.inst.seed xor mix2(wx, wz)) * s.jitterAmp

        // Roughness (only matters when we are actually modifying)
        val rough01 = (s.ctx.noise.get(BaseNoiseKeys.TerrainDetail).noise3D(wx, surface, wz) + 1.0) * 0.5
        val rough = (rough01 - 0.5) * 2.0 * s.roughCarveAmp

        s.desired[idx(lx, lz)] = (base + jitter + rough).toInt()
      }
    }
    return true
  }

  private fun idx(x: Int, z: Int) = x + z * 16

  private fun mix2(wx: Int, wz: Int): Long {
    return (wx.toLong() * 341873128712L) xor (wz.toLong() * 132897987541L)
  }

  private fun hashSigned01(seed: Long): Double {
    var v = seed
    v = (v xor (v ushr 30)) * -4658895280553007687L
    v = (v xor (v ushr 27)) * -7723592293110705685L
    v = v xor (v ushr 31)
    val u = (v and Long.MAX_VALUE).toDouble() / Long.MAX_VALUE.toDouble()
    return u * 2.0 - 1.0
  }
}

class ApplyCarveFillStep : TerraformStep {
  override fun run(s: TerraformState): Boolean {
    val chunk = s.chunk

    for (lx in s.workMinX..s.workMaxX) {
      for (lz in s.workMinZ..s.workMaxZ) {
        val f = s.falloff[idx(lx, lz)]
        if (f <= 0.0001) continue

        val surface = s.surface[idx(lx, lz)]
        val desired = s.desired[idx(lx, lz)]
        if (desired == Int.MIN_VALUE) continue

        val wx = s.chunkWorldX + lx
        val wz = s.chunkWorldZ + lz

        if (surface > desired) {
          carveDown(s, lx, lz, surface, desired, wx, wz)
          paintTop(s, lx, lz, desired, wx, wz, f)
        } else if (surface < desired) {
          fillUp(s, lx, lz, surface, desired, wx, wz, f)
          paintTop(s, lx, lz, desired, wx, wz, f)
        }
      }
    }

    return true
  }

  private fun idx(x: Int, z: Int) = x + z * 16

  private fun carveDown(s: TerraformState, lx: Int, lz: Int, surface: Int, desired: Int, wx: Int, wz: Int) {
    val chunk = s.chunk
    val keepChance = s.keepBlockChance01

    for (y in surface downTo desired + 1) {
      if (y !in chunk.minHeight until chunk.maxHeight) continue

      // leave occasional blocks to roughen the cut
      val r01 = hash01(s.inst.seed xor (wx.toLong() * 11_000_003L) xor (wz.toLong() * 97_000_019L) xor (y.toLong() * 1_000_000_007L))
      if (r01 < keepChance) continue

      //chunk.setBlock(lx, y, lz, Material.AIR)
    }
  }

  private fun fillUp(s: TerraformState, lx: Int, lz: Int, surface: Int, desired: Int, wx: Int, wz: Int, falloff: Double) {
    val chunk = s.chunk
    val palette = s.palette

    // deeper topsoil near center, less near edge
    val topSoil = lerpInt(s.topSoilMin, s.topSoilMax, falloff)

    for (y in (surface + 1)..desired) {
      if (y !in chunk.minHeight until chunk.maxHeight) continue

      val depthFromTop = desired - y
      val mat =
        if (depthFromTop < topSoil) {
          palette.fillerBlock(s.ctx, wx, y, wz)
        } else {
          // mix in foundation sometimes
          val r01 = (s.ctx.noise.get(BaseNoiseKeys.TerrainDetail).noise3D(wx, y, wz) + 1.0) * 0.5
          if (r01 < (1.0 - falloff) * 0.25) palette.foundationBlock(s.ctx, wx, y, wz)
          else palette.fillerBlock(s.ctx, wx, y, wz)
        }

      chunk.setBlock(lx, y, lz, mat)
    }
  }

  private fun paintTop(s: TerraformState, lx: Int, lz: Int, y: Int, wx: Int, wz: Int, falloff: Double) {
    val chunk = s.chunk
    val palette = s.palette
    if (y !in chunk.minHeight until chunk.maxHeight) return

    chunk.setBlock(lx, y, lz, palette.topBlock(s.ctx, wx, y, wz))

    // simple 1-2 filler under top (you can make this slope-dependent later)
    if (y - 1 >= chunk.minHeight) chunk.setBlock(lx, y - 1, lz, palette.fillerBlock(s.ctx, wx, y - 1, wz))
    if (y - 2 >= chunk.minHeight && falloff > 0.5) chunk.setBlock(lx, y - 2, lz, palette.fillerBlock(s.ctx, wx, y - 2, wz))
  }

  private fun lerpInt(a: Int, b: Int, t: Double): Int = (a + (b - a) * t).toInt()
}


/** A single chunk terraformer built from composable steps. */
class NaturalPadTerraformer(
  private val bufferRadius: Int = 6,           // how far it blends outwards
  private val edgeWarpAmp: Double = 2.5,       // irregular boundary size (blocks)
  private val jitterAmp: Double = 0.6,         // small pad imperfections (blocks)
  private val roughCarveAmp: Double = 1.5,     // roughness to avoid perfect cuts (blocks)
  private val keepBlockChance01: Double = 0.06,// leave some blocks when carving => more natural walls
  private val topSoilMin: Int = 2,
  private val topSoilMax: Int = 6,
  private val steps: List<TerraformStep> = listOf(
    BuildSurfaceCacheStep(),
    ComputeFootprintStep(),
    FitPlaneHeightModelStep(),
    ComputeFalloffStep(),
    BuildDesiredSurfaceStep(),
    ApplyCarveFillStep()
  )
) : Terraformer {

  override fun terraformChunk(ctx: GenerateContext, inst: StructureInstance, template: StructureTemplate) {
    val state = TerraformState(
      ctx = ctx,
      inst = inst,
      template = template,
      bufferRadius = bufferRadius,
      edgeWarpAmp = edgeWarpAmp,
      jitterAmp = jitterAmp,
      roughCarveAmp = roughCarveAmp,
      keepBlockChance01 = keepBlockChance01,
      topSoilMin = topSoilMin,
      topSoilMax = topSoilMax
    )

    // Run steps
    for (step in steps) {
      if (!step.run(state)) return
    }
  }
}

/** Pipeline step */
interface TerraformStep {
  /** Return false to abort terraforming (e.g., footprint crosses chunk in single-chunk mode). */
  fun run(s: TerraformState): Boolean
}

/** Holds intermediate products between steps. */
class TerraformState(
  val ctx: GenerateContext,
  val inst: StructureInstance,
  val template: StructureTemplate,

  val bufferRadius: Int,
  val edgeWarpAmp: Double,
  val jitterAmp: Double,
  val roughCarveAmp: Double,
  val keepBlockChance01: Double,
  val topSoilMin: Int,
  val topSoilMax: Int,
) {
  val chunk: ChunkContext get() = ctx.chunkContext
  val chunkWorldX: Int get() = ctx.chunkX * chunk.width
  val chunkWorldZ: Int get() = ctx.chunkZ * chunk.depth

  // Anchor in local chunk coords
  var anchorLX: Int = 0
  var anchorLZ: Int = 0

  // Footprint rect in local coords (inclusive)
  var minLX: Int = 0
  var maxLX: Int = 0
  var minLZ: Int = 0
  var maxLZ: Int = 0

  // Work rect including buffer (clamped)
  var workMinX: Int = 0
  var workMaxX: Int = 0
  var workMinZ: Int = 0
  var workMaxZ: Int = 0

  // Cached surfaceY for local columns (only for work rect)
  lateinit var surface: IntArray // (x + z*16) => y

  // Height model
  var plane: FlattenPadTerraformer.Plane = FlattenPadTerraformer.Plane(0.0, 0.0, 64.0)

  // Falloff per column (0..1)
  lateinit var falloff: DoubleArray // same indexing

  // Desired surface per column
  lateinit var desired: IntArray

  // Simple palette (pluggable later)
  var palette: TerrainPalette = DefaultTerrainPalette
}

/** Palette abstraction (later you can swap this with biome palette). */
interface TerrainPalette {
  fun topBlock(ctx: GenerateContext, wx: Int, y: Int, wz: Int): BlockData
  fun fillerBlock(ctx: GenerateContext, wx: Int, y: Int, wz: Int): BlockData
  fun foundationBlock(ctx: GenerateContext, wx: Int, y: Int, wz: Int): BlockData
}

object DefaultTerrainPalette : TerrainPalette {
  override fun topBlock(ctx: GenerateContext, wx: Int, y: Int, wz: Int): BlockData = BlockData.EMPTY
  override fun fillerBlock(ctx: GenerateContext, wx: Int, y: Int, wz: Int): BlockData = BlockData.EMPTY
  override fun foundationBlock(ctx: GenerateContext, wx: Int, y: Int, wz: Int): BlockData = BlockData.EMPTY
}
