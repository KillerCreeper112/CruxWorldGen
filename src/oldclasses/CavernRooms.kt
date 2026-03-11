package killercreepr.cruxworldgen.standard.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.extension.remap01
import kotlin.math.max

class CavernRooms(
  val threshold01: Double = 0.65,     // 0.90..0.95
  val strength: Double = 1.15,
  val openMarginBlocks: Double = 18.0,

  override val surfaceFadeStart : Int = 6,
  override val surfaceFadeRamp: Int = 16
) : CaveType, Noised {

  object Noise : NoiseModule{
    object Cavern3D : NoiseKey{ override val id = "cave.cavern.3D" }

    override fun install(bank: NoiseBank) {
      bank.register(Cavern3D){ seed ->
        NoiseField.noiseField(seed){
          frequency(0.005) // lower frequency = bigger rooms
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
    }
  }

  override val noiseModule = Noise

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double {
    val solidDensity = max(0.0, cave.terrainDensity)
    if (solidDensity <= 0.0) return 0.0

    val n01 = ctx.noise.get(Noise.Cavern3D).noise3D(cave.worldX, cave.y, cave.worldZ).remap01()
    val t = ((n01 - threshold01) / (1.0 - threshold01)).coerceIn(0.0, 1.0)
    val mask = t * t // sharper/rarer than smoothstep
    if (mask < 0.25) return 0.0

    return mask * (solidDensity * strength + openMarginBlocks)
  }
}