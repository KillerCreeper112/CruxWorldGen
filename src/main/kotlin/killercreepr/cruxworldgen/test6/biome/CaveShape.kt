package killercreepr.cruxworldgen.test6.biome

import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class PillarAdditive(
  private val seedSalt: Long = 0x71A12B3L,

  private val cellSize: Int = 8,          // spacing between pillar centers (8..18)
  private val baseRadius: Double = 2.0,    // 1.0..2.3
  private val radiusVar: Double = 1.5,     // extra random radius
  private val halfHeight: Double = 24.0,   // how tall the bulge envelope is
  private val midPinch: Double = 0.60      // 0..1 (higher = skinnier mid)
) : CaveType {

  override fun carveBlocks(ctx: GenerateContext, c: CaveContext): Double = 0.0

  override fun addBlocks(ctx: GenerateContext, c: CaveContext, carved: Double): Double {
    // only in real cave space
    if (carved < 1.0) return 0.0

    val postCarve = c.terrainDensity - carved
    if (postCarve >= -0.25) return 0.0

    val seed = ctx.worldContext.seed xor seedSalt

    val nearest = nearestPillarPoint2D(seed, c.worldX, c.worldZ, cellSize)
    val id = nearest.id

    val radius = baseRadius + hash01(id xor 0xC0FFEE) * radiusVar

    // radial mask (solid inside radius)
    val t = ((radius - nearest.dist) / radius).coerceIn(0.0, 1.0)
    if (t <= 0.0) return 0.0
    val radialMask = t * t * (3.0 - 2.0 * t) // smoothstep

    // pick a per-pillar “center Y” so it doesn’t look perfectly uniform
    val centerY = (c.surfaceY - 14 - (hash01(id xor 0x1234) * 40.0)).toInt()

    val dy = kotlin.math.abs(c.y - centerY).toDouble()
    val yn = (dy / halfHeight).coerceIn(0.0, 1.0)

    // bulge at ends, pinch in middle:
    // yn=0 at center -> skinny
    // yn=1 at ends   -> bulgy
    val endsBulge = yn * yn * yn   // stronger bulge at ends

    val pinch = (1.0 - midPinch) + midPinch * endsBulge

    // how much add is needed to actually become solid here
    //val needed = (-postCarve) + 1.25
    val needed = ((-postCarve) + 2.0).coerceAtLeast(6.0)

    val add = radialMask * pinch * needed

    // never add more than carve is creating (keeps caves caves)
    return add.coerceAtMost(carved + 0.75)
  }
}


private data class Nearest(val dist: Double, val id: Long)

private fun nearestPillarPoint2D(seed: Long, x: Int, z: Int, cell: Int): Nearest {
  val cx = Math.floorDiv(x, cell)
  val cz = Math.floorDiv(z, cell)

  var bestD2 = Double.POSITIVE_INFINITY
  var bestId = 0L

  // search neighbor cells so pillars are continuous
  for (dx in -1..1) for (dz in -1..1) {
    val gx = cx + dx
    val gz = cz + dz

    val id = mix64(seed xor (gx.toLong() * 341873128712L) xor (gz.toLong() * 132897987541L))
    val jx = ((hash01(id xor 0xA1L) * cell).toInt())
    val jz = ((hash01(id xor 0xB2L) * cell).toInt())

    val px = gx * cell + jx
    val pz = gz * cell + jz

    val ox = (x - px).toDouble()
    val oz = (z - pz).toDouble()
    val d2 = ox * ox + oz * oz

    if (d2 < bestD2) {
      bestD2 = d2
      bestId = id
    }
  }

  return Nearest(sqrt(bestD2), bestId)
}


private fun mix64(x: Long): Long {
  var v = x
  v = (v xor (v ushr 30)) * -4658895280553007687L
  v = (v xor (v ushr 27)) * -7723592293110705685L
  return v xor (v ushr 31)
}

