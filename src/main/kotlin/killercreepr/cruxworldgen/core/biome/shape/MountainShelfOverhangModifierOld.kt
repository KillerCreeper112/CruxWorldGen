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
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

class MountainShelfOverhangModifierOld(
  private val regionMask2D: NoiseKey,
  private val sheet3D: NoiseKey,
  private val shelfHeight2D: NoiseKey? = null,

  private val shelfAboveSurface: Double = 20.0,
  private val shelfHeightAmp: Double = 60.0,
  private val stepBlocks: Double = 30.0,

  private val regionThreshold: Double = 0.10,
  private val regionPower: Double = 3.0,

  private val slabHalfThickness: Double = 22.0,
  private val slabStrength: Double = 520.0,

  private val sheetThreshold: Double = 0.3,
  private val sheetPower: Double = 2.0,

  private val undercutOffset: Double = 18.0,
  private val undercutHalfThickness: Double = 14.0,
  private val undercutStrength: Double = 180.0,

  private val airStart: Double = -2.0,
  private val airRamp: Double = 10.0,
  private val rockAnchorDepth: Double = 12.0
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
    val base = baseStack.base
    val surfaceY = y.toDouble() + base

    // --- region gate (don’t power-crush) ---
    val rm = ctx.noise.get(regionMask2D).noise2D(worldX, worldZ)
    val rm01 = 1.0 - abs(rm)
    val region = ((rm01 - regionThreshold) / (1.0 - regionThreshold)).coerceIn(0.0, 1.0)
    val regionShaped = smoothstep01(region).pow(regionPower)
    if (regionShaped <= 1e-6) return

    // --- shelf plane (above surface) ---
    var shelfY = surfaceY + shelfAboveSurface
    if (shelfHeight2D != null) {
      val hn = ctx.noise.get(shelfHeight2D).noise2D(worldX + 1337, worldZ - 777)
      shelfY += hn * shelfHeightAmp
    }
    if (stepBlocks > 0.0) shelfY = floor(shelfY / stepBlocks) * stepBlocks

    val minY = ctx.chunkContext.minHeight
    val maxY = ctx.chunkContext.maxHeight - 1
    val H = (maxY - minY + 1).toDouble()
    val y01 = (y - minY) / H

    val slab = band(
      center01 = (shelfY - minY) / H,
      halfWidth01 = slabHalfThickness / H,
      y01 = y01
    )
    if (slab <= 1e-6) return

    // --- BIG blob sheet volume (not ridged veins) ---
    val n = ctx.noise.get(sheet3D).noise3D(worldX, y, worldZ) // -1..1
    val n01 = (n * 0.5 + 0.5).coerceIn(0.0, 1.0)
    val sheet = smoothstep01(((n01 - sheetThreshold) / (1.0 - sheetThreshold)).coerceIn(0.0, 1.0))
      .pow(sheetPower)
    if (sheet <= 1e-6) return

    // --- expansion gate: must work in air + anchor shallow rock ---
    val heightAboveSurface = (-base) // >0 in air
    val airGate = smoothstep01(((heightAboveSurface - airStart) / airRamp).coerceIn(0.0, 1.0))

    val insideRock = max(0.0, base)
    val rockGate = smoothstep01(((rockAnchorDepth - insideRock) / rockAnchorDepth).coerceIn(0.0, 1.0))

    val expandGate = max(airGate, rockGate * 0.6)
    if (expandGate <= 1e-6) return

    // --- ADD shelf mass (big) ---
    out.addAdditive(regionShaped * slab * sheet * expandGate * slabStrength)

    // --- carve undercut (big air void makes it read as an overhang) ---
    val under = band(
      center01 = ((shelfY - undercutOffset) - minY) / H,
      halfWidth01 = undercutHalfThickness / H,
      y01 = y01
    )
    out.addCarve(regionShaped * under * sheet * undercutStrength)
  }
}
