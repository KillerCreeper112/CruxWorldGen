package killercreepr.cruxworldgen.core

import killercreepr.cruxgeneration.util.CruxNoise
import killercreepr.cruxworldgen.test2.BiomeRegistry
import killercreepr.cruxworldgen.test2.VolumetricBiome


class DensityEngine(
  val biomeWorley: Worley3D,
  val biomeRegistry: BiomeRegistry
) {

    fun computeDensityWithBiome(x: Int, y: Int, z: Int): Pair<Double, VolumetricBiome> {
        val sample = biomeWorley.sample(x.toDouble(), y.toDouble(), z.toDouble())
        val biomeA = biomeRegistry.fromCell(sample.cellX1, sample.cellY1, sample.cellZ1)
        val biomeB = biomeRegistry.fromCell(sample.cellX2, sample.cellY2, sample.cellZ2)

        val densityA = biomeA.density(x, y, z)
        val densityB = biomeB.density(x, y, z)

        val blend = ((sample.f2 - sample.f1) * 4).coerceIn(0.0, 1.0)
        val density = lerp(densityA, densityB, blend)

        // Return dominant biome for block logic
        return density to if (blend < 0.5) biomeA else biomeB
    }

    private fun lerp(a: Double, b: Double, t: Double): Double {
        return a + (b - a) * t
    }
}
