package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import kotlin.math.max

object CaveMasks {

  fun depthFade(
    cave: CaveContext,
    deepStart: Double = 6.0, // Depth below the surface where this fade starts turning on.
    // At depths <= deepStart, this returns near 0.
    deepFull: Double = 20.0  // Depth below the surface where the fade is fully on.
    // At depths >= deepFull, this returns 1.
  ): Double {
    if (cave.depthBelowSurface < 0) return 0.0
    val depth = cave.depthBelowSurface.toDouble()
    val t = ((depth - deepStart) / (deepFull - deepStart)).coerceIn(0.0, 1.0)
    return smoothstep01(t)
  }

  fun breakthrough2D(
    ctx: GenerateContext,
    cave: CaveContext,
    noiseKey: NoiseKey,              // 2D noise field used to decide where surface breakthroughs are allowed.
    nearSurfaceDepth: Double = 10.0, // Maximum depth where breakthrough influence matters.
    // Deeper than this, nearSurfaceMask fades to 0.
    breakThreshold: Double = 0.78    // Threshold for the breakthrough noise.
    // Higher = rarer openings, lower = more common openings.
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
    noiseKey: NoiseKey,              // 2D noise field controlling where rare surface openings are allowed.
    deepStart: Double = 6.0,         // Depth where normal underground cave allowance starts fading in.
    deepFull: Double = 20.0,         // Depth where normal underground cave allowance reaches full strength.
    nearSurfaceDepth: Double = 10.0, // Depth range near the surface where breakthrough zones can matter.
    breakThreshold: Double = 0.78    // Threshold controlling how rare breakthrough zones are.
  ): Double {
    return max(
      depthFade(cave, deepStart, deepFull),
      breakthrough2D(ctx, cave, noiseKey, nearSurfaceDepth, breakThreshold)
    )
  }

  fun softDepthPreference(
    cave: CaveContext,
    centerDepth: Double,         // Preferred cave depth below the surface.
    // This is where the mask is strongest.
    halfWidth: Double,           // Half-width of the preferred depth band.
    // Larger values make the preference broader/softer.
    minValue: Double = 0.25      // Minimum mask value outside the preferred band.
    // Keeps the cave type possible outside the ideal depth instead of fully disabling it.
  ): Double {
    if (cave.depthBelowSurface < 0) return 0.0

    val dy = kotlin.math.abs(cave.depthBelowSurface.toDouble() - centerDepth)
    val t = ((halfWidth - dy) / halfWidth).coerceIn(0.0, 1.0)
    return minValue + smoothstep01(t) * (1.0 - minValue)
  }

  fun occupancy3D(
    ctx: GenerateContext,
    cave: CaveContext,
    noiseKey: NoiseKey,
    minValue: Double = 0.35
  ): Double {
    val n01 = (ctx.noise.get(noiseKey).noise3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val s = smoothstep01(n01)
    return minValue + s * (1.0 - minValue)
  }
}