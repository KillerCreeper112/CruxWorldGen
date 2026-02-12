package killercreepr.cruxworldgen.test3

import kotlin.math.abs

class CaveCarver(
  private val id: String,
  private val threshold: Double = 0.25, // lower = more caves
  private val strength: Double = 3.5,   // higher = bigger caves
  private val yMin: Int = -64,
  private val yMax: Int = 96
) : DensityCarver {

  override fun sample(ctx: DensityCtx, x: Int, y: Int, z: Int, out: DensityStack, core: Double) {
    if (core <= 0.0) return
    if (y < yMin || y > yMax) return

    // 3D noise in [-1..1]
    val n = ctx.noise.density3D(id, x, y, z)

    // Use abs to get "tunnel-ish blobs" (0..1)
    val v = abs(n)

    // Only carve when we're above threshold
    val t = ((v - threshold) / (1.0 - threshold)).coerceIn(0.0, 1.0)

    // Vertical fade so caves don't abruptly stop
    val vertical = verticalFalloff(y, yMin, yMax)

    val carve = t * strength * vertical * core
    if (carve > 0.0) {
      out.addCarve(carve)
    }
  }

  private fun verticalFalloff(y: Int, yMin: Int, yMax: Int): Double {
    val low = smoothstep(yMin.toDouble(), (yMin + 16).toDouble(), y.toDouble())
    val high = 1.0 - smoothstep((yMax - 16).toDouble(), yMax.toDouble(), y.toDouble())
    return (low * high).coerceIn(0.0, 1.0)
  }
}
