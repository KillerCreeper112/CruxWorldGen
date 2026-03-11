package killercreepr.cruxworldgen.api.cave

import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext

interface CaveType<CornerCache: Any, BlockCache: Any> {
  fun coarseCache(
    ctx: GenerateContext,
    worldX: Int,
    worldY: Int,
    worldZ: Int,
    terrainDensity: Double
  ): CornerCache? = null

  fun interpolateCache(
    c000: CornerCache,
    c100: CornerCache,
    c010: CornerCache,
    c110: CornerCache,
    c001: CornerCache,
    c101: CornerCache,
    c011: CornerCache,
    c111: CornerCache,
    tx: Double,
    ty: Double,
    tz: Double
  ): BlockCache? = null

  @Suppress("UNCHECKED_CAST")
  fun interpolateCacheUntyped(
    c000: Any?,
    c100: Any?,
    c010: Any?,
    c110: Any?,
    c001: Any?,
    c101: Any?,
    c011: Any?,
    c111: Any?,
    tx: Double,
    ty: Double,
    tz: Double
  ): Any? {
    return interpolateCache(
      c000 as CornerCache,
      c100 as CornerCache,
      c010 as CornerCache,
      c110 as CornerCache,
      c001 as CornerCache,
      c101 as CornerCache,
      c011 as CornerCache,
      c111 as CornerCache,
      tx, ty, tz
    )
  }

  fun carveBlocks(ctx: GenerateContext, cave: CaveContext,
                  cache: BlockCache?): Double = 0.0
  fun carveBlocksUntyped(ctx: GenerateContext, cave: CaveContext,
                  cache: Any?): Double = carveBlocks(ctx, cave, cache as? BlockCache)

  @Deprecated("")
  fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double = 0.0

  fun addBlocks(ctx: GenerateContext, cave: CaveContext, add : Double): Double = 0.0

  /**
   * If surfaceFadeStart = 0
   * carving is allowed to start right at the surface.
   *
   * If surfaceFadeStart = 6
   * carving is blocked for the top 6 blocks, and only starts at depth 6.
   */
  val surfaceFadeStart: Int get() = 0//6

  /**
   * how many blocks does it take to ramp up to full strength?
   * Small ramp (like 6–10): caves ramp to full strength quickly
   * -> good for ravines / openings / dramatic mouths
   *
   * Large ramp (like 16–32): caves ramp in slowly
   * -> good for normal caves (surface stays mostly intact)
   */
  val surfaceFadeRamp: Int get() = 16

  interface HasSurfaceOpenings<CornerCache: Any, BlockCache: Any> : CaveType<CornerCache, BlockCache> {
    val surfaceOpenChance: Double
      get() = 0.03
    val surfaceOpenMaxAbove: Int
      get() = 2
    val surfaceOpenMaxBelow: Int
      get() = 18
    val surfaceOpenFeather: Double
      get() = 6.0
  }
}