package killercreepr.cruxworldgen.api.cave

import killercreepr.crux.core.Crux
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext

interface CaveShape<CornerCache : Any, BlockCache : Any> {
  fun coarseCache(
    ctx: GenerateContext,
    worldX: Int,
    worldY: Int,
    worldZ: Int,
    terrainDensity: Double
  ): CornerCache?

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
  ): BlockCache?

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
    //todo bad bandaid fix
    return runCatching {
      interpolateCache(
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
    }.getOrNull()
  }
  @Suppress("UNCHECKED_CAST")
  fun carveUntyped(ctx: GenerateContext, cave: CaveContext, cache: Any?) = runCatching { carve(ctx, cave, cache as? BlockCache) }.getOrElse{0.0}
  @Suppress("UNCHECKED_CAST")
  fun addUntyped(ctx: GenerateContext, cave: CaveContext, cache: Any?) = runCatching { add(ctx, cave, cache as? BlockCache) }.getOrElse{0.0}

  fun carve(ctx: GenerateContext, cave: CaveContext, cache: BlockCache?): Double = 0.0
  fun add(ctx: GenerateContext, c: CaveContext, cache: BlockCache?): Double = 0.0

  fun carve(ctx: GenerateContext, cave: CaveContext): Double
  fun add(ctx: GenerateContext, c: CaveContext): Double = 0.0
}