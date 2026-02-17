package killercreepr.cruxworldgen.test.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01

class MountainCutCavesOld(
  val threshold01: Double = 0.45,      // lower value = more frequent caves
  val strength: Double = 2.0,          // higher strength to make larger caves
  val openMarginBlocks: Double = 50.0, // Increase carving margin to break through large surfaces

  // Start closer to the surface but still cut through large mountains
  val centerDepthBlocks: Double = 80.0,// Higher value for caves to appear closer to the surface but still cutting through mountains
  val halfWidthBlocks: Double = 50.0,  // Wider vertical band to make larger, more open caves
  override val surfaceFadeStart : Int = 10,
  override val surfaceFadeRamp: Int = 30
) : CaveType, Noised {

  object Noise : NoiseModule {
    object MountainCut3D : NoiseKey { override val id = "cave.mountaincut.3D" }

    override fun install(bank: NoiseBank) {
      bank.register(MountainCut3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.005)  // Lower frequency for larger-scale noise
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2) // More octaves to create larger features
        }
      }
    }
  }

  override val noiseModule = Noise

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    // Vertical band around a depth below surface
    val targetY = cave.surfaceY - centerDepthBlocks
    val dy = kotlin.math.abs(cave.y.toDouble() - targetY)
    val bandT = ((halfWidthBlocks - dy) / halfWidthBlocks).coerceIn(0.0, 1.0)
    val verticalMask = smoothstep01(bandT)
    if (verticalMask <= 0.001) return 0.0

    // Noise to create the cave shape, this makes larger, more open caves
    val n01 = (ctx.noise.get(Noise.MountainCut3D).noise3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val blobMask = smoothstep01(t)

    // Combine vertical and blob masks to shape the cave
    val mask = blobMask * verticalMask
    return mask * (solidDensity * strength + openMarginBlocks)
  }
}
