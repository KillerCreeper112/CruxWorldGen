package killercreepr.cruxworldgen.test.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01

class HorizontalMountainTunnel(
  val threshold01: Double = 0.45,          // Threshold for cave frequency (lower = more frequent)
  val strength: Double = 5.0,              // Carve strength to punch through the mountain
  val openMarginBlocks: Double = 100.0,    // Extra carve margin to ensure passage breaks through solid rock

  // Horizontal span for carving through the mountain (across X and Z axes)
  val horizontalSpanWidth: Double = 700.0, // How wide the tunnel should be horizontally (across the mountain)

  // Fixed height (in absolute terms) where the tunnel should carve horizontally through the mountain
  val fixedCarveHeight: Double = 180.0,     // Height for carving the tunnel (absolute Y-coordinate)

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

    // Step 1: Get the column's surface height (this is the Y-coordinate of the surface for the specific column).
    val surfaceHeight = cave.surfaceY

    // Step 2: Define the fixed carving height for the tunnel (ignore the column surface height here).
    // Use the absolute value of `fixedCarveHeight` for a consistent height across the mountain
    val targetY = fixedCarveHeight  // This is the height at which we want the tunnel carved

    // Step 3: Carve horizontally across the mountain range (across X and Z)
    val distanceFromCaveCenterX = kotlin.math.abs(cave.worldX - surfaceHeight)  // Distance along X axis from the center
    val distanceFromCaveCenterZ = kotlin.math.abs(cave.worldZ - surfaceHeight)  // Distance along Z axis from the center

    // Horizontal spread calculation across both X and Z axes (to cut through the whole mountain horizontally)
    val horizontalMaskX = ((horizontalSpanWidth - distanceFromCaveCenterX) / horizontalSpanWidth).coerceIn(0.0, 1.0)
    val horizontalMaskZ = ((horizontalSpanWidth - distanceFromCaveCenterZ) / horizontalSpanWidth).coerceIn(0.0, 1.0)

    val adjustedHorizontalMask = smoothstep01(horizontalMaskX * horizontalMaskZ)

    // Step 4: Now focus on the vertical range. The tunnel should carve at a fixed height and width.
    // Create a vertical mask that ensures the tunnel is flat at `fixedCarveHeight`.
    val dy = kotlin.math.abs(cave.y.toDouble() - targetY)

    // Vertical range control (this prevents the tunnel from cutting vertically too deep or too shallow).
    val verticalMask = (1.0 - (dy / 10.0)).coerceIn(0.0, 1.0) // Adjust the vertical range as needed
    val adjustedVerticalMask = smoothstep01(verticalMask)

    // If both horizontal and vertical masks are low, return 0.0 (no carving)
    if (adjustedHorizontalMask <= 0.001 || adjustedVerticalMask <= 0.001) return 0.0

    // Step 5: Apply noise for cave variation, generating smoother transitions
    val n01 = (ctx.noise.get(Noise.MountainCut3D).noise3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val blobMask = smoothstep01(t)

    // Step 6: Combine the horizontal and vertical masks to define the final shape of the tunnel
    val finalMask = blobMask * adjustedHorizontalMask * adjustedVerticalMask
    return finalMask * (solidDensity * strength + openMarginBlocks)
  }
}