private fun hash01(seed: Long): Double {
  val v = mix64(seed)
  val positive = v and Long.MAX_VALUE
  return positive.toDouble() / Long.MAX_VALUE.toDouble()
}


class PillarAdditiveOld(
  private val pillarFreq: Double = 0.06,      // bigger = more pillars
  private val maxAddBlocks: Double = 18.0,    // how thick/strong
  private val midPinch: Double = 0.55         // 0..1: how skinny middle gets
) : CaveType {

  override fun carveBlocks(ctx: GenerateContext, c: CaveContext): Double = 0.0 // doesn't carve

  override fun addBlocks(ctx: GenerateContext, c: CaveContext, carved: Double): Double {
    // Only operate where cave carve is actually opening space
    if (carved < 1.0) return 0.0

    // A simple "column mask" noise (you can implement pillarNoise2D/3D in NoiseBank)
    val n = 1.0//todo ctx.noise.pillar2D(c.worldX, c.worldZ) // [-1..1]
    val n01 = (n + 1.0) * 0.5

    val effectiveThreshold = (0.90 - pillarFreq * 0.35).coerceIn(0.45, 0.90)
// pillarFreq: 0 -> 0.90 (rare), 1 -> 0.55 (common)
    val raw = ((n01 - effectiveThreshold) / (1.0 - effectiveThreshold)).coerceIn(0.0, 1.0)


    //val raw = ((n01 - threshold) / (1.0 - threshold)).coerceIn(0.0, 1.0)
    if (raw <= 0.0) return 0.0

    // We want bulge at top/bottom and pinch in middle.
    // Use another low-freq noise to pick a "pillar core height" relative to surface.
    //val pillarCenterY = c.surfaceY - (12 + ((ctx.noise.pillarHeight2D(c.worldX, c.worldZ) + 1.0) * 0.5 * 30.0)).toInt()
    val pillarCenterY = 1.0 //todo c.surfaceY - (35 + ((ctx.noise.pillarHeight2D(c.worldX, c.worldZ) + 1.0) * 0.5 * 30.0)).toInt()
// depth ~35..65 below surface (much closer to cheese at 45)



    val dy = kotlin.math.abs(c.y - pillarCenterY).toDouble()
    val halfHeight = 18.0
    val t = (dy / halfHeight).coerceIn(0.0, 1.0)

    // Shape curve: high at ends, low in middle (∩∪ kind of)
    // t=0 at center -> skinny; t=1 at ends -> bulgy
    val endsBulge = t * t
    val pinch = (1.0 - midPinch) + midPinch * endsBulge

    // Convert raw to actual add
    val strength = raw * raw * maxAddBlocks * pinch

    // IMPORTANT: don't add more than carve is creating, or it will "fill caves back in"
    return strength.coerceAtMost(carved + 0.75)
  }
}

