package killercreepr.cruxworldgen.test6.biome

import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
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

