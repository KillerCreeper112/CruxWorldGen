package killercreepr.cruxworldgen.core.biome.shape

import killercreepr.cruxworldgen.api.biome.BiomeShapeType
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.density.DensityBank
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve.band
import kotlin.math.abs
import kotlin.math.floor

class FlatOverhangModifier(
  // “Where do shelves exist?” (2D)
  private val mask2D: NoiseKey,
  // “What height are shelves at here?” (2D)
  private val height2D: NoiseKey,

  private val strength: Double = 16.0,
  private val threshold01: Double = 0.55,      // higher = fewer shelves
  private val centerOffset: Double = 4.0,      // above surface
  private val heightAmp: Double = 18.0,        // how much shelf height varies
  private val stepBlocks: Double = 3.0,        // snap to strata steps
  private val halfWidthBlocks: Double = 2.0,   // shelf thickness (vertical)

  private val underOffset: Double = 10.0,
  private val underHalfWidth: Double = 4.0,
  private val underCarveStrength: Double = 6.0
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
    // Base convention: base = surfaceY - y
    val surfaceY = y.toDouble() + baseStack.base

    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val H = (maxY - minY + 1).toDouble()

    // 1) Decide “shelf presence” in XZ (flat across Y)
    // noise2D is [-1..1] -> ridged 0..1
    val maskN = ctx.noise.get(mask2D).noise2D(worldX, worldZ)
    val mask01 = 1.0 - abs(maskN) // 0..1
    val presence = ((mask01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val presenceShaped = presence * presence

    if (presenceShaped <= 0.0) return

    // 2) Pick a shelf height near the surface, but quantized (“strata”)
    val hN = ctx.noise.get(height2D).noise2D(worldX, worldZ) // [-1..1]
    var shelfY = surfaceY + centerOffset + (hN * heightAmp)

    // snap to steps (3 blocks, etc.)
    shelfY = snapToStep(shelfY, stepBlocks)

    // 3) Thin vertical band around shelfY (this is what makes it “flat”)
    val shelfBand = band(
      center01 = (shelfY - minY) / H,
      halfWidth01 = halfWidthBlocks / H,
      y01 = (y - minY) / H
    )

    val add = presenceShaped * shelfBand * strength
    out.addAdditive(add)

    // 4) Optional: carve underneath for a crisp lip
    val underBand = band(
      center01 = ((shelfY - underOffset) - minY) / H,
      halfWidth01 = underHalfWidth / H,
      y01 = (y - minY) / H
    )

    val carve = presenceShaped * underBand * underCarveStrength
    out.addCarve(carve)
  }

  private fun snapToStep(y: Double, step: Double): Double {
    if (step <= 0.0) return y
    return floor(y / step) * step
  }
}
