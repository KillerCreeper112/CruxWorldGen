package killercreepr.cruxworldgen.test.biome

import killercreepr.cruxworldgen.api.biome.BiomeShapeType
import killercreepr.cruxworldgen.api.biome.volumetric.VolBiomeShapeType
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.volumetric.VolumeEnv
import killercreepr.cruxworldgen.api.density.DensityBank
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.density.VolDensityBank
import killercreepr.cruxworldgen.api.density.VolDensityStack
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve.band01
import killercreepr.cruxworldgen.api.util.Curve.smoothstep
import killercreepr.cruxworldgen.api.util.NoiseShaper
import killercreepr.cruxworldgen.api.util.NoiseUtil.densityBand01
import killercreepr.cruxworldgen.api.util.NoiseUtil.fract
import killercreepr.cruxworldgen.api.util.NoiseUtil.ridgedFbm3

class AbyssStartOverhang(
  val shaper: NoiseShaper,
  val noise3D: NoiseKey,
  val overhangWarp2D: NoiseKey,
  val warpX3D: NoiseKey,
  val warpY3D: NoiseKey,
  val warpZ3D: NoiseKey,
  val carve3D: NoiseKey,
  val layers: List<Layer> = listOf(
    Layer(center = -18.0, half = 22.0, weight = 0.85),
    Layer(center =  18.0, half = 20.0, weight = 1.00),
    Layer(center =  55.0, half = 26.0, weight = 0.95),
    Layer(center =  95.0, half = 30.0, weight = 0.75)
  )
) : BiomeShapeType, VolBiomeShapeType {
  data class Layer(val center: Double, val half: Double, val weight: Double)
  override fun density(
    ctx: GenerateContext,
    worldX: Int,
    y: Int,
    worldZ: Int,
    edge: BiomeEdgeContext,
    signalWriter: SignalWriter,
    baseStack: DensityStack,
    out: DensityBank
  ) {
    density(ctx, worldX, y, worldZ, signalWriter, baseStack, out)
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

  fun density(
    ctx: GenerateContext,
    worldX: Int,
    y: Int,
    worldZ: Int,
    signalWriter: SignalWriter,
    baseStack: DensityStack,
    out: DensityBank
  ){
    val baseDensity = out.base

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
      val attachL = densityBand01(baseDensity, L.center, L.half) * L.weight
      if (attachL <= 1e-4) continue

      val o = 10000.0 * (i + 1)

      val ridgeL = ridgedFbm3(ctx, noise3D, shaper, sx + o, sy + o * 0.2, sz - o, octaves = 4)
      val carveN = ctx.noise.get(carve3D).noise3D(sx - o, sy + o * 0.15, sz + o)
      val holesL = 1.0 - smoothstep(0.15, 0.55, carveN)

      val massL = ((ridgeL - threshold).coerceAtLeast(0.0)) * strength
      stacked += attachL * shelfBias * holesL * massL
    }
    out.addAdditive((stacked))
  }

  override fun density(
    ctx: GenerateContext,
    worldX: Int,
    y: Int,
    worldZ: Int,
    env: VolumeEnv,
    signalWriter: SignalWriter,
    baseStack: VolDensityStack,
    out: VolDensityBank
  ) {
    density(ctx, worldX, y, worldZ, signalWriter, baseStack, out)
  }
}

