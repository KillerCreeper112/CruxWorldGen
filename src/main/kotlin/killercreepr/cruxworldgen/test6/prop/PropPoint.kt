package killercreepr.cruxworldgen.test6.prop

import killercreepr.cruxworldgen.test6.biome.BiomeBlendSample
import killercreepr.cruxworldgen.test6.context.ChunkContext
import killercreepr.cruxworldgen.test6.context.GenerateContext
import org.bukkit.Material
import kotlin.math.sqrt


data class FloorHit(val y: Int)
data class CeilingHit(val y: Int)

data class CavePocket(val floorY: Int, val ceilingY: Int) {
  val gap: Int get() = ceilingY - floorY - 1
}

class TerrainQueries(
  private val ctx: GenerateContext
) {
  private val chunk: ChunkContext get() = ctx.chunkContext

  /** Finds the topmost solid block in this (localX, localZ) column with air above it. */
  fun surfaceY(localX: Int, localZ: Int): Int {
    val minY = chunk.minHeight
    val maxY = chunk.maxHeight - 1

    for (y in (maxY - 1) downTo (minY + 1)) {
      if (chunk.isSolid(localX, y, localZ) && chunk.isAir(localX, y + 1, localZ)) {
        return y
      }
    }
    return minY
  }

  /** Convenience: world coords -> local coords inside THIS chunk; returns null if not in chunk. */
  fun surfaceYWorld(worldX: Int, worldZ: Int): Int? {
    val chunkWorldX = ctx.chunkX * 16
    val chunkWorldZ = ctx.chunkZ * 16
    val localX = worldX - chunkWorldX
    val localZ = worldZ - chunkWorldZ
    if (localX !in 0..15 || localZ !in 0..15) return null
    return surfaceY(localX, localZ)
  }

  fun depthBelowSurface(y: Int, surfaceY: Int): Int = surfaceY - y

  /** Counts air blocks straight up (stops at first solid or maxY). */
  fun airBlocksAbove(localX: Int, y: Int, localZ: Int, maxCount: Int = 255): Int {
    val maxY = chunk.maxHeight - 1
    var count = 0
    var yy = y + 1
    while (yy <= maxY && count < maxCount) {
      if (!chunk.isAir(localX, yy, localZ)) break
      count++
      yy++
    }
    return count
  }

  /** Counts air blocks straight down (stops at first solid or minY). */
  fun airBlocksBelow(localX: Int, y: Int, localZ: Int, maxCount: Int = 255): Int {
    val minY = chunk.minHeight
    var count = 0
    var yy = y - 1
    while (yy >= minY && count < maxCount) {
      if (!chunk.isAir(localX, yy, localZ)) break
      count++
      yy--
    }
    return count
  }

  /** A quick slope metric based on nearby surfaceY differences. Returns 0..1-ish. */
  fun slope01(localX: Int, localZ: Int): Double {
    val center = surfaceY(localX, localZ)

    // 4-neighbor sample (clamped to chunk bounds)
    val sx1 = surfaceY((localX - 1).coerceIn(0, 15), localZ)
    val sx2 = surfaceY((localX + 1).coerceIn(0, 15), localZ)
    val sz1 = surfaceY(localX, (localZ - 1).coerceIn(0, 15))
    val sz2 = surfaceY(localX, (localZ + 1).coerceIn(0, 15))

    val dx = (sx2 - sx1).toDouble() * 0.5
    val dz = (sz2 - sz1).toDouble() * 0.5

    // Convert gradient magnitude into 0..1 range (tune divisor)
    val grad = sqrt(dx * dx + dz * dz)
    return (grad / 6.0).coerceIn(0.0, 1.0) // 6 blocks per step ~= "steep"
  }

  /** Only meaningful once you actually place water. For now: underwater if surface below sea level. */
  fun isUnderwater(surfaceY: Int): Boolean {
    return surfaceY < chunk.seaLevel
  }

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
  ): CavePocket? {
    val minY = chunk.minHeight
    val maxY = chunk.maxHeight - 1

    var y = (surfaceY - searchDepthStartBelowSurface).coerceAtMost(maxY - 2)
    y = y.coerceAtLeast(minY + 2)

    while (y > minY + 2) {

      // air block with solid below => start of pocket
      if (chunk.isAir(localX, y, localZ) && chunk.isSolid(localX, y - 1, localZ)) {
        val floorY = y - 1

        // walk upward through air
        var topAirY = y
        while (topAirY < maxY && chunk.isAir(localX, topAirY, localZ)) {
          topAirY++
        }

        val ceilingY = topAirY

        // must be enclosed by solid ceiling and below surface
        if (ceilingY < maxY &&
          chunk.isSolid(localX, ceilingY, localZ) &&
          ceilingY < surfaceY - 1
        ) {
          val gap = ceilingY - floorY - 1
          if (gap in minGap..maxGap) return CavePocket(floorY, ceilingY)
        }

        // continue searching deeper
        y = floorY - 1
        continue
      }

      y--
    }

    return null
  }

  /** Utility: “near solid” for placing things inside caves so they hug walls. */
  fun nearSolid(localX: Int, y: Int, localZ: Int, radius: Int = 1): Boolean {
    for (dx in -radius..radius) {
      for (dz in -radius..radius) {
        if (dx == 0 && dz == 0) continue
        val x = localX + dx
        val z = localZ + dz
        if (x !in 0..15 || z !in 0..15) continue
        if (chunk.isSolid(x, y, z)) return true
      }
    }
    return false
  }
}


