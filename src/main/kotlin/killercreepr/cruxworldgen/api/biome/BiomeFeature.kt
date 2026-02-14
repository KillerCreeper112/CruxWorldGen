package killercreepr.cruxworldgen.api.biome

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.density.DensityBank


interface BiomeFeature {
  fun applyDensity(genCtx : GenerateContext, density : DensityBank, x : Int, y : Int, z : Int)
}