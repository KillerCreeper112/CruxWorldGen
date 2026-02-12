package killercreepr.cruxworldgen.test3

class PillarAdditive(
  private val id: String,
  private val cellThreshold: Double = 0.7,
  private val strength: Double = 2.5
) : DensityAdditive {
  override fun sample(ctx: DensityCtx, x: Int, y: Int, z: Int, out: DensityStack, core: Double) {
    if (core <= 0.0) return

    // 2D pillar field controls where pillars spawn
    val cell = ctx.noise.cellular2D(x, z) // [0..1]
    val pillarMask = ((cell - cellThreshold) / (1.0 - cellThreshold)).coerceIn(0.0, 1.0)

    // height falloff so pillars taper upward (example)
    val taper = ((80.0 - y) / 80.0).coerceIn(0.0, 1.0)

    out.addAdditive(pillarMask * taper * strength * core)
  }
}
