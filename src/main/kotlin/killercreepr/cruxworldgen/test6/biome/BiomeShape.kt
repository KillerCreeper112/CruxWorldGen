package killercreepr.cruxworldgen.test6.biome

import killercreepr.cruxworldgen.test6.context.GenerateContext
import killercreepr.cruxworldgen.test6.density.DensityStack

interface BiomeShape {
  fun density(
    ctx: GenerateContext,
    worldX: Int,
    y: Int,
    worldZ: Int,
    edge: BiomeEdgeContext
  ): DensityStack
}
