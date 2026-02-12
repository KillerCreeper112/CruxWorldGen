package killercreepr.cruxworldgen.test4

import killercreepr.cruxworldgen.test3.NoiseBank
import killercreepr.cruxworldgen.test4.info.GenContext
import killercreepr.cruxworldgen.test4.info.SectionCtx
import org.bukkit.Material

interface Section {
  fun suitability(ctx: SectionCtx, x: Int, z: Int): Double
  fun densityForms(): List<DensityForm>
  fun caveForms(): List<CaveForm>

  fun surface() : Surface
}

fun interface Surface{
  fun applyTo(ctx: SurfaceContext)
}

abstract class SurfaceContext(
  val genCtx : GenContext,
  val density : Double,
  val weight : Double
){
  abstract fun setBlock(material : Material)
}

fun interface DensityForm{
  fun sample(ctx: DensityContext, x: Int, y: Int, z: Int, out: Density, weight: Double)
}
fun interface CaveForm{
  fun sample(ctx: DensityContext, x: Int, y: Int, z: Int, out: Density, weight: Double)
}
class Density {
  var base: Double = 0.0           // main terrain density
  var carve: Double = 0.0          // subtract to carve (caves/overhangs)
  var add: Double = 0.0            // add to add solids (pillars)

  fun addBase(v: Double) { base += v }
  fun addCarve(v: Double) { carve += v }
  fun addAdditive(v: Double) { add += v }

  fun finalDensity(): Double = base + add - carve
}

data class DensityContext(
  val seed: Long,
  val noise: NoiseBank
)
