package killercreepr.cruxworldgen.core.biome.shape

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.biome.BiomeShapeType
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.density.DensityBank
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.signal.SignalWriter
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

/**
 * Connected floating islands:
 * - A big 2D region mask picks where islands exist.
 * - A 2D height noise picks the island plane Y (relative to the local surface).
 * - A slab (band) around that plane is ADDED to density -> island mass.
 * - Underside band is CARVED -> creates a clear "lip" / floating silhouette.
 * - A second 2D mask creates occasional connecting sheet-bridges.
 * - A 3D noise carves "windows" through islands/bridges so they read as arches.
 *
 * IMPORTANT:
 * - This modifier assumes your base terrain is already present in baseStack.base,
 *   so it can estimate surfaceY = y + baseStack.base.
 * - Ensure its noise keys are registered (see note below).
 */
class FloatingIslandModifier(
  private val region2D: NoiseKey = Noise.Region2D,
  private val height2D: NoiseKey = Noise.Height2D,
  private val bridge2D: NoiseKey = Noise.Bridge2D,
  private val window3D: NoiseKey = Noise.Window3D,

  // --- Island placement ---
  private val regionThreshold: Double = 0.3,     // higher => rarer islands (0..1)
  private val aboveSurfaceBase: Double = 0.0,    // island plane sits this far ABOVE surface
  private val aboveSurfaceAmp: Double = 32.0,     // +/- variation of that plane
  private val stepBlocks: Double = 6.0,           // snap plane to make flatter layers
  private val minClearance: Double = 20.0,        // don't spawn islands too close to surface

  // --- Island slab ---
  private val slabHalfThickness: Double = 10.0,   // thickness of island body
  private val slabStrength: Double = 34.0,        // how “solid” the island is in density units
  private val topFlattenPow: Double = 1.35,       // bigger => flatter top/plateau feel

  // --- Underside carve to make it feel floating ---
  private val undercutOffset: Double = 16.0,      // carve band center below island plane
  private val undercutHalf: Double = 10.0,        // carve thickness
  private val undercutStrength: Double = 42.0,    // carve amount

  // --- Bridges (sheet connectors) ---
  private val bridgeThreshold: Double = 0.72,     // higher => fewer bridges
  private val bridgeDrop: Double = 6.0,           // bridge plane slightly below island plane
  private val bridgeHalfThickness: Double = 6.0,  // bridge thickness
  private val bridgeStrength: Double = 22.0,      // bridge solidity
  private val bridgeFringeWidth: Double = 0.25,   // encourage bridges near island edges (0..1)

  // --- Window / arch holes through the mass ---
  private val windowThreshold: Double = 0.62,     // higher => fewer holes
  private val windowStrength: Double = 32.0
) : BiomeShapeType, Noised {

  /**
   * Register these keys in your biome's NoiseModule.install(bank).
   * Easiest:
   *   FloatingIslandModifier.Noise.install(bank)
   */
  object Noise : NoiseModule {
    object Region2D : NoiseKey { override val id = "shape.floating_islands.region2D" }
    object Height2D : NoiseKey { override val id = "shape.floating_islands.height2D" }
    object Bridge2D : NoiseKey { override val id = "shape.floating_islands.bridge2D" }
    object Window3D : NoiseKey { override val id = "shape.floating_islands.window3D" }

    override fun install(bank: NoiseBank) {
      // Big blob regions (NOT 1-abs(n) ribbons). We'll threshold n01.
      bank.register(Region2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0035) // big islands (~300 blocks-ish features)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
      // Slow height drift for plane Y.
      bank.register(Height2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.0025)
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
            .fractalGain(0.5)
            .fractalLacunarity(2.0)
        }
      }
      // Bridge occurrences (separate mask so not every island connects).
      bank.register(Bridge2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.01)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(1)
        }
      }
      // Window holes detail.
      bank.register(Window3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.025)
            .noiseType(CruxNoise.NoiseType.Perlin)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
            .fractalGain(0.5)
            .fractalLacunarity(2.0)
        }
      }
    }
  }

  override val noiseModule = Noise

  override fun density(
    ctx: GenerateContext,
    worldX: Int,
    y: Int,
    worldZ: Int,
    edge: BiomeEdgeContext,
    signalWriter: SignalWriter,
    baseStack: DensityStack,
    out: DensityBank
  ) {
    // Estimate the local surface from the base stack:
    // baseStack.base is (surfaceY - y) for simple heightfield base.
    val surfaceY = y.toDouble() + baseStack.base

    // --- Region mask: blobs in [0..1], thresholded ---
    val r01 = (ctx.noise.get(region2D).noise2D(worldX, worldZ) * 0.5 + 0.5).coerceIn(0.0, 1.0)
    val region = ((r01 - regionThreshold) / (1.0 - regionThreshold)).coerceIn(0.0, 1.0)
    if (region <= 0.0) return
    val region2 = region * region

    // --- Island plane height relative to surface ---
    val h01 = (ctx.noise.get(height2D).noise2D(worldX + 1337, worldZ - 777) * 0.5 + 0.5).coerceIn(0.0, 1.0)
    var islandY = surfaceY + aboveSurfaceBase + ((h01 * 2.0 - 1.0) * aboveSurfaceAmp)
    islandY = snapToStep(islandY, stepBlocks)

    // Don't spawn islands that intersect the base terrain too much.
    val clearance = islandY - surfaceY
    if (clearance < minClearance) return

    // --- Island slab add (flat-ish pancake) ---
    val dy = abs(y.toDouble() - islandY)
    var slab = smoothBand(dy, radius = slabHalfThickness, softness = slabHalfThickness * 0.75)

    // Make tops a bit flatter / more plateau-like:
    // This biases density to stay strong near the center of the band.
    slab = slabPow(slab, topFlattenPow)

    val islandAdd = region2 * slab * slabStrength
    if (islandAdd > 0.0001) out.addAdditive(islandAdd)

    // --- Underside carve to create a distinct floating silhouette ---
    val dyUnder = abs(y.toDouble() - (islandY - undercutOffset))
    val under = smoothBand(dyUnder, radius = undercutHalf, softness = undercutHalf * 0.75)
    val underCarve = region2 * under * undercutStrength
    if (underCarve > 0.0001) out.addCarve(underCarve)

    // --- Bridges: sheet connectors near island edges + in gaps ---
    // Bridge mask blob threshold.
    val b01 = (ctx.noise.get(bridge2D).noise2D(worldX - 900, worldZ + 500) * 0.5 + 0.5).coerceIn(0.0, 1.0)
    val b = ((b01 - bridgeThreshold) / (1.0 - bridgeThreshold)).coerceIn(0.0, 1.0)
    if (b > 0.0) {
      // Encourage bridges near the "fringe" of regions so they connect island edges,
      // not fill the whole island interior.
      // fringe ≈ 1 when region is around ~ (1 - bridgeFringeWidth), fades near 0 and 1.
      val fringe = fringe01(region, width = bridgeFringeWidth)

      // Also allow bridges in nearby gaps (so it can span between blobs).
      val gapPref = (1.0 - region).coerceIn(0.0, 1.0)

      val bridgeWeight = (b * (0.65 * fringe + 0.35 * gapPref)).coerceIn(0.0, 1.0)
      val dyBridge = abs(y.toDouble() - (islandY - bridgeDrop))
      val sheet = smoothBand(dyBridge, radius = bridgeHalfThickness, softness = bridgeHalfThickness * 0.85)
      val bridgeAdd = bridgeWeight * sheet * bridgeStrength
      if (bridgeAdd > 0.0001) out.addAdditive(bridgeAdd)

      // Carve windows through bridges too
      val holeCarve = windowCarve(ctx, worldX, y, worldZ, bridgeWeight * sheet)
      if (holeCarve > 0.0001) out.addCarve(holeCarve)
    }

    // --- Windows / arches through the island mass ---
    val holeCarveIslands = windowCarve(ctx, worldX, y, worldZ, region2 * slab)
    if (holeCarveIslands > 0.0001) out.addCarve(holeCarveIslands)
  }

  private fun windowCarve(ctx: GenerateContext, wx: Int, y: Int, wz: Int, presence: Double): Double {
    if (presence <= 0.0001) return 0.0
    val n = ctx.noise.get(window3D).noise3D(wx, y, wz) // -1..1
    val n01 = (n * 0.5 + 0.5).coerceIn(0.0, 1.0)
    val hole = ((n01 - windowThreshold) / (1.0 - windowThreshold)).coerceIn(0.0, 1.0)
    val hole2 = hole * hole
    return presence * hole2 * windowStrength
  }

  private fun snapToStep(y: Double, step: Double): Double {
    if (step <= 0.0) return y
    return floor(y / step) * step
  }

  /**
   * 1 inside radius, fades to 0 by radius+softness
   */
  private fun smoothBand(d: Double, radius: Double, softness: Double): Double {
    val s = softness.coerceAtLeast(1e-6)
    val t = ((radius + s) - d) / s
    return t.coerceIn(0.0, 1.0)
  }

  private fun slabPow(v: Double, p: Double): Double {
    if (p <= 1.0) return v
    return v.coerceIn(0.0, 1.0).pow(p)
  }

  /**
   * Produces a “ring-ish” weight: high in mid-range, low near 0 and near 1.
   * width is roughly how thick the fringe band is (0..1).
   */
  private fun fringe01(x: Double, width: Double): Double {
    val w = width.coerceIn(0.01, 0.49)
    // peak around 0.5, suppress near ends
    val a = ((x - w) / (1.0 - 2.0 * w)).coerceIn(0.0, 1.0)
    // bell-ish: 4a(1-a) peaks at 1 when a=0.5
    return (4.0 * a * (1.0 - a)).coerceIn(0.0, 1.0)
  }
}
