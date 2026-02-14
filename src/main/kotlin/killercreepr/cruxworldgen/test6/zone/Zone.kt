package killercreepr.cruxworldgen.test6.zone

import killercreepr.cruxworldgen.api.biome.BiomeRegistry
import killercreepr.cruxworldgen.api.zone.Zone
import killercreepr.cruxworldgen.test6.biome.CharredWastes
import killercreepr.cruxworldgen.test6.biome.FjordIce
import killercreepr.cruxworldgen.test6.biome.Mountains
import killercreepr.cruxworldgen.test6.biome.Plains
import killercreepr.cruxworldgen.test6.biome.Plateaus
import killercreepr.cruxworldgen.test6.biome.SpiralHills

class TestZone : Zone{
  override val biomes: BiomeRegistry = BiomeRegistry.biomeRegistry(listOf(
    Plains(),
    CharredWastes(),
    Mountains(),
    Plateaus(),
    SpiralHills(),
    FjordIce()
  ), 256, 32.0)
}