fun findCavePocket(
  chunk: ChunkContext,
  localX: Int,
  localZ: Int,
  minY: Int,
  maxY: Int,
  surfaceY: Int,
  minGap: Int,
  maxGap: Int,
  searchDepthStartBelowSurface: Int = 6 // start looking below surface to avoid “sky”
): CavePocket? {

  // Start below surface so we don't detect the open sky
  var y = (surfaceY - searchDepthStartBelowSurface).coerceAtMost(maxY - 2)
  y = y.coerceAtLeast(minY + 2)

  while (y > minY + 2) {

    // Look for start of an air pocket where below is solid (so it's a floor)
    if (chunk.isAir(localX, y, localZ) && chunk.isSolid(localX, y - 1, localZ)) {
      val floorY = y - 1

      // Walk upward through air until it stops
      var topAirY = y
      while (topAirY < maxY - 1 && chunk.isAir(localX, topAirY, localZ)) {
        topAirY++
      }

      // Now topAirY is first non-air above the pocket (ideally solid ceiling)
      val ceilingY = topAirY

      // Must be enclosed by a solid ceiling, and must be below surface (not sky)
      if (ceilingY < maxY - 1 &&
        chunk.isSolid(localX, ceilingY, localZ) &&
        ceilingY < surfaceY - 1) {

        val gap = ceilingY - floorY - 1
        if (gap in minGap..maxGap) {
          return CavePocket(floorY, ceilingY)
        }
      }

      // Skip past this pocket and continue searching deeper
      y = floorY - 1
      continue
    }

    y--
  }

  return null
}


fun findFloor(chunk: ChunkContext, localX: Int, localZ: Int, minY: Int, maxY: Int): FloorHit? {
  // floor = solid block with air above it
  for (y in (maxY - 2) downTo (minY + 1)) {
    val isSolid = chunk.isSolid(localX, y, localZ)
    val airAbove = chunk.isAir(localX, y + 1, localZ)
    if (isSolid && airAbove) return FloorHit(y)
  }
  return null
}

fun findCeilingAbove(chunk: ChunkContext, localX: Int, localZ: Int, startY: Int, maxY: Int): CeilingHit? {
  // ceiling = solid block with air below it
  for (y in (startY + 2) until (maxY - 1)) {
    val isSolid = chunk.isSolid(localX, y, localZ)
    val airBelow = chunk.isAir(localX, y - 1, localZ)
    if (isSolid && airBelow) return CeilingHit(y)
  }
  return null
}


data class PropPoint(val worldX: Int, val worldZ: Int, val localX: Int, val localZ: Int, val seed: Long)

