package killercreepr.cruxworldgen.test.zone

import killercreepr.cruxworldgen.api.biome.BiomeRegistry
import killercreepr.cruxworldgen.api.zone.Zone
import killercreepr.cruxworldgen.test.biome.AmplifiedHighlands
import killercreepr.cruxworldgen.test.biome.CharredWastes
import killercreepr.cruxworldgen.test.biome.FjordIce
import killercreepr.cruxworldgen.test.biome.Mountains
import killercreepr.cruxworldgen.test.biome.PlagueMireHighlands
import killercreepr.cruxworldgen.test.biome.Plains
import killercreepr.cruxworldgen.test.biome.Plateaus
import killercreepr.cruxworldgen.test.biome.SpiralHills
import killercreepr.cruxworldgen.test.biome.ToxicFogBasins

class TestZone : Zone{
  override val biomes: BiomeRegistry = BiomeRegistry.biomeRegistry(listOf(
    AmplifiedHighlands(),
  ), 256, 32.0)
}