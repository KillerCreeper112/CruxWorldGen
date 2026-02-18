package killercreepr.cruxworldgen.core.biome.shape

import killercreepr.cruxworldgen.api.biome.BiomeShapeType
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.density.DensityBank
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve.band
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import kotlin.math.abs
import kotlin.math.floor

class ShelfOverhangModifier(
  private val mask2D: NoiseKey,
  private val height2D: NoiseKey,

  // how often shelves appear in XZ
  private val threshold01: Double = 0.62,   // higher => rarer shelves

  // shelf plane placement
  private val dropBelowSurface: Double = 10.0, // shelf sits this far BELOW local surface
  private val heightAmp: Double = 10.0,        // additional variation (+/-)
  private val stepBlocks: Double = 4.0,        // snap to flat strata steps

  // slab shape
  private val halfWidthBlocks: Double = 2.0,   // slab thickness
  private val strength: Double = 28.0,         // how strong the slab is

  // attachment rules: prevents floating shelves over valleys
  private val attachDepth: Double = 10.0,      // needs this much mountain above shelf to "attach"
  private val maxAttachDepth: Double = 60.0,   // optional: don't form deep inside rock

  // underside lip
  private val underOffset: Double = 10.0,
  private val underHalfWidth: Double = 4.0,
  private val underCarveStrength: Double = 10.0
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
    // Base convention: base = surfaceY - y  => surfaceY = y + base
    val surfaceY = y.toDouble() + baseStack.base

    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val H = (maxY - minY + 1).toDouble()

    // 1) Where shelves exist (XZ only)
    val maskN = ctx.noise.get(mask2D).noise2D(worldX, worldZ) // [-1..1]
    val mask01 = 1.0 - abs(maskN)                             // [0..1] ridged-ish
    val presence = ((mask01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val presenceShaped = presence * presence
    if (presenceShaped <= 0.0) return

    // 2) Choose a shelf plane height (XZ only), snapped to steps
    val hN = ctx.noise.get(height2D).noise2D(worldX + 1337, worldZ - 777) // offset to decorrelate
    var shelfY = surfaceY - dropBelowSurface + (hN * heightAmp)
    shelfY = snapToStep(shelfY, stepBlocks)

    // 3) Attach factor: only form where the mountain surface is ABOVE the shelf plane
    val depth = surfaceY - shelfY
    if (depth <= 0.0) return // shelf plane is above surface here -> would float
    if (depth >= maxAttachDepth) return // optional safety: don't make shelves deep inside

    val attach01 = smoothstep01((depth / attachDepth).coerceIn(0.0, 1.0))

    // 4) The slab itself (flat band around shelfY)
    val slab = band(
      center01 = (shelfY - minY) / H,
      halfWidth01 = halfWidthBlocks / H,
      y01 = (y - minY) / H
    )

    val add = presenceShaped  * slab * strength
    out.addAdditive(add)

    // 5) Carve slightly underneath to separate it visually
    val under = band(
      center01 = ((shelfY - underOffset) - minY) / H,
      halfWidth01 = underHalfWidth / H,
      y01 = (y - minY) / H
    )
    val carve = presenceShaped * attach01 * under * underCarveStrength
    out.addCarve(carve)
  }

  private fun snapToStep(y: Double, step: Double): Double {
    if (step <= 0.0) return y
    return floor(y / step) * step
  }
}
