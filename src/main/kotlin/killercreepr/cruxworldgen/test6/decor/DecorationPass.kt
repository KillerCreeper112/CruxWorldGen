package killercreepr.cruxworldgen.test6.decor

import killercreepr.cruxworldgen.test6.biome.BiomeBlendSample
import killercreepr.cruxworldgen.test6.context.GenerateContext
import killercreepr.cruxworldgen.test6.prop.PropPoint

enum class DecorationPass {
  UNDERGROUND,
  SURFACE,
  POST_SURFACE
}

interface Decoration {
  val pass: DecorationPass

  /** Controls distribution. Examples: grid spacing, noise chance, biome-weight scaling */
  fun shouldTry(ctx: GenerateContext, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean

  /** Pattern scan: find an anchor/placement candidate */
  fun findPlacement(ctx: GenerateContext, point: PropPoint, biomeBlend: BiomeBlendSample): Placement?

  /** Apply: place blocks using placement info */
  fun place(ctx: GenerateContext, placement: Placement, biomeBlend: BiomeBlendSample)
}

interface Placement
