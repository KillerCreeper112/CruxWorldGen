package killercreepr.cruxworldgen.core.context

import killercreepr.cruxworldgen.api.context.BiomeEdgeContext

open class SimpleBiomeEdgeContext(
  override val distanceToEdgeBlocks: Double,
  override val blendRadiusBlocks: Double
) : BiomeEdgeContext {
}