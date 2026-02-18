package killercreepr.cruxworldgen.core.biome.shape

import killercreepr.cruxworldgen.api.biome.BiomeShapeType
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.density.DensityBank
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve.band
import killercreepr.cruxworldgen.api.util.NoiseShaper
import killercreepr.cruxworldgen.api.util.NoiseShaper.Point
import killercreepr.cruxworldgen.api.util.NoiseShaper.ShapingFunction
import kotlin.math.abs
import kotlin.math.pow

class OverhangModifier(
  private val noiseKey: NoiseKey,
  private val strength: Double = 18.0,
  private val threshold: Double = 0.45,
  private val centerOffset: Double = 4.0,
  private val halfWidthBlocks: Double = 10.0
) : BiomeShapeType {

  val shaper = NoiseShaper(
    listOf(
      Point(-1.0, ShapingFunction.VALLEY),
      Point(-0.3, ShapingFunction.FLAT),
      Point(0.0, ShapingFunction.FLAT),
      Point(0.7, ShapingFunction.FLAT),
      Point(0.8, ShapingFunction.HILLS),
      Point(1.0, ShapingFunction.MOUNTAIN)
    )
  )

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
    val surfaceY = y.toDouble() + baseStack.base

    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val H = maxY - minY + 1

    val n = shaper.shape(ctx.noise.get(noiseKey).noise3D(worldX, y, worldZ))
    val ridged = 1.0 - abs(n)
    val m = band(
      center01 = ((surfaceY - 6.0) - minY) / H.toDouble(),
      halfWidth01 = 10.0 / H.toDouble(),
      y01 = (y - minY) / H.toDouble()
    )
    val t = 0.45
    val over = ((ridged - t) / (1.0 - t)).coerceIn(0.0, 1.0)

    val overShaped = over.pow(1.6)
    val overhangAdd = overShaped * m * 18.0

    val under = band(
      center01 = ((surfaceY - 16.0) - minY) / H.toDouble(),
      halfWidth01 = 6.0 / H.toDouble(),
      y01 = (y - minY) / H.toDouble()
    )
    val underCarve = overShaped * under * 10.0

    out.addAdditive(overhangAdd)
    out.addCarve(underCarve)
  }
}
