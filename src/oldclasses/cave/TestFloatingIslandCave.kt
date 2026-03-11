package killercreepr.cruxworldgen.test.cave

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.api.cave.CaveType
import killercreepr.cruxworldgen.api.context.CaveContext
import killercreepr.cruxworldgen.api.context.GenerateContext
import killercreepr.cruxworldgen.api.noise.*
import killercreepr.cruxworldgen.api.util.NoiseShaper
import killercreepr.cruxworldgen.api.util.NoiseShaper.Point
import killercreepr.cruxworldgen.api.util.NoiseShaper.ShapingFunction

class TestFloatingIslandCave(
) : CaveType, Noised {

  object Noise : NoiseModule {
    object Island3D : NoiseKey { override val id = "cave.test.floating_island" }
    object Something2D : NoiseKey { override val id = "cave.test.something2D" }

    override fun install(bank: NoiseBank) {
      bank.register(Island3D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.005)  // Large-scale noise for large terrain features
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(3) // More octaves for large, varied terrain carving
        }
      }
      bank.register(Something2D) { seed ->
        NoiseField.noiseField(seed) {
          frequency(0.001)
            .noiseType(CruxNoise.NoiseType.OpenSimplex2)
            .fractalType(CruxNoise.FractalType.FBm)
            .fractalOctaves(2)
        }
      }
    }
  }

  override val noiseModule = Noise

  val shaper = NoiseShaper(listOf(
    Point(-1.0, ShapingFunction.VALLEY),
    Point(-0.3, ShapingFunction.FLAT),
    Point(0.0, ShapingFunction.FLAT),
    Point(0.7, ShapingFunction.FLAT),
    Point(0.8, ShapingFunction.HILLS),
    Point(1.0, ShapingFunction.MOUNTAIN)
  ))

  override fun addBlocks(
    ctx: GenerateContext,
    cave: CaveContext,
    add: Double
  ): Double {
    if(cave.y < cave.surfaceY) return 0.0
    val x = cave.worldX
    val y = cave.y
    val z = cave.worldZ

    val island3D = shaper.shape(ctx.noise.get(Noise.Island3D).noise3D(x,y,z))
    if(island3D > 0.3){
      val strength = 15.0
      val something2D = ctx.noise.get(Noise.Something2D).noise2D(x,z)
      return add + something2D * strength
    }
    return 0.0
  }

  override fun carveBlocks(ctx: GenerateContext, cave: CaveContext): Double = 0.0
}