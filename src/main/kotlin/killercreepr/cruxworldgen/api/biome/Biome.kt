package killercreepr.cruxworldgen.api.biome

import killercreepr.cruxworldgen.api.cave.CaveShape
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.material.MaterialProvider
import killercreepr.cruxworldgen.core.feature.PlacedFeature

interface Biome {
  val shape: BiomeShape
  val materialProvider: MaterialProvider
  val caves: CaveShape?
  val decorations: List<Decoration>
    get() = listOf()
  val features: List<PlacedFeature<*>>
    get() = listOf()

  val rarityWeight: Double
    get() = 1.0

  interface Noised : Biome, killercreepr.cruxworldgen.api.noise.Noised
}