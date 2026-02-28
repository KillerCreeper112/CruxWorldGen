package killercreepr.cruxworldgen.api.generation.chunk

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.terrain.TerrainSnapshot
import killercreepr.cruxworldgen.api.context.volumetric.VolBiomeBlendSample
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample

interface SampledChunk{
  val ctx: GenerateContext
  val density: DoubleArray
  val surfaceY: IntArray
  val surfaceBlend: Array<BiomeBlendSample?>
  val dominantBiomeByBlock: Array<Biome?>
  val volBiomeCorners: Array<VolBiomeBlendSample?>
  val terrainSnapshot: TerrainSnapshot
}