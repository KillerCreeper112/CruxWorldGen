package killercreepr.cruxworldgen.api.context

import killercreepr.cruxworldgen.api.cave.CavePocket

interface TerrainQueries{
  fun isReplaceable(worldX: Int, worldY: Int, worldZ: Int): Boolean
  fun isEmpty(worldX: Int, worldY: Int, worldZ: Int): Boolean
  fun isSolid(worldX: Int, worldY: Int, worldZ: Int): Boolean
  fun isLiquid(worldX: Int, worldY: Int, worldZ: Int): Boolean

  /** Finds the topmost solid block in this (localX, localZ) column with air above it. */
  fun surfaceY(worldX: Int, worldZ: Int): Int

  /** True surface for trees/etc: topmost solid block that has an open air column to the top of the world. */
  fun skySurfaceY(worldX: Int, worldZ: Int, maxAirCheck: Int = 128): Int

  /** Counts air blocks straight up (stops at first solid or maxY). */
  fun airBlocksAbove(worldX: Int, worldY: Int, worldZ: Int, maxCount: Int = 255): Int
  /** Counts air blocks straight down (stops at first solid or minY). */
  fun airBlocksBelow(worldX: Int, worldY: Int, worldZ: Int, maxCount: Int = 255): Int

  /** A quick slope metric based on nearby surfaceY differences. Returns 0..1-ish. */
  fun slope01(worldX : Int, worldZ : Int): Double

  /**
   * Finds an enclosed air pocket below the surface:
   * - starts a little below the surface
   * - looks for air with solid under it (floor)
   * - climbs through air to find ceiling solid
   * - validates gap range
   */
  fun findCavePocket(
    worldX: Int, worldZ: Int,
    surfaceY: Int = surfaceY(worldX, worldZ),
    minGap: Int,
    maxGap: Int,
    searchDepthStartBelowSurface: Int = 6
  ): CavePocket?

  /** Utility: “near solid” for placing things inside caves so they hug walls. */
  fun nearSolid(worldX: Int, worldY: Int, worldZ: Int, radius: Int = 1): Boolean

  fun findNearestSolidWithAirAbove(worldX: Int, worldY: Int, worldZ: Int, aboveRange: Int = 3, belowRange: Int = 3,
                                   airAbove: Int = 1): Int?

  fun findNearestSolidWithAirBelow(
    worldX: Int,
    worldY: Int,
    worldZ: Int,
    aboveRange: Int = 3,
    belowRange: Int = 3,
    airBelow: Int = 1
  ): Int?
}