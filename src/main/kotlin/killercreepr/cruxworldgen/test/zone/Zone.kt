package killercreepr.cruxworldgen.test.zone

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.biome.BiomeRegistry
import killercreepr.cruxworldgen.api.zone.Zone
import killercreepr.cruxworldgen.core.biome.SimpleBiomeRegistry
import killercreepr.cruxworldgen.test.biome.BasaltSpires
import killercreepr.cruxworldgen.test.biome.CharredWastes

class TestZone : Zone{
  override val biomes: BiomeRegistry = SimpleBiomeRegistry(
    biomes = listOf(
      CharredWastes()
      //CharredWastes(),
      //BasaltSpires(),
      //ToxicFogBasins(),
      /*AmplifiedHighlands(
        baseYAboveSea = 0.0
      ),
      CharredWastes(),
      ToxicFogBasins()*/
    ),
    biomeCellSizeBlocks = 256,
    blendRadiusBlocks = 32.0,
    rules = object : SimpleBiomeRegistry.BiomeRuleProvider{
      override fun ruleFor(biome: Biome): SimpleBiomeRegistry.BiomeRule? {
        if(biome is BasaltSpires){
          return SimpleBiomeRegistry.BiomeRule.AnyNeighbour{biome -> biome is CharredWastes}
        }
        return null
      }
    }
  )
}