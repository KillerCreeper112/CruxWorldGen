package killercreepr.cruxworldgen.test.zone

import killercreepr.cruxworldgen.api.biome.BiomeRegistry
import killercreepr.cruxworldgen.api.zone.Zone
import killercreepr.cruxworldgen.test.biome.AbyssStart
import killercreepr.cruxworldgen.test.biome.AmplifiedHighlands
import killercreepr.cruxworldgen.test.biome.CharredWastes
import killercreepr.cruxworldgen.test.biome.EldritchWastes
import killercreepr.cruxworldgen.test.biome.TestAmp
import killercreepr.cruxworldgen.test.biome.ToxicFogBasins

class TestZone : Zone{
  override val biomes: BiomeRegistry = BiomeRegistry.biomeRegistry(listOf(
    EldritchWastes(),
    AbyssStart(),
    CharredWastes(),
    /*AmplifiedHighlands(
      baseYAboveSea = 0.0
    ),
    CharredWastes(),
    ToxicFogBasins()*/
  ), 256, 32.0)
}