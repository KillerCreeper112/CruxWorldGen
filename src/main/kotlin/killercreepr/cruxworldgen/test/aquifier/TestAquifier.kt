package killercreepr.cruxworldgen.test.aquifier

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*

enum class FluidType {
  AIR, WATER, LAVA
}

data class AquiferCell(
  val centerX: Double,
  val centerY: Double,
  val centerZ: Double,
  val fluidLevel: Int,
  val fluidType: FluidType
)

data class AquiferResult(
  val fluidType: FluidType,
  val fluidLevel: Int,
  val shouldBarrier: Boolean
)

object AquiferNoise : NoiseModule {
  object FluidLevel2D : NoiseKey { override val id = "aquifer.fluid_level_2D" }
  object LavaChance2D : NoiseKey { override val id = "aquifer.lava_chance_2D" }

  override fun install(bank: NoiseBank) {
    bank.register(FluidLevel2D) { seed ->
      NoiseField.noiseField(seed) {
        frequency(0.004)
          .noiseType(CruxNoise.NoiseType.OpenSimplex2)
          .fractalType(CruxNoise.FractalType.FBm)
          .fractalOctaves(2)
      }
    }

    bank.register(LavaChance2D) { seed ->
      NoiseField.noiseField(seed) {
        frequency(0.008)
          .noiseType(CruxNoise.NoiseType.OpenSimplex2)
          .fractalType(CruxNoise.FractalType.FBm)
          .fractalOctaves(2)
      }
    }
  }
}