class RavineCarver(
  private val threshold01: Double = 0.9,      // higher = rarer ravines
  private val baseHalfWidth: Double = 3.0,     // blocks (half width)
  private val widthVar: Double = 4.0,         // extra half-width from noise
  private val baseDepth: Double = 70.0,        // blocks down from surface
  private val depthVar: Double = 100.0,         // extra depth from noise
  private val wallPower: Double = 1.7,         // higher = steeper walls
  private val warpAmp: Double = 18.0,          // blocks of horizontal meander
  private val strength: Double = 1.4,         // must be > 1 to open reliably
  private val openMarginBlocks: Double = 10.0,  // extra carve to guarantee air
  private val bridgeThreshold01: Double = 0.82,  // higher = rarer bridges
  private val bridgeThickness: Double = 3.0,     // vertical thickness in blocks
  private val bridgeStrength: Double = 1.0       // 0..1 how much it suppresses carving
) : CaveType {

  override val canOpenToSky: Boolean = true
  override val surfaceFadeDepth: Int = 3
  private val widthToNoise = 0.02 // tune: bigger => wider ravines for same halfWidth


  override fun carveBlocks(ctx: GenerateContext, c: CaveContext): Double {
    val solidDensity = max(0.0, c.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    // --- 1) Domain warp so ravines meander ---
    val wx = c.worldX.toDouble()
    val wz = c.worldZ.toDouble()
    val warpX = 1.0//todo ctx.noise.ravineWarp2D(wx, wz) * warpAmp
    val warpZ = 1.0//todo ctx.noise.ravineWarp2D(wx + 1000.0, wz + 1000.0) * warpAmp
    val xw = wx + warpX
    val zw = wz + warpZ

    // --- 2) Make "ribbons": ridge = 1 - abs(noise) gives long stripe-like bands ---
    val n = 1.0//todo ctx.noise.ravineMask2D(xw, zw) // [-1,1]
    val ridge01 = 1.0 - abs(n)             // [0,1], high near the ridge centerline

    val t = ((ridge01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    if (t <= 0.0) return 0.0

    // --- 3) Width & depth vary along the ribbon ---
    val v01 = 1.0//todo (ctx.noise.ravineVar2D(xw, zw) + 1.0) * 0.5
    val halfWidth = (baseHalfWidth + widthVar * v01).coerceAtLeast(2.0)
    val depth = (baseDepth + depthVar * v01).coerceAtLeast(8.0)

    // Convert ridge strength -> distance-ish factor.
    // ridge01 is 1 at centerline, 0 away.
    // We want width falloff: center strong, edge fades.
    val widthMask = smoothstep01(t) // already pretty good "center mask"

    // --- 4) Vertical profile: carve from near surface down to (surfaceY - depth) ---
    val y = c.y.toDouble()

    // 0 at top (surface), 1 at bottom (floor)
    val v = ((c.surfaceY - y) / depth).coerceIn(0.0, 1.0)

    // Walls: strongest carve in the middle, less near bottom if you want a flatter floor.
    // This gives a canyon feel (steeper near top, rounding near bottom).
    val verticalMask = (1.0 - v.pow(wallPower))

    // Final carve strength
    val mask = widthMask * verticalMask
    if (mask <= 0.001) return 0.0

    val bridge01 = 1.0//todo (ctx.noise.ravineBridge2D(xw, zw) + 1.0) * 0.5
    if (bridge01 > bridgeThreshold01) {
      // pick a bridge height inside the ravine (biased toward upper half)
      val t01 = ((bridge01 - bridgeThreshold01) / (1.0 - bridgeThreshold01)).coerceIn(0.0, 1.0)
      val bridgeY = c.surfaceY - depth * (0.25 + 0.35 * t01)  // 25%..60% down

      // gaussian-ish vertical band
      val dy = kotlin.math.abs(y - bridgeY) / bridgeThickness
      val band = kotlin.math.exp(-(dy * dy)) // 1 at center, fades out

      val suppress = (t01 * band * bridgeStrength).coerceIn(0.0, 1.0)

      // reduce carving locally => leaves a “bridge / roof segment”
      val finalMask = mask * (1.0 - suppress)
      if (finalMask <= 0.001) return 0.0
      return finalMask * (solidDensity * strength + openMarginBlocks)
    }

    return mask * (solidDensity * strength + openMarginBlocks)
  }

  private fun smoothstep01(t: Double): Double = t * t * (3.0 - 2.0 * t)
}

/*
class CaveProfile(
  private val caveTypes: List<CaveType>
) : CaveShape {
  override fun add(
    ctx: GenerateContext,
    c: CaveContext
  ): Double {
    if (c.depthBelowSurface <= 0) return 0.0

    var strongestCarve = 0.0
    var strongestType: CaveType? = null

    for (type in caveTypes) {
      val v = type.carveBlocks(ctx, cave)
      if (v > strongestCarve) {
        strongestCarve = v
        strongestType = type
      }
    }

// choose fade based on the strongest type at this voxel
    val undergroundFade = run {
      val t = strongestType
      if (t?.canOpenToSky == true) {
        // fades in almost immediately, so it can reach the surface
        smoothstep01((cave.depthBelowSurface.toDouble() / t.surfaceFadeDepth.toDouble()).coerceIn(0.0, 1.0))
      } else {
        // your original underground fade
        smoothstep01(((cave.depthBelowSurface - 6).toDouble() / 16.0).coerceIn(0.0, 1.0))
      }
    }


    // Same fades as carve() so style transitions are consistent
    */
/*val undergroundFade = smoothstep01(
      ((c.depthBelowSurface - 6).toDouble() / 16.0).coerceIn(0.0, 1.0)
    )*//*

    val edgeFade = smoothstep01((1.0 - c.edge.edgeBlendFactor()).coerceIn(0.0, 1.0))

    // Compute carve strength (same as carve() does) so add can be gated properly
    var strongestCarve = 0.0
    for (type in caveTypes) {
      strongestCarve = maxOf(strongestCarve, type.carveBlocks(ctx, c))
    }

    // If there's no cave here, do NOT add pillars (prevents "pillars in solid rock")
    if (strongestCarve <= 0.0001) return 0.0

    // You can choose a slightly higher gate if you want pillars only in "real cave space"
    // e.g. if (strongestCarve < 2.0) return 0.0

    // Ask types for additive solids (pillars/etc), and take strongest
    var strongestAdd = 0.0
    for (type in caveTypes) {
      strongestAdd = maxOf(strongestAdd, type.addBlocks(ctx, c, strongestCarve))
    }

    val added = strongestAdd * undergroundFade * edgeFade

    // SAFETY: never add more than (carve + margin).
    // This guarantees: finalDensity = terrain - carve + add still carves caves.
    val maxAllowedAdd = strongestCarve + 0.75
    return added.coerceIn(0.0, maxAllowedAdd)
  }

  override fun carve(ctx: GenerateContext, cave: CaveContext): Double {
    if (cave.depthBelowSurface <= 0) return 0.0

    // Fade in caves below the surface (prevents open-to-sky)
    val undergroundFade = smoothstep01(
      ((cave.depthBelowSurface - 6).toDouble() / 16.0).coerceIn(0.0, 1.0)
    )

    // Fade cave "style" near biome edges so blending stays clean
    val edgeFade = smoothstep01((1.0 - cave.edge.edgeBlendFactor()).coerceIn(0.0, 1.0))

    var strongestCarve = 0.0
    for (type in caveTypes) {
      strongestCarve = maxOf(strongestCarve, type.carveBlocks(ctx, cave))
    }

    // Apply fades
    val carved = strongestCarve * undergroundFade * edgeFade

    // Safety cap: never carve more than the local solid density + margin.
    // (Prevents hollow-earth even if masks go crazy.)
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    val maxAllowed = solidDensity + 2.0
    return carved.coerceAtMost(maxAllowed)
  }

  private fun smoothstep01(t: Double): Double = t * t * (3.0 - 2.0 * t)
}
*/

class SpaghettiCaves(
  private val noodleRadius: Double = 1.0,        // thickness of noodles in XZ-mask space
  private val verticalRadiusBlocks: Double = 6.0, // thickness in Y (tunnel "height")
  private val baseDepthBelowSurface: Double = 28.0, // average depth of noodle network
  private val depthVariationBlocks: Double = 14.0,  // how much centerline moves up/down
  private val strength: Double = 1.15,
  private val openMarginBlocks: Double = 6.0,
  private val warpBlocks: Double = 22.0
) : CaveType {

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0
    if (cave.depthBelowSurface <= 0) return 0.0

    // ---------- Pick a center Y for the spaghetti layer ----------
    val heightNoise = 1.0//todo ctx.noise.spaghettiHeight2D(cave.worldX, cave.worldZ) // [-1..1]
    val targetDepth = baseDepthBelowSurface + heightNoise * depthVariationBlocks
    val centerY = cave.surfaceY - targetDepth

    // Only carve near that centerline (prevents vertical shafts)
    val dy = kotlin.math.abs(cave.y.toDouble() - centerY)
    val vT = ((verticalRadiusBlocks - dy) / verticalRadiusBlocks).coerceIn(0.0, 1.0)
    val verticalMask = vT * vT * (3.0 - 2.0 * vT)
    if (verticalMask <= 0.001) return 0.0

    // ---------- Build a noodle network in XZ ----------
    // Warp in XZ only (stable with Y)
    val warp = 1.0//todo ctx.noise.caveWarp3D(cave.worldX, 0, cave.worldZ) // using y=0 intentionally
    val wx = (cave.worldX + warp * warpBlocks).toInt()
    val wz = (cave.worldZ + warp * warpBlocks).toInt()

    // Worm noise sampled with constant y -> gives an XZ noodle network
    val worm = 1.0//todo ctx.noise.caveWorm3D(wx, 0, wz) // using y=0 intentionally
    val axisDist = kotlin.math.abs(worm)

    val nT = ((noodleRadius - axisDist) / noodleRadius).coerceIn(0.0, 1.0)
    val noodleMask = nT * nT * nT

    // Cutoff to prevent tiny 1–2 block pimples
    if (noodleMask < 0.55) return 0.0

    val mask = noodleMask * verticalMask

    // Scalable carve: relative to local terrain density
    return mask * (solidDensity * strength + openMarginBlocks)
  }
}


class WideThinVerticalCaves(
  private val radius: Double = 0.06,        // smaller = rarer/thinner
  private val strength: Double = 1.10,      // MUST be > 1 to open when mask ~ 1
  private val openMarginBlocks: Double = 6.0 // thickness / reliability
) : CaveType {

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    //val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    //if (solidDensity <= 0.0) return 0.0

    //val wormNoise = ctx.noise.caveWorm3D(cave.worldX, cave.y, cave.worldZ) // signed
    //val axisDistance = kotlin.math.abs(wormNoise)

    // mask in [0..1]
    //val normalized = ((radius - axisDistance) / radius).coerceIn(0.0, 1.0)
    //val mask = normalized * normalized * normalized

    // The scalable carve: beats solidDensity near centerline without "+ depth" hacks
    //return mask * (solidDensity * strength + openMarginBlocks)

    val solidDensity = maxOf(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    val warp =1.0//todo  ctx.noise.caveWarp3D(cave.worldX, 0, cave.worldZ)
    val wx = (cave.worldX + warp * 20.0).toInt()
    val wz = (cave.worldZ + warp * 20.0).toInt()

    val worm = 0.0//todo ctx.noise.caveWorm3D(wx.toDouble(), cave.y * 0.25, wz.toDouble())
    val axisDist = kotlin.math.abs(worm)

    val radius = 0.07
    val t = ((radius - axisDist) / radius).coerceIn(0.0, 1.0)
    val mask = t * t * (3.0 - 2.0 * t)

    if (mask < 0.55) return 0.0

    return mask * (solidDensity * 1.10 + 6.0)

  }
}

class CheeseCaves(
  private val threshold01: Double = 0.65,      // higher = rarer
  private val strength: Double = 1.08,         // must be > 1 to open reliably
  private val openMarginBlocks: Double = 20.0,

  // where it lives vertically (below surface)
  private val centerDepthBlocks: Double = 45.0,
  private val halfWidthBlocks: Double = 22.0
) : CaveType {

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0
    if (cave.depthBelowSurface <= 0) return 0.0

    // Vertical band around a depth below surface
    val targetY = cave.surfaceY - centerDepthBlocks
    val dy = kotlin.math.abs(cave.y.toDouble() - targetY)
    val bandT = ((halfWidthBlocks - dy) / halfWidthBlocks).coerceIn(0.0, 1.0)
    val verticalMask = smoothstep01(bandT)
    if (verticalMask <= 0.001) return 0.0

    val n01 = 1.0//todo (ctx.noise.cheese3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val blobMask = smoothstep01(t)
    if (blobMask < 0.35) return 0.0  // stops tiny freckles

    val mask = blobMask * verticalMask
    return mask * (solidDensity * strength + openMarginBlocks)
  }

  private fun smoothstep01(t: Double): Double = t * t * (3.0 - 2.0 * t)
}

class CavernRooms(
  private val threshold01: Double = 0.65,     // 0.90..0.95
  private val strength: Double = 1.15,
  private val openMarginBlocks: Double = 18.0,

  private val minDepthBlocks: Int = 25        // don't open near surface
) : CaveType {

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0
    if (cave.depthBelowSurface < minDepthBlocks) return 0.0

    val n01 = 1.0//todo (ctx.noise.cavern3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val mask = t * t // sharper/rarer than smoothstep
    if (mask < 0.25) return 0.0

    return mask * (solidDensity * strength + openMarginBlocks)
  }
}