class PropPointGrid(
  private val spacingBlocks: Int = 8,  // 6..12 typical
  private val jitterBlocks: Int = 3
) {
  fun pointsForChunk(ctx: GenerateContext, chunkX: Int, chunkZ: Int): List<PropPoint> {
    val chunkWorldX = chunkX * 16
    val chunkWorldZ = chunkZ * 16

    val points = ArrayList<PropPoint>()
    val baseSeed = ctx.worldContext.seed

    // cover a little outside chunk so features can straddle borders cleanly
    val startX = chunkWorldX - spacingBlocks
    val startZ = chunkWorldZ - spacingBlocks
    val endX = chunkWorldX + 16 + spacingBlocks
    val endZ = chunkWorldZ + 16 + spacingBlocks

    var gridX = (startX / spacingBlocks) * spacingBlocks
    while (gridX <= endX) {
      var gridZ = (startZ / spacingBlocks) * spacingBlocks
      while (gridZ <= endZ) {

        val pointSeed = hash2D(baseSeed, gridX, gridZ)
        val jitterX = ((pointSeed ushr 0).toInt() and 0x7FFFFFFF) % (jitterBlocks * 2 + 1) - jitterBlocks
        val jitterZ = ((pointSeed ushr 21).toInt() and 0x7FFFFFFF) % (jitterBlocks * 2 + 1) - jitterBlocks

        val worldX = gridX + jitterX
        val worldZ = gridZ + jitterZ

        val localX = worldX - chunkWorldX
        val localZ = worldZ - chunkWorldZ

        if (localX in 0..15 && localZ in 0..15) {
          points.add(PropPoint(worldX, worldZ, localX, localZ, pointSeed))
        }

        gridZ += spacingBlocks
      }
      gridX += spacingBlocks
    }

    return points
  }

  /*private fun hash2D(seed: Long, x: Int, z: Int): Long {
    var value = seed xor (x.toLong() * 0x632BE59BD9B4E019L) xor (z.toLong() * 0x9E3779B97F4A7C15L)
    value = (value xor (value ushr 30)) * 0xBF58476D1CE4E5B9L
    value = (value xor (value ushr 27)) * 0x94D049BB133111EBL
    return value xor (value ushr 31)
  }*/

  private val HASH_SALT: Long = -7046029254386353131L
  private val HASH_MUL_X: Long = 7145483588892929177L
  private val HASH_MIX_1: Long = -4658895280553007687L
  private val HASH_MIX_2: Long = -7723592293110705685L

  private fun hash2D(seed: Long, x: Int, z: Int): Long {
    var value = seed
    value = value xor (x.toLong() * HASH_MUL_X)
    value = value xor (z.toLong() * HASH_SALT)
    value = (value xor (value ushr 30)) * HASH_MIX_1
    value = (value xor (value ushr 27)) * HASH_MIX_2
    return value xor (value ushr 31)
  }
}


class CavernPillarRule(
  private val minGapBlocks: Int = 5,
  private val maxGapBlocks: Int = 40,   // <-- 10 is way too small for most caves
  private val minDepthBelowSurface: Int = 18,
  private val cavernThreshold01: Double = 0.0
) {

  fun tryPlace(ctx: GenerateContext, biomeBlend: BiomeBlendSample, point: PropPoint): Boolean {
    val chunk = ctx.chunkContext
    val minY = chunk.minHeight
    val maxY = chunk.maxHeight

    val surfaceY = estimateSurfaceY(chunk, point.localX, point.localZ, minY, maxY)
    val depthBelowSurface = surfaceY - point.localX // ignore, just showing we computed surface

    // Find an enclosed air pocket to pillar inside
    val pocket = findCavePocket(
      chunk = chunk,
      localX = point.localX,
      localZ = point.localZ,
      minY = minY,
      maxY = maxY,
      surfaceY = surfaceY,
      minGap = minGapBlocks,
      maxGap = maxGapBlocks,
      searchDepthStartBelowSurface = 6
    ) ?: return false

    val floorY = pocket.floorY
    val ceilingY = pocket.ceilingY
    val gap = pocket.gap

    val depth = surfaceY - floorY
    if (depth < minDepthBelowSurface) return false

    // OPTIONAL GATE (turn off until working):
    // val cavernNoise01 = (ctx.noise.cavern3D(point.worldX, floorY + gap / 2, point.worldZ) + 1.0) * 0.5
    // val cavernMask01 = smoothstep01(((cavernNoise01 - cavernThreshold01) / (1.0 - cavernThreshold01)).coerceIn(0.0, 1.0))
    // if (cavernMask01 < 0.55) return false

    // For now, always place to prove it works:
    val radius = 2.0
    placePillar(chunk, point.localX, point.localZ, floorY + 1, ceilingY - 1, radius)

    return true
  }

  private fun estimateSurfaceY(chunk: ChunkContext, localX: Int, localZ: Int, minY: Int, maxY: Int): Int {
    for (y in (maxY - 2) downTo (minY + 1)) {
      if (chunk.isSolid(localX, y, localZ) && chunk.isAir(localX, y + 1, localZ)) return y
    }
    return minY
  }

  private fun placePillar(chunk: ChunkContext, cx: Int, cz: Int, yMin: Int, yMax: Int, radius: Double) {
    val rInt = kotlin.math.ceil(radius).toInt()
    for (y in yMin..yMax) {
      for (dx in -rInt..rInt) {
        for (dz in -rInt..rInt) {
          val x = cx + dx
          val z = cz + dz
          if (x !in 0..15 || z !in 0..15) continue

          val dist2 = (dx * dx + dz * dz).toDouble()
          if (dist2 > radius * radius) continue

          if (chunk.isAir(x, y, z)) {
            chunk.setBlock(x, y, z, Material.DRIPSTONE_BLOCK)
          }
        }
      }
    }
  }
}

