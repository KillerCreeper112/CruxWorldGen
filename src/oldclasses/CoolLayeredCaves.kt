package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.api.util.Curve.band01
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import killercreepr.cruxworldgen.api.util.NoiseShaper
import killercreepr.cruxworldgen.api.util.NoiseUtil.fract
import killercreepr.cruxworldgen.api.util.NoiseUtil.ridgedFbm3
import kotlin.math.abs
import kotlin.math.max

class CoolLayeredCaves(
  val shaper: NoiseShaper = NoiseShaper.dummy(),

  // Main shelf / mass shape
  val shelfRidged3D: NoiseKey = Noise.ShelfRidged3D,
  val carve3D: NoiseKey = Noise.Carve3D,

  // Extra variation
  val chamber3D: NoiseKey = Noise.Chamber3D,
  val occupancy3D: NoiseKey = Noise.Occupancy3D,
  val pathA2D: NoiseKey = Noise.PathA2D,
  val pathB2D: NoiseKey = Noise.PathB2D,

  // Warps
  val shelfWarp2D: NoiseKey = Noise.ShelfWarp2D,
  val warpX3D: NoiseKey = Noise.WarpX3D,
  val warpY3D: NoiseKey = Noise.WarpY3D,
  val warpZ3D: NoiseKey = Noise.WarpZ3D,

  // Use DEPTH BELOW SURFACE here, not world Y
  val layers: List<Layer> = listOf(
    Layer(depth = 18.0, half = 8.0, weight = 0.50),
    Layer(depth = 34.0, half = 12.0, weight = 0.80),
    Layer(depth = 60.0, half = 18.0, weight = 1.10),
    Layer(depth = 120.0, half = 22.0, weight = 1.5),
  ),
  val strength: Double = 5.0,
  override val surfaceFadeRamp: Int = 0,
  override val surfaceFadeStart: Int = 0,
) : CaveType, Noised {

  object Noise : NoiseModule{
    object ShelfRidged3D : NoiseKey{ override val id = "cave.cool_layered.shelf_ridge3D" }
    object Carve3D : NoiseKey{ override val id = "cave.cool_layered.carve3D" }
    object Chamber3D : NoiseKey{ override val id = "cave.cool_layered.chamber3D" }
    object Occupancy3D : NoiseKey{ override val id = "cave.cool_layered.occupancy3D" }
    object PathA2D : NoiseKey{ override val id = "cave.cool_layered.path_a2D" }
    object PathB2D : NoiseKey{ override val id = "cave.cool_layered.path_b2D" }
    object ShelfWarp2D : NoiseKey{ override val id = "cave.cool_layered.shelf_warp2D" }
    object WarpX3D : NoiseKey{ override val id = "cave.cool_layered.warp_x3D" }
    object WarpY3D : NoiseKey{ override val id = "cave.cool_layered.warp_y3D" }
    object WarpZ3D : NoiseKey{ override val id = "cave.cool_layered.warp_z3D" }


    override fun install(bank: NoiseBank) {
      // --- Main shelf / mass shape ---
      bank.register(ShelfRidged3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0012)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
            .fractalGain(0.5)
            .fractalLacunarity(2.0)
        }
      }

      // --- Carve driver (higher frequency than shelf mass) ---
      bank.register(Carve3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0045)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
            .fractalGain(0.5)
            .fractalLacunarity(2.0)
        }
      }

      // --- Chambers: bigger blobs / roominess ---
      bank.register(Chamber3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0022)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
            .fractalGain(0.55)
            .fractalLacunarity(2.0)
        }
      }

      // --- Occupancy: where caves are allowed at all (slow, smooth mask) ---
      bank.register(Occupancy3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0010)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }

      // --- 2D path masks: long streaky influence fields (use in XY-dependent logic) ---
      bank.register(PathA2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0016)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
            .fractalGain(0.5)
            .fractalLacunarity(2.0)
        }
      }

      bank.register(PathB2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0011)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
            .fractalGain(0.5)
            .fractalLacunarity(2.0)
        }
      }

      // --- Warps ---
      bank.register(ShelfWarp2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0010)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }

      // Domain warp components (keep these smooth/low-frequency; amplitude comes from your code)
      bank.register(WarpX3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0020)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }

      bank.register(WarpY3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0020)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }

      bank.register(WarpZ3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0020)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
    }
  }

  override val noiseModule = Noise

  data class Layer(
    val depth: Double,
    val half: Double,
    val weight: Double
  )

  override fun carveBlocks(
    ctx: GenerateContext,
    cave: CaveContext
  ): Double {
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0
    if (cave.depthBelowSurface < 0) return 0.0

    val worldX = cave.worldX
    val y = cave.y
    val worldZ = cave.worldZ
    val depth = cave.depthBelowSurface.toDouble()

    // 3D domain warp
    val warpAmpXZ = 18.0
    val warpAmpY = 10.0

    val wx = ctx.noise.get(warpX3D).noise3D(worldX, y, worldZ) * warpAmpXZ
    val wy = ctx.noise.get(warpY3D).noise3D(worldX, y, worldZ) * warpAmpY
    val wz = ctx.noise.get(warpZ3D).noise3D(worldX, y, worldZ) * warpAmpXZ

    val sx = worldX.toDouble() + wx
    val sy = y.toDouble() + wy
    val sz = worldZ.toDouble() + wz

    // Connector paths
    val a = abs(ctx.noise.get(pathA2D).noise2D(sx, sz))
    val b = abs(ctx.noise.get(pathB2D).noise2D(sx, sz))

    val pathA = 1.0 - smoothstep01((a / 0.22).coerceIn(0.0, 1.0))
    val pathB = 1.0 - smoothstep01((b / 0.22).coerceIn(0.0, 1.0))

    // A dominates; B only lightly helps so connectivity doesn't explode
    val connectorMask = max(pathA, pathB * 0.55)

    // Big chamber pockets
    val chamberN01 = (ctx.noise.get(chamber3D).noise3D(sx, sy, sz) + 1.0) * 0.5
    val chamberMask = remapAbove(chamberN01, 0.64)

    // Dead zones / provinces so caves don't become one giant network
    val occN01 = (ctx.noise.get(occupancy3D).noise3D(sx, sy, sz) + 1.0) * 0.5
    val occupancyMask = remapAbove(occN01, 0.42)
    if (occupancyMask <= 0.001) return 0.0

    var carve = 0.0

    for ((i, layer) in layers.withIndex()) {
      // IMPORTANT: band by depth below surface, not terrain density
      val layerMask = bandMask(depth, layer.depth, layer.half)
      if (layerMask <= 0.001) continue

      // Also make the shelf pattern relative to depth, not absolute Y
      val shelves = shelfGateJittered(
        depth = depth,
        wx = worldX,
        wz = worldZ,
        ctx = ctx,
        baseSpacing = 26.0 + i * 3.0,
        spacingJitter = 8.0,
        halfWidth = 3.0 + i * 0.8
      )
      val shelfBias = 0.35 + 0.65 * shelves

      val o = 8192.0 * (i + 1)

      // Main layered cave body
      val ridged = ridgedFbm3(
        ctx,
        shelfRidged3D,
        shaper,
        sx + o,
        sy + o * 0.23,
        sz - o,
        octaves = 4
      )

      val carveNoise = ctx.noise.get(carve3D).noise3D(
        sx - o,
        sy + o * 0.11,
        sz + o
      )

      // Cuts some openings into the shelf mass
      val holesMask = 1.0 - Curve.smoothstep(0.18, 0.56, carveNoise)

      // Shelf mass
      val shelfMass = ((ridged - 0.43).coerceAtLeast(0.0)) * 95.0

      // Chambers widen parts of the shelves
      val chamberMass = chamberMask * (18.0 + 28.0 * layerMask)

      // Connectors thread some zones together
      val connectorMass = connectorMask * (10.0 + 16.0 * chamberMask)

      val localCarve =
        layerMask *
          layer.weight *
          occupancyMask *
          shelfBias *
          holesMask *
          (shelfMass + chamberMass + connectorMass)

      // Using max instead of sum keeps overlapping layers from blowing open
      // into one giant over-carved slab.
      carve = max(carve, localCarve)
    }

    // Small cap so it doesn't go insane in overlap-heavy zones
    return carve * strength//min(carve, solidDensity * 1.35 + 26.0)
  }

  fun shelfGateJittered(
    depth: Double,
    wx: Int,
    wz: Int,
    ctx: GenerateContext,
    baseSpacing: Double,
    spacingJitter: Double,
    halfWidth: Double
  ): Double {
    val jitter01 =
      ctx.noise.get(shelfWarp2D).noise2D(wx.toDouble(), wz.toDouble()) * 0.5 + 0.5

    val spacing =
      (baseSpacing + (jitter01 * 2.0 - 1.0) * spacingJitter).coerceAtLeast(8.0)

    val phaseWarp =
      ctx.noise.get(shelfWarp2D).noise2D(wx.toDouble() + 1000.0, wz.toDouble() - 1000.0) * spacing

    val yp = (depth + phaseWarp) / spacing
    val phase = fract(yp)

    val halfWidth01 = (halfWidth / spacing).coerceIn(0.01, 0.49)
    return band01(center01 = 0.5, halfWidth01 = halfWidth01, t01 = phase)
  }

  private fun bandMask(v: Double, center: Double, half: Double): Double {
    return 1.0 - smoothstep01((abs(v - center) / half).coerceIn(0.0, 1.0))
  }

  private fun remapAbove(v01: Double, threshold: Double): Double {
    val t = ((v01 - threshold) / (1.0 - threshold)).coerceIn(0.0, 1.0)
    return smoothstep01(t)
  }
}