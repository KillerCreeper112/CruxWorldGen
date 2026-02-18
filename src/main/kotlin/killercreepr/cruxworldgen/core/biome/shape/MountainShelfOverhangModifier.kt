package killercreepr.cruxworldgen.core.biome.shape

import killercreepr.cruxworldgen.api.biome.BiomeShapeType
import killercreepr.cruxworldgen.api.context.BiomeEdgeContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.density.DensityBank
import killercreepr.cruxworldgen.api.density.DensityStack
import killercreepr.cruxworldgen.api.noise.NoiseKey
import killercreepr.cruxworldgen.api.signal.SignalWriter
import killercreepr.cruxworldgen.api.util.Curve.band
import killercreepr.cruxworldgen.api.util.Curve.smoothstep01
import kotlin.math.pow

class MountainShelfOverhangModifier(
  private val regionMask2D: NoiseKey,
  private val sheet3D: NoiseKey,
  private val shelfHeight2D: NoiseKey? = null,

  // NEW: macro surface sampler (heightfield at x,z)
  private val macroSurfaceY: ((GenerateContext, Int, Int) -> Double)? = {ctx,x,z -> 100.0},

  private val shelfAboveSurface: Double = 18.0,
  private val shelfHeightAmp: Double = 40.0,
  private val stepBlocks: Double = 12.0,

  // mountain gating (HIGHLY recommended)
  private val minMacroSurfaceY: Double = 70.0,

  private val regionThreshold: Double = 0.12,
  private val regionPower: Double = 2.2,

  private val slabHalfThickness: Double = 34.0,
  private val slabStrength: Double = 1200.0,

  private val sheetThreshold: Double = 0.60,
  private val sheetPower: Double = 1.8,

  // NEW: flatten the sheet vertically (makes plates instead of blobs)
  private val sheetVerticalScale: Double = 0.18, // <1 = flatter

  private val undercutOffset: Double = 26.0,
  private val undercutHalfThickness: Double = 22.0,
  private val undercutStrength: Double = 520.0,

  private val airStart: Double = -4.0,
  private val airRamp: Double = 10.0,
  private val rockAnchorDepth: Double = 18.0
) : BiomeShapeType {

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
    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val H = (maxY - minY + 1).toDouble()
    val y01 = (y - minY) / H

    // ----- 1) macro surface anchor (NOT per-column local surface) -----
    val macroSurface = macroSurfaceY?.invoke(ctx, worldX, worldZ)
      ?: (y.toDouble() + baseStack.base) // fallback (will be smaller)

    if (macroSurface < minMacroSurfaceY) return

    // ----- 2) region blobs (xz) -----
    val rm = ctx.noise.get(regionMask2D).noise2D(worldX, worldZ)
    val rm01 = 1.0 - kotlin.math.abs(rm)
    val region = ((rm01 - regionThreshold) / (1.0 - regionThreshold)).coerceIn(0.0, 1.0)
    val regionShaped = smoothstep01(region).pow(regionPower)
    if (regionShaped <= 1e-6) return

    // ----- 3) shelf plane -----
    var shelfY = macroSurface + shelfAboveSurface

    if (shelfHeight2D != null) {
      val hn = ctx.noise.get(shelfHeight2D).noise2D(worldX + 1337, worldZ - 777)
      shelfY += hn * shelfHeightAmp
    }
    if (stepBlocks > 0.0) shelfY = kotlin.math.floor(shelfY / stepBlocks) * stepBlocks

    // vertical slab band
    val slab = band(
      center01 = (shelfY - minY) / H,
      halfWidth01 = slabHalfThickness / H,
      y01 = y01
    )
    if (slab <= 1e-6) return

    // ----- 4) sheet volume in SHELF-LOCAL coords (makes big flat plates) -----
    val localY = ((y.toDouble() - shelfY) * sheetVerticalScale).toInt()
    val n = ctx.noise.get(sheet3D).noise3D(worldX, localY, worldZ) // -1..1
    val n01 = (n * 0.5 + 0.5).coerceIn(0.0, 1.0)

    val sheet = smoothstep01(((n01 - sheetThreshold) / (1.0 - sheetThreshold)).coerceIn(0.0, 1.0))
      .pow(sheetPower)
    if (sheet <= 1e-6) return

    // ----- 5) expand gate: air + shallow rock anchor -----
    // baseStack.base is local surface relation. If you can, replace this with macroSurface - y.
    val localBase = baseStack.base
    val heightAboveLocalSurface = (-localBase)
    val airGate = smoothstep01(((heightAboveLocalSurface - airStart) / airRamp).coerceIn(0.0, 1.0))
    val rockGate = smoothstep01(((rockAnchorDepth - kotlin.math.max(0.0, localBase)) / rockAnchorDepth).coerceIn(0.0, 1.0))
    val expandGate = kotlin.math.max(airGate, rockGate * 0.7)
    if (expandGate <= 1e-6) return

    // ----- 6) add shelf mass + carve undercut -----
    out.addAdditive(regionShaped * slab * sheet * expandGate * slabStrength)

    val under = band(
      center01 = ((shelfY - undercutOffset) - minY) / H,
      halfWidth01 = undercutHalfThickness / H,
      y01 = y01
    )
    out.addCarve(regionShaped * under * sheet * undercutStrength)
  }
}
