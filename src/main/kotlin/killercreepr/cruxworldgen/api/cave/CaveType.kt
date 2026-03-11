package killercreepr.cruxworldgen.api.cave

import killercreepr.cruxworldgen.api.cache.CoarseCache
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.standard.cave.CellCorners3D
import killercreepr.cruxworldgen.standard.cave.CornerField3D

interface CaveType {
  fun coarseCache(ctx: GenerateContext, worldX: Int, worldY: Int, worldZ: Int, terrainDensity: Double): CoarseCache?  = null

  fun carveBlocks(ctx: GenerateContext, cave: CaveContext,
                  cache: CellCorners3D): Double = 0.0

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

  interface HasSurfaceOpenings : CaveType {
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