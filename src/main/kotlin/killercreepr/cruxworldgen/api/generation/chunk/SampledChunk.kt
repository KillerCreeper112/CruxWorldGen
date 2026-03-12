package killercreepr.cruxworldgen.api.generation.chunk

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.terrain.TerrainSnapshot
import killercreepr.cruxworldgen.api.context.volumetric.VolBiomeBlendSample
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import java.util.*

interface SampledChunk{
  val ctx: GenerateContext
  val densityTerrainMacroByCorner: DoubleArray
  val densityCavesMacroByCorner: DoubleArray
  val terrainSnapshot: TerrainSnapshot
  val surfaceBlendByCornerColumn: Array<BiomeBlendSample?>
  val volumetricBlendByCorner: Array<VolBiomeBlendSample?>

  val densityByBlock: DoubleArray
  val surfaceYByBlockColumn: IntArray
  val primaryBiomeByBlock: Array<Biome?>
  val solidNoCavesByBlock: BitSet


  /*val density: DoubleArray
  val surfaceY: IntArray
  val surfaceBlend: Array<BiomeBlendSample?>
  val dominantBiomeByBlock: Array<Biome?>
  val volBiomeCorners: Array<VolBiomeBlendSample?>
  val terrainSnapshot: TerrainSnapshot
  val solidNoCavesByBlock: BitSet
  val terrainMacroByCell: DoubleArray
  val caveMacroByCell: DoubleArray*/
}