package killercreepr.cruxworldgen.core.biome.shape

import killercreepr.cruxworldgen.api.biome.BiomeShapeType
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.density.DensityBank
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve.band

class BridgeLayerModifier(
  private val mask2D: NoiseKey,
  private val cave3D: NoiseKey,
  private val pillar2D: NoiseKey,

  private val maskThreshold: Double = 0.4,
  private val slabStrength: Double = 30.0,
  private val slabHalfWidth: Double = 10.0,

  private val bridgeBelowSurface: Double = 6.0, // put bridge near top of mountain
  private val minSurfaceForBridge: Double = 50.0, // only tall mountains

  private val caveThreshold: Double = 0.20,
  private val caveStrength: Double = 34.0,

  private val pillarStrength: Double = 20.0,
  private val pillarRadius: Double = 5.0
) : BiomeShapeType {

  override fun density(
    ctx: GenerateContext, worldX: Int, y: Int, worldZ: Int,
    edge: BiomeEdgeContext, signalWriter: SignalWriter,
    baseStack: DensityStack, out: DensityBank
  ) {
    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val H = (maxY - minY + 1).toDouble()
    val y01 = (y - minY) / H

    val surfaceY = y.toDouble() + baseStack.base
    if (surfaceY < minSurfaceForBridge) return

    // big region mask (XZ)
    val mN = ctx.noise.get(mask2D).noise2D(worldX, worldZ) // -1..1
    val m01 = 1.0 - kotlin.math.abs(mN)
    val region = ((m01 - maskThreshold) / (1.0 - maskThreshold)).coerceIn(0.0, 1.0)
    val region2 = region * region
    if (region2 <= 0.0) return

    // bridge slab centered near surface
    val bridgeY = surfaceY - bridgeBelowSurface
    val slab = band(
      center01 = (bridgeY - minY) / H,
      halfWidth01 = slabHalfWidth / H,
      y01 = y01
    )
    out.addAdditive(region2 * slab * slabStrength)

    // carve “holes” inside slab to create windows/arches
    val c = ctx.noise.get(cave3D).noise3D(worldX, y, worldZ) // -1..1
    val cave01 = ((c - caveThreshold) / (1.0 - caveThreshold)).coerceIn(0.0, 1.0)
    out.addCarve(region2 * slab * cave01 * caveStrength)

    // optional pillars: add vertical supports where a 2D mask is strong
    val pN = ctx.noise.get(pillar2D).noise2D(worldX, worldZ)
    val p01 = (1.0 - kotlin.math.abs(pN)).coerceIn(0.0, 1.0)
    val pillar = ((p01 - 0.75) / 0.25).coerceIn(0.0, 1.0)

    // apply pillar mostly below bridge
    val below = ((bridgeY - y.toDouble()) / 80.0).coerceIn(0.0, 1.0)
    out.addAdditive(region2 * pillar * below * pillarStrength)
  }
}
