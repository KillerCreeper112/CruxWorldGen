package killercreepr.cruxworldgen.api.biome

import killercreepr.cruxworldgen.api.cave.CaveShape
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.material.MaterialProvider

interface Biome {
  val shape: BiomeShape
  val materialProvider: MaterialProvider
  val caves: CaveShape?
  val decorations: List<Decoration>
    get() = listOf()
}