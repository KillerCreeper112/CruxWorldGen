package killercreepr.cruxworldgen.test3

import killercreepr.cruxstructures.api.structure.generation.StructureGenerator
import org.bukkit.Material

interface CruxBiome {
  val id: String
  fun suitability(ctx: BiomeContext, x: Int, z: Int): Double

  fun densityLandforms(): List<DensityLandform>
  fun densityCarvers(): List<DensityCarver>
  fun densityAdditives(): List<DensityAdditive>

  fun decorators(): List<Decorator>
  fun structures(): List<StructureGenerator>
  fun surfaceRule(): SurfaceRule
}

data class SurfaceContext(
  val seed: Long,
  val x: Int,
  val z: Int,
  val topY: Int,
  val y: Int,
  val depthFromTop: Int, // 0 = top, 1.. below
  val slope: Double      // 0..1
)

fun interface SurfaceRule {
  fun material(ctx: SurfaceContext): Material
}
fun interface DensityLandform {
  fun sample(ctx: DensityCtx, x: Int, y: Int, z: Int, out: DensityStack, w: Double)
}

fun interface DensityCarver {
  fun sample(ctx: DensityCtx, x: Int, y: Int, z: Int, out: DensityStack, core: Double)
}

fun interface DensityAdditive {
  fun sample(ctx: DensityCtx, x: Int, y: Int, z: Int, out: DensityStack, core: Double)
}