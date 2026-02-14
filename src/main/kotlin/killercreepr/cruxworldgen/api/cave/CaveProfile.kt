package killercreepr.cruxworldgen.api.cave

import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.util.Curve

open class CaveProfile(
  val caveTypes: List<CaveType>
) : CaveShape {

  override fun carve(ctx: GenerateContext, cave: CaveContext): Double {
    // Above surface => never carve
    if (cave.depthBelowSurface < 0) return 0.0

    val (strongestCarve, strongestType) = strongestCarveAndType(ctx, cave)
    if (strongestCarve <= 0.0001) return 0.0

    val fade = depthFade(cave.depthBelowSurface, strongestType)
    val edgeFade = Curve.smoothstep01((1.0 - cave.edge.edgeBlendFactor()).coerceIn(0.0, 1.0))

    val carved = strongestCarve * fade * edgeFade

    // Safety cap: never carve more than local solid density + margin
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    val maxAllowed = solidDensity + 2.0
    return carved.coerceAtMost(maxAllowed)
  }

  override fun add(ctx: GenerateContext, c: CaveContext): Double {
    // Above surface => no cave additions
    if (c.depthBelowSurface < 100) return 0.0

    val (strongestCarve, strongestType) = strongestCarveAndType(ctx, c)
    if (strongestCarve <= 0.0001) return 0.0

    val fade = depthFade(c.depthBelowSurface, strongestType)
    val edgeFade = Curve.smoothstep01((1.0 - c.edge.edgeBlendFactor()).coerceIn(0.0, 1.0))

    var strongestAdd = 0.0
    for (type in caveTypes) {
      strongestAdd = maxOf(strongestAdd, type.addBlocks(ctx, c, strongestCarve))
    }

    val added = strongestAdd * fade * edgeFade

    // SAFETY: never add more than carve + margin (prevents filling caves back in)
    val maxAllowedAdd = strongestCarve + 0.75
    return added.coerceIn(0.0, maxAllowedAdd)
  }

  fun strongestCarveAndType(ctx: GenerateContext, cave: CaveContext): Pair<Double, CaveType?> {
    var best = 0.0
    var bestType: CaveType? = null
    for (type in caveTypes) {
      val v = type.carveBlocks(ctx, cave)
      if (v > best) {
        best = v
        bestType = type
      }
    }
    return best to bestType
  }

  fun depthFade(depthBelowSurface: Int, type: CaveType?): Double {
    // depthBelowSurface is >= 0 here
    return if (type?.canOpenToSky == true) {
      // Ravines: fade in quickly near the surface so they can open to sky
      val d = type.surfaceFadeDepth.coerceAtLeast(1)
      Curve.smoothstep01(((depthBelowSurface.toDouble()+10.0) / d.toDouble()).coerceIn(0.0, 1.0))
    } else {
      // Regular caves: your original fade (prevents open-to-sky caves)
      Curve.smoothstep01(((depthBelowSurface - 6).toDouble() / 16.0).coerceIn(0.0, 1.0))
    }
  }
}