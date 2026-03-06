package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.signal.SignalKey
import killercreepr.cruxworldgen.api.util.Curve
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

class RavineCarver(
  val threshold01: Double = 0.9,      // higher = rarer ravines
  val baseHalfWidth: Double = 3.0,     // blocks (half width)
  val widthVar: Double = 4.0,         // extra half-width from noise
  val baseDepth: Double = 70.0,        // blocks down from surface
  val depthVar: Double = 100.0,         // extra depth from noise
  val wallPower: Double = 1.7,         // higher = steeper walls
  val warpAmp: Double = 18.0,          // blocks of horizontal meander
  val strength: Double = 1.4,         // must be > 1 to open reliably
  val openMarginBlocks: Double = 10.0,  // extra carve to guarantee air
  val bridgeThreshold01: Double = 0.82,  // higher = rarer bridges
  val bridgeThickness: Double = 3.0,     // vertical thickness in blocks
  val bridgeStrength: Double = 1.0,       // 0..1 how much it suppresses carving
  override val surfaceFadeStart : Int = 0,
  override val surfaceFadeRamp: Int = 3
) : CaveType, Noised {
  object Signal {
    object RavineFloor : SignalKey.Companion.DoubleSignalKey()
    object RavineMask : SignalKey.Companion.DoubleSignalKey()
  }

  object Noise : NoiseModule {
    object Warp2D : NoiseKey { override val id = "cave.ravine.warp2D" }
    object Mask2D : NoiseKey { override val id = "cave.ravine.mask2D" }
    object Bridge2D : NoiseKey { override val id = "cave.ravine.bridge2D" }
    object Var2D : NoiseKey { override val id = "cave.ravine.var2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Warp2D){ seed ->
        NoiseField.Companion.noiseField(seed){
          frequency(0.008)  // higher = wigglier
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(Mask2D){ seed ->
        NoiseField.Companion.noiseField(seed){
          frequency(0.0018) // lower = longer ravines
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(Bridge2D){ seed ->
        NoiseField.Companion.noiseField(seed){
          frequency(0.0016) // controls width/depth patches
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(Var2D){ seed ->
        NoiseField.Companion.noiseField(seed){
          frequency(0.0035) // controls width/depth patches
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
    }
  }

  override val noiseModule = Noise

  val widthToNoise = 0.02 // tune: bigger => wider ravines for same halfWidth

  override fun carveBlocks(ctx: GenerateContext, c: CaveContext): Double {
    val solidDensity = max(0.0, c.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    // --- 1) Domain warp so ravines meander ---
    val wx = c.worldX.toDouble()
    val wz = c.worldZ.toDouble()
    val warpX = ctx.noise.get(Noise.Warp2D).noise2D(wx, wz) * warpAmp
    val warpZ = ctx.noise.get(Noise.Warp2D).noise2D(wx + 1000.0, wz + 1000.0) * warpAmp
    val xw = wx + warpX
    val zw = wz + warpZ

    // --- 2) Make "ribbons": ridge = 1 - abs(noise) gives long stripe-like bands ---
    val n = 1.0//todo ctx.noise.ravineMask2D(xw, zw) // [-1,1]
    val ridge01 = 1.0 - abs(n)             // [0,1], high near the ridge centerline

    val t = ((ridge01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    if (t <= 0.0) return 0.0

    // --- 3) Width & depth vary along the ribbon ---
    val v01 = (ctx.noise.get(Noise.Var2D).noise2D(xw, zw) + 1.0) * 0.5
    val halfWidth = (baseHalfWidth + widthVar * v01).coerceAtLeast(2.0)
    val depth = (baseDepth + depthVar * v01).coerceAtLeast(8.0)

    // Convert ridge strength -> distance-ish factor.
    // ridge01 is 1 at centerline, 0 away.
    // We want width falloff: center strong, edge fades.
    val widthMask = Curve.smoothstep01(t) // already pretty good "center mask"

    // --- 4) Vertical profile: carve from near surface down to (surfaceY - depth) ---
    val y = c.y.toDouble()

    // 0 at top (surface), 1 at bottom (floor)
    val v = ((c.surfaceY - y) / depth).coerceIn(0.0, 1.0)

    // Walls: strongest carve in the middle, less near bottom if you want a flatter floor.
    // This gives a canyon feel (steeper near top, rounding near bottom).
    val verticalMask = (1.0 - v.pow(wallPower))

    // Final carve strength
    val mask = widthMask * verticalMask
    c.signalWriter.max(c.worldX, c.y, c.worldZ,
      Signal.RavineMask, mask)

    val nearBottom01 = ((v - 0.85) / 0.15).coerceIn(0.0, 1.0)
    c.signalWriter.max(c.worldX, c.y, c.worldZ,
      Signal.RavineFloor, nearBottom01)
    if (mask <= 0.001) return 0.0

    val bridge01 = (ctx.noise.get(Noise.Bridge2D).noise2D(xw, zw) + 1.0) * 0.5
    if (bridge01 > bridgeThreshold01) {
      // pick a bridge height inside the ravine (biased toward upper half)
      val t01 = ((bridge01 - bridgeThreshold01) / (1.0 - bridgeThreshold01)).coerceIn(0.0, 1.0)
      val bridgeY = c.surfaceY - depth * (0.25 + 0.35 * t01)  // 25%..60% down

      // gaussian-ish vertical band
      val dy = abs(y - bridgeY) / bridgeThickness
      val band = exp(-(dy * dy)) // 1 at center, fades out

      val suppress = (t01 * band * bridgeStrength).coerceIn(0.0, 1.0)

      // reduce carving locally => leaves a “bridge / roof segment”
      val finalMask = mask * (1.0 - suppress)
      if (finalMask <= 0.001) return 0.0
      return finalMask * (solidDensity * strength + openMarginBlocks)
    }
    return mask * (solidDensity * strength + openMarginBlocks)
  }
}