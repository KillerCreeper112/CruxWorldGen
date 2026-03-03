package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.util.Curve.band01
import killercreepr.cruxworldgen.api.util.Curve.smoothstep
import killercreepr.cruxworldgen.api.util.NoiseShaper
import killercreepr.cruxworldgen.api.util.NoiseUtil.densityBand01
import killercreepr.cruxworldgen.api.util.NoiseUtil.fract
import killercreepr.cruxworldgen.api.util.NoiseUtil.ridgedFbm3

class TestCaverBoi(
  val shaper: NoiseShaper,
  val noise3D: NoiseKey,
  val overhangWarp2D: NoiseKey,
  val warpX3D: NoiseKey,
  val warpY3D: NoiseKey,
  val warpZ3D: NoiseKey,
  val carve3D: NoiseKey,
  val layers: List<Layer> = listOf(
    Layer(center = -100.0, half = 22.0, weight = 0.85),
  )
) : CaveType {
  data class Layer(val center: Double, val half: Double, val weight: Double)

  override fun carveBlocks(
    ctx: GenerateContext,
    cave: CaveContext
  ): Double {
    val baseDensity = cave.terrainDensity
    val worldX = cave.worldX
    val y = cave.y
    val worldZ = cave.worldZ

    // 2) 3D domain warp
    val warpAmpXZ = 18.0
    val warpAmpY = 10.0
    val wx = ctx.noise.get(warpX3D).noise3D(worldX, y, worldZ) * warpAmpXZ
    val wy = ctx.noise.get(warpY3D).noise3D(worldX, y, worldZ) * warpAmpY
    val wz = ctx.noise.get(warpZ3D).noise3D(worldX, y, worldZ) * warpAmpXZ

    val sx = worldX.toDouble() + wx
    val sy = y.toDouble() + wy
    val sz = worldZ.toDouble() + wz

    val threshold = 0.38//0.28
    val strength = 170.0//210.0

    val shelves = shelfGateJittered(
      y, worldX, worldZ, ctx,
      baseSpacing = 32.0,
      spacingJitter = 14.0,
      halfWidth = 4.0
    )
    val shelfBias = 0.40 + 0.60 * shelves

    // Define 3–4 strata relative to the surface

    var stacked = 0.0
    for ((i, L) in layers.withIndex()) {
      val attachL = densityBand01(y.toDouble(), L.center, L.half) * L.weight
      if (attachL <= 1e-4) continue

      val o = 10000.0 * (i + 1)

      val ridgeL = ridgedFbm3(ctx, noise3D, shaper, sx + o, sy + o * 0.2, sz - o, octaves = 4)
      val carveN = ctx.noise.get(carve3D).noise3D(sx - o, sy + o * 0.15, sz + o)
      val holesL = 1.0 - smoothstep(0.15, 0.55, carveN)

      val massL = ((ridgeL - threshold).coerceAtLeast(0.0)) * strength
      stacked += attachL * shelfBias * holesL * massL
    }
    return stacked
  }

  fun shelfGateJittered(
    y: Int,
    wx: Int,
    wz: Int,
    ctx: GenerateContext,
    baseSpacing: Double,      // e.g. 28.0
    spacingJitter: Double,    // e.g. 12.0 (adds +/- jitter)
    halfWidth: Double         // e.g. 4.0
  ): Double {
    // Low-frequency field that changes slowly across the world
    val jitter01 = ctx.noise.get(overhangWarp2D).noise2D(wx.toDouble(), wz.toDouble()) * 0.5 + 0.5
    val spacing = (baseSpacing + (jitter01 * 2.0 - 1.0) * spacingJitter).coerceAtLeast(8.0)

    // ALSO vary the center position a bit so bands don't align
    val phaseWarp = ctx.noise.get(overhangWarp2D).noise2D(wx.toDouble() + 1000.0, wz.toDouble() - 1000.0) * spacing

    val yp = (y.toDouble() + phaseWarp) / spacing
    val phase = fract(yp)

    val halfWidth01 = (halfWidth / spacing).coerceIn(0.01, 0.49)
    return band01(center01 = 0.5, halfWidth01 = halfWidth01, t01 = phase)
  }
}