package killercreepr.cruxworldgen.api.decor

import killercreepr.cruxworldgen.api.biome.Biome
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.core.decor.SimplePropPoint

interface VolumetricDecoration {
  val pass: DecorationPass

  /** Controls distribution. Examples: grid spacing, noise chance, biome-weight scaling */
  fun shouldTry(region: LimitedRegion, point: VolumetricPropPoint, biomeBlend: BiomeBlendSample, biome: Biome): Boolean

  /** Pattern scan: find an anchor/placement candidate */
  fun findPlacement(region: LimitedRegion, point: VolumetricPropPoint, biomeBlend: BiomeBlendSample, biome: Biome): Placement?

  /** Apply: place blocks using placement info */
  fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample, biome: Biome)

  interface LazyImpl: Decoration, VolumetricDecoration{
    override fun shouldTry(
      region: LimitedRegion,
      point: VolumetricPropPoint,
      biomeBlend: BiomeBlendSample,
      biome: Biome
    ): Boolean {
      return shouldTry(region, SimplePropPoint(point.worldX, point.worldZ, point.localX, point.localZ, point.seed), biomeBlend)
    }

    /** Pattern scan: find an anchor/placement candidate */
    override fun findPlacement(
      region: LimitedRegion,
      point: VolumetricPropPoint,
      biomeBlend: BiomeBlendSample,
      biome: Biome
    ): Placement? {
      return findPlacement(region, SimplePropPoint(point.worldX, point.worldZ, point.localX, point.localZ, point.seed), biomeBlend)
    }

    /** Apply: place blocks using placement info */
    override fun place(
      region: LimitedRegion,
      placement: Placement,
      biomeBlend: BiomeBlendSample,
      biome: Biome
    ) {
      place(region, placement, biomeBlend)
    }
  }
}