package killercreepr.cruxworldgen.test2

import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.collections.flatten
import kotlin.math.*

/**
 * Reworked CruxChunkGenerator:
 * - true 3D volumetric biome weights via BiomeVolumeField (Worley-like)
 * - consistent Triple<Int,Int,Int> keys for section cells / caches
 * - keeps family map (2D) for optional family bias
 * - removes mixed Pair/Triple bugs and broken 2D merge code
 *
 * Assumes:
 * - biomeRegistry.biomeSpecs: Map<*, List<BiomeSpec>> (so we flatten values)
 * - biomeRegistry.specOf(biome: VolumetricBiome): BiomeSpec? exists
 * - BiomeSpec contains verticalRole or similar
 * - VolumetricBiome has density(...) and getBlockBlended(...) methods as before
 */
class CruxChunkGenerator(
  private val biomeRegistry: BiomeRegistry
) : ChunkGenerator() {

  // Tunables
  private val sectionSizeX = 16
  private val sectionSizeZ = 16
  private val sectionSizeY = 16
  private val familyScale = 128.0
  private val familySmoothIters = 3
  private val familyPaddingSections = 3
  private val familyBiasMultiplier = 1.0   // if you want family to bias weights
  private val radiusSections = 1
  private val DEBUG_FAMILY_RENDER = false
  private val DEBUG_CLAIM_RENDER = false

  // LRU cache for weights per section triple
  private val weightsCache = object : LinkedHashMap<Triple<Int, Int, Int>, Map<VolumetricBiome, Double>>(512, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Triple<Int, Int, Int>, Map<VolumetricBiome, Double>>?): Boolean {
      return size > 4096
    }
  }

  // family map cache per chunk (chunkX,chunkZ) -> map(sectionX,sectionZ -> Family)
  private val familyMapCache = mutableMapOf<Pair<Int,Int>, MutableMap<Pair<Int,Int>, Family>>()

  // --- public chunk generation entry
  override fun generateNoise(
    worldInfo: org.bukkit.generator.WorldInfo,
    random: Random,
    chunkX: Int,
    chunkZ: Int,
    chunkData: ChunkData
  ) {
    val minY = worldInfo.minHeight
    val maxY = worldInfo.maxHeight
    val seed = worldInfo.seed

    val heightmap = IntArray(16 * 16) { Int.MIN_VALUE }
    val topBlock = Array(16 * 16) { Material.AIR }

    // volumetric biome field (responsible for weights per section triple)
    val biomeField = BiomeVolumeField(biomeRegistry, seed)

    // 1) Family map for this chunk (2D) and smoothing (optional)
    val familyMap = familyMapCache.getOrPut(Pair(chunkX, chunkZ)) {
      val m = generateFamilyMapForChunk(chunkX, chunkZ, sectionSizeX, seed, scale = familyScale, paddingSections = familyPaddingSections)
      smoothFamilyMapWithAdjacency(m, iterations = familySmoothIters)
      m
    }

    // 2) Main voxel fill loop using volumetric weights with vertical interpolation
    for (x in 0 until 16) {
      for (z in 0 until 16) {
        val worldX = chunkX * 16 + x
        val worldZ = chunkZ * 16 + z
        val heightMapKey = x + z * 16

        val sxCol = floor(worldX.toDouble() / sectionSizeX.toDouble()).toInt()
        val szCol = floor(worldZ.toDouble() / sectionSizeZ.toDouble()).toInt()

        val startSectionY = floor(minY.toDouble() / sectionSizeY.toDouble()).toInt()
        val endSectionY = floor((maxY - 1).toDouble() / sectionSizeY.toDouble()).toInt()

        for (sy in startSectionY..endSectionY) {
          val sectionYMin = sy * sectionSizeY
          val sectionYMaxExclusive = sectionYMin + sectionSizeY
          val yStart = max(minY, sectionYMin)
          val yEnd = min(maxY, sectionYMaxExclusive)

          // fetch weights at this section and the next (for vertical blending)
          val weightsA = biomeField.weightsCached(sxCol, sy, szCol)
          val weightsB = biomeField.weightsCached(sxCol, sy + 1, szCol)

          if (weightsA.isEmpty() && weightsB.isEmpty()) continue

          for (y in yStart until yEnd) {
            val t = (y - sectionYMin).toDouble() / sectionSizeY.toDouble()
            val weights = lerpAndNormalizeWeights(weightsA, weightsB, t)
            if (weights.isEmpty()) continue

            // pick primary + secondary for blended block selection
            val sorted = weights.entries.sortedByDescending { it.value }
            val primary = sorted[0].key
            val secondary = if (sorted.size > 1) sorted[1].key else primary
            val twoSum = (sorted.getOrNull(0)?.value ?: 0.0) + (sorted.getOrNull(1)?.value ?: 0.0)
            val blendT = if (twoSum > 0.0) (sorted.getOrNull(1)?.value ?: 0.0) / twoSum else 0.0

            // compute density from weighted sum of biome densities
            var density = 0.0
            for ((biome, w) in weights) {
              density += w * biome.density(worldX, y, worldZ)
            }

            val block = primary.getBlockBlended(worldX, y, worldZ, density, secondary, blendT)
            if (block != Material.AIR) {
              chunkData.setBlock(x, y, z, block)
              if (y > heightmap[heightMapKey]) {
                heightmap[heightMapKey] = y
                topBlock[heightMapKey] = block
              }
            }
          }
        }
      }
    }

    // Debug renders
    if (DEBUG_FAMILY_RENDER) renderFamilyDebug(chunkX, chunkZ, familyMap, chunkData, worldInfo)
    // claim debug removed (we no longer have 2D claims)

    // cleanup family cache for this chunk to bound memory (optional)
    familyMapCache.remove(Pair(chunkX, chunkZ))
  }

  // ---------- helper: blend+normalize maps ----------
  private fun lerpAndNormalizeWeights(
    a: Map<VolumetricBiome, Double>,
    b: Map<VolumetricBiome, Double>,
    t: Double
  ): Map<VolumetricBiome, Double> {
    if (a.isEmpty()) return b
    if (b.isEmpty()) return a
    val out = mutableMapOf<VolumetricBiome, Double>()
    val keys = HashSet<VolumetricBiome>()
    keys.addAll(a.keys)
    keys.addAll(b.keys)
    var total = 0.0
    for (k in keys) {
      val va = a[k] ?: 0.0
      val vb = b[k] ?: 0.0
      val v = va * (1.0 - t) + vb * t
      if (v > 0.0) {
        out[k] = v
        total += v
      }
    }
    if (total <= 0.0) return emptyMap()
    return out.mapValues { it.value / total }
  }

  // ---------- Family map (kept as 2D) ----------
  fun generateFamilyMapForChunk(
    chunkX: Int, chunkZ: Int,
    sectionSize: Int,
    seed: Long,
    scale: Double = 64.0,
    paddingSections: Int = 2
  ): MutableMap<Pair<Int,Int>, Family> {
    val map = mutableMapOf<Pair<Int,Int>, Family>()
    val chunkWorldX = chunkX * 16
    val chunkWorldZ = chunkZ * 16
    val secX0 = floor((chunkWorldX - paddingSections * sectionSize).toDouble() / sectionSize).toInt()
    val secZ0 = floor((chunkWorldZ - paddingSections * sectionSize).toDouble() / sectionSize).toInt()
    val secX1 = floor((chunkWorldX + 16 + paddingSections * sectionSize - 1).toDouble() / sectionSize).toInt()
    val secZ1 = floor((chunkWorldZ + 16 + paddingSections * sectionSize - 1).toDouble() / sectionSize).toInt()

    for (sx in secX0..secX1) {
      for (sz in secZ0..secZ1) {
        val noise = valueNoise2D(sx.toDouble(), sz.toDouble(), seed, scale)
        val family = when {
          noise < 0.18 -> Family.OCEAN
          noise < 0.45 -> Family.PLAINS
          noise < 0.65 -> Family.VOLCANIC
          noise < 0.85 -> Family.CAVE
          else -> Family.SKY
        }
        map[Pair(sx, sz)] = family
      }
    }
    return map
  }

  fun smoothFamilyMapWithAdjacency(map: MutableMap<Pair<Int,Int>, Family>, iterations: Int = 2) {
    if (iterations <= 0) return
    val allowed = mapOf(
      Family.OCEAN to setOf(Family.OCEAN, Family.PLAINS),
      Family.PLAINS to setOf(Family.PLAINS, Family.OCEAN, Family.VOLCANIC),
      Family.VOLCANIC to setOf(Family.VOLCANIC, Family.PLAINS),
      Family.CAVE to setOf(Family.CAVE, Family.PLAINS),
      Family.SKY to setOf(Family.SKY, Family.PLAINS)
    )
    val neighbors = listOf(Pair(-1,0), Pair(1,0), Pair(0,-1), Pair(0,1), Pair(-1,-1), Pair(1,1), Pair(-1,1), Pair(1,-1))
    var copy = map.toMutableMap()
    repeat(iterations) {
      val next = copy.toMutableMap()
      for ((key, _) in copy) {
        val (sx, sz) = key
        val counts = mutableMapOf<Family, Int>()
        for (d in neighbors) {
          val n = Pair(sx + d.first, sz + d.second)
          val f = copy[n] ?: continue
          counts[f] = (counts[f] ?: 0) + 1
        }
        val self = copy[key]!!
        counts[self] = (counts[self] ?: 0) + 1
        val allowedCounts = counts.filterKeys { allowed[self]?.contains(it) ?: true }
        val majority = if (allowedCounts.isNotEmpty()) allowedCounts.maxByOrNull { it.value }?.key else counts.maxByOrNull { it.value }?.key
        next[key] = majority ?: self
      }
      copy = next
    }
    map.clear()
    map.putAll(copy)
  }

  // ---------- Noise utilities ----------
  // Deterministic seeded double using Random(mix)
  fun seededDouble(x: Int, z: Int, seed: Long): Double {
    val mix = seed xor (x.toLong() * 73856093L) xor (z.toLong() * 19349663L)
    return Random(mix).nextDouble()
  }

  // Value noise at fractional coords (2D)
  fun valueNoise2D(sx: Double, sz: Double, seed: Long, scale: Double): Double {
    val fx = sx / scale
    val fz = sz / scale
    val x0 = floor(fx).toInt()
    val z0 = floor(fz).toInt()
    val tx = fx - x0
    val tz = fz - z0

    val v00 = seededDouble(x0, z0, seed)
    val v10 = seededDouble(x0 + 1, z0, seed)
    val v01 = seededDouble(x0, z0 + 1, seed)
    val v11 = seededDouble(x0 + 1, z0 + 1, seed)

    fun smooth(t: Double) = t * t * (3 - 2 * t)
    val sxT = smooth(tx)
    val szT = smooth(tz)

    val ix0 = v00 * (1 - sxT) + v10 * sxT
    val ix1 = v01 * (1 - sxT) + v11 * sxT
    return ix0 * (1 - szT) + ix1 * szT
  }

  // ---------- Debug renderers ----------
  private fun renderFamilyDebug(chunkX: Int, chunkZ: Int, familyMap: MutableMap<Pair<Int,Int>, Family>, chunkData: ChunkData, worldInfo: org.bukkit.generator.WorldInfo) {
    val familyToMaterial = mapOf(
      Family.OCEAN to Material.BLUE_WOOL,
      Family.PLAINS to Material.GREEN_WOOL,
      Family.VOLCANIC to Material.RED_WOOL,
      Family.CAVE to Material.GRAY_WOOL,
      Family.SKY to Material.LIGHT_BLUE_WOOL
    )
    for (sx in (chunkX * 16 / sectionSizeX) .. (chunkX * 16 / sectionSizeX + 1)) {
      for (sz in (chunkZ * 16 / sectionSizeZ) .. (chunkZ * 16 / sectionSizeZ + 1)) {
        val family = familyMap[Pair(sx, sz)] ?: continue
        val mat = familyToMaterial[family] ?: Material.WHITE_WOOL
        val worldX = sx * sectionSizeX + sectionSizeX / 2
        val worldZ = sz * sectionSizeZ + sectionSizeZ / 2
        val localX = worldX - chunkX * 16
        val localZ = worldZ - chunkZ * 16
        if (localX in 0..15 && localZ in 0..15) {
          val y = min(worldInfo.maxHeight - 1, worldInfo.minHeight + sectionSizeY * 2)
          chunkData.setBlock(localX, y, localZ, mat)
        }
      }
    }
  }

  // ---------- small helpers ----------
  private fun smoothStep(min: Double, max: Double, v: Double): Double {
    if (v <= min) return 0.0
    if (v >= max) return 1.0
    val t = (v - min) / (max - min)
    return t * t * (3.0 - 2.0 * t)
  }

  private fun verticalRoleWeight(role: VerticalRole, sy: Int): Double {
    // sy is a section index. tune these bands to your world.
    return when (role) {
      VerticalRole.SURFACE -> smoothStep(-2.0, 6.0, sy.toDouble())
      VerticalRole.SUBSURFACE -> smoothStep(-8.0, 2.0, sy.toDouble())
      VerticalRole.CAVE -> smoothStep(-12.0, 1.0, sy.toDouble())
      VerticalRole.SKY -> smoothStep(4.0, 14.0, sy.toDouble())
    }
  }

  // ---------- Inner volumetric field (Worley-like) ----------
  private inner class BiomeVolumeField(
    private val registry: BiomeRegistry,
    private val seed: Long,
    private val cellScale: Double = 24.0,      // larger -> larger biome cells
    private val blendCount: Int = 3            // how many top biomes to blend
  ) {
    // use outer weightsCache for LRU caching
    fun weightsCached(sx: Int, sy: Int, sz: Int): Map<VolumetricBiome, Double> {
      val key = Triple(sx, sy, sz)
      synchronized(weightsCache) {
        weightsCache[key]?.let { return it }
      }
      val w = computeWeights(sx, sy, sz)
      synchronized(weightsCache) { weightsCache[key] = w }
      return w
    }

    private fun computeWeights(sx: Int, sy: Int, sz: Int): Map<VolumetricBiome, Double> {
      // gather candidate biomes from registry
      val specs = registry.biomeSpecs.values//.flatten()
      val candidates = mutableListOf<Pair<VolumetricBiome, Double>>()

      val fx = sx.toDouble() / cellScale
      val fy = sy.toDouble() / cellScale
      val fz = sz.toDouble() / cellScale

      for (spec in specs) {
        val biome = spec.biome
        // habitat bias from spec vertical role (if present)
        val habitat = spec.verticalRole.let { verticalRoleWeight(it, sy) } ?: 1.0
        if (habitat <= 0.0) continue

        // compute Worley-like distance to this biome's anchored point(s)
        val dist = worleyDistance3D(fx, fy, fz, biome, seed)
        // score: bias by habitat and inverse distance
        val score = habitat / (1.0 + dist)
        if (score > 0.0) candidates.add(biome to score)
      }

      if (candidates.isEmpty()) return emptyMap()

      val top = candidates.sortedByDescending { it.second }.take(blendCount)
      val total = top.sumOf { it.second }
      if (total <= 0.0) return emptyMap()
      return top.associate { it.first to (it.second / total) }
    }

    // Worley-like: look at neighboring lattice cells with per-cell jitter seeded by biome + coords
    private fun worleyDistance3D(
      fx: Double, fy: Double, fz: Double,
      biome: VolumetricBiome,
      seed: Long
    ): Double {
      val cellX = floor(fx).toInt()
      val cellY = floor(fy).toInt()
      val cellZ = floor(fz).toInt()
      var best = Double.MAX_VALUE

      for (dx in -1..1) {
        for (dy in -1..1) {
          for (dz in -1..1) {
            val px = cellX + dx
            val py = cellY + dy
            val pz = cellZ + dz
            val jitter = seededJitter(px, py, pz, biome, seed)
            val cx = px + jitter.first
            val cy = py + jitter.second
            val cz = pz + jitter.third
            val d2 = (fx - cx).let { it * it } + (fy - cy).let { it * it } + (fz - cz).let { it * it }
            if (d2 < best) best = d2
          }
        }
      }
      return sqrt(best)
    }

    private fun seededJitter(x: Int, y: Int, z: Int, biome: VolumetricBiome, seed: Long): Triple<Double, Double, Double> {
      // combine coords + biome into a deterministic seed
      val mix = seed xor (x.toLong() * 73428767L) xor (y.toLong() * 912931L) xor (z.toLong() * 123781L) xor biome.hashCode().toLong()
      val rnd = Random(mix)
      return Triple(rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble())
    }
  }

  // SectionCell model (kept for future use if required)
  data class SectionCell(
    val sx: Int,
    val sy: Int,
    val sz: Int,
    val family: Family,
    var claimed: VolumetricBiome? = null
  )
}