class LavaTubes(
  private val noodleRadius: Double = 0.5,
  private val verticalRadiusBlocks: Double = 6.0,

  private val baseDepthBelowSurface: Double = 75.0,
  private val depthVariationBlocks: Double = 10.0,

  private val strength: Double = 1.12,
  private val openMarginBlocks: Double = 10.0,
  private val warpBlocks: Double = 18.0
) : CaveType {

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0
    if (cave.depthBelowSurface <= 0) return 0.0

    // centerline depth slowly varies across XZ
    val hNoise = 1.0//todo ctx.noise.spaghettiHeight2D(cave.worldX, cave.worldZ) // [-1..1]
    val centerY = cave.surfaceY - (baseDepthBelowSurface + hNoise * depthVariationBlocks)

    val dy = kotlin.math.abs(cave.y.toDouble() - centerY)
    val vT = ((verticalRadiusBlocks - dy) / verticalRadiusBlocks).coerceIn(0.0, 1.0)
    val verticalMask = smoothstep01(vT)
    if (verticalMask <= 0.001) return 0.0

    val warp = 1.0//todo ctx.noise.caveWarp3D(cave.worldX, 0, cave.worldZ)
    val wx = (cave.worldX + warp * warpBlocks).toInt()
    val wz = (cave.worldZ + warp * warpBlocks).toInt()

    val worm = 1.0//todo ctx.noise.caveWorm3D(wx, 0, wz)
    val axisDist = kotlin.math.abs(worm)

    val t = ((noodleRadius - axisDist) / noodleRadius).coerceIn(0.0, 1.0)
    val noodleMask = t * t * t
    if (noodleMask < 0.50) return 0.0

    val mask = noodleMask * verticalMask
    return mask * (solidDensity * strength + openMarginBlocks)
  }

  private fun smoothstep01(t: Double): Double = t * t * (3.0 - 2.0 * t)
}


/*class CheeseCaves(
  private val threshold01: Double = 0.86,    // higher = rarer
  private val strength: Double = 1.05,
  private val openMarginBlocks: Double = 10.0
) : CaveType {

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    val n = ctx.noise.cavern3D(cave.worldX, cave.y, cave.worldZ) // signed
    val n01 = (n + 1.0) * 0.5

    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val mask = t * t * (3.0 - 2.0 * t)

    return mask * (solidDensity * strength + openMarginBlocks)
  }
}*/

/*class CavernRooms(
  private val threshold01: Double = 0.92,
  private val strength: Double = 1.10,
  private val openMarginBlocks: Double = 18.0
) : CaveType {

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    val n = ctx.noise.cavern3D(cave.worldX + 10000, cave.y, cave.worldZ - 10000) // offset = different field
    val n01 = (n + 1.0) * 0.5

    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val mask = t * t

    return mask * (solidDensity * strength + openMarginBlocks)
  }
}*/

