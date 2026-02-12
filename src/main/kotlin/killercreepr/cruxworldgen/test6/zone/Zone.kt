package killercreepr.cruxworldgen.test6.zone

import killercreepr.cruxworldgen.test6.biome.BiomeRegistry
import killercreepr.cruxworldgen.test6.biome.FjordIce
import killercreepr.cruxworldgen.test6.biome.Mountains
import killercreepr.cruxworldgen.test6.biome.Plains
import killercreepr.cruxworldgen.test6.biome.Plateaus
import killercreepr.cruxworldgen.test6.biome.SpiralHills

interface Zone{
  val biomes : BiomeRegistry
}

class TestZone : Zone{
  override val biomes: BiomeRegistry = BiomeRegistry(listOf(
    Plains(),
    Mountains(),
    Plateaus(),
    SpiralHills(),
    FjordIce()
  ))
}