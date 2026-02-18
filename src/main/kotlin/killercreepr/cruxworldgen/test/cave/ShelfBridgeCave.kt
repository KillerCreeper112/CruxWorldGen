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

class ShelfBridgeCave(
  private val mask2D: NoiseKey = Noise.Mask2D,
  private val height2D: NoiseKey = Noise.Height2D,
  private val window3D: NoiseKey = Noise.Window3D,

  private val regionThreshold: Double = 0.3,   // higher => rarer shelves
  private val shelfBelowSurface: Double = 4.0,  // shelf plane is this far BELOW surface
  private val heightAmp: Double = 10.0,         // +/- height variation
  private val stepBlocks: Double = 6.0,         // snap for flat layers

  private val slabHalfThickness: Double = 10.0,  // thicker => more “platform”
  private val slabStrength: Double = 26.0,

  private val undercutOffset: Double = 12.0,
  private val undercutHalf: Double = 8.0,
  private val undercutStrength: Double = 50.0,

  private val windowThreshold: Double = 0.25,   // higher => fewer holes
  private val windowStrength: Double = 60.0,
  override val surfaceFadeRamp: Int = 0,
  override val surfaceFadeStart: Int = 0
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Mask2D : NoiseKey { override val id = "cave.shelf.mask2D" }
    object Height2D : NoiseKey { override val id = "cave.shelf.height2D" }
    object Window3D : NoiseKey { override val id = "cave.shelf.window3D" }

    override fun install(bank: NoiseBank) {
      bank.register(Mask2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.008) // BIG regions (100-ish blocks)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      bank.register(Height2D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.005) // slow drift for shelf height
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      bank.register(Window3D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.03) // holes/windows detail
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
        }
      }
    }
  }

  override val noiseModule = Noise

  override fun addBlocks(ctx: GenerateContext, c: CaveContext, add : Double): Double {
    val wx = c.worldX
    val wy = c.y
    val wz = c.worldZ

    // 1) Big region mask (XZ)
    val m = ctx.noise.get(mask2D).noise2D(wx, wz)          // -1..1
    val m01 = 1.0 - kotlin.math.abs(m)                     // 0..1
    val region = ((m01 - regionThreshold) / (1.0 - regionThreshold)).coerceIn(0.0, 1.0)
    val region2 = region * region
    if (region2 <= 0.0) return 0.0

    // 2) Choose a shelf plane height (flat-ish)
    val hN = ctx.noise.get(height2D).noise2D(wx + 1337, wz - 777) // -1..1
    var shelfY = c.surfaceY.toDouble() - shelfBelowSurface + (hN * heightAmp)
    shelfY = snapToStep(shelfY, stepBlocks)

    // 3) Slab around shelfY
    val dy = kotlin.math.abs(wy.toDouble() - shelfY)
    val slab = smoothBand(dy, radius = slabHalfThickness, softness = slabHalfThickness * 0.6)

    // 4) IMPORTANT: prefer forming where base terrain is not super solid.
    // If terrainDensity is already huge positive, adding won't change visible shape much.
    // This keeps shelves near edges & surfaces instead of deep interior.
    val base = c.terrainDensity
    val edgePref = 1.0//((2.0 - base) / 4.0).coerceIn(0.0, 1.0) // tune if needed

    return add + region2 * slab  * slabStrength
  }

  override fun carveBlocks(ctx: GenerateContext, c: CaveContext): Double {
    val wx = c.worldX
    val wy = c.y
    val wz = c.worldZ

    // Same region gating as add()
    val m = ctx.noise.get(mask2D).noise2D(wx, wz)
    val m01 = 1.0 - kotlin.math.abs(m)
    val region = ((m01 - regionThreshold) / (1.0 - regionThreshold)).coerceIn(0.0, 1.0)
    val region2 = region * region
    if (region2 <= 0.0) return 0.0

    val hN = ctx.noise.get(height2D).noise2D(wx + 1337, wz - 777)
    var shelfY = c.surfaceY.toDouble() - shelfBelowSurface + (hN * heightAmp)
    shelfY = snapToStep(shelfY, stepBlocks)

    // A) Undercut carve below the slab (gives that distinct “lip”)
    val dyUnder = kotlin.math.abs(wy.toDouble() - (shelfY - undercutOffset))
    val under = smoothBand(dyUnder, radius = undercutHalf, softness = undercutHalf * 0.6)
    var carve = region2 * under * undercutStrength

    // B) Window/arch holes through the slab (turn shelves into bridges)
    val dySlab = kotlin.math.abs(wy.toDouble() - shelfY)
    val slab = smoothBand(dySlab, radius = slabHalfThickness, softness = slabHalfThickness * 0.6)

    val n3 = ctx.noise.get(window3D).noise3D(wx, wy, wz) // -1..1
    val n01 = (n3 * 0.5 + 0.5).coerceIn(0.0, 1.0)
    val hole = ((n01 - windowThreshold) / (1.0 - windowThreshold)).coerceIn(0.0, 1.0)
    val hole2 = hole * hole

    carve += region2 * slab * hole2 * windowStrength

    return carve
  }

  private fun snapToStep(y: Double, step: Double): Double {
    if (step <= 0.0) return y
    return kotlin.math.floor(y / step) * step
  }

  private fun smoothBand(d: Double, radius: Double, softness: Double): Double {
    // 1 inside radius, fades to 0 by radius+softness
    val t = ((radius + softness) - d) / softness
    return t.coerceIn(0.0, 1.0)
  }
}
