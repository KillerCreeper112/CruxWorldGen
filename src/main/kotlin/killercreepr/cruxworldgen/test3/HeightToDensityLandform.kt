package killercreepr.cruxworldgen.test3

class HeightToDensityLandform(
  private val baseHeight: (DensityCtx, Int, Int) -> Double, // (ctx, x, z) -> surfaceY
  private val thickness: (DensityCtx, Int, Int) -> Double,                            // bigger = smoother slopes
  private val yOffset: Double = 0.0                         // optional vertical shift
) : DensityLandform {

  override fun sample(
    ctx: DensityCtx,
    x: Int,
    y: Int,
    z: Int,
    out: DensityStack,
    w: Double
  ) {
    if (w <= 0.0) return

    val surfaceY = baseHeight(ctx, x, z) + yOffset

    // Positive below surface, negative above surface.
    // thickness controls how quickly density falls off with height.
    val d = (surfaceY - y.toDouble()) / thickness(ctx, x, z)

    // Landforms should typically contribute to base density (not carve/additive).
    out.addBase(d * w)
  }
}
