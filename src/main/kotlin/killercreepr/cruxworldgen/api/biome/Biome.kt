package killercreepr.cruxworldgen.api.biome

import killercreepr.crux.api.data.CruxKeyed
import killercreepr.cruxworldgen.api.cave.CaveShape
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.decor.VolumetricDecoration
import killercreepr.cruxworldgen.api.feature.PlacedFeature
import killercreepr.cruxworldgen.api.material.MaterialProvider

interface Biome {
  val shape: BiomeShape
  val fineShape: FineBiomeShape?
    get() = null
  val materialProvider: MaterialProvider
  val caves: CaveShape<*, *>?
    get() = null
  val decorations: List<Decoration>
    get() = listOf()
  val volumetricDecorations: List<VolumetricDecoration>
    get() = listOf()
  val features: List<PlacedFeature<*>>
    get() = listOf()

  val rarityWeight: Double
    get() = 1.0

  interface Noised : Biome, killercreepr.cruxworldgen.api.noise.Noised
  interface Keyed : Biome, CruxKeyed
}