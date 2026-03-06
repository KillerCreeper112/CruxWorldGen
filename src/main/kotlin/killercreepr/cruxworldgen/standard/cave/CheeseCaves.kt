package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import kotlin.math.max

class CheeseCaves(
  val threshold01: Double = 0.5,
  val strength: Double = 1.08,
  val openMarginBlocks: Double = 20.0,

  val deepStart: Double = 6.0,
  val deepFull: Double = 20.0,
  val nearSurfaceDepth: Double = 10.0,
  val breakThreshold: Double = 0.78,

  override val surfaceFadeStart : Int = 4,
  override val surfaceFadeRamp: Int = 12
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Cheese3D : NoiseKey { override val id = "cave.cheese.3D" }
    object SurfaceBreak2D : NoiseKey { override val id = "cave.cheese.surface_break_2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Cheese3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.010)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      bank.register(SurfaceBreak2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.02)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
    }
  }

  override val noiseModule = Noise

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0
    //if (cave.depthBelowSurface < 0) return 0.0

    val n01 = (ctx.noise.get(Noise.Cheese3D).noise3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val blobMask = smoothstep01(t)
    if (blobMask < 0.35) return 0.0

    val verticalMask = CaveMasks.depthWithBreakthrough(
      ctx = ctx,
      cave = cave,
      noiseKey = Noise.SurfaceBreak2D,
      deepStart = deepStart,
      deepFull = deepFull,
      nearSurfaceDepth = nearSurfaceDepth,
      breakThreshold = breakThreshold
    )

    val mask = blobMask * verticalMask
    if (mask <= 0.001) return 0.0

    return mask * (solidDensity * strength + openMarginBlocks)
  }
}

/*class CheeseCaves(*/
/*  val threshold01: Double = 0.5,      // higher = rarer
  val strength: Double = 1.08,         // must be > 1 to open reliably
  val openMarginBlocks: Double = 20.0, // Flat additive carve amount in “blocks”.
                                       // Helps punch through near-surface / low-density areas so openings actually appear

  // where it lives vertically (below surface)
  val centerDepthBlocks: Double = 45.0,// Preferred depth of this cave layer BELOW THE LOCAL SURFACE.
  val halfWidthBlocks: Double = 90.0,
  val deepStart: Double = 6.0,
  val deepFull: Double = 20.0,
  val surfaceBreakNoise: NoiseKey = Noise.SurfaceBreak2D,
  //val centerYBlocks: GenerateHeightSampler = GenerateHeightSampler.relative(0.5),
  override val surfaceFadeStart : Int = 4,
  override val surfaceFadeRamp: Int = 12
) : CaveType, Noised {

  object Noise : NoiseModule{
    object Cheese3D : NoiseKey{ override val id = "cave.cheese.3D" }
    object SurfaceBreak2D : NoiseKey { override val id = "cave.cheese.surface_break_2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Cheese3D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.010)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }

      bank.register(SurfaceBreak2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.02)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
    }
  }

  override val noiseModule = Noise*/
  /*override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    val dy = abs(cave.y.toDouble() - centerYBlocks.sampleY(ctx))
    val bandT = ((halfWidthBlocks - dy) / halfWidthBlocks).coerceIn(0.0, 1.0)
    val verticalMask = smoothstep01(bandT)
    if (verticalMask <= 0.001) return 0.0

    val n01 = (ctx.noise.get(Noise.Cheese3D).noise3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val blobMask = smoothstep01(t)
    if (blobMask <= 0.001) return 0.0

    val mask = blobMask * verticalMask
    return mask * (solidDensity * strength + openMarginBlocks)
  }*/

  /*override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0
    if (cave.depthBelowSurface < 0) return 0.0

    val depth = cave.depthBelowSurface.toDouble()

    // Main cheese noise
    val n01 = (ctx.noise.get(Noise.Cheese3D).noise3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val blobMask = smoothstep01(t)
    if (blobMask < 0.35) return 0.0

    // General underground preference:
    // weak near surface, fully allowed deeper down
    val deepT = ((depth - deepStart) / (deepFull - deepStart)).coerceIn(0.0, 1.0)
    val deepMask = smoothstep01(deepT)

    // Rare surface breakthrough zones
    val break01 = (ctx.noise.get(Noise.SurfaceBreak2D).noise2D(cave.worldX, cave.worldZ) + 1.0) * 0.5
    val breakThreshold = 0.78
    val breakT = ((break01 - breakThreshold) / (1.0 - breakThreshold)).coerceIn(0.0, 1.0)
    val breakNoiseMask = smoothstep01(breakT)

    // Only matter near the surface
    val nearSurfaceT = (1.0 - depth / 10.0).coerceIn(0.0, 1.0)
    val nearSurfaceMask = smoothstep01(nearSurfaceT)

    val breakthroughMask = breakNoiseMask * nearSurfaceMask

    // Use deep mask normally, but allow rare near-surface override
    val verticalMask = max(deepMask, breakthroughMask)

    val mask = blobMask * verticalMask
    if (mask <= 0.001) return 0.0

    return mask * (solidDensity * strength + openMarginBlocks)
  }*/

  /*override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0
    //if (cave.depthBelowSurface <= 0) return 0.0

    // Vertical band around a depth below surface
    val targetY = cave.surfaceY - centerDepthBlocks
    val dy = kotlin.math.abs(cave.y.toDouble() - targetY)
    val bandT = ((halfWidthBlocks - dy) / halfWidthBlocks).coerceIn(0.0, 1.0)
    val verticalMask = smoothstep01(bandT)
    if (verticalMask <= 0.001) return 0.0

    val n01 = (ctx.noise.get(Noise.Cheese3D).noise3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val blobMask = smoothstep01(t)
    if (blobMask < 0.35) return 0.0  // stops tiny freckles

    val mask = blobMask * verticalMask
    return mask * (solidDensity * strength + openMarginBlocks)
  }*/