/*class HorizontalMountainTunnel(
  val threshold01: Double = 0.45,          // Threshold for cave frequency (lower = more frequent)
  val strength: Double = 5.0,              // Carve strength to punch through the mountain
  val openMarginBlocks: Double = 100.0,    // Extra carve margin to ensure passage breaks through solid rock

  // Horizontal span for carving through the mountain (across X and Z axes)
  val horizontalSpanWidth: Double = 700.0, // How wide the tunnel should be horizontally (across the mountain)

  // Vertical starting depth of the tunnel
  val verticalCutDepth: Double = 50.0,     // Starting depth from the surface for the tunnel

  // Control the height of the tunnel (how far up/down it should carve)
  val verticalTunnelHeight: Double = 40.0, // Vertical height of the tunnel (how high and low it carves)

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

    // Step 1: Get the surface height of the terrain (this is the Y-coordinate of the surface).
    val surfaceHeight = cave.surfaceY

    // Step 2: Calculate the starting position of the tunnel, based on surface height and vertical cut depth
    val targetY = surfaceHeight - verticalCutDepth  // This will be the Y-level where the tunnel starts

    // Step 3: Carve horizontally across the mountain range (across X and Z)
    val distanceFromCaveCenterX = kotlin.math.abs(cave.worldX - surfaceHeight)  // Distance along X axis from the center
    val distanceFromCaveCenterZ = kotlin.math.abs(cave.worldZ - surfaceHeight)  // Distance along Z axis from the center

    // Horizontal spread calculation across both X and Z axes (to cut through the whole mountain horizontally)
    val horizontalMaskX = ((horizontalSpanWidth - distanceFromCaveCenterX) / horizontalSpanWidth).coerceIn(0.0, 1.0)
    val horizontalMaskZ = ((horizontalSpanWidth - distanceFromCaveCenterZ) / horizontalSpanWidth).coerceIn(0.0, 1.0)

    val adjustedHorizontalMask = smoothstep01(horizontalMaskX * horizontalMaskZ)

    // Step 4: Adjust the vertical mask to define the height of the tunnel
    val dy = kotlin.math.abs(cave.y.toDouble() - targetY)

    // Vertical range control (this prevents the tunnel from cutting vertically too deep)
    val verticalMask = ((verticalTunnelHeight - dy) / verticalTunnelHeight).coerceIn(0.0, 1.0)
    val adjustedVerticalMask = smoothstep01(verticalMask)

    // If both horizontal and vertical masks are low, return 0.0 (no carving)
    if (adjustedHorizontalMask <= 0.001 || adjustedVerticalMask <= 0.001) return 0.0

    // Step 5: Apply noise for cave variation, generating smoother transitions
    val n01 = (ctx.noise.get(Noise.MountainCut3D).noise3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val blobMask = smoothstep01(t)

    // Step 6: Combine the horizontal and vertical masks to define the final shape of the tunnel
    val finalMask = blobMask * adjustedHorizontalMask * adjustedVerticalMask
    return finalMask * (solidDensity * strength + openMarginBlocks)
  }
}*/


/*class HorizontalMountainTunnel(
  val threshold01: Double = 0.3,         // Threshold for cave frequency (lower = more frequent)
  val strength: Double = 5.0,             // Increase strength to carve larger passages through the mountain
  val openMarginBlocks: Double = 100.0,   // Carve additional margin to ensure we punch through solid rock

  // Define the horizontal span of the cave to go through the entire mountain horizontally
  val horizontalSpanWidth: Double = 700.0, // How wide the cave should be horizontally (across the mountain)

  // Adjust the vertical cut depth (how far down the tunnel goes into the mountain)
  val verticalCutDepth: Double = 50.0,    // Depth from surface to start the cave cut (this is the depth at which the horizontal tunnel starts)

  // Set the vertical range of the tunnel to determine how tall the horizontal tunnel should be
  val verticalTunnelHeight: Double = 40.0, // Height of the tunnel (vertical spread), how high and low the tunnel stretches

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

    // Step 1: Get the surface height of the terrain (this is the Y-coordinate of the surface).
    val surfaceHeight = cave.surfaceY

    // Step 2: Calculate the starting position of the tunnel, based on surface height and vertical cut depth
    val targetY = surfaceHeight - verticalCutDepth  // This will be the Y-level where the tunnel starts

    // Step 3: Focus on the horizontal spread of the tunnel. Spread the carve across the X and Z axes.
    val distanceFromCaveCenterX = kotlin.math.abs(cave.worldX - surfaceHeight)  // Distance along X axis from the center
    val distanceFromCaveCenterZ = kotlin.math.abs(cave.worldZ - surfaceHeight)  // Distance along Z axis from the center

    // Horizontal spread calculation across both X and Z axes
    val horizontalMaskX = ((horizontalSpanWidth - distanceFromCaveCenterX) / horizontalSpanWidth).coerceIn(0.0, 1.0)
    val horizontalMaskZ = ((horizontalSpanWidth - distanceFromCaveCenterZ) / horizontalSpanWidth).coerceIn(0.0, 1.0)

    val adjustedHorizontalMask = smoothstep01(horizontalMaskX * horizontalMaskZ)

    // Step 4: Now focus on the vertical "cut" range. The tunnel should cut through the mountain horizontally,
    // but we want it to be a certain height (based on `verticalTunnelHeight`).
    val dy = kotlin.math.abs(cave.y.toDouble() - targetY)

    // Create a vertical mask to control the tunnel height. This controls how high and low the tunnel goes.
    val verticalMask = ((verticalTunnelHeight - dy) / verticalTunnelHeight).coerceIn(0.0, 1.0)
    val adjustedVerticalMask = smoothstep01(verticalMask)

    // If both horizontal and vertical masks are low, return 0.0 (no carving)
    if (adjustedHorizontalMask <= 0.001 || adjustedVerticalMask <= 0.001) return 0.0

    // Step 5: Apply noise to introduce some variation into the tunnel (randomize the shape slightly)
    val n01 = (ctx.noise.get(Noise.MountainCut3D).noise3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val blobMask = smoothstep01(t)

    // Step 6: Combine the horizontal and vertical masks to define the final shape of the tunnel
    val finalMask = blobMask * adjustedHorizontalMask * adjustedVerticalMask
    return finalMask * (solidDensity * strength + openMarginBlocks)
  }
}*/

