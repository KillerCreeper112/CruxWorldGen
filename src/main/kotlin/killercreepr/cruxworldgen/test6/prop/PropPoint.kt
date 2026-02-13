package killercreepr.cruxworldgen.test6.prop

import killercreepr.cruxworldgen.test6.HashUtil
import killercreepr.cruxworldgen.test6.HashUtil.HASH_MIX_1
import killercreepr.cruxworldgen.test6.HashUtil.HASH_MIX_2
import killercreepr.cruxworldgen.test6.HashUtil.HASH_MUL_X
import killercreepr.cruxworldgen.test6.HashUtil.HASH_SALT
import killercreepr.cruxworldgen.test6.HashUtil.hash01
import killercreepr.cruxworldgen.test6.biome.BiomeBlendSample
import killercreepr.cruxworldgen.test6.context.ChunkContext
import killercreepr.cruxworldgen.test6.context.GenerateContext
import killercreepr.cruxworldgen.test6.decor.Decoration
import killercreepr.cruxworldgen.test6.decor.DecorationPass
import killercreepr.cruxworldgen.test6.decor.Placement
import org.bukkit.Material
import kotlin.math.pow
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

  /** True surface for trees/etc: topmost solid block that has an open air column to the top of the world. */
  fun skySurfaceY(localX: Int, localZ: Int, maxAirCheck: Int = 128): Int {
    val minY = chunk.minHeight
    val topY = chunk.maxHeight - 1

    for (y in (topY - 1) downTo (minY + 1)) {
      if (!chunk.isSolid(localX, y, localZ)) continue
      if (!chunk.isAir(localX, y + 1, localZ)) continue

      // ensure "sky exposure": above must remain air (up to some limit)
      var air = 0
      var yy = y + 1
      while (yy <= topY && air < maxAirCheck) {
        if (!chunk.isAir(localX, yy, localZ)) {
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

  fun slopeBlocks(localX: Int, localZ: Int): Double {
    val sx1 = surfaceY((localX - 1).coerceIn(0, 15), localZ)
    val sx2 = surfaceY((localX + 1).coerceIn(0, 15), localZ)
    val sz1 = surfaceY(localX, (localZ - 1).coerceIn(0, 15))
    val sz2 = surfaceY(localX, (localZ + 1).coerceIn(0, 15))
    val dx = (sx2 - sx1).toDouble() * 0.5
    val dz = (sz2 - sz1).toDouble() * 0.5
    return kotlin.math.sqrt(dx*dx + dz*dz)
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

    val startX = chunkWorldX - spacingBlocks
    val startZ = chunkWorldZ - spacingBlocks
    val endX = chunkWorldX + 16 + spacingBlocks
    val endZ = chunkWorldZ + 16 + spacingBlocks

    var gridX = Math.floorDiv(startX, spacingBlocks) * spacingBlocks
    while (gridX <= endX) {

      var gridZ = Math.floorDiv(startZ, spacingBlocks) * spacingBlocks
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


  private fun hash2D(seed: Long, x: Int, z: Int): Long {
    var value = seed
    value = value xor (x.toLong() * HASH_MUL_X)
    value = value xor (z.toLong() * HASH_SALT)
    value = (value xor (value ushr 30)) * HASH_MIX_1
    value = (value xor (value ushr 27)) * HASH_MIX_2
    return value xor (value ushr 31)
  }
}

data class CavernPillarRulePlacement(
  val cx: Int,
  val cz: Int,
  val yMin: Int,
  val yMax: Int,

  val baseRadius: Double,
  val taperPower: Double,     // 0.8..2.5
  val bulgeStrength: Double,  // 0..0.6
  val roughness: Double,      // 0..0.5
  val breakChance: Double,    // 0..0.25

  val seed: Long
) : Placement


class CavernPillarRule(
  private val minGapBlocks: Int = 5,
  private val maxGapBlocks: Int = 40,
  private val minDepthBelowSurface: Int = 18
) : Decoration {
  override val pass = DecorationPass.UNDERGROUND

  /** Controls distribution. Examples: grid spacing, noise chance, biome-weight scaling */
  override fun shouldTry(
    ctx: GenerateContext,
    point: PropPoint,
    biomeBlend: BiomeBlendSample
  ): Boolean {
    val chunk = ctx.chunkContext
    val minY = chunk.minHeight
    val maxY = chunk.maxHeight

    val surfaceY = estimateSurfaceY(chunk, point.localX, point.localZ, minY, maxY)
    val depthBelowSurface = surfaceY - point.localX

    // Find an enclosed air pocket to pillar inside
    return findCavePocket(
      chunk = chunk,
      localX = point.localX,
      localZ = point.localZ,
      minY = minY,
      maxY = maxY,
      surfaceY = surfaceY,
      minGap = minGapBlocks,
      maxGap = maxGapBlocks,
      searchDepthStartBelowSurface = 6
    ) != null
  }

  /** Pattern scan: find an anchor/placement candidate */
  override fun findPlacement(ctx: GenerateContext, point: PropPoint, biomeBlend: BiomeBlendSample): Placement? {
    val chunk = ctx.chunkContext
    val minY = chunk.minHeight
    val maxY = chunk.maxHeight

    val surfaceY = estimateSurfaceY(chunk, point.localX, point.localZ, minY, maxY)

    val pocket = findCavePocket(
      chunk, point.localX, point.localZ,
      minY, maxY, surfaceY,
      minGapBlocks, maxGapBlocks,
      searchDepthStartBelowSurface = 6
    ) ?: return null

    val floorY = pocket.floorY
    val ceilingY = pocket.ceilingY
    val gap = pocket.gap

    val depth = surfaceY - floorY
    if (depth < minDepthBelowSurface) return null

    // --- 1) Cavern mask (only spawn in “open-ish” caves) ---
    val midY = floorY + gap / 2
    val cavern01 = ((ctx.noise.cavern3D(point.worldX, midY, point.worldZ) + 1.0) * 0.5)
    // If you want pillars mainly in caverns, push this threshold up (0.65..0.85)
    if (cavern01 < 0.4) return null

    // --- 2) Patch noise: makes regions of many pillars ---
    val patch01 = ((ctx.noise.pillarPatch2D.noise(point.worldX.toDouble(), point.worldZ.toDouble()) + 1.0) * 0.5)
    // Turn it into a “presence mask”
    val patchMask = smoothstep01(((patch01 - 0.55) / (1.0 - 0.55)).coerceIn(0.0, 1.0))
    if (patchMask < 0.2) return null

    // --- 3) Deterministic chance inside patch ---
    val r01 = hash01(point.seed xor 0x5131AA77L)
    val chance = (0.15 + 0.75 * patchMask) * cavern01 // 5%..50% depending on patch+cavern
    if (r01 > chance) return null

    // --- 4) Shape params ---
    val baseRadius = (1.0 + 2.8 * patchMask).coerceIn(1.0, 3.5)
    val taperPower = 1.0 + 1.5 * hash01(point.seed xor 0x2222L) // 1..2.5
    val bulgeStrength = 0.10 + 0.45 * hash01(point.seed xor 0x3333L) // 0.1..0.55
    val roughness = 0.10 + 0.35 * (1.0 - cavern01) // rougher when less “pure cavern”
    val breakChance = 0.05 + 0.20 * (1.0 - patchMask) // sparse areas = more broken pillars

    return CavernPillarRulePlacement(
      cx = point.localX,
      cz = point.localZ,
      yMin = floorY + 1,
      yMax = ceilingY - 1,
      baseRadius = baseRadius,
      taperPower = taperPower,
      bulgeStrength = bulgeStrength,
      roughness = roughness,
      breakChance = breakChance,
      seed = point.seed
    )
  }


  /** Apply: place blocks using placement info */
  override fun place(ctx: GenerateContext, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as CavernPillarRulePlacement
    val chunk = ctx.chunkContext

    val height = (p.yMax - p.yMin + 1).coerceAtLeast(1)

    val baseRadius = placement.baseRadius          // e.g. 2.5
    val waistRadius = 0.8                      // minimum radius in the middle
    val pinchPower = 2.2                       // 1.5..4.0 typical (bigger = thinner middle)

    for (y in p.yMin..p.yMax) {
      val t = (y - p.yMin).toDouble() / (p.yMax - p.yMin).coerceAtLeast(1).toDouble()

      val f = hourglassFactor(t, pinchPower)   // 1 at ends, 0 at middle
      var r = waistRadius + (baseRadius - waistRadius) * f

      //val t = (y - p.yMin).toDouble() / height.toDouble() // 0..1 bottom->top

      // Taper (thicker in middle, thinner at ends)
      val endFade = (1.0 - kotlin.math.abs(t * 2.0 - 1.0)) // 0 at ends, 1 at middle
      val bulge = 1.0 + p.bulgeStrength * (endFade * endFade) // bulge at mid

      // Stronger taper => skinnier ends
      val taper = endFade.coerceIn(0.0, 1.0).pow(p.taperPower).coerceIn(0.0, 1.0)

      // Base radius at this y
      //var r = p.baseRadius * (0.35 + 0.65 * taper) * bulge

      // Roughness modulated by 3D noise (world coords for continuity)
      val n = ctx.noise.pillarDetail3D.noise(ctx.chunkX * 16.0 + p.cx, y.toDouble(), ctx.chunkZ * 16.0 + p.cz) // [-1..1]
      r *= (1.0 + n * p.roughness).coerceIn(0.6, 1.4)

      // Random “breaks” -> missing rings / holes / snapped pillars
      val ringR01 = HashUtil.hash01(p.seed xor (y.toLong() * HASH_SALT))
      if (ringR01 < p.breakChance * 0.35) continue

      placePillarDisc(chunk, p.cx, p.cz, y, r, Material.DRIPSTONE_BLOCK)
    }
  }

  private fun placePillarDisc(chunk: ChunkContext, cx: Int, cz: Int, y: Int, radius: Double, mat: Material) {
    val rInt = kotlin.math.ceil(radius).toInt()
    for (dx in -rInt..rInt) for (dz in -rInt..rInt) {
      val x = cx + dx
      val z = cz + dz
      if (x !in 0..15 || z !in 0..15) continue
      if (!chunk.isAir(x, y, z)) continue

      val dist2 = (dx * dx + dz * dz).toDouble()
      if (dist2 <= radius * radius) {
        chunk.setBlock(x, y, z, mat)
      }
    }
  }

  private fun smoothstep01(t: Double): Double = t * t * (3.0 - 2.0 * t)


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

  private fun hourglassFactor(t: Double, pinchPower: Double): Double {
    // t in [0..1]
    val s = kotlin.math.sin(Math.PI * t) // 0..1..0
    return 1.0 - s.pow(pinchPower) // 1..0..1
  }

}

