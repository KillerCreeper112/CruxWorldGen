package killercreepr.cruxworldgen.test6.zone

import killercreepr.cruxworldgen.api.biome.BiomeRegistry
import killercreepr.cruxworldgen.api.zone.Zone
import killercreepr.cruxworldgen.test6.biome.CharredWastes

class TestZone : Zone{
  override val biomes: BiomeRegistry = BiomeRegistry.biomeRegistry(listOf(
    //Plains(),
    CharredWastes()
    /*Mountains(),
    Plateaus(),
    SpiralHills(),
    FjordIce()*/
  ))
}