/*
class HorizontalThroughMountainCave(
  val threshold01: Double = 0.3,         // Threshold for cave frequency (lower = more frequent)
  val strength: Double = 10.0,             // Increase strength to carve larger passages through the mountain
  val openMarginBlocks: Double = 100.0,   // Carve additional margin to ensure we punch through solid rock

  // Define the horizontal span of the cave to go through the entire mountain horizontally
  val horizontalSpanWidth: Double = 1500.0, // How wide the cave should be horizontally (across the mountain)

  // Adjust the vertical cut depth and make sure the cave starts higher up
  val verticalCutDepth: Double = 50.0,    // Depth from surface to start the cave cut (make this smaller)

  // Decrease the vertical carving range to focus on horizontal cutting
  val verticalCarveHeight: Double = 20.0, // Controls the vertical carving range (keep it shallow)

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
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    // Step 1: Get the highest point (surfaceY) for the current mountain location
    val surfaceHeight = cave.surfaceY

    // Step 2: Calculate the starting position of the cave, which will be based on the surface height and desired depth
    val targetY = surfaceHeight - verticalCutDepth

    // Step 3: Carve horizontally across the mountain range (across x/z)
    val distanceFromCaveCenterX = abs(cave.worldX - surfaceHeight)  // Relative to the surface (horizontal X distance)
    val distanceFromCaveCenterZ = abs(cave.worldZ - surfaceHeight)  // Relative to the surface (horizontal Z distance)

    // Horizontal span calculation (spread out in X and Z direction)
    val horizontalMaskX = ((horizontalSpanWidth - distanceFromCaveCenterX) / horizontalSpanWidth).coerceIn(0.0, 1.0)
    val horizontalMaskZ = ((horizontalSpanWidth - distanceFromCaveCenterZ) / horizontalSpanWidth).coerceIn(0.0, 1.0)

    val adjustedHorizontalMask = smoothstep01(horizontalMaskX * horizontalMaskZ)

    // Step 4: Adjust the vertical mask to carve from the surface to the target depth (cut vertically)
    val dy = abs(cave.y.toDouble() - targetY)

    // Smooth the vertical mask to reduce the blob effect.
    // A smoother transition can help maintain a continuous tunnel and avoid disconnected blobs
    val verticalMask = (1.0 - Math.pow(dy / verticalCarveHeight, 2.0)).coerceIn(0.0, 1.0)
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


/*
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

class HorizontalThroughMountainCave(
  val threshold01: Double = 0.45,         // Threshold for cave frequency (lower = more frequent)
  val strength: Double = 5.0,             // Increase strength to carve larger passages through the mountain
  val openMarginBlocks: Double = 100.0,   // Carve additional margin to ensure we punch through solid rock

  // Define the horizontal span of the cave to go through the entire mountain horizontally
  val horizontalSpanWidth: Double = 600.0, // How wide the cave should be horizontally (across the mountain)
  
  // Adjust the vertical cut depth and make sure the cave starts higher up
  val verticalCutDepth: Double = 50.0,    // Depth from surface to start the cave cut (make this smaller)
  
  // Decrease the vertical carving range to focus on horizontal cutting
  val verticalCarveHeight: Double = 60.0, // Controls the vertical carving range (shallow carve)
  
  override val surfaceFadeStart: Int = 0,
  override val surfaceFadeRamp: Int = 0
) : CaveType, Noised {

  object Noise : NoiseModule {
    object MountainCut3D : NoiseKey { override val id = "cave.mountaincut.3D" }//todo naming

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
    val distanceFromCaveCenterX = kotlin.math.abs(cave.worldX - surfaceHeight)  // Relative to the surface (horizontal X distance)
    val distanceFromCaveCenterZ = kotlin.math.abs(cave.worldZ - surfaceHeight)  // Relative to the surface (horizontal Z distance)

    // Horizontal span calculation (spread out in X and Z direction)
    val horizontalMaskX = ((horizontalSpanWidth - distanceFromCaveCenterX) / horizontalSpanWidth).coerceIn(0.0, 1.0)
    val horizontalMaskZ = ((horizontalSpanWidth - distanceFromCaveCenterZ) / horizontalSpanWidth).coerceIn(0.0, 1.0)

    val adjustedHorizontalMask = smoothstep01(horizontalMaskX * horizontalMaskZ)

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
*/


