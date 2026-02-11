package killercreepr.cruxworldgen.test2

import killercreepr.cruxworldgen.test2.biome.BadlandsPlateauBiome
import killercreepr.cruxworldgen.test2.biome.CharredWastesBiome
import killercreepr.cruxworldgen.test2.biome.PlagueAbyssBiome
import kotlin.math.abs
import kotlin.math.exp
import kotlin.reflect.KClass

// Add to BiomeRegistry.kt (or a new file)
enum class BiomeScale { TINY, SMALL, MEDIUM, LARGE, HUGE }
enum class VerticalRole { SURFACE, SUBSURFACE, CAVE, SKY }

data class BiomeSpec(
  val biome: VolumetricBiome,
  val scale: BiomeScale,
  val minSections: Int = 1,
  val maxSections: Int = Int.MAX_VALUE,
  val preferredRadius: Int = 0, // in sections
  val verticalRole: VerticalRole = VerticalRole.SURFACE
)

class BiomeRegistry(private val seed: Long) {

  val biomes = listOf<VolumetricBiome>(
    PlagueAbyssBiome(),
    CharredWastesBiome(),
    BadlandsPlateauBiome()
  )

  // New: specs per biome (tune values)
  val biomeSpecs: Map<KClass<out VolumetricBiome>, BiomeSpec> = mapOf(
    PlagueAbyssBiome::class to BiomeSpec(PlagueAbyssBiome(), BiomeScale.MEDIUM, minSections = 16, preferredRadius = 4, verticalRole = VerticalRole.SURFACE),
    CharredWastesBiome::class to BiomeSpec(CharredWastesBiome(), BiomeScale.LARGE, minSections = 64, preferredRadius = 12, verticalRole = VerticalRole.SURFACE),
    BadlandsPlateauBiome::class to BiomeSpec(BadlandsPlateauBiome(), BiomeScale.SMALL, minSections = 9, preferredRadius = 3, verticalRole = VerticalRole.SURFACE)
  )

  fun specOf(b: VolumetricBiome): BiomeSpec? {
    return biomeSpecs[b::class]
  }


  // Existing 3D cell-based deterministic selection (keeps your fromCell semantics)
  fun fromCell(cellX: Int, cellY: Int, cellZ: Int): VolumetricBiome {
    val hash = hash(cellX, cellY, cellZ)
    return biomes[(hash % biomes.size).toInt()]
  }
  //fun specOf(b: VolumetricBiome): BiomeSpec? = biomeSpecs[b]

  // New: 3D hash-based lookup including sectionY
  fun getBiomeByHash(sectionX: Int, sectionY: Int, sectionZ: Int, worldSeed: Long = 0L): VolumetricBiome {
    var h = sectionX * 374761393L + sectionY * 668265263L + sectionZ * 1274126177L + worldSeed
    h = (h xor (h shr 13)) * 1274126177L
    val biomeIndex = abs(h % biomes.size)
    return biomes[biomeIndex.toInt()]
  }

  // New: deterministic pseudo-noise in [0,1) from integer coords + seed
  private fun hashToDouble(x: Int, y: Int, z: Int, salt: Long = 0L): Double {
    var h = seed
    h = h * 31 + x
    h = h * 31 + y
    h = h * 31 + z
    h = h xor salt
    // mix
    h = (h xor (h shr 33)) * -0x00ae502812aa7733L
    h = (h xor (h shr 33)) * -0x39b26f1e35d7a9adL
    h = h xor (h shr 33)
    // map to [0,1)
    val unsigned = h ushr 1
    return (unsigned.toDouble() / Long.MAX_VALUE.toDouble()).coerceIn(0.0, 1.0)
  }

  // Optional: map a single noise value to a biome (useful for 3D noise approach)
  fun getBiomeFromNoise3D(x: Int, y: Int, z: Int, salt: Long = 0L): VolumetricBiome {
    val f = hashToDouble(x, y, z, salt)
    val index = (f * biomes.size).toInt().coerceIn(0, biomes.size - 1)
    return biomes[index]
  }

  // New: sample neighboring 3D cells and return normalized weights per biome
  // centerCellX/Y/Z are integer section indices (e.g., sectionX, sectionY, sectionZ)
  fun getWeightsFromCells(
    centerCellX: Int,
    centerCellY: Int,
    centerCellZ: Int,
    radiusCells: Int,
    sigma: Double = -1.0,
    worldSeed: Long = 0L
  ): Map<VolumetricBiome, Double> {
    val s = if (sigma > 0.0) sigma else maxOf(0.5, radiusCells.toDouble() / 2.0)
    val twoSigmaSq = 2.0 * s * s

    val accum = mutableMapOf<VolumetricBiome, Double>()
    var total = 0.0

    for (dx in -radiusCells..radiusCells) {
      for (dy in -radiusCells..radiusCells) {
        for (dz in -radiusCells..radiusCells) {
          val cx = centerCellX + dx
          val cy = centerCellY + dy
          val cz = centerCellZ + dz

          val nx = dx.toDouble()
          val ny = dy.toDouble()
          val nz = dz.toDouble()
          val distSq = nx * nx + ny * ny + nz * nz

          val weight = exp(-distSq / twoSigmaSq)
          if (weight <= 1e-8) continue

          // choose biome for that cell (deterministic)
          val biome = getBiomeByHash(cx, cy, cz, worldSeed)
          accum[biome] = (accum[biome] ?: 0.0) + weight
          total += weight
        }
      }
    }

    if (total <= 0.0) return emptyMap()
    return accum.mapValues { it.value / total }
  }

  // inside BiomeRegistry
  fun familyOf(biome: VolumetricBiome): Family {
    //todo ye
    return when (biome) {
      is PlagueAbyssBiome -> Family.PLAINS
      is CharredWastesBiome -> Family.VOLCANIC
      is BadlandsPlateauBiome -> Family.PLAINS
      // map other biomes accordingly
      else -> Family.PLAINS
    }
  }

  // Keep your original 2D helper for compatibility
  fun getBiomeByHash(sectionX: Int, sectionZ: Int, worldSeed: Long = 0L): VolumetricBiome {
    var hash = sectionX * 374761393 + sectionZ * 668265263 + worldSeed.toInt()
    hash = (hash xor (hash shr 13)) * 1274126177
    val biomeIndex = abs(hash) % biomes.size
    return biomes[biomeIndex]
  }

  fun getBiomeFromNoise(f1: Double): VolumetricBiome {
    val hash = ((f1 * 10_000).toLong() xor seed)
    val index = abs(hash % biomes.size).toInt()
    return biomes[index]
  }

  fun hash(x: Int, y: Int, z: Int): Long {
    var h = seed
    h = h * 31 + x
    h = h * 31 + y
    h = h * 31 + z
    return abs(h)
  }
}