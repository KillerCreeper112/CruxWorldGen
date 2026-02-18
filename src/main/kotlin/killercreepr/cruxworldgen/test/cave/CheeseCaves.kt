package killercreepr.cruxworldgen.test.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import killercreepr.cruxworldgen.core.feature.GenerateHeightSampler
import killercreepr.cruxworldgen.core.feature.HeightSampler
import killercreepr.cruxworldgen.core.feature.UniformHeightSampler

class CheeseCaves(
  val threshold01: Double = 0.65,      // higher = rarer
  val strength: Double = 1.08,         // must be > 1 to open reliably
  val openMarginBlocks: Double = 20.0, // Flat additive carve amount in “blocks”.
                                       // Helps punch through near-surface / low-density areas so openings actually appear

  // where it lives vertically (below surface)
  //val centerDepthBlocks: Double = 45.0,// Preferred depth of this cave layer BELOW THE LOCAL SURFACE.
  val halfWidthBlocks: Double = 48.0,
  val centerYBlocks: GenerateHeightSampler = GenerateHeightSampler.relative(0.5),
  override val surfaceFadeStart : Int = 8,
  override val surfaceFadeRamp: Int = 4
) : CaveType, Noised {

  object Noise : NoiseModule{
    object Cheese3D : NoiseKey{ override val id = "cave.cheese.3D" }

    override fun install(bank: NoiseBank) {
      bank.register(Cheese3D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.010)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
    }
  }

  override val noiseModule = Noise

  override fun addBlocks(
    ctx: GenerateContext,
    cave: CaveContext,
    add: Double
  ): Double {
    return super.addBlocks(ctx, cave, add)
  }

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    val dy = kotlin.math.abs(cave.y.toDouble() - centerYBlocks.sampleY(ctx))
    val bandT = ((halfWidthBlocks - dy) / halfWidthBlocks).coerceIn(0.0, 1.0)
    val verticalMask = smoothstep01(bandT)
    if (verticalMask <= 0.001) return 0.0

    val n01 = (ctx.noise.get(Noise.Cheese3D).noise3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val blobMask = smoothstep01(t)
    if (blobMask <= 0.001) return 0.0

    val mask = blobMask * verticalMask
    return mask * (solidDensity * strength + openMarginBlocks)
  }


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
    //if (blobMask < 0.35) return 0.0  // stops tiny freckles

    val mask = blobMask// * verticalMask
    return mask * (solidDensity * strength + openMarginBlocks)
  }*/
}