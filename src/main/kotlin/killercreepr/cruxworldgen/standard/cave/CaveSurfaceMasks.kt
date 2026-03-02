package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import kotlin.math.max

object CaveMasks {

  fun depthFade(
    cave: CaveContext,
    deepStart: Double = 6.0,
    deepFull: Double = 20.0
  ): Double {
    if (cave.depthBelowSurface < 0) return 0.0
    val depth = cave.depthBelowSurface.toDouble()
    val t = ((depth - deepStart) / (deepFull - deepStart)).coerceIn(0.0, 1.0)
    return smoothstep01(t)
  }

  fun breakthrough2D(
    ctx: GenerateContext,
    cave: CaveContext,
    noiseKey: NoiseKey,
    nearSurfaceDepth: Double = 10.0,
    breakThreshold: Double = 0.78
  ): Double {
    if (cave.depthBelowSurface < 0) return 0.0
    val depth = cave.depthBelowSurface.toDouble()

    val break01 = (ctx.noise.get(noiseKey).noise2D(cave.worldX, cave.worldZ) + 1.0) * 0.5
    val breakT = ((break01 - breakThreshold) / (1.0 - breakThreshold)).coerceIn(0.0, 1.0)
    val breakNoiseMask = smoothstep01(breakT)

    val nearSurfaceT = (1.0 - depth / nearSurfaceDepth).coerceIn(0.0, 1.0)
    val nearSurfaceMask = smoothstep01(nearSurfaceT)

    return breakNoiseMask * nearSurfaceMask
  }

  fun depthWithBreakthrough(
    ctx: GenerateContext,
    cave: CaveContext,
    noiseKey: NoiseKey,
    deepStart: Double = 6.0,
    deepFull: Double = 20.0,
    nearSurfaceDepth: Double = 10.0,
    breakThreshold: Double = 0.78
  ): Double {
    return max(
      depthFade(cave, deepStart, deepFull),
      breakthrough2D(ctx, cave, noiseKey, nearSurfaceDepth, breakThreshold)
    )
  }
}