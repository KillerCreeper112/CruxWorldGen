package killercreepr.cruxworldgen.test6.biome

import killercreepr.cruxworldgen.test6.context.GenerateContext
import killercreepr.cruxworldgen.test6.density.DensityBank

interface BiomeFeature {
  fun applyDensity(genCtx : GenerateContext, density : DensityBank, x : Int, y : Int, z : Int)
}