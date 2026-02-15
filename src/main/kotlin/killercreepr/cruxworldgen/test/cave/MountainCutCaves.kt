package killercreepr.cruxworldgen.test.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.NoiseBank
import killercreepr.cruxworldgen.api.noise.NoiseField
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.noise.NoiseModule
import killercreepr.cruxworldgen.api.noise.Noised
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01

class MountainCutCaves(
  val threshold01: Double = 0.45,      // lower value = more frequent caves
  val strength: Double = 2.0,          // higher strength to make larger caves
  val openMarginBlocks: Double = 50.0, // Increase carving margin to break through large surfaces

  // Center depth is a range around surface, with a flexible cut through different altitudes
  val centerDepthRange: Double = 100.0, // Allow caves to start at varying depths
  val verticalBandThickness: Double = 50.0,  // Adjustable width for vertical carving
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

    // Calculate the dynamic surface height at this point (e.g., the top of the mountain)
    val surfaceHeight = cave.surfaceY

    // Calculate the vertical offset of the cave relative to the surface
    val targetY = surfaceHeight - centerDepthRange
    val dy = kotlin.math.abs(cave.y.toDouble() - targetY)

    // Create a more dynamic vertical mask based on cave's height relative to the surface
    val verticalMask = ((verticalBandThickness - dy) / verticalBandThickness).coerceIn(0.0, 1.0)
    val adjustedVerticalMask = smoothstep01(verticalMask)

    // If the cave is too far from the surface, return 0.0 to avoid carving in the wrong spot
    if (adjustedVerticalMask <= 0.001) return 0.0

    // Use noise to determine if the cave should exist at this point
    val n01 = (ctx.noise.get(Noise.MountainCut3D).noise3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val blobMask = smoothstep01(t)

    // Combine the vertical and blob masks to define the cave's shape
    val mask = blobMask * adjustedVerticalMask
    return mask * (solidDensity * strength + openMarginBlocks)
  }
}
