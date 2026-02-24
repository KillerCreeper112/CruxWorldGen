package killercreepr.cruxworldgen.test.zone

import killercreepr.cruxworldgen.api.biome.BiomeRegistry
import killercreepr.cruxworldgen.api.zone.Zone
import killercreepr.cruxworldgen.test.biome.*

class TestZone : Zone{
  override val biomes: BiomeRegistry = BiomeRegistry.biomeRegistry(listOf(
    EldritchWastes(),
    AbyssStart(),
    CharredWastes(),
    ToxicFogBasins(),
    AmplifiedHighlands(
      baseYAboveSea = 0.0
    ),
    Plains(),
    Mountains(),
    Plateaus(),
    SpiralHills(),
    FjordIce()
    /*AmplifiedHighlands(
      baseYAboveSea = 0.0
    ),
    CharredWastes(),
    ToxicFogBasins()*/
  ), 256, 32.0)
}