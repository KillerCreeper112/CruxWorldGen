package killercreepr.cruxworldgen.test2

import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

class BiomeVolumeField(
  private val registry: BiomeRegistry,
  private val seed: Long,
  private val cellScale: Double = 96.0,
  private val blendCount: Int = 3
) {

  fun weights(sx: Int, sy: Int, sz: Int): Map<VolumetricBiome, Double> {

    val candidates = mutableListOf<Pair<VolumetricBiome, Double>>()

    for (biome in registry.biomes) {

      val dist = worleyDistance3D(sx, sy, sz, biome)
      val habitat = biome.habitatWeight(sx, sy, sz)

      if (habitat <= 0.0) continue

      val score = habitat / (1.0 + dist)

      candidates += biome to score
    }

    if (candidates.isEmpty()) return emptyMap()

    val top = candidates
      .sortedByDescending { it.second }
      .take(blendCount)

    val total = top.sumOf { it.second }

    return top.associate { it.first to it.second / total }
  }

  private fun worleyDistance3D(
    sx: Int,
    sy: Int,
    sz: Int,
    biome: VolumetricBiome
  ): Double {

    val fx = sx / cellScale
    val fy = sy / cellScale
    val fz = sz / cellScale

    val cellX = floor(fx).toInt()
    val cellY = floor(fy).toInt()
    val cellZ = floor(fz).toInt()

    var minDist = Double.MAX_VALUE

    for (dx in -1..1) {
      for (dy in -1..1) {
        for (dz in -1..1) {

          val px = cellX + dx
          val py = cellY + dy
          val pz = cellZ + dz

          val jitter = seededVector(px, py, pz, biome)

          val cx = px + jitter.first
          val cy = py + jitter.second
          val cz = pz + jitter.third

          val dist = squaredDistance(fx, fy, fz, cx, cy, cz)

          if (dist < minDist) {
            minDist = dist
          }
        }
      }
    }

    return sqrt(minDist)
  }

  private fun seededVector(
    x: Int,
    y: Int,
    z: Int,
    biome: VolumetricBiome
  ): Triple<Double, Double, Double> {

    val mix = seed xor
      (x * 73428767L) xor
      (y * 912931L) xor
      (z * 123781L) xor
      biome.hashCode().toLong()

    val rnd = Random(mix)

    return Triple(
      rnd.nextDouble(),
      rnd.nextDouble(),
      rnd.nextDouble()
    )
  }

  private fun squaredDistance(
    x1: Double, y1: Double, z1: Double,
    x2: Double, y2: Double, z2: Double
  ): Double {
    val dx = x1 - x2
    val dy = y1 - y2
    val dz = z1 - z2
    return dx * dx + dy * dy + dz * dz
  }
}
