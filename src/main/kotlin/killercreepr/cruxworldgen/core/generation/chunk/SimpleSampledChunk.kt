package killercreepr.cruxworldgen.core.generation.chunk

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.terrain.TerrainSnapshot
import killercreepr.cruxworldgen.api.context.volumetric.VolBiomeBlendSample
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.generation.chunk.SampledChunk

data class SimpleSampledChunk(
  override val ctx: GenerateContext,
  override val density: DoubleArray,
  override val surfaceY: IntArray,
  override val surfaceBlend: Array<BiomeBlendSample?>,
  override val dominantBiomeByBlock: Array<Biome?>,
  override val volBiomeCorners: Array<VolBiomeBlendSample?>,
  override val terrainSnapshot: TerrainSnapshot
) : SampledChunk
