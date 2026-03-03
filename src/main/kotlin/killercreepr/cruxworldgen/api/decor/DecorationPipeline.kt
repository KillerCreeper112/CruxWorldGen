package killercreepr.cruxworldgen.api.decor

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.biome.volumetric.VolumetricBiome
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.prop.PropPointGrid

interface DecorationPipeline{
  val grid: PropPointGrid

  fun runAllPasses(
    region : LimitedRegion,
    chunkX: Int,
    chunkZ: Int,
    sampleBlendAt: (worldX: Int, worldZ: Int) -> BiomeBlendSample,
    sampleBiomeAt: (Int, Int, Int) -> Biome,
    sampleSurfaceYAt: (Int, Int) -> Int
  )

  fun runVolumetricDecorationsPass(
    region: LimitedRegion,
    chunkX: Int,
    chunkZ: Int,
    points: List<PropPoint>,
    pass: DecorationPass,
    sampleBlendAt: (Int, Int) -> BiomeBlendSample,
    sampleBiomeAt: (Int, Int, Int) -> Biome,
    sampleSurfaceYAt: (Int, Int) -> Int
  )
}