/*class ThroughMountainCave(
  val threshold01: Double = 0.45,         // Threshold for cave frequency (lower = more frequent)
  val strength: Double = 5.0,             // Increase strength to carve larger passages through the mountain
  val openMarginBlocks: Double = 100.0,   // Carve additional margin to ensure we punch through solid rock

  // Define the horizontal span of the cave to go through the entire mountain horizontally
  val horizontalSpanWidth: Double = 300.0, // How wide the cave should be horizontally (across the mountain)

  // Adjust the vertical cut depth and make sure the cave starts higher up
  val verticalCutDepth: Double = 50.0,    // Depth from surface to start the cave cut (make this smaller)

  // Decrease the vertical carving range to focus on horizontal cutting
  val verticalCarveHeight: Double = 100.0, // Controls the vertical carving range (increase to make sure the cave cuts through the entire mountain)

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
    val distanceFromCaveCenterX = kotlin.math.abs(cave.worldX - surfaceHeight)  // Relative to the surface (horizontal X distance)
    val distanceFromCaveCenterZ = kotlin.math.abs(cave.worldZ - surfaceHeight)  // Relative to the surface (horizontal Z distance)

    // Horizontal span calculation (spread out in X and Z direction)
    val horizontalMaskX = ((horizontalSpanWidth - distanceFromCaveCenterX) / horizontalSpanWidth).coerceIn(0.0, 1.0)
    val horizontalMaskZ = ((horizontalSpanWidth - distanceFromCaveCenterZ) / horizontalSpanWidth).coerceIn(0.0, 1.0)

    val adjustedHorizontalMask = smoothstep01(horizontalMaskX * horizontalMaskZ)

    // Step 4: Adjust the vertical mask to carve from the surface to the target depth (cut vertically)
    val dy = kotlin.math.abs(cave.y.toDouble() - targetY)

    // Gradually carve through the mountain and avoid creating gaps or blobs.
    val verticalMask = (1.0 - Math.pow(dy / verticalCarveHeight, 2.0)).coerceIn(0.0, 1.0)
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
}*/
