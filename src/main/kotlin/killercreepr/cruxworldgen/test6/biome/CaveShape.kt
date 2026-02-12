package killercreepr.cruxworldgen.test6.biome

import killercreepr.cruxworldgen.test6.context.GenerateContext

interface CaveType {
  fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double
}

interface CaveShape {
  fun carve(ctx: GenerateContext, cave: CaveContext): Double
}



class CaveProfile(
  private val caveTypes: List<CaveType>
) : CaveShape {

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
    val heightNoise = ctx.noise.spaghettiHeight2D(cave.worldX, cave.worldZ) // [-1..1]
    val targetDepth = baseDepthBelowSurface + heightNoise * depthVariationBlocks
    val centerY = cave.surfaceY - targetDepth

    // Only carve near that centerline (prevents vertical shafts)
    val dy = kotlin.math.abs(cave.y.toDouble() - centerY)
    val vT = ((verticalRadiusBlocks - dy) / verticalRadiusBlocks).coerceIn(0.0, 1.0)
    val verticalMask = vT * vT * (3.0 - 2.0 * vT)
    if (verticalMask <= 0.001) return 0.0

    // ---------- Build a noodle network in XZ ----------
    // Warp in XZ only (stable with Y)
    val warp = ctx.noise.caveWarp3D(cave.worldX, 0, cave.worldZ) // using y=0 intentionally
    val wx = (cave.worldX + warp * warpBlocks).toInt()
    val wz = (cave.worldZ + warp * warpBlocks).toInt()

    // Worm noise sampled with constant y -> gives an XZ noodle network
    val worm = ctx.noise.caveWorm3D(wx, 0, wz) // using y=0 intentionally
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

    val warp = ctx.noise.caveWarp3D(cave.worldX, 0, cave.worldZ)
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

    val n01 = (ctx.noise.cheese3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
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

    val n01 = (ctx.noise.cavern3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
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
    val hNoise = ctx.noise.spaghettiHeight2D(cave.worldX, cave.worldZ) // [-1..1]
    val centerY = cave.surfaceY - (baseDepthBelowSurface + hNoise * depthVariationBlocks)

    val dy = kotlin.math.abs(cave.y.toDouble() - centerY)
    val vT = ((verticalRadiusBlocks - dy) / verticalRadiusBlocks).coerceIn(0.0, 1.0)
    val verticalMask = smoothstep01(vT)
    if (verticalMask <= 0.001) return 0.0

    val warp = ctx.noise.caveWarp3D(cave.worldX, 0, cave.worldZ)
    val wx = (cave.worldX + warp * warpBlocks).toInt()
    val wz = (cave.worldZ + warp * warpBlocks).toInt()

    val worm = ctx.noise.caveWorm3D(wx, 0, wz)
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

