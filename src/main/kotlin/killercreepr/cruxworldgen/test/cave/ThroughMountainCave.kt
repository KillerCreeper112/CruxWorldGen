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

class ThroughMountainCave(
  val threshold01: Double = 0.45,         // Threshold for cave frequency (lower = more frequent)
  val strength: Double = 5.0,             // Increase strength to carve larger passages through the mountain
  val openMarginBlocks: Double = 100.0,   // Carve additional margin to ensure we punch through solid rock

  // Define horizontal span of the cave to go through the entire mountain
  val horizontalSpanWidth: Double = 600.0, // How wide the cave should be horizontally (across the mountain)

  // Reduce the depth of the cave cut to make it generate higher up
  val verticalCutDepth: Double = 40.0,    // Depth from surface to start the cave cut (make this smaller)

  // Increase the vertical carving range to ensure the cave breaks through completely
  val verticalCarveHeight: Double = 50.0,  // Controls the vertical height of the carving (more depth)

  override val surfaceFadeStart: Int = 0,
  override val surfaceFadeRamp: Int = 0
) : CaveType, Noised {

  object Noise : NoiseModule {
    object MountainCut3D : NoiseKey { override val id = "cave.mountaincut.3D" }

    override fun install(bank: NoiseBank) {
      bank.register(MountainCut3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.005)  // Large-scale noise for large terrain features
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3) // More octaves for large, varied terrain carving
        }
      }
    }
  }

  override val noiseModule = Noise

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    // Step 1: Get the highest point (surfaceY) for the current mountain location
    val surfaceHeight = cave.surfaceY

    // Step 2: Calculate the starting position of the cave, which will be based on the surface height and desired depth
    val targetY = surfaceHeight - verticalCutDepth

    // Step 3: Carve horizontally across the mountain range (across x/z)
    val distanceFromCaveCenter = kotlin.math.abs(cave.worldX - surfaceHeight)  // Relative to the surface (horizontal distance)
    val horizontalMask = ((horizontalSpanWidth - distanceFromCaveCenter) / horizontalSpanWidth).coerceIn(0.0, 1.0)
    val adjustedHorizontalMask = smoothstep01(horizontalMask)

    // Step 4: Adjust the vertical mask to carve from the surface to the target depth (cut vertically)
    val dy = kotlin.math.abs(cave.y.toDouble() - targetY)
    val verticalMask = ((verticalCarveHeight - dy) / verticalCarveHeight).coerceIn(0.0, 1.0)
    val adjustedVerticalMask = smoothstep01(verticalMask)

    // If both horizontal and vertical masks are low, return 0.0 (no carving)
    if (adjustedHorizontalMask <= 0.001 || adjustedVerticalMask <= 0.001) return 0.0

    // Step 5: Apply noise for cave variation
    val n01 = (ctx.noise.get(Noise.MountainCut3D).noise3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val blobMask = smoothstep01(t)

    // Step 6: Combine masks to define the final carving shape (both horizontal and vertical)
    val finalMask = blobMask * adjustedHorizontalMask * adjustedVerticalMask
    return finalMask * (solidDensity * strength + openMarginBlocks)
  }
}



/*todo
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

class ThroughMountainCave(
  val threshold01: Double = 0.45,         // Threshold for cave frequency (lower = more frequent)
  val strength: Double = 3.0,             // Strength to carve larger passages through the mountain
  val openMarginBlocks: Double = 100.0,   // Carve additional margin to ensure we punch through solid rock

  // Define horizontal span of the cave to go through the entire mountain
  val horizontalSpanWidth: Double = 150.0, // How wide the cave should be horizontally (across the mountain)
  val verticalCutDepth: Double = 80.0,    // The depth of the cave cut through the mountain (from peak downwards)
  override val surfaceFadeStart: Int = 10,
  override val surfaceFadeRamp: Int = 30
) : CaveType, Noised {

  object Noise : NoiseModule {
    object MountainCut3D : NoiseKey { override val id = "cave.mountaincut.3D" }

    override fun install(bank: NoiseBank) {
      bank.register(MountainCut3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.005)  // Large-scale noise for large terrain features
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3) // More octaves for large, varied terrain carving
        }
      }
    }
  }

  override val noiseModule = Noise

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    // Step 1: Get the highest point (surfaceY) for the current mountain location
    val surfaceHeight = cave.surfaceY

    // Step 2: Calculate the starting position of the cave, which will be based on the surface height and desired depth
    val targetY = surfaceHeight - verticalCutDepth

    // Step 3: Carve horizontally across the mountain range (across x/z)
    val distanceFromCaveCenter = kotlin.math.abs(cave.worldX - surfaceHeight)  // Relative to the surface (horizontal distance)
    val horizontalMask = ((horizontalSpanWidth - distanceFromCaveCenter) / horizontalSpanWidth).coerceIn(0.0, 1.0)
    val adjustedHorizontalMask = smoothstep01(horizontalMask)

    // Step 4: Adjust the vertical mask to carve from the surface to the target depth (cut vertically)
    val dy = kotlin.math.abs(cave.y.toDouble() - targetY)
    val verticalMask = ((verticalCutDepth - dy) / verticalCutDepth).coerceIn(0.0, 1.0)
    val adjustedVerticalMask = smoothstep01(verticalMask)

    // If both horizontal and vertical masks are low, return 0.0 (no carving)
    if (adjustedHorizontalMask <= 0.001 || adjustedVerticalMask <= 0.001) return 0.0

    // Step 5: Apply noise for cave variation
    val n01 = (ctx.noise.get(Noise.MountainCut3D).noise3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val blobMask = smoothstep01(t)

    // Step 6: Combine masks to define the final carving shape (both horizontal and vertical)
    val finalMask = blobMask * adjustedHorizontalMask * adjustedVerticalMask
    return finalMask * (solidDensity * strength + openMarginBlocks)
  }
}
*/
