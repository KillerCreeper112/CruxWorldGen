package killercreepr.cruxworldgen.api.decor

import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample

interface Decoration {
  val pass: DecorationPass

  /** Controls distribution. Examples: grid spacing, noise chance, biome-weight scaling */
  fun shouldTry(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean

  /** Pattern scan: find an anchor/placement candidate */
  fun findPlacement(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Placement?

  /** Apply: place blocks using placement info */
  fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample)
}

enum class DecorationPass {
  UNDERGROUND,
  SURFACE,
  POST_SURFACE
}