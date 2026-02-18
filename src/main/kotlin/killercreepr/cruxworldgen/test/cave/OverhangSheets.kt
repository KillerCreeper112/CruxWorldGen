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

class OverhangSheets(
  val bandBlocks: Double = 28.0,        // how thick around surface it can affect
  val belowSurfaceBias: Double = -6.0,   // shift band slightly below surface
  val threshold01: Double = 0.1,       // higher = rarer bridges/sheets
  val strength: Double = 100.0,
  val warpBlocks: Double = 18.0
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Warp2D : NoiseKey { override val id = "overhang.warp2D" }
    object Sheets3D : NoiseKey { override val id = "overhang.sheets3D" }

    override fun install(bank: NoiseBank) {
      bank.register(Warp2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.007)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(4)
        }
      }
      bank.register(Sheets3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.03)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3)
        }
      }
    }
  }

  override val noiseModule = Noise
  override fun carveBlocks(
    ctx: GenerateContext,
    cave: CaveContext
  ): Double {
    return 0.0
  }

  override fun addBlocks(ctx: GenerateContext, cave: CaveContext, add: Double): Double {
    // Put the band ABOVE the macro surface
    val targetY = cave.surfaceY - 8.0
    val dy = kotlin.math.abs(cave.y.toDouble() - targetY)
    val bandT = ((bandBlocks - dy) / bandBlocks).coerceIn(0.0, 1.0)
    val nearSurface = smoothstep01(bandT)
    if (nearSurface <= 0.001) return 0.0

    // Only solidify air (and don’t over-filter yet)
    val air = (-cave.terrainDensity).coerceAtLeast(0.0)
    if (air <= 0.0) return 0.0

    val w = ctx.noise.get(Noise.Warp2D).noise2D(cave.worldX, cave.worldZ)
    val wx = cave.worldX + w * warpBlocks
    val wz = cave.worldZ + ctx.noise.get(Noise.Warp2D).noise2D(cave.worldX + 9999, cave.worldZ + 9999) * warpBlocks

    val n = ctx.noise.get(Noise.Sheets3D).noise3D(wx, cave.y.toDouble(), wz) // keep y as Int unless your API is truly Double-based
    val ridged01 = 1.0 - kotlin.math.abs(n)

    val t = ((ridged01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val sheetMask = smoothstep01(t)

    val attach = (air / 0.8).coerceIn(0.0, 1.0)

    // IMPORTANT: don't multiply by add until you confirm add is nonzero
    return nearSurface * sheetMask * attach * strength
  }

}

