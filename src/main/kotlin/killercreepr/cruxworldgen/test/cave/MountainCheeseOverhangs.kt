package killercreepr.cruxworldgen.test.cave

import io.papermc.paper.command.brigadier.argument.ArgumentTypes.world
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

class MountainCheeseOverhangs(
  val threshold01: Double = 0.62,
  val strength: Double = 1.15,
  val openMarginBlocks: Double = 26.0,

  // band positioning (relative to local surface)
  val centerDepthBlocks: Double = 26.0,  // shallower than normal cheese = more likely to open to air
  val halfWidthBlocks: Double = 26.0,

  // gating
  val minMountainY: Int = 92,            // ABSOLUTE cutoff: only carve above this Y
  val minMountainYVariation : Double = 3.0,
  val minSurfaceAboveSea: Double = 38.0, // only carve if the local surface is "mountain-ish"
  val fadeRange: Double = 22.0,          // smooth fade into mountain zone

  override val surfaceFadeStart: Int = 0,
  override val surfaceFadeRamp: Int = 0
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Cheese3D : NoiseKey { override val id = "cave.mountain_cheese.3D" }
    object YVariation2D : NoiseKey { override val id = "cave.mountain_cheese.y_variation" }
    override fun install(bank: NoiseBank) {
      bank.register(Cheese3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.008) // bigger blobs than 0.010
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(1)
        }
      }
      bank.register(YVariation2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.025) // bigger blobs than 0.010
          noiseType(CruxNoise.NoiseType.OpenSimplex2)
          fractalType(CruxNoise.FractalType.FBm)
          fractalOctaves(1)
        }
      }
    }
  }
  override val noiseModule = Noise

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = kotlin.math.max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    // --- Gate 1: absolute Y (cheap) ---
    val minY = minMountainY * (ctx.noise.get(Noise.YVariation2D).noise2D(cave.worldX, cave.worldZ) * minMountainYVariation)
    if (cave.y < minY) return 0.0

    // --- Gate 2: only where the *surface* is high enough to be "mountain area" ---
    // If you can access sea level here:
    val sea = ctx.chunkContext.seaLevel.toDouble()
    val surfaceAboveSea = cave.surfaceY - sea

    // Smooth fade so it doesn’t hard-cut
    val mountainT = ((surfaceAboveSea - minSurfaceAboveSea) / fadeRange).coerceIn(0.0, 1.0)
    val mountainMask = smoothstep01(mountainT)
    if (mountainMask <= 0.001) return 0.0

    // --- Vertical band (relative to surface) ---
    val targetY = cave.surfaceY - centerDepthBlocks
    val dy = kotlin.math.abs(cave.y.toDouble() - targetY)
    val bandT = ((halfWidthBlocks - dy) / halfWidthBlocks).coerceIn(0.0, 1.0)
    val verticalMask = smoothstep01(bandT)
    if (verticalMask <= 0.001) return 0.0

    // --- Blob mask ---
    val n01 = (ctx.noise.get(Noise.Cheese3D).noise3D(cave.worldX, cave.y, cave.worldZ) + 1.0) * 0.5
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val blobMask = smoothstep01(t)
    if (blobMask <= 0.001) return 0.0

    // Combine masks
    val mask = blobMask * mountainMask

    // Carve amount
    return mask * (solidDensity * strength + openMarginBlocks)
  }
}
