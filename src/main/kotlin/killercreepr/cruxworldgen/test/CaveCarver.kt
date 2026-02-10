package killercreepr.cruxworldgen.test

import killercreepr.cruxworldgen.api.world.NoiseProvider

class CaveCarver(
    val scale: Double,
    val threshold: Double,
    val noise: NoiseProvider
) : DensityModifier {

    override fun modify(x: Int, y: Int, z: Int, current: Double): Double {
        val cave = noise.noise3D(x * scale, y * scale, z * scale)
        return if (cave > threshold) current - 1.5 else current
    }
}
