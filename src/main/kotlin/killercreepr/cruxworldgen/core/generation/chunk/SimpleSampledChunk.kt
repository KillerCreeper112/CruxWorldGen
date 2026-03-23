package killercreepr.cruxworldgen.core.generation.chunk

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.terrain.TerrainSnapshot
import killercreepr.cruxworldgen.api.context.volumetric.VolBiomeBlendSample
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.generation.chunk.SampledChunk
import java.util.*

data class SimpleSampledChunk(
  override val ctx: GenerateContext,
  override val densityTerrainMacroByCorner: DoubleArray,
  override val densityCavesMacroByCorner: DoubleArray,
  override val terrainSnapshot: TerrainSnapshot,
  override val surfaceBlendByCornerColumn: Array<BiomeBlendSample?>,
  override val volumetricBlendByCorner: Array<VolBiomeBlendSample?>,
  override val surfaceYByBlockColumn: IntArray,
  override val densityByBlock: DoubleArray,
  override val primaryBiomeByBlock: Array<Biome?>,
  override val solidNoCavesByBlock: BitSet,
  override val materialBiomeByBlock: Array<Biome?>,
  /*override val ctx: GenerateContext,
  override val density: DoubleArray,
  override val surfaceY: IntArray,
  override val surfaceBlend: Array<BiomeBlendSample?>,
  override val dominantBiomeByBlock: Array<Biome?>,
  override val volBiomeCorners: Array<VolBiomeBlendSample?>,
  override val terrainSnapshot: TerrainSnapshot,
  override val solidNoCavesByBlock: BitSet,
  override val terrainMacroByCell: DoubleArray,
  override val caveMacroByCell: DoubleArray,*/
) : SampledChunk
