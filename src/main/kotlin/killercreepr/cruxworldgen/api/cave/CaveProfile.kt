package killercreepr.cruxworldgen.api.cave

import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.util.Curve
import killercreepr.cruxworldgen.api.util.HashUtil

open class CaveProfile(
  val caveTypes: List<CaveType<*, *>>
) : CaveShape<Array<Any?>, Array<Any?>> {
  override fun coarseCache(
    ctx: GenerateContext,
    worldX: Int,
    worldY: Int,
    worldZ: Int,
    terrainDensity: Double
  ): Array<Any?>? {
    val cache = arrayOfNulls<Any>(caveTypes.size)
    caveTypes.forEachIndexed { index, type ->
      cache[index] = type.coarseCache(
        ctx, worldX, worldY, worldZ, terrainDensity
      )
    }
    return cache
  }

  override fun interpolateCache(
    c000: Array<Any?>,
    c100: Array<Any?>,
    c010: Array<Any?>,
    c110: Array<Any?>,
    c001: Array<Any?>,
    c101: Array<Any?>,
    c011: Array<Any?>,
    c111: Array<Any?>,
    tx: Double,
    ty: Double,
    tz: Double
  ): Array<Any?> {
    val cache = arrayOfNulls<Any>(caveTypes.size)
    caveTypes.forEachIndexed { index, type ->
      cache[index] = type.interpolateCacheUntyped(
        c000[index],
        c100[index],
        c010[index],
        c110[index],
        c001[index],
        c101[index],
        c011[index],
        c111[index],
        tx, ty, tz
      )
    }
    return cache
  }

  fun strongestCarveAndType(ctx: GenerateContext, cave: CaveContext,
                            cache: Array<Any?>): Pair<Double, CaveType<*, *>?> {
    var best = 0.0
    var bestType: CaveType<*, *>? = null
    caveTypes.forEachIndexed { index, type ->
      val v = type.carveBlocksUntyped(ctx, cave, cache[index])
      if (v > best) {
        best = v
        bestType = type
      }
    }
    return best to bestType
  }

  override fun carve(
    ctx: GenerateContext,
    cave: CaveContext,
    cache: Array<Any?>?
  ): Double {
    if(cache == null) return 0.0
    // Above surface => never carve
    //if (cave.depthBelowSurface < 0) return 0.0
    val (strongestCarve, strongestType) = strongestCarveAndType(ctx, cave, cache)
    if (strongestCarve <= 0.0001) return 0.0

    val allowSurface = surfaceOpeningMask(ctx, cave, strongestType)
    if (cave.depthBelowSurface < 0 && allowSurface <= 0.0) return 0.0

    val fade = depthFade(cave.depthBelowSurface, strongestType)
    val effectiveFade = kotlin.math.max(fade, allowSurface) // or allowSurface * 0.6
    //val edgeFade = Curve.smoothstep01((1.0 - cave.edge.edgeBlendFactor()).coerceIn(0.0, 1.0))

    val carved = strongestCarve * effectiveFade// * edgeFade

    // Safety cap: never carve more than local solid density + margin
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    val maxAllowed = solidDensity + 2.0
    return carved.coerceAtMost(maxAllowed)
  }

  override fun carve(ctx: GenerateContext, cave: CaveContext): Double {
    // Above surface => never carve
    //if (cave.depthBelowSurface < 0) return 0.0
    val (strongestCarve, strongestType) = strongestCarveAndType(ctx, cave)
    if (strongestCarve <= 0.0001) return 0.0

    val allowSurface = surfaceOpeningMask(ctx, cave, strongestType)
    if (cave.depthBelowSurface < 0 && allowSurface <= 0.0) return 0.0

    val fade = depthFade(cave.depthBelowSurface, strongestType)
    val effectiveFade = kotlin.math.max(fade, allowSurface) // or allowSurface * 0.6
    //val edgeFade = Curve.smoothstep01((1.0 - cave.edge.edgeBlendFactor()).coerceIn(0.0, 1.0))

    val carved = strongestCarve * effectiveFade// * edgeFade

    // Safety cap: never carve more than local solid density + margin
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    val maxAllowed = solidDensity + 2.0
    return carved.coerceAtMost(maxAllowed)
  }
  fun surfaceOpeningMask(ctx: GenerateContext, cave: CaveContext, type: CaveType<*, *>?): Double {
    if(type !is CaveType.HasSurfaceOpenings) return 0.0
    val chance = type.surfaceOpenChance ?: 0.0
    if (chance <= 0.0) return 0.0

    val maxAbove = type.surfaceOpenMaxAbove ?: 2
    val maxBelow = type.surfaceOpenMaxBelow ?: 18
    val feather  = type.surfaceOpenFeather ?: 6

    // only near the surface band
    val h = cave.depthBelowSurface // negative above surface
    if (h > maxBelow) return 0.0
    if (h < -maxAbove) return 0.0

    // Stable per (x,z) gate so entire vertical column agrees
    val colSeed = HashUtil.hash2D(ctx.worldContext.seed, cave.worldX shr 4, cave.worldZ shr 4)
    val roll = ((colSeed ushr 8) and 1023L).toDouble() / 1023.0
    if (roll > chance) return 0.0

    // Fade toward the edges of the allowed band
    val t = when {
      h >= 0 -> 1.0 - (h.toDouble() / maxBelow.toDouble())
      else   -> 1.0 - ((-h).toDouble() / maxAbove.toDouble())
    }.coerceIn(0.0, 1.0)

    // feather controls how quickly it ramps
    return Curve.smoothstep01((t * (maxBelow.coerceAtLeast(maxAbove)).toDouble() / feather.toDouble()).coerceIn(0.0, 1.0))
  }
  /**
   * depthBelowSurface = 0 means "at the surface"
  depthBelowSurface = 10 means "10 blocks under the surface"
   */
  fun depthFade(depthBelowSurface: Int, type: CaveType<*, *>?): Double {

    val start = type?.surfaceFadeStart ?: 6
    if ((type?.surfaceFadeRamp ?: 16) <= 0) return if (depthBelowSurface >= start) 1.0 else 0.0

    val ramp  = (type?.surfaceFadeRamp ?: 16).coerceAtLeast(1)

    val t = ((depthBelowSurface - start).toDouble() / ramp.toDouble()).coerceIn(0.0, 1.0)
    return Curve.smoothstep01(t)
  }

  fun strongestCarveAndType(ctx: GenerateContext, cave: CaveContext): Pair<Double, CaveType<*, *>?> {
    var best = 0.0
    var bestType: CaveType<*, *>? = null
    for (type in caveTypes) {
      val v = type.carveBlocks(ctx, cave)
      if (v > best) {
        best = v
        bestType = type
      }
    }
    return best to bestType
  }

  override fun add(ctx: GenerateContext, c: CaveContext): Double {
    // Above surface => no cave additions
    //if (c.depthBelowSurface < 100) return 0.0

    val (strongestAdd, strongestType) = strongestAddAndType(ctx, c, strongestCarveAndType(ctx, c).first)
    //if (strongestCarve <= 0.0001) return 0.0

    val fade = depthFade(c.depthBelowSurface, strongestType)
    val edgeFade = Curve.smoothstep01((1.0 - c.edge.edgeBlendFactor()).coerceIn(0.0, 1.0))

    val added = strongestAdd// * fade// * edgeFade

    // SAFETY: never add more than carve + margin (prevents filling caves back in)
    return added//.coerceIn(0.0, maxAllowedAdd)
  }

  fun strongestAddAndType(ctx: GenerateContext, cave: CaveContext, strongestCarve: Double): Pair<Double, CaveType<*, *>?> {
    var best = 0.0
    var bestType: CaveType<*, *>? = null
    for (type in caveTypes) {
      val v = type.addBlocks(ctx, cave, strongestCarve)
      if (v > best) {
        best = v
        bestType = type
      }
    }
    return best to bestType
  }


  /*fun depthFade(depthBelowSurface: Int, type: CaveType?): Double {
    if(type == null) return Curve.smoothstep01(((depthBelowSurface - 6).toDouble() / 16.0).coerceIn(0.0, 1.0))

    return Curve.smoothstep01(((depthBelowSurface - type.surfaceFadeDepth).toDouble() / 16.0).coerceIn(0.0, 1.0))
    // depthBelowSurface is >= 0 here
    return if (type?.canOpenToSky == true) {
      // Ravines: fade in quickly near the surface so they can open to sky
      val d = type.surfaceFadeDepth//.coerceAtLeast(1)
      Curve.smoothstep01(((depthBelowSurface.toDouble()+10.0) / d.toDouble()).coerceIn(0.0, 1.0))
    } else {
      // Regular caves: your original fade (prevents open-to-sky caves)
      Curve.smoothstep01(((depthBelowSurface - 6).toDouble() / 16.0).coerceIn(0.0, 1.0))
    }
  }*/
}