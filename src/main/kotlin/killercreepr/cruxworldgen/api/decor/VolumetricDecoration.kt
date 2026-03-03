package killercreepr.cruxworldgen.api.decor

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample

interface VolumetricDecoration {
  val pass: DecorationPass

  /** Controls distribution. Examples: grid spacing, noise chance, biome-weight scaling */
  fun shouldTry(region: LimitedRegion, point: VolumetricPropPoint, biomeBlend: BiomeBlendSample, biome: Biome): Boolean

  /** Pattern scan: find an anchor/placement candidate */
  fun findPlacement(region: LimitedRegion, point: VolumetricPropPoint, biomeBlend: BiomeBlendSample, biome: Biome): Placement?

  /** Apply: place blocks using placement info */
  fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample, biome: Biome)
}