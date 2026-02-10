package killercreepr.cruxworldgen.test

import killercreepr.cruxworldgen.api.world.NoiseProvider

class SimpleCaveModifier(
    private val noise: NoiseProvider
) : DensityModifier {

    override fun modify(x: Int, y: Int, z: Int, current: Double): Double {

        val cave = noise.noise3D(x * 0.05, y * 0.05, z * 0.05)

        return if (cave > 0.6) {
            current - 2.0
        } else {
            current
        }
    }
}
