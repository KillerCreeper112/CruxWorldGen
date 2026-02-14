package killercreepr.cruxworldgen.test6.zone

import killercreepr.cruxworldgen.test6.FungalPillars
import killercreepr.cruxworldgen.test6.biome.BiomeRegistry
import killercreepr.cruxworldgen.test6.biome.CharredWastes
import killercreepr.cruxworldgen.test6.biome.FjordIce
import killercreepr.cruxworldgen.test6.biome.Mountains
import killercreepr.cruxworldgen.test6.biome.OverhangMountains
import killercreepr.cruxworldgen.test6.biome.PlagueMire
import killercreepr.cruxworldgen.test6.biome.PlagueMireHighlands
import killercreepr.cruxworldgen.test6.biome.Plains
import killercreepr.cruxworldgen.test6.biome.Plateaus
import killercreepr.cruxworldgen.test6.biome.SpiralHills
import killercreepr.cruxworldgen.test6.biome.ToxicFogBasins

class TestZone : Zone{
  override val biomes: BiomeRegistry = BiomeRegistry(listOf(
    //Plains(),
    CharredWastes()
    /*Mountains(),
    Plateaus(),
    SpiralHills(),
    FjordIce()*/
  ))
}