class VoronoiAquiferSystem(
  val cellSizeXZ: Int = 16,
  val cellSizeY: Int = 12,

  val barrierDistance: Double = 2.0,
  val lavaAlwaysBelowY: Int = -55,
  val lavaPossibleBelowY: Int = 0
) : Noised {

  override val noiseModule = AquiferNoise

  fun sample(ctx: GenerateContext, cave: CaveContext): AquiferResult {
    val wx = cave.worldX.toDouble()
    val wy = cave.y.toDouble()
    val wz = cave.worldZ.toDouble()

    val cellX = floorDiv(cave.worldX, cellSizeXZ)
    val cellY = floorDiv(cave.y, cellSizeY)
    val cellZ = floorDiv(cave.worldZ, cellSizeXZ)

    var best: AquiferCell? = null
    var second: AquiferCell? = null
    var bestDist2 = Double.POSITIVE_INFINITY
    var secondDist2 = Double.POSITIVE_INFINITY

    for (dz in -1..1) {
      for (dx in -1..1) {
        for (dy in -1..1) {
          val cx = cellX + dx
          val cy = cellY + dy
          val cz = cellZ + dz

          val cell = buildCell(ctx, cx, cy, cz)

          val ddx = wx - cell.centerX
          val ddy = wy - cell.centerY
          val ddz = wz - cell.centerZ
          val dist2 = ddx * ddx + ddy * ddy + ddz * ddz

          if (dist2 < bestDist2) {
            second = best
            secondDist2 = bestDist2
            best = cell
            bestDist2 = dist2
          } else if (dist2 < secondDist2) {
            second = cell
            secondDist2 = dist2
          }
        }
      }
    }

    val primary = best ?: return AquiferResult(FluidType.AIR, Int.MIN_VALUE, false)
    val secondary = second

    val shouldBarrier = if (secondary != null) {
      shouldPlaceBarrier(cave.y, primary, secondary, bestDist2, secondDist2)
    } else {
      false
    }

    return AquiferResult(
      fluidType = primary.fluidType,
      fluidLevel = primary.fluidLevel,
      shouldBarrier = shouldBarrier
    )
  }

  private fun buildCell(ctx: GenerateContext, cellX: Int, cellY: Int, cellZ: Int): AquiferCell {
    val seed = hash3(ctx.worldContext.seed, cellX, cellY, cellZ)

    val ox = random01(seed xor 0x1234ABCDL)
    val oy = random01(seed xor 0x5678EF01L)
    val oz = random01(seed xor 0x2468ACE0L)

    val centerX = cellX * cellSizeXZ + ox * cellSizeXZ
    val centerY = cellY * cellSizeY + oy * cellSizeY
    val centerZ = cellZ * cellSizeXZ + oz * cellSizeXZ

    val fluidLevel = computeFluidLevel(ctx, centerX, centerY, centerZ, seed)
    val fluidType = computeFluidType(ctx, centerX, centerY, centerZ, fluidLevel, seed)

    return AquiferCell(centerX, centerY, centerZ, fluidLevel, fluidType)
  }

  private fun computeFluidLevel(
    ctx: GenerateContext,
    centerX: Double,
    centerY: Double,
    centerZ: Double,
    seed: Long
  ): Int {
    val coarse = ctx.noise.get(AquiferNoise.FluidLevel2D).noise2D(centerX, centerZ) // -1..1
    val base = lerp(-8.0, 40.0, (coarse + 1.0) * 0.5)

    val local = ((random01(seed xor 0xABCDEF12L) * 10.0) - 5.0)

    // snap to reduce fragmentation
    val snapped = (kotlin.math.round((base + local) / 3.0) * 3.0).toInt()

    return snapped
  }

  private fun computeFluidType(
    ctx: GenerateContext,
    centerX: Double,
    centerY: Double,
    centerZ: Double,
    fluidLevel: Int,
    seed: Long
  ): FluidType {
    val y = centerY.toInt()

    if (y <= lavaAlwaysBelowY) return FluidType.LAVA

    val dryness = random01(seed xor 0x13572468L)

    // some cells are dry
    if (dryness < 0.28) return FluidType.AIR

    if (y < lavaPossibleBelowY) {
      val lavaNoise = (ctx.noise.get(AquiferNoise.LavaChance2D).noise2D(centerX, centerZ) + 1.0) * 0.5
      val depthT = ((lavaPossibleBelowY - y).toDouble() / (lavaPossibleBelowY - lavaAlwaysBelowY).toDouble())
        .coerceIn(0.0, 1.0)

      val lavaChance = 0.12 + lavaNoise * 0.28 + depthT * 0.35
      if (random01(seed xor 0xCAFEBABEL) < lavaChance) return FluidType.LAVA
    }

    return FluidType.WATER
  }

  private fun shouldPlaceBarrier(
    worldY: Int,
    a: AquiferCell,
    b: AquiferCell,
    distA2: Double,
    distB2: Double
  ): Boolean {
    val typeConflict = a.fluidType != b.fluidType
    val levelConflict = kotlin.math.abs(a.fluidLevel - b.fluidLevel) >= 4

    if (!typeConflict && !levelConflict) return false

    val distA = kotlin.math.sqrt(distA2)
    val distB = kotlin.math.sqrt(distB2)
    val edgeDelta = kotlin.math.abs(distA - distB)

    // near Voronoi boundary
    return edgeDelta < barrierDistance
  }

  private fun floorDiv(x: Int, d: Int): Int = kotlin.math.floor(x.toDouble() / d.toDouble()).toInt()

  private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

  private fun hash3(seed: Long, x: Int, y: Int, z: Int): Long {
    var h = seed
    h = h xor (x.toLong() * 341873128712L)
    h = h xor (y.toLong() * 132897987541L)
    h = h xor (z.toLong() * 42317861L)
    h = h xor (h ushr 33)
    h *= -0xae502812aa7333L
    h = h xor (h ushr 29)
    return h
  }

  private fun random01(seed: Long): Double {
    var x = seed
    x = x xor (x ushr 12)
    x = x xor (x shl 25)
    x = x xor (x ushr 27)
    val r = x * 2685821657736338717L
    val bits = (r ushr 11) and ((1L shl 53) - 1)
    return bits.toDouble() / (1L shl 53).toDouble()
  }
}