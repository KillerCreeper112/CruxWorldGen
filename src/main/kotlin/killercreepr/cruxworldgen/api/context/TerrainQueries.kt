package killercreepr.cruxworldgen.api.context

import killercreepr.cruxworldgen.api.cave.CavePocket

interface TerrainQueries{
  val ctx: GenerateContext

  /** Finds the topmost solid block in this (localX, localZ) column with air above it. */
  fun surfaceY(localX: Int, localZ: Int): Int

  /** True surface for trees/etc: topmost solid block that has an open air column to the top of the world. */
  fun skySurfaceY(localX: Int, localZ: Int, maxAirCheck: Int = 128): Int

  /** Convenience: world coords -> local coords inside THIS chunk; returns null if not in chunk. */
  fun surfaceYWorld(worldX: Int, worldZ: Int): Int?

  fun depthBelowSurface(y: Int, surfaceY: Int): Int = surfaceY - y

  /** Counts air blocks straight up (stops at first solid or maxY). */
  fun airBlocksAbove(localX: Int, y: Int, localZ: Int, maxCount: Int = 255): Int
  /** Counts air blocks straight down (stops at first solid or minY). */
  fun airBlocksBelow(localX: Int, y: Int, localZ: Int, maxCount: Int = 255): Int

  fun slopeBlocks(localX: Int, localZ: Int): Double


  /** A quick slope metric based on nearby surfaceY differences. Returns 0..1-ish. */
  fun slope01(localX: Int, localZ: Int): Double

  fun isUnderwater(surfaceY: Int): Boolean

  /**
   * Finds an enclosed air pocket below the surface:
   * - starts a little below the surface
   * - looks for air with solid under it (floor)
   * - climbs through air to find ceiling solid
   * - validates gap range
   */
  fun findCavePocket(
    localX: Int,
    localZ: Int,
    surfaceY: Int = surfaceY(localX, localZ),
    minGap: Int,
    maxGap: Int,
    searchDepthStartBelowSurface: Int = 6
  ): CavePocket?

  /** Utility: “near solid” for placing things inside caves so they hug walls. */
  fun nearSolid(localX: Int, y: Int, localZ: Int, radius: Int = 1): Boolean
}