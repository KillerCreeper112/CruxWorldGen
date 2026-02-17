package killercreepr.cruxworldgen.test.zone

import killercreepr.cruxworldgen.api.biome.BiomeRegistry
import killercreepr.cruxworldgen.api.zone.Zone
import killercreepr.cruxworldgen.test.biome.AmplifiedHighlands

class TestZone : Zone{
  override val biomes: BiomeRegistry = BiomeRegistry.biomeRegistry(listOf(
    AmplifiedHighlands(
      baseYAboveSea = 0.0
    ),
  ), 256, 32.0)
}