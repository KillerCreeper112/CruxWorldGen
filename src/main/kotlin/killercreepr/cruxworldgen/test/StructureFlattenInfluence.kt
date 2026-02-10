package killercreepr.cruxworldgen.test

import kotlin.math.sqrt

class StructureFlattenInfluence(
    private val centerX: Int,
    private val centerZ: Int,
    private val radius: Int,
    private val targetHeight: Int
) : StructureInfluence {

    override fun influence(x: Int, y: Int, z: Int, currentDensity: Double): Double {
        val dx = x - centerX
        val dz = z - centerZ
        val dist = sqrt((dx*dx + dz*dz).toDouble())

        if (dist > radius) return currentDensity

        val falloff = 1 - (dist / radius)
        val flattenDensity = targetHeight - y

        return currentDensity * (1 - falloff) + flattenDensity * falloff
    }
}
