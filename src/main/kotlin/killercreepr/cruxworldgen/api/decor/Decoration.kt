package killercreepr.cruxworldgen.api.decor

import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample

interface Decoration {
  val pass: DecorationPass

  /** Controls distribution. Examples: grid spacing, noise chance, biome-weight scaling */
  fun shouldTry(ctx: GenerateContext, point: PropPoint, biomeBlend: BiomeBlendSample): Boolean

  /** Pattern scan: find an anchor/placement candidate */
  fun findPlacement(ctx: GenerateContext, point: PropPoint, biomeBlend: BiomeBlendSample): Placement?

  /** Apply: place blocks using placement info */
  fun place(ctx: GenerateContext, placement: Placement, biomeBlend: BiomeBlendSample)
}

enum class DecorationPass {
  UNDERGROUND,
  SURFACE,
  POST_SURFACE
}