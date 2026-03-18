package killercreepr.cruxworldgen.api.context

import killercreepr.cruxworldgen.core.context.SimpleBiomeEdgeContext

interface BiomeEdgeContext{
  companion object{
    private val DUMMY = biomeEdgeContext(0.0, 0.0)
    fun dummy(): BiomeEdgeContext = DUMMY

    fun biomeEdgeContext(
      distanceToEdgeBlocks: Double,
      blendRadiusBlocks: Double
    ) = SimpleBiomeEdgeContext(distanceToEdgeBlocks, blendRadiusBlocks)
  }

  val distanceToEdgeBlocks: Double
  val blendRadiusBlocks: Double

  fun edgeBlendFactor(): Double {
    // 1 near edge, 0 far inside
    val normalizedDistance = (distanceToEdgeBlocks / blendRadiusBlocks).coerceIn(0.0, 1.0)
    return 1.0 - normalizedDistance
  }
}
