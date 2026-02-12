package killercreepr.cruxworldgen.test2

import org.bukkit.Material
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import java.util.PriorityQueue
import java.util.Random
import kotlin.collections.iterator
import kotlin.compareTo
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class CruxChunkGenerator(
  val biomeRegistry: BiomeRegistry
) : ChunkGenerator() {

  // Tunables
  private val sectionSizeX = 16
  private val sectionSizeZ = 16
  private val sectionSizeY = 16
  private val familyScale = 128.0
  private val familySmoothIters = 3
  private val familyPaddingSections = 3
  private val biasFactorMismatch = 0.0
  private val radiusSections = 1
  private val DEBUG_FAMILY_RENDER = false
  private val DEBUG_CLAIM_RENDER = false

  // LRU cache for weights per section triple
  private val weightsCache =
    object : LinkedHashMap<Triple<Int, Int, Int>, Map<VolumetricBiome, Double>>(512, 0.75f, true) {
      override fun removeEldestEntry(
        eldest: MutableMap.MutableEntry<Triple<Int, Int, Int>, Map<VolumetricBiome, Double>>?
      ): Boolean {
        return size > 2048
      }
    }

  // family map cache per chunk (chunkX,chunkZ) -> map(sectionX,sectionZ -> Family)
  private val familyMapCache = mutableMapOf<Pair<Int, Int>, MutableMap<Pair<Int, Int>, Family>>()

  // section claims cache per chunk (chunkX,chunkZ) -> map(sectionX,sectionZ -> claimed biome or null)
  private val claimMapCache = mutableMapOf<Pair<Int, Int>, MutableMap<Pair<Int, Int>, VolumetricBiome?>>()

  override fun generateNoise(
    worldInfo: WorldInfo,
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

    // 1) Family map for this chunk (2D) and smoothing
    val familyMap = familyMapCache.getOrPut(Pair(chunkX, chunkZ)) {
      val m = generateFamilyMapForChunk(
        chunkX,
        chunkZ,
        sectionSizeX,
        seed,
        scale = familyScale,
        paddingSections = familyPaddingSections
      )
      smoothFamilyMapWithAdjacency(m, iterations = familySmoothIters)
      m
    }

    // 2) Build section cells for chunk +/- padding and run seeded growth if not cached
    val claimKey = Pair(chunkX, chunkZ)
    val sectionClaims = claimMapCache.getOrPut(claimKey) {

      // Build cells
      val (cells, bounds) =
        buildSectionCellsForChunk(chunkX, chunkZ, sectionSizeX, familyPaddingSections, familyMap)
      val secX0 = bounds[0]
      val secX1 = bounds[1]
      val secZ0 = bounds[2]
      val secZ1 = bounds[3]

      // For each biome spec, place seeds and grow regions (2D growth per horizontal section)
      val specs = biomeRegistry.biomeSpecs.values.sortedByDescending { it.scale.ordinal }
      // If you have more biomes, iterate biomeRegistry.specs map instead

      // Prepare min size map for merging later
      val minSizeByBiome = mutableMapOf<VolumetricBiome, Int>()
      for (spec in specs) {
        minSizeByBiome[spec.biome] = spec.minSections

        // place seeds deterministically
        val spacing = max(1, spec.preferredRadius * 2)
        val seeds = placeSeedsForBiome(
          spec,
          familyMap,
          secX0,
          secX1,
          secZ0,
          secZ1,
          seed,
          spacing
        )

        val target = spec.minSections.coerceAtLeast(1)

        // grow region but only claim cells whose family matches and vertical role allowed
        // For 3D extension: only claim horizontal cells for sy ranges that match vertical role.
        // Here we operate on 2D horizontal grid; vertical role filtering will be applied later when computing per-section weights.
        growRegionFromSeeds(seeds, cells, spec, seed, target)
      }

      // Merge tiny regions by minSections
      mergeSmall2DRegions(cells, minSizeByBiome)

      // Convert cells to a simple claim map (sectionX,sectionZ -> claimed biome or null)
      val claimMap = mutableMapOf<Pair<Int, Int>, VolumetricBiome?>()
      for ((coord, cell) in cells) {
        claimMap[coord] = cell.claimed
      }
      claimMap
    }

    // 3) Build a per-section weight map from claims for quick lookup in computeBiomeWeights3DForSection
    val sectionWeightMap = buildSectionWeightMapFromClaims(sectionClaims)

    // 4) Main voxel fill loop using section-level claims and 3D weights with vertical interpolation
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

          // compute weights for this section and next (vertical interpolation)
          val weightsA = computeBiomeWeights3DForSectionWithClaims(
            sxCol,
            sy,
            szCol,
            radiusSections,
            biomeRegistry,
            seed,
            familyMap,
            sectionWeightMap
          )
          val weightsB = computeBiomeWeights3DForSectionWithClaims(
            sxCol,
            sy + 1,
            szCol,
            radiusSections,
            biomeRegistry,
            seed,
            familyMap,
            sectionWeightMap
          )

          if (weightsA.isEmpty() && weightsB.isEmpty()) continue

          for (y in yStart until yEnd) {
            val t = (y - sectionYMin).toDouble() / sectionSizeY.toDouble()
            val weights = lerpAndNormalizeWeights(weightsA, weightsB, t)
            if (weights.isEmpty()) continue

            val sorted = weights.entries.sortedByDescending { it.value }
            val primary = sorted[0].key
            val secondary = if (sorted.size > 1) sorted[1].key else primary
            val twoSum =
              (sorted.getOrNull(0)?.value ?: 0.0) + (sorted.getOrNull(1)?.value ?: 0.0)
            val blendT =
              if (twoSum > 0.0) (sorted.getOrNull(1)?.value ?: 0.0) / twoSum else 0.0

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
    if (DEBUG_CLAIM_RENDER) renderClaimDebug(chunkX, chunkZ, sectionClaims, chunkData, worldInfo)

    // cleanup caches for this chunk to bound memory
    familyMapCache.remove(Pair(chunkX, chunkZ))
    claimMapCache.remove(Pair(chunkX, chunkZ))
  }

  // Compute weights for a section triple (sx,sy,sz) using claims first, then fallback to registry sampling
  private fun computeBiomeWeights3DForSectionWithClaims(
    sx: Int,
    sy: Int,
    sz: Int,
    radiusSections: Int,
    biomeRegistry: BiomeRegistry,
    worldSeed: Long,
    familyMap: Map<Pair<Int, Int>, Family>,
    sectionWeightMap: Map<Pair<Int, Int>, Map<VolumetricBiome, Double>>
  ): Map<VolumetricBiome, Double> {
    val key = Triple(sx, sy, sz)
    synchronized(weightsCache) {
      weightsCache[key]?.let { return it }
    }

    // 1) If we have a claim-based weight map for this horizontal section, use it but enforce vertical role
    val claimWeights = sectionWeightMap[Pair(sx, sz)]
    val allowedByRole = isSectionAllowedByRole(sx, sy, sz, biomeRegistry)
    val finalWeights = mutableMapOf<VolumetricBiome, Double>()

    if (claimWeights != null && claimWeights.isNotEmpty()) {
      for ((b, w) in claimWeights) {
        val spec = biomeRegistry.specOf(b)
        if (spec != null) {
          if (!isRoleCompatible(spec.verticalRole, sy)) continue
        }
        finalWeights[b] = (finalWeights[b] ?: 0.0) + w
      }
    } else {
      // 2) fallback: raw weights from registry (section indices)
      val rawWeights = biomeRegistry.getWeightsFromCells(sx, sy, sz, radiusSections, worldSeed = worldSeed)

      // bias by family
      val sectionFamily = familyMap[Pair(sx, sz)] ?: Family.PLAINS
      var total = 0.0
      for ((biome, w) in rawWeights) {
        val f = biomeRegistry.familyOf(biome)
        val bw = if (f == sectionFamily) w * 1.0 else w * biasFactorMismatch
        val spec = biomeRegistry.specOf(biome)
        if (spec != null && !isRoleCompatible(spec.verticalRole, sy)) continue
        if (bw > 0.0) {
          finalWeights[biome] = (finalWeights[biome] ?: 0.0) + bw
          total += bw
        }
      }
      if (total <= 0.0) {
        synchronized(weightsCache) { weightsCache[key] = emptyMap() }
        return emptyMap()
      }

      // normalize
      val normalized = finalWeights.mapValues { it.value / finalWeights.values.sum() }
      synchronized(weightsCache) { weightsCache[key] = normalized }
      return normalized
    }

    if (finalWeights.isEmpty()) {
      synchronized(weightsCache) { weightsCache[key] = emptyMap() }
      return emptyMap()
    }

    val sum = finalWeights.values.sum()
    val normalized = finalWeights.mapValues { it.value / sum }
    synchronized(weightsCache) { weightsCache[key] = normalized }
    return normalized
  }

  // Linear interpolate two weight maps and renormalize
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

  // Build section cells for chunk +/- padding
  private fun buildSectionCellsForChunk(
    chunkX: Int,
    chunkZ: Int,
    sectionSize: Int,
    padding: Int,
    familyMap: Map<Pair<Int, Int>, Family>
  ): Pair<MutableMap<Pair<Int, Int>, SectionCell>, IntArray> {
    val chunkWorldX = chunkX * 16
    val chunkWorldZ = chunkZ * 16
    val secX0 = floor((chunkWorldX - padding * sectionSize).toDouble() / sectionSize).toInt()
    val secZ0 = floor((chunkWorldZ - padding * sectionSize).toDouble() / sectionSize).toInt()
    val secX1 = floor((chunkWorldX + 16 + padding * sectionSize - 1).toDouble() / sectionSize).toInt()
    val secZ1 = floor((chunkWorldZ + 16 + padding * sectionSize - 1).toDouble() / sectionSize).toInt()

    val cells = mutableMapOf<Pair<Int, Int>, SectionCell>()
    for (sx in secX0..secX1) {
      for (sz in secZ0..secZ1) {
        val fam = familyMap[Pair(sx, sz)] ?: Family.PLAINS
        cells[Pair(sx, sz)] = SectionCell(sx, sz, fam, null)
      }
    }

    return Pair(cells, intArrayOf(secX0, secX1, secZ0, secZ1))
  }

  // Seed placement: jittered grid seeds (deterministic)
  private fun placeSeedsForBiome(
    biomeSpec: BiomeSpec,
    familyMap: Map<Pair<Int, Int>, Family>,
    secX0: Int,
    secX1: Int,
    secZ0: Int,
    secZ1: Int,
    worldSeed: Long,
    spacing: Int
  ): List<Pair<Int, Int>> {
    val seeds = mutableListOf<Pair<Int, Int>>()
    val mix = worldSeed xor biomeSpec.biome.hashCode().toLong()
    val rnd = Random(mix)
    val step = max(1, spacing)

    for (sx in secX0..secX1 step step) {
      for (sz in secZ0..secZ1 step step) {
        val family = familyMap[Pair(sx, sz)] ?: continue
        // optionally restrict seeds to family-compatible cells
        // Here we allow seeds anywhere in familyMap; refine if needed
        if (rnd.nextDouble() < 0.6) {
          val jitterX = rnd.nextInt(step)
          val jitterZ = rnd.nextInt(step)
          seeds.add(Pair(sx + jitterX, sz + jitterZ))
        }
      }
    }
    return seeds
  }

  // Growth from seeds using priority queue (noise-biased)
  private fun growRegionFromSeeds(
    seeds: List<Pair<Int, Int>>,
    cells: MutableMap<Pair<Int, Int>, SectionCell>,
    biomeSpec: BiomeSpec,
    worldSeed: Long,
    targetSize: Int
  ) {
    if (seeds.isEmpty()) return

    val mix = worldSeed xor biomeSpec.biome.hashCode().toLong()
    val rnd = Random(mix)
    val pq = PriorityQueue<Pair<Double, Pair<Int, Int>>>(compareBy { it.first })
    for (s in seeds) pq.add(Pair(-rnd.nextDouble(), s))

    var claimed = 0
    val visited = mutableSetOf<Pair<Int, Int>>()
    val dirs = listOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))

    while (pq.isNotEmpty() && claimed < targetSize) {
      val (_, coord) = pq.poll()
      if (coord in visited) continue
      visited.add(coord)

      val cell = cells[coord] ?: continue
      if (cell.claimed != null) continue

      // claim only if family matches or allow mismatch based on policy
      // Here we allow claim regardless; you can restrict to family if desired
      cell.claimed = biomeSpec.biome
      claimed++

      for (d in dirs) {
        val n = Pair(cell.sx + d.first, cell.sz + d.second)
        val neighbor = cells[n] ?: continue
        if (neighbor.claimed != null) continue
        val noise = valueNoise2D(
          n.first.toDouble(),
          n.second.toDouble(),
          worldSeed xor biomeSpec.biome.hashCode().toLong(),
          128.0
        )
        val priority = -(noise + rnd.nextDouble() * 0.1)
        pq.add(Pair(priority, n))
      }
    }
  }

  // Merge small 2D regions (connected components) using minSizeByBiome
  private fun mergeSmall2DRegions(
    cells: MutableMap<Pair<Int, Int>, SectionCell>,
    minSizeByBiome: Map<VolumetricBiome, Int>
  ) {
    val visited = mutableSetOf<Pair<Int, Int>>()
    val dirs = listOf(Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))

    for (coord in cells.keys) {
      if (coord in visited) continue

      val startClaim = cells[coord]?.claimed
      val stack = ArrayDeque<Pair<Int, Int>>()
      val comp = mutableListOf<Pair<Int, Int>>()

      stack.add(coord)
      visited.add(coord)

      while (stack.isNotEmpty()) {
        val cur = stack.removeLast()
        comp.add(cur)
        for (d in dirs) {
          val n = Pair(cur.first + d.first, cur.second + d.second)
          if (n in visited) continue
          val c = cells[n] ?: continue
          if (c.claimed == startClaim) {
            visited.add(n)
            stack.add(n)
          }
        }
      }

      if (startClaim == null) continue

      val minSize = minSizeByBiome[startClaim] ?: 1
      if (comp.size < minSize) {
        val neighborCounts = mutableMapOf<VolumetricBiome, Int>()
        for (c in comp) {
          for (d in dirs) {
            val n = Pair(c.first + d.first, c.second + d.second)
            val nf = cells[n]?.claimed ?: continue
            if (nf != startClaim) neighborCounts[nf] = (neighborCounts[nf] ?: 0) + 1
          }
        }
        val target = neighborCounts.maxByOrNull { it.value }?.key
        for (c in comp) {
          cells[c]?.claimed = target
        }
      }
    }
  }

  // Convert claimed cells into per-section weight map (hard assignment or soft blend)
  private fun buildSectionWeightMapFromClaims(
    cells: Map<Pair<Int, Int>, VolumetricBiome?>
  ): Map<Pair<Int, Int>, Map<VolumetricBiome, Double>> {
    val out = mutableMapOf<Pair<Int, Int>, Map<VolumetricBiome, Double>>()
    val dirs = listOf(Pair(0, 0), Pair(1, 0), Pair(-1, 0), Pair(0, 1), Pair(0, -1))

    for ((coord, claim) in cells) {
      if (claim != null) {
        out[coord] = mapOf(claim to 1.0)
      } else {
        val counts = mutableMapOf<VolumetricBiome, Int>()
        for (d in dirs) {
          val n = Pair(coord.first + d.first, coord.second + d.second)
          val nf = cells[n] ?: continue
          if (nf != null) counts[nf] = (counts[nf] ?: 0) + 1
        }
        val total = counts.values.sum().takeIf { it > 0 } ?: 1
        val weights = counts.mapValues { it.value.toDouble() / total.toDouble() }
        out[coord] = if (weights.isEmpty()) emptyMap() else weights
      }
    }
    return out
  }

  // Helpers and utilities
  private fun isRoleCompatible(role: VerticalRole, sy: Int): Boolean {
    // Map sy to approximate vertical band. Tune thresholds to your world height.
    return when (role) {
      VerticalRole.SURFACE -> sy >= 0
      VerticalRole.SUBSURFACE -> sy in -4..2
      VerticalRole.CAVE -> sy <= 1
      VerticalRole.SKY -> sy >= 6
    }
  }

  private fun isSectionAllowedByRole(sx: Int, sy: Int, sz: Int, biomeRegistry: BiomeRegistry): Boolean {
    // Placeholder: you can implement more complex vertical masks here
    return true
  }

  // Family map generation and smoothing (2D)
  fun generateFamilyMapForChunk(
    chunkX: Int,
    chunkZ: Int,
    sectionSize: Int,
    seed: Long,
    scale: Double = 64.0,
    paddingSections: Int = 2
  ): MutableMap<Pair<Int, Int>, Family> {
    val map = mutableMapOf<Pair<Int, Int>, Family>()
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

  fun smoothFamilyMapWithAdjacency(map: MutableMap<Pair<Int, Int>, Family>, iterations: Int = 2) {
    if (iterations <= 0) return

    val allowed = mapOf(
      Family.OCEAN to setOf(Family.OCEAN, Family.PLAINS),
      Family.PLAINS to setOf(Family.PLAINS, Family.OCEAN, Family.VOLCANIC),
      Family.VOLCANIC to setOf(Family.VOLCANIC, Family.PLAINS),
      Family.CAVE to setOf(Family.CAVE, Family.PLAINS),
      Family.SKY to setOf(Family.SKY, Family.PLAINS)
    )

    val neighbors = listOf(
      Pair(-1, 0),
      Pair(1, 0),
      Pair(0, -1),
      Pair(0, 1),
      Pair(-1, -1),
      Pair(1, 1),
      Pair(-1, 1),
      Pair(1, -1)
    )

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
        val majority =
          if (allowedCounts.isNotEmpty()) allowedCounts.maxByOrNull { it.value }?.key
          else counts.maxByOrNull { it.value }?.key
        next[key] = majority ?: self
      }
      copy = next
    }
    map.clear()
    map.putAll(copy)
  }

  // Deterministic seeded double using Random(mix)
  fun seededDouble(x: Int, z: Int, seed: Long): Double {
    val mix = seed xor (x.toLong() * 73856093L) xor (z.toLong() * 19349663L)
    return Random(mix).nextDouble()
  }

  // Value noise at fractional coords
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

  // Debug renderers
  private fun renderFamilyDebug(
    chunkX: Int,
    chunkZ: Int,
    familyMap: MutableMap<Pair<Int, Int>, Family>,
    chunkData: ChunkData,
    worldInfo: WorldInfo
  ) {
    val familyToMaterial = mapOf(
      Family.OCEAN to Material.BLUE_WOOL,
      Family.PLAINS to Material.GREEN_WOOL,
      Family.VOLCANIC to Material.RED_WOOL,
      Family.CAVE to Material.GRAY_WOOL,
      Family.SKY to Material.LIGHT_BLUE_WOOL
    )
    for (sx in (chunkX * 16 / sectionSizeX)..(chunkX * 16 / sectionSizeX + 1)) {
      for (sz in (chunkZ * 16 / sectionSizeZ)..(chunkZ * 16 / sectionSizeZ + 1)) {
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

  private fun renderClaimDebug(
    chunkX: Int,
    chunkZ: Int,
    claimMap: Map<Pair<Int, Int>, VolumetricBiome?>,
    chunkData: ChunkData,
    worldInfo: WorldInfo
  ) {
    val mat = Material.YELLOW_WOOL
    for ((coord, _) in claimMap) {
      val sx = coord.first
      val sz = coord.second
      val worldX = sx * sectionSizeX + sectionSizeX / 2
      val worldZ = sz * sectionSizeZ + sectionSizeZ / 2
      val localX = worldX - chunkX * 16
      val localZ = worldZ - chunkZ * 16
      if (localX in 0..15 && localZ in 0..15) {
        val y = min(worldInfo.maxHeight - 2, worldInfo.minHeight + sectionSizeY * 2)
        chunkData.setBlock(localX, y, localZ, mat)
      }
    }
  }

  // SectionCell model
  data class SectionCell(
    var sx: Int,
    var sz: Int,
    var family: Family,
    var claimed: VolumetricBiome? = null
  )
}
