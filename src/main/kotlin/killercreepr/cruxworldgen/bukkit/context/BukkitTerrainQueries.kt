package killercreepr.cruxworldgen.bukkit.context

import killercreepr.cruxworldgen.api.cave.CavePocket
import killercreepr.cruxworldgen.api.context.TerrainQueries
import killercreepr.cruxworldgen.bukkit.region.BukkitLimitedRegion
import org.bukkit.Material
import kotlin.math.sqrt

class BukkitTerrainQueries(
  val region : BukkitLimitedRegion
) : TerrainQueries {
  override fun isReplaceable(worldX: Int, worldY: Int, worldZ: Int): Boolean = isEmpty(worldX, worldY, worldZ) //todo proper implementation for isReplaceable

  override fun isEmpty(worldX: Int, worldY: Int, worldZ: Int): Boolean = region.region.getType(worldX, worldY, worldZ).isEmpty

  override fun isSolid(worldX: Int, worldY: Int, worldZ: Int): Boolean = region.region.getType(worldX, worldY, worldZ).isSolid

  override fun isLiquid(worldX: Int, worldY: Int, worldZ: Int): Boolean {
    val type = region.region.getType(worldX, worldY, worldZ)
    return when(type){
      Material.WATER, Material.LAVA -> true
      else -> false
    }
  }

  /** Finds the topmost solid block in this (localX, localZ) column with air above it. */
  override fun surfaceY(worldX: Int, worldZ: Int): Int {
    for(i in region.regionBounds.maxY..region.regionBounds.minY){
      if(isSolid(worldX, i, worldZ)) return i
    }
    return region.regionBounds.minY
  }

  /** True surface for trees/etc: topmost solid block that has an open air column to the top of the world. */
  override fun skySurfaceY(worldX: Int, worldZ: Int, maxAirCheck: Int): Int {
    val minY = region.regionBounds.minY
    val topY = region.regionBounds.maxY

    for (y in (topY - 1) downTo (minY + 1)) {
      if (!isSolid(worldX, y, worldZ)) continue
      if (!isEmpty(worldX, y + 1, worldZ)) continue

      // ensure "sky exposure": above must remain air (up to some limit)
      var air = 0
      var yy = y + 1
      while (yy <= topY && air < maxAirCheck) {
        if (!isEmpty(worldX, yy, worldZ)) {
          air = -999 // blocked
          break
        }
        air++
        yy++
      }

      if (air >= 0) return y
    }
    return minY
  }

  /** Counts air blocks straight up (stops at first solid or maxY). */
  override fun airBlocksAbove(
    worldX: Int,
    worldY: Int,
    worldZ: Int,
    maxCount: Int
  ): Int {
    val maxY = region.regionBounds.maxY
    var count = 0
    var yy = worldY + 1
    while (yy <= maxY && count < maxCount) {
      if (!isEmpty(worldX, yy, worldZ)) break
      count++
      yy++
    }
    return count
  }

  /** Counts air blocks straight down (stops at first solid or minY). */
  override fun airBlocksBelow(
    worldX: Int,
    worldY: Int,
    worldZ: Int,
    maxCount: Int
  ): Int {
    val minY = region.regionBounds.minY
    var count = 0
    var yy = worldY - 1
    while (yy >= minY && count < maxCount) {
      if (!isEmpty(worldX, yy, worldZ)) break
      count++
      yy--
    }
    return count
  }

  /** A quick slope metric based on nearby surfaceY differences. Returns 0..1-ish. */
  override fun slope01(worldX: Int, worldZ: Int): Double {
    // 4-neighbor sample (clamped to chunk bounds)
    val sx1 = surfaceY((worldX - 1).coerceIn(
      region.regionBounds.minX, region.regionBounds.maxX
    ), worldZ)
    val sx2 = surfaceY((worldX + 1).coerceIn(
      region.regionBounds.minX, region.regionBounds.maxX
    ), worldZ)
    val sz1 = surfaceY(worldZ, (worldZ - 1).coerceIn(
      region.regionBounds.minZ, region.regionBounds.maxZ
    ))
    val sz2 = surfaceY(worldZ, (worldZ + 1).coerceIn(
      region.regionBounds.minZ, region.regionBounds.maxZ
    ))

    val dx = (sx2 - sx1).toDouble() * 0.5
    val dz = (sz2 - sz1).toDouble() * 0.5

    // Convert gradient magnitude into 0..1 range (tune divisor)
    val grad = sqrt(dx * dx + dz * dz)
    return (grad / 6.0).coerceIn(0.0, 1.0) // 6 blocks per step ~= "steep"
  }

  /**
   * Finds an enclosed air pocket below the surface:
   * - starts a little below the surface
   * - looks for air with solid under it (floor)
   * - climbs through air to find ceiling solid
   * - validates gap range
   */
  override fun findCavePocket(
    worldX: Int,
    worldZ: Int,
    surfaceY: Int,
    minGap: Int,
    maxGap: Int,
    searchDepthStartBelowSurface: Int
  ): CavePocket? {
    TODO("Not yet implemented")
  }

  /** Utility: “near solid” for placing things inside caves so they hug walls. */
  override fun nearSolid(
    worldX: Int,
    worldY: Int,
    worldZ: Int,
    radius: Int
  ): Boolean {
    TODO("Not yet implemented")
  }
}

