package killercreepr.cruxworldgen.test2

import killercreepr.cruxworldgen.core.DensityEngine
import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator
import java.util.*
import kotlin.math.floor
import kotlin.math.min

class CruxChunkGenerator(
  val densityEngine: DensityEngine
) : ChunkGenerator() {

    override fun generateNoise(
      worldInfo: org.bukkit.generator.WorldInfo,
      random: Random,
      chunkX: Int,
      chunkZ: Int,
      chunkData: ChunkData
    ) {
        val minY = worldInfo.minHeight
        val maxY = worldInfo.maxHeight

        val radiusSections = 1 // try 1 or 2; larger = softer but more cost

        for (x in 0 until 16) {
            for (z in 0 until 16) {
                val worldX = chunkX * 16 + x
                val worldZ = chunkZ * 16 + z

                val weights = computeBiomeWeights(worldX, worldZ, 128, radiusSections, densityEngine.biomeRegistry, worldSeed = 0L)
                // If empty fallback to center section biome
                if (weights.isEmpty()) {
                    val fallback = densityEngine.biomeRegistry.getBiomeByHash(
                      floor(worldX / 128.0).toInt(),
                      floor(worldZ / 128.0).toInt(),
                      0L
                    )
                    for (y in minY until maxY) {
                        val d = fallback.density(worldX, y, worldZ)
                        val block = fallback.getBlockBlended(worldX, y, worldZ, d, fallback, 0.0)
                        if (block != Material.AIR) chunkData.setBlock(x, y, z, block)
                    }
                    continue
                }

                // Pre-sort top two biomes by weight for block blending
                val sorted = weights.entries.sortedByDescending { it.value }
                val primary = sorted[0].key
                val primaryW = sorted[0].value
                val secondary = if (sorted.size > 1) sorted[1].key else primary
                val secondaryW = if (sorted.size > 1) sorted[1].value else 0.0
                val twoSum = primaryW + secondaryW
                val blendT = if (twoSum > 0.0) (secondaryW / twoSum) else 0.0

                for (y in minY until maxY) {
                    // continuous density = weighted sum of densities
                    var density = 0.0
                    for ((biome, w) in weights) {
                        density += w * biome.density(worldX, y, worldZ)
                    }

                    // block: blend top two biomes using blendT
                    val block = primary.getBlockBlended(worldX, y, worldZ, density, secondary, blendT)

                    if (block != Material.AIR) {
                        chunkData.setBlock(x, y, z, block)
                    }
                }
            }
        }
    }

    private fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t

    fun computeBiomeBlend(
      worldX: Int,
      worldZ: Int,
      sectionSize: Int,
      blendSize: Int,
      biomeRegistry: BiomeRegistry,
      worldSeed: Long = 0L
    ): Pair<Double, Pair<VolumetricBiome, VolumetricBiome>> {

        val sectionX = floor(worldX / sectionSize.toDouble()).toInt()
        val sectionZ = floor(worldZ / sectionSize.toDouble()).toInt()

        val localX = Math.floorMod(worldX, sectionSize)
        val localZ = Math.floorMod(worldZ, sectionSize)

        val tX = ((blendSize - min(localX, sectionSize - localX - 1)).coerceIn(0, blendSize)).toDouble() / blendSize
        val tZ = ((blendSize - min(localZ, sectionSize - localZ - 1)).coerceIn(0, blendSize)).toDouble() / blendSize
        val blendFactor = (tX * tX * (3 - 2 * tX) + tZ * tZ * (3 - 2 * tZ)) / 2.0

        val biomeA = biomeRegistry.getBiomeByHash(sectionX, sectionZ, worldSeed)

        // Pick neighbor biome for blending
        /*val neighborSectionX = if (localX < blendSize) sectionX - 1 else if (localX > sectionSize - blendSize - 1) sectionX + 1 else sectionX
        val neighborSectionZ = if (localZ < blendSize) sectionZ - 1 else if (localZ > sectionSize - blendSize - 1) sectionZ + 1 else sectionZ
*/
        // Determine which edge is closer
        val distX = min(localX, sectionSize - localX - 1)
        val distZ = min(localZ, sectionSize - localZ - 1)

        val useX = distX < distZ

        val neighborSectionX = if (useX) {
            if (localX < blendSize) sectionX - 1 else if (localX > sectionSize - blendSize - 1) sectionX + 1 else sectionX
        } else sectionX

        val neighborSectionZ = if (!useX) {
            if (localZ < blendSize) sectionZ - 1 else if (localZ > sectionSize - blendSize - 1) sectionZ + 1 else sectionZ
        } else sectionZ

        val biomeB = biomeRegistry.getBiomeByHash(neighborSectionX, neighborSectionZ, worldSeed)
        return blendFactor to (biomeA to biomeB)
    }

    // Smoothstep helper
    private fun smoothStep(t: Double): Double = t * t * (3.0 - 2.0 * t)

    fun computeBiomeBlendBilinear(
      worldX: Int,
      worldZ: Int,
      sectionSize: Int,
      blendSize: Int,
      biomeRegistry: BiomeRegistry,
      worldSeed: Long = 0L
    ): Quadruple<Double, Double, Array<VolumetricBiome>, Pair<Int, Int>> {
        // section coords
        val sectionX = floor(worldX / sectionSize.toDouble()).toInt()
        val sectionZ = floor(worldZ / sectionSize.toDouble()).toInt()

        // local position inside section [0 .. sectionSize-1]
        val localX = Math.floorMod(worldX, sectionSize)
        val localZ = Math.floorMod(worldZ, sectionSize)

        // fractional position inside section in [0,1]
        val fx = (localX.toDouble() + 0.5) / sectionSize.toDouble()
        val fz = (localZ.toDouble() + 0.5) / sectionSize.toDouble()

        // apply smoothstep only inside the blend band; outside blend band we clamp to 0/1
        // compute normalized blend coordinate relative to edges
        val edgeBlend = blendSize.toDouble() / sectionSize.toDouble()
        val sx = when {
            fx < edgeBlend -> smoothStep((fx / edgeBlend).coerceIn(0.0, 1.0))
            fx > 1.0 - edgeBlend -> smoothStep(((fx - (1.0 - edgeBlend)) / edgeBlend).coerceIn(0.0, 1.0))
            else -> 0.5 + (fx - 0.5) // keep center mapping; we'll still use fx for interpolation
        }
        val sz = when {
            fz < edgeBlend -> smoothStep((fz / edgeBlend).coerceIn(0.0, 1.0))
            fz > 1.0 - edgeBlend -> smoothStep(((fz - (1.0 - edgeBlend)) / edgeBlend).coerceIn(0.0, 1.0))
            else -> 0.5 + (fz - 0.5)
        }

        // Simpler: use fx/fz directly for interpolation but apply smoothStep to them
        val interpX = smoothStep(fx.coerceIn(0.0, 1.0))
        val interpZ = smoothStep(fz.coerceIn(0.0, 1.0))

        // four neighboring section biomes
        val b00 = biomeRegistry.getBiomeByHash(sectionX, sectionZ, worldSeed)
        val b10 = biomeRegistry.getBiomeByHash(sectionX + 1, sectionZ, worldSeed)
        val b01 = biomeRegistry.getBiomeByHash(sectionX, sectionZ + 1, worldSeed)
        val b11 = biomeRegistry.getBiomeByHash(sectionX + 1, sectionZ + 1, worldSeed)

        // Return interpolation weights and the four biomes
        return Quadruple(interpX, interpZ, arrayOf(b00, b10, b01, b11), Pair(sectionX, sectionZ))
    }

    private fun computeBiomeWeights(
      worldX: Int,
      worldZ: Int,
      sectionSize: Int,
      radiusSections: Int, // how many sections to sample in each direction (1 => 3x3)
      biomeRegistry: BiomeRegistry,
      worldSeed: Long = 0L
    ): Map<VolumetricBiome, Double> {
        val sectionX = floor(worldX / sectionSize.toDouble()).toInt()
        val sectionZ = floor(worldZ / sectionSize.toDouble()).toInt()

        // sigma controls softness; use radiusSections / 2 for reasonable falloff
        val sigma = maxOf(0.5, radiusSections.toDouble() / 2.0)
        val twoSigmaSq = 2.0 * sigma * sigma

        val accum = mutableMapOf<VolumetricBiome, Double>()
        var total = 0.0

        for (dx in -radiusSections..radiusSections) {
            for (dz in -radiusSections..radiusSections) {
                val sx = sectionX + dx
                val sz = sectionZ + dz

                // center of that section in world coords
                val centerX = ((sx.toDouble() + 0.5) * sectionSize.toDouble())
                val centerZ = ((sz.toDouble() + 0.5) * sectionSize.toDouble())

                val nx = (worldX.toDouble() - centerX) / sectionSize.toDouble()
                val nz = (worldZ.toDouble() - centerZ) / sectionSize.toDouble()
                val distSq = nx * nx + nz * nz

                // Gaussian weight; you can replace with smoothstep if you prefer
                val weight = kotlin.math.exp(-distSq / twoSigmaSq)

                if (weight <= 1e-6) continue

                val biome = biomeRegistry.getBiomeByHash(sx, sz, worldSeed)
                accum[biome] = (accum[biome] ?: 0.0) + weight
                total += weight
            }
        }

        if (total <= 0.0) return emptyMap()
        // normalize
        return accum.mapValues { it.value / total }
    }

    // Small helper container (Kotlin doesn't have a built-in Quadruple)
    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

}
