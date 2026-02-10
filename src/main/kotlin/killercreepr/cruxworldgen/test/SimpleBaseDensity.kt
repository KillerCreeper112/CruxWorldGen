package killercreepr.cruxworldgen.test

import killercreepr.cruxworldgen.api.world.NoiseProvider

/*class SimpleBaseDensity(
    val noise: NoiseProvider
) : DensityFunction {

    override fun compute(x: Int, y: Int, z: Int): Double {

        val continental = noise.noise2D(x * 0.001, z * 0.001)
        val terrain = noise.noise2D(x * 0.01, z * 0.01)

        val baseHeight = 80 + continental * 40 + terrain * 15

        return baseHeight - y
    }
}*/