/*
package killercreepr.cruxworldgen.bukkit.context

import killercreepr.cruxworldgen.api.cave.CavePocket
import killercreepr.cruxworldgen.api.context.ChunkContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.TerrainQueries
import kotlin.math.sqrt

class BukkitTerrainQueries() : TerrainQueries {
  override fun surfaceY(localX: Int, localZ: Int): Int {
    val minY = chunk.minHeight
    val maxY = chunk.maxHeight - 1

    for (y in (maxY - 1) downTo (minY + 1)) {
      if (chunk.isSolid(localX, y, localZ) && chunk.isEmpty(localX, y + 1, localZ)) {
        return y
      }
    }
    return minY
  }


  override fun skySurfaceY(localX: Int, localZ: Int, maxAirCheck: Int): Int {
    val minY = chunk.minHeight
    val topY = chunk.maxHeight - 1

    for (y in (topY - 1) downTo (minY + 1)) {
      if (!chunk.isSolid(localX, y, localZ)) continue
      if (!chunk.isEmpty(localX, y + 1, localZ)) continue

      // ensure "sky exposure": above must remain air (up to some limit)
      var air = 0
      var yy = y + 1
      while (yy <= topY && air < maxAirCheck) {
        if (!chunk.isEmpty(localX, yy, localZ)) {
          air = -999 // blocked
          break
        }
        air++
        yy++
      }

      if (air >= 0) return y
    }
    return minY
  }

  override fun surfaceYWorld(worldX: Int, worldZ: Int): Int? {
    val chunkWorldX = ctx.chunkX * ctx.chunkContext.width
    val chunkWorldZ = ctx.chunkZ * ctx.chunkContext.depth
    val localX = worldX - chunkWorldX
    val localZ = worldZ - chunkWorldZ
    if (localX !in 0..<ctx.chunkContext.width || localZ !in 0..<ctx.chunkContext.depth) return null
    return surfaceY(localX, localZ)
  }

  override fun depthBelowSurface(y: Int, surfaceY: Int): Int = surfaceY - y

  override fun airBlocksAbove(localX: Int, y: Int, localZ: Int, maxCount: Int): Int {
    val maxY = chunk.maxHeight - 1
    var count = 0
    var yy = y + 1
    while (yy <= maxY && count < maxCount) {
      if (!chunk.isEmpty(localX, yy, localZ)) break
      count++
      yy++
    }
    return count
  }

  override fun airBlocksBelow(localX: Int, y: Int, localZ: Int, maxCount: Int): Int {
    val minY = chunk.minHeight
    var count = 0
    var yy = y - 1
    while (yy >= minY && count < maxCount) {
      if (!chunk.isEmpty(localX, yy, localZ)) break
      count++
      yy--
    }
    return count
  }

  override fun slopeBlocks(localX: Int, localZ: Int): Double {
    val width = ctx.chunkContext.width-1
    val depth = ctx.chunkContext.depth-1

    val sx1 = surfaceY((localX - 1).coerceIn(0, width), localZ)
    val sx2 = surfaceY((localX + 1).coerceIn(0, width), localZ)
    val sz1 = surfaceY(localX, (localZ - 1).coerceIn(0, depth))
    val sz2 = surfaceY(localX, (localZ + 1).coerceIn(0, depth))
    val dx = (sx2 - sx1).toDouble() * 0.5
    val dz = (sz2 - sz1).toDouble() * 0.5
    return kotlin.math.sqrt(dx*dx + dz*dz)
  }

  override fun slope01(localX: Int, localZ: Int): Double {
    val center = surfaceY(localX, localZ)
    val width = ctx.chunkContext.width-1
    val depth = ctx.chunkContext.depth-1

    // 4-neighbor sample (clamped to chunk bounds)
    val sx1 = surfaceY((localX - 1).coerceIn(0, width), localZ)
    val sx2 = surfaceY((localX + 1).coerceIn(0, width), localZ)
    val sz1 = surfaceY(localX, (localZ - 1).coerceIn(0, depth))
    val sz2 = surfaceY(localX, (localZ + 1).coerceIn(0, depth))

    val dx = (sx2 - sx1).toDouble() * 0.5
    val dz = (sz2 - sz1).toDouble() * 0.5

    // Convert gradient magnitude into 0..1 range (tune divisor)
    val grad = sqrt(dx * dx + dz * dz)
    return (grad / 6.0).coerceIn(0.0, 1.0) // 6 blocks per step ~= "steep"
  }

  override fun isUnderwater(surfaceY: Int): Boolean {
    return surfaceY < chunk.seaLevel
  }

  override fun findCavePocket(
    localX: Int,
    localZ: Int,
    surfaceY: Int,
    minGap: Int,
    maxGap: Int,
    searchDepthStartBelowSurface: Int
  ): CavePocket? {
    val minY = chunk.minHeight
    val maxY = chunk.maxHeight - 1

    var y = (surfaceY - searchDepthStartBelowSurface).coerceAtMost(maxY - 2)
    y = y.coerceAtLeast(minY + 2)

    while (y > minY + 2) {

      // air block with solid below => start of pocket
      if (chunk.isEmpty(localX, y, localZ) && chunk.isSolid(localX, y - 1, localZ)) {
        val floorY = y - 1

        // walk upward through air
        var topAirY = y
        while (topAirY < maxY && chunk.isEmpty(localX, topAirY, localZ)) {
          topAirY++
        }

        val ceilingY = topAirY

        // must be enclosed by solid ceiling and below surface
        if (ceilingY < maxY &&
          chunk.isSolid(localX, ceilingY, localZ) &&
          ceilingY < surfaceY - 1
        ) {
          val gap = ceilingY - floorY - 1
          if (gap in minGap..maxGap) return CavePocket.cavePocket(floorY, ceilingY)
        }

        // continue searching deeper
        y = floorY - 1
        continue
      }

      y--
    }

    return null
  }

  override fun nearSolid(localX: Int, y: Int, localZ: Int, radius: Int): Boolean {
    val chunk = ctx.chunkContext
    for (dx in -radius..radius) {
      for (dz in -radius..radius) {
        if (dx == 0 && dz == 0) continue
        val x = localX + dx
        val z = localZ + dz
        if (x !in 0..<chunk.width || z !in 0..<chunk.depth) continue
        if (chunk.isSolid(x, y, z)) return true
      }
    }
    return false
  }
}
*/
