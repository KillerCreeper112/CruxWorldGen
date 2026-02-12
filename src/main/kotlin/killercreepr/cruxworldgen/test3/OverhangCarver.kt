package killercreepr.cruxworldgen.test3

import kotlin.math.abs
import kotlin.math.max

class OverhangCarver(
  private val id: String,
  private val threshold: Double = 0.15,
  private val strength: Double = 2.2,
  private val nearSurfaceRange: Double = 35.0
) : DensityCarver {

  override fun sample(ctx: DensityCtx, x: Int, y: Int, z: Int, out: DensityStack, core: Double) {
    if (core <= 0.0) return

    // Estimate surface from your highlands base function (fast hack).
    // Better: pass surfaceY into ctx (later), but this works now.
    val macro = ctx.noise.low2D(x, z)
    val ridge = ctx.noise.ridge2D(x, z)
    val surfaceY = 50.0 + 80.0 * macro + 45.0 * ridge

    // Only carve near the surface to form cliffs/overhangs, not deep swiss cheese.
    val near = max(0.0, 1.0 - abs(surfaceY - y) / nearSurfaceRange)

    // 3D noise for ledges
    val n = ctx.noise.density3D("overhang:$id", x, y, z) // [-1..1]
    val v = ((n - threshold) / (1.0 - threshold)).coerceIn(0.0, 1.0)

    out.addCarve(v * strength * near * core)
  }
}