/*
package killercreepr.cruxworldgen.test.biome

import com.sun.tools.attach.VirtualMachine.attach
import killercreepr.cruxworldgen.api.biome.BiomeShapeType
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.density.DensityBank
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve.smoothstep
import killercreepr.cruxworldgen.api.util.NoiseShaper
import kotlin.math.abs

class AbyssStartOverhang(
  val shaper: NoiseShaper,
  val noise3D: NoiseKey,
  val overhangWarp2D: NoiseKey,
  val warpX3D: NoiseKey,
  val warpY3D: NoiseKey,
  val warpZ3D: NoiseKey,
  val carve3D: NoiseKey
) : BiomeShapeType {
  override fun density(
    ctx: GenerateContext,
    worldX: Int,
    y: Int,
    worldZ: Int,
    edge: BiomeEdgeContext,
    signalWriter: SignalWriter,
    baseStack: DensityStack,
    out: DensityBank
  ) {
    val baseDensity = out.base

    // 2) 3D domain warp
    val warpAmpXZ = 18.0
    val warpAmpY = 10.0
    val wx = ctx.noise.get(warpX3D).noise3D(worldX, y, worldZ) * warpAmpXZ
    val wy = ctx.noise.get(warpY3D).noise3D(worldX, y, worldZ) * warpAmpY
    val wz = ctx.noise.get(warpZ3D).noise3D(worldX, y, worldZ) * warpAmpXZ

    val sx = worldX.toDouble() + wx
    val sy = y.toDouble() + wy
    val sz = worldZ.toDouble() + wz

    val threshold = 0.28
    val strength = 210.0

    val shelves = shelfGateJittered(
      y, worldX, worldZ, ctx,
      baseSpacing = 32.0,
      spacingJitter = 14.0,
      halfWidth = 4.0
    )
    val shelfBias = 0.40 + 0.60 * shelves

    // Define 3–4 strata relative to the surface
    data class Layer(val center: Double, val half: Double, val weight: Double)
    val layers = listOf(
      Layer(center = -18.0, half = 22.0, weight = 0.85),
      Layer(center =  18.0, half = 20.0, weight = 1.00),
      Layer(center =  55.0, half = 26.0, weight = 0.95),
      Layer(center =  95.0, half = 30.0, weight = 0.75)
    )


    var stacked = 0.0

// Gate: suppress contributions right near the base iso-surface (major floater reducer)
    val support = margin01(baseDensity, inner = 0.9, outer = 3.5)

    for ((i, L) in layers.withIndex()) {
      val attachL = densityBand01(baseDensity, L.center, L.half) * L.weight
      if (attachL <= 1e-4) continue

      val o = 10000.0 * (i + 1)

      val ridgeL = ridgedFbm3(ctx, noise3D, shaper, sx + o, sy + o * 0.2, sz - o, octaves = 4)
      val carveN = ctx.noise.get(carve3D).noise3D(sx - o, sy + o * 0.15, sz + o)

      // Optional: make holes less "speckly"
      val holesRaw = 1.0 - smoothstep(0.15, 0.55, carveN)
      val holesL = smoothstep(0.35, 0.75, holesRaw)

      val massL = ((ridgeL - threshold).coerceAtLeast(0.0)) * strength
      stacked += attachL * shelfBias * holesL * massL * support
    }

// Gate: kill tiny additive wisps that create 1-block islands
    val addGate = deadzone01(stacked, min = 6.0, max = 18.0)

    out.addAdditive(stacked * addGate)

    */
/*var stacked = 0.0
    for ((i, L) in layers.withIndex()) {
      val attachL = densityBand01(baseDensity, L.center, L.half) * L.weight
      if (attachL <= 1e-4) continue

      val o = 10000.0 * (i + 1)

      val ridgeL = ridgedFbm3(ctx, noise3D, shaper, sx + o, sy + o * 0.2, sz - o, octaves = 4)
      val carveN = ctx.noise.get(carve3D).noise3D(sx - o, sy + o * 0.15, sz + o)
      val holesL = 1.0 - smoothstep(0.15, 0.55, carveN)

      val massL = ((ridgeL - threshold).coerceAtLeast(0.0)) * strength
      stacked += attachL * shelfBias * holesL * massL
    }
    out.addAdditive(stacked)*//*

  }

  fun margin01(baseDensity: Double, inner: Double, outer: Double): Double {
    // 0 if we're too close to the base surface (|baseDensity| small),
    // 1 if we're safely away (reduces speckle floaters).
    val a = kotlin.math.abs(baseDensity)
    return smoothstep(inner, outer, a).coerceIn(0.0, 1.0)
  }

  fun deadzone01(value: Double, min: Double, max: Double): Double {
    // 0 below min, ramps to 1 by max (use to suppress tiny additive)
    return smoothstep(min, max, value).coerceIn(0.0, 1.0)
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

  fun densityBand01(baseDensity: Double, center: Double, halfWidth: Double): Double {
    // 1 at baseDensity=center, 0 outside [center-halfWidth .. center+halfWidth]
    val d = abs(baseDensity - center) / halfWidth
    val c = d.coerceIn(0.0, 1.0)
    val s = c * c * (3.0 - 2.0 * c)
    return 1.0 - s
  }

  fun ridgedFbm3(
    ctx: GenerateContext,
    key: NoiseKey,
    shaper: NoiseShaper,
    x: Double, y: Double, z: Double,
    octaves: Int,
    lacunarity: Double = 2.0,
    gain: Double = 0.5
  ): Double {
    var amp = 1.0
    var freq = 1.0
    var sum = 0.0
    var norm = 0.0
    repeat(octaves) {
      val n = ctx.noise.get(key).noise3D(x * freq, y * freq, z * freq)
      val r = 1.0 - abs(shaper.shape(n))
      sum += r * amp
      norm += amp
      amp *= gain
      freq *= lacunarity
    }
    return if (norm <= 0.0) 0.0 else (sum / norm).coerceIn(0.0, 1.0)
  }

  fun fract(x: Double) = x - kotlin.math.floor(x)

  fun band01(center01: Double, halfWidth01: Double, t01: Double): Double {
    val d = kotlin.math.abs(t01 - center01) / halfWidth01
    val c = d.coerceIn(0.0, 1.0)
    val s = c * c * (3.0 - 2.0 * c)     // smoothstep
    return 1.0 - s                       // 1 at center, 0 outside
  }
}*/
