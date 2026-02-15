package killercreepr.cruxworldgen.test.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import kotlin.math.abs
import kotlin.math.max

class HorizontalCarve(
  val threshold01: Double = 0.65,      // higher = rarer
  val strength: Double = 1.08,         // must be > 1 to open reliably
  val openMarginBlocks: Double = 20.0, // Flat additive carve amount in “blocks”.
                                       // Helps punch through near-surface / low-density areas so openings actually appear

  // where it lives vertically (below surface)
  val centerDepthBlocks: Double = 45.0,// Preferred depth of this cave layer BELOW THE LOCAL SURFACE.
                                       // targetY = surfaceY - centerDepthBlocks
  val halfWidthBlocks: Double = 22.0,// Half-thickness of the layer around targetY.
                                    // Larger = cheese exists in a thicker vertical band.
  override val surfaceFadeStart : Int = 0,
  override val surfaceFadeRamp: Int = 0
) : CaveType, Noised {

  object Noise : NoiseModule{
    object Cheese3D : NoiseKey{ override val id = "cave.horizontal.3D" }
    object Detail3D : NoiseKey{ override val id = "cave.horizontal.detail3D" }

    override fun install(bank: NoiseBank) {
      bank.register(Cheese3D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.001)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(Detail3D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.05)
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
    //if (cave.depthBelowSurface <= 0) return 0.0

    // Vertical band around a depth below surface
    val targetY = cave.surfaceY - centerDepthBlocks
    val dy = abs(cave.y.toDouble() - targetY)
    val bandT = ((halfWidthBlocks - dy) / halfWidthBlocks).coerceIn(0.0, 1.0)
    val verticalMask = smoothstep01(bandT)
    if (verticalMask <= 0.001) return 0.0

    val n01 = (ctx.noise.get(Noise.Cheese3D).noise3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val blobMask = smoothstep01(t)
    //if (blobMask < 0.35) return 0.0  // stops tiny freckles

    val mask = blobMask// * verticalMask
    return mask * (solidDensity * strength + openMarginBlocks)
  }
}