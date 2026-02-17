package killercreepr.cruxworldgen.test6.prop

import killercreepr.cruxworldgen.api.block.BlockData
import killercreepr.cruxworldgen.api.cave.CavePocket
import killercreepr.cruxworldgen.api.context.ChunkContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.context.LimitedRegion
import killercreepr.cruxworldgen.api.decor.Decoration
import killercreepr.cruxworldgen.api.decor.DecorationPass
import killercreepr.cruxworldgen.api.decor.Placement
import killercreepr.cruxworldgen.api.decor.PropPoint
import killercreepr.cruxworldgen.api.generation.BiomeBlendSample
import killercreepr.cruxworldgen.api.util.HashUtil.HASH_SALT
import killercreepr.cruxworldgen.api.util.HashUtil.hash01
import killercreepr.cruxworldgen.bukkit.block.BukkitBlockResolver
import killercreepr.cruxworldgen.core.cave.SimpleCavePocket
import org.bukkit.Material
import kotlin.math.pow


data class FloorHit(val y: Int)
data class CeilingHit(val y: Int)

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
    if (chunk.isEmpty(localX, y, localZ) && chunk.isSolid(localX, y - 1, localZ)) {
      val floorY = y - 1

      // Walk upward through air until it stops
      var topAirY = y
      while (topAirY < maxY - 1 && chunk.isEmpty(localX, topAirY, localZ)) {
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
          return SimpleCavePocket(floorY, ceilingY)
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
    val airAbove = chunk.isEmpty(localX, y + 1, localZ)
    if (isSolid && airAbove) return FloorHit(y)
  }
  return null
}

fun findCeilingAbove(chunk: ChunkContext, localX: Int, localZ: Int, startY: Int, maxY: Int): CeilingHit? {
  // ceiling = solid block with air below it
  for (y in (startY + 2) until (maxY - 1)) {
    val isSolid = chunk.isSolid(localX, y, localZ)
    val airBelow = chunk.isEmpty(localX, y - 1, localZ)
    if (isSolid && airBelow) return CeilingHit(y)
  }
  return null
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
    region: LimitedRegion,
    point: PropPoint,
    biomeBlend: BiomeBlendSample
  ): Boolean {
    val ctx = region.ctx
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
  override fun findPlacement(region: LimitedRegion, point: PropPoint, biomeBlend: BiomeBlendSample): Placement? {
    val ctx = region.ctx
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
    val cavern01 = 1.0//todo ((ctx.noise.cavern3D(point.worldX, midY, point.worldZ) + 1.0) * 0.5)
    // If you want pillars mainly in caverns, push this threshold up (0.65..0.85)
    if (cavern01 < 0.4) return null

    // --- 2) Patch noise: makes regions of many pillars ---
    val patch01 = 1.0//todo ((ctx.noise.pillarPatch2D.noise(point.worldX.toDouble(), point.worldZ.toDouble()) + 1.0) * 0.5)
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
  override fun place(region: LimitedRegion, placement: Placement, biomeBlend: BiomeBlendSample) {
    val p = placement as CavernPillarRulePlacement
    val ctx = region.ctx
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
      val n = 1.0//todo ctx.noise.pillarDetail3D.noise(ctx.chunkX * 16.0 + p.cx, y.toDouble(), ctx.chunkZ * 16.0 + p.cz) // [-1..1]
      r *= (1.0 + n * p.roughness).coerceIn(0.6, 1.4)

      // Random “breaks” -> missing rings / holes / snapped pillars
      val ringR01 = hash01(p.seed xor (y.toLong() * HASH_SALT))
      if (ringR01 < p.breakChance * 0.35) continue

      placePillarDisc(chunk, p.cx, p.cz, y, r, BukkitBlockResolver.INSTANCE.resolve(Material.DRIPSTONE_BLOCK))
    }
  }

  private fun placePillarDisc(chunk: ChunkContext, cx: Int, cz: Int, y: Int, radius: Double, mat: BlockData) {
    val rInt = kotlin.math.ceil(radius).toInt()
    for (dx in -rInt..rInt) for (dz in -rInt..rInt) {
      val x = cx + dx
      val z = cz + dz
      if (x !in 0..15 || z !in 0..15) continue
      if (!chunk.isEmpty(x, y, z)) continue

      val dist2 = (dx * dx + dz * dz).toDouble()
      if (dist2 <= radius * radius) {
        chunk.setBlock(x, y, z, mat)
      }
    }
  }

  private fun smoothstep01(t: Double): Double = t * t * (3.0 - 2.0 * t)


  private fun estimateSurfaceY(chunk: ChunkContext, localX: Int, localZ: Int, minY: Int, maxY: Int): Int {
    for (y in (maxY - 2) downTo (minY + 1)) {
      if (chunk.isSolid(localX, y, localZ) && chunk.isEmpty(localX, y + 1, localZ)) return y
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

          if (chunk.isEmpty(x, y, z)) {
            chunk.setBlock(x, y, z, BukkitBlockResolver.INSTANCE.resolve(Material.DRIPSTONE_BLOCK